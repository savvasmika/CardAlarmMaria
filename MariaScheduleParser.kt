package com.example.excel

import android.util.Log
import com.example.data.model.ShiftDay
import java.text.Normalizer
import java.util.Calendar

/**
 * Αναλυτής προγράμματος εργασίας αποκλειστικά για την εργαζόμενη «ΜΑΡΙΑ».
 *
 * Σχεδιασμένος για το πραγματικό layout του Excel:
 * - Φύλλα ανά μήνα (DEC, JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCTO κ.α.)
 * - Στήλη A: Ημερομηνίες για όλη τη γραμμή (π.χ. 28.09, 01.10, 05.10)
 * - Ενότητες εργαζομένων:
 *   ΔΕΣΠΟΙΝΑ (B, C, D) | ΜΑΡΙΑ (E, F, G) | ΜΥΡΤΩ (H, I, J) | ΝΕΚΤΑΡΙΑ (K, L, M)
 *   Στη στήλη E (ΜΑΡΙΑ): Ημέρα εβδομάδας (ΔΕΥ, ΤΡ, ΤΕΤ, ΠΕΜ, ΠΑΡ, ΣΑΒ, ΚΥΡ)
 *   Στη στήλη F (ΩΡΑΡΙΟ): Ωράριο (9-15, 12-20, 9-13/19-21, 10-18, ΡΕΠΟ, ΑΔΕΙΑ)
 *   Στη στήλη G (ΩΡΕΣ/ΗΜΕΡΑ): Ώρες (6, 8, κτλ.)
 * - Εβδομαδιαία μπλοκ: Κάθε εβδομάδα ξεκινά με επικεφαλίδες (ΜΑΡΙΑ | ΩΡΑΡΙΟ | ΩΡΕΣ/ΗΜΕΡΑ)
 *   και ακολουθούν 7 ημέρες και γραμμή συνόλου.
 */
class MariaScheduleParser {

    data class ShiftTiming(
        val isOff: Boolean,
        val offType: String? = null,
        val firstStartTime: String? = null, // "09:00"
        val lastEndTime: String? = null,   // "15:00" ή "21:00"
        val estimatedHours: Double = 0.0
    )

    data class ParseResult(
        val shifts: List<ShiftDay>,
        val processedSheets: List<String>,
        val mariaFoundInSheets: List<String>,
        val debugInfo: String
    )

    fun parseWorkbook(workbook: XlsxWorkbookParser.Workbook): ParseResult {
        val allShifts = mutableListOf<ShiftDay>()
        val processedSheets = mutableListOf<String>()
        val mariaFoundSheets = mutableListOf<String>()
        val debugLogs = StringBuilder()

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        for (sheet in workbook.sheets) {
            processedSheets.add(sheet.name)
            val sheetShifts = parseSheetForMaria(sheet, currentYear)
            if (sheetShifts.isNotEmpty()) {
                mariaFoundSheets.add(sheet.name)
                allShifts.addAll(sheetShifts)
                debugLogs.append("Φύλλο '${sheet.name}': Βρέθηκαν ${sheetShifts.size} ημέρες για τη Μαρία.\n")
            } else {
                debugLogs.append("Φύλλο '${sheet.name}': Δεν εντοπίστηκαν δεδομένα Μαρίας.\n")
            }
        }

        val sortedShifts = allShifts.sortedBy { it.dateKey }
        return ParseResult(
            shifts = sortedShifts,
            processedSheets = processedSheets,
            mariaFoundInSheets = mariaFoundSheets,
            debugInfo = debugLogs.toString()
        )
    }

    private fun parseSheetForMaria(sheet: XlsxWorkbookParser.Sheet, defaultYear: Int): List<ShiftDay> {
        val rows = sheet.rows
        if (rows.isEmpty()) return emptyList()

        // 1. Εντοπισμός όλων των γραμμών όπου εμφανίζεται επικεφαλίδα «ΜΑΡΙΑ»
        // Στο Excel της Μαρίας υπάρχει επικεφαλίδα ανά εβδομάδα (π.χ. γραμμή 1, γραμμή 10, γραμμή 19)
        val mariaHeaders = mutableListOf<Pair<Int, Int>>() // List of (rowIndex, colIndex)
        for ((rIdx, cols) in rows) {
            for ((cIdx, cellText) in cols) {
                if (isMariaHeader(cellText)) {
                    mariaHeaders.add(Pair(rIdx, cIdx))
                }
            }
        }

        if (mariaHeaders.isEmpty()) {
            return emptyList()
        }

        val shiftsFound = mutableListOf<ShiftDay>()
        val sheetYear = inferYearFromSheetOrCalendar(sheet.name, defaultYear)

        // 2. Ανάλυση κάθε εβδομαδιαίου μπλοκ κάτω από το αντίστοιχο «ΜΑΡΙΑ»
        for (i in mariaHeaders.indices) {
            val (mariaRow, mariaCol) = mariaHeaders[i]
            val nextHeaderRow = mariaHeaders.getOrNull(i + 1)?.first ?: (mariaRow + 20)

            val sectionShifts = parseWeeklyBlock(
                sheet = sheet,
                headerRow = mariaRow,
                mariaCol = mariaCol,
                maxRow = nextHeaderRow - 1,
                defaultYear = sheetYear
            )
            shiftsFound.addAll(sectionShifts)
        }

        return shiftsFound.distinctBy { it.dateKey }
    }

    /**
     * Αναλύει ένα μπλοκ ημερών για τη Μαρία.
     */
    private fun parseWeeklyBlock(
        sheet: XlsxWorkbookParser.Sheet,
        headerRow: Int,
        mariaCol: Int,
        maxRow: Int,
        defaultYear: Int
    ): List<ShiftDay> {
        val rows = sheet.rows
        val shifts = mutableListOf<ShiftDay>()

        val headerRowMap = rows[headerRow] ?: emptyMap()

        // 1. Εύρεση της στήλης ΩΡΑΡΙΟ της Μαρίας:
        // Συνήθως mariaCol + 1 (π.χ. Col F αν Μαρία είναι στο Col E)
        var scheduleCol = mariaCol + 1
        for (c in mariaCol..(mariaCol + 2)) {
            val txt = headerRowMap[c]?.uppercase() ?: ""
            if (txt.contains("ΩΡΑΡΙΟ") || txt.contains("SHIFT")) {
                scheduleCol = c
                break
            }
        }

        // 2. Εύρεση της στήλης ΩΡΕΣ/ΗΜΕΡΑ:
        var hoursCol = scheduleCol + 1
        for (c in scheduleCol..(scheduleCol + 2)) {
            val txt = headerRowMap[c]?.uppercase() ?: ""
            if (txt.contains("ΩΡΕΣ") || txt.contains("HOURS")) {
                hoursCol = c
                break
            }
        }

        // 3. Εύρεση της στήλης Ημέρας (ΔΕΥ, ΤΡ, ΤΕΤ):
        // Στο Excel της Μαρίας η ημέρα είναι ακριβώς στη στήλη mariaCol (Col E)
        val dayCol = mariaCol

        // 4. Εύρεση της στήλης Ημερομηνίας:
        // Στο Excel της Μαρίας όλες οι ημερομηνίες βρίσκονται στη Στήλη A (col 0)!
        // Ελέγχουμε αν η πρώτη γραμμή δεδομένων έχει ημερομηνία στη στήλη 0.
        var dateCol = 0
        val firstDataRow = rows[headerRow + 1] ?: emptyMap()
        if (!isDateString(firstDataRow[0] ?: "")) {
            // Αν δεν είναι στη στήλη 0, αναζήτηση στις στήλες [0..mariaCol]
            for (c in 0..mariaCol) {
                if (isDateString(firstDataRow[c] ?: "")) {
                    dateCol = c
                    break
                }
            }
        }

        var currentRow = headerRow + 1
        while (currentRow <= maxRow && currentRow < headerRow + 12) {
            val rowData = rows[currentRow]
            if (rowData == null || rowData.isEmpty()) {
                currentRow++
                continue
            }

            // Έλεγχος αν συναντήσαμε γραμμή συνόλου (π.χ. κενή ημερομηνία ή άλλη επικεφαλίδα)
            val rawDate = rowData[dateCol]?.trim()
            if (rawDate.isNullOrEmpty() || !isDateString(rawDate)) {
                // Ελέγχουμε αν υπάρχει ημερομηνία σε άλλη στήλη αριστερά της Μαρίας
                var foundOtherDate: String? = null
                for (c in 0 until mariaCol) {
                    val candidate = rowData[c]?.trim() ?: ""
                    if (isDateString(candidate)) {
                        foundOtherDate = candidate
                        break
                    }
                }
                if (foundOtherDate == null) {
                    // Γραμμή συνόλων ή κενή γραμμή
                    currentRow++
                    continue
                }
            }

            val finalDate = rawDate?.takeIf { isDateString(it) }
                ?: (0 until mariaCol).mapNotNull { rowData[it]?.trim() }.firstOrNull { isDateString(it) }

            if (finalDate == null) {
                currentRow++
                continue
            }

            val rawDay = rowData[dayCol]?.trim() ?: ""
            val rawSchedule = rowData[scheduleCol]?.trim() ?: ""
            val rawHours = rowData[hoursCol]?.trim()

            val parsedTiming = parseShiftTiming(rawSchedule)
            val dateInfo = normalizeDate(finalDate, sheet.name, defaultYear)

            if (dateInfo != null) {
                val parsedHoursNum = rawHours?.replace(',', '.')?.toDoubleOrNull()
                val finalHours = if (parsedHoursNum != null && parsedHoursNum > 0) {
                    parsedHoursNum
                } else {
                    parsedTiming.estimatedHours
                }

                val shift = ShiftDay(
                    dateKey = dateInfo.first, // "2026-09-28"
                    displayDate = dateInfo.second, // "28.09"
                    dayOfWeek = if (rawDay.isNotEmpty() && isDayOfWeekString(rawDay)) rawDay else dateInfo.third,
                    rawSchedule = rawSchedule.ifEmpty { if (parsedTiming.isOff) (parsedTiming.offType ?: "ΚΕΝΟ") else "" },
                    isOff = parsedTiming.isOff,
                    offType = parsedTiming.offType,
                    firstStartTime = parsedTiming.firstStartTime,
                    lastEndTime = parsedTiming.lastEndTime,
                    hoursWorked = finalHours,
                    monthKey = dateInfo.first.substring(0, 7), // "2026-09" ή "2026-10"
                    sheetName = sheet.name
                )
                shifts.add(shift)
            }

            currentRow++
        }

        return shifts
    }

    /**
     * Αναλύει το κείμενο του ωραρίου.
     * Παραδείγματα:
     * - "9-15" -> 09:00 - 15:00
     * - "12-20" -> 12:00 - 20:00
     * - "15-21" -> 15:00 - 21:00
     * - "9-13/19-21" -> πρώτη έναρξη 09:00, τελευταία λήξη 21:00
     * - "ΡΕΠΟ", "ΑΔΕΙΑ", "" -> isOff = true
     */
    fun parseShiftTiming(raw: String): ShiftTiming {
        val trimmed = raw.trim()
        val normalized = normalizeGreek(trimmed).uppercase()

        if (normalized.contains("ΡΕΠΟ") || normalized.contains("REPO")) {
            return ShiftTiming(isOff = true, offType = "ΡΕΠΟ")
        }
        if (normalized.contains("ΑΔΕΙΑ") || normalized.contains("ADEIA")) {
            return ShiftTiming(isOff = true, offType = "ΑΔΕΙΑ")
        }
        if (normalized == "OFF" || normalized == "ΚΕΝΟ" || normalized == "-" || normalized.isEmpty()) {
            return ShiftTiming(isOff = true, offType = "ΚΕΝΟ")
        }

        val parts = trimmed.split("/", ";", ",", "&")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var overallStart: String? = null
        var overallEnd: String? = null
        var totalHours = 0.0

        for (part in parts) {
            val range = parseSingleRange(part)
            if (range != null) {
                if (overallStart == null) {
                    overallStart = range.first
                }
                overallEnd = range.second
                totalHours += range.third
            }
        }

        return if (overallStart != null && overallEnd != null) {
            ShiftTiming(
                isOff = false,
                firstStartTime = overallStart,
                lastEndTime = overallEnd,
                estimatedHours = totalHours
            )
        } else {
            ShiftTiming(isOff = true, offType = "ΚΕΝΟ")
        }
    }

    private fun parseSingleRange(rangeStr: String): Triple<String, String, Double>? {
        val hyphenIdx = rangeStr.indexOf('-')
        if (hyphenIdx == -1) return null

        val startPart = rangeStr.substring(0, hyphenIdx).trim()
        val endPart = rangeStr.substring(hyphenIdx + 1).trim()

        val startTime = formatTime(startPart) ?: return null
        val endTime = formatTime(endPart) ?: return null

        val startHours = parseHourDecimal(startTime)
        val endHours = parseHourDecimal(endTime)
        val hours = if (endHours >= startHours) endHours - startHours else (24 - startHours) + endHours

        return Triple(startTime, endTime, hours)
    }

    private fun formatTime(timeStr: String): String? {
        val clean = timeStr.trim().replace('.', ':')
        if (clean.contains(':')) {
            val parts = clean.split(':')
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59) {
                return String.format("%02d:%02d", h, m)
            }
        } else {
            val h = clean.toIntOrNull() ?: return null
            if (h in 0..23) {
                return String.format("%02d:00", h)
            }
        }
        return null
    }

    private fun parseHourDecimal(formattedTime: String): Double {
        val parts = formattedTime.split(':')
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h + (m / 60.0)
    }

    private fun isMariaHeader(text: String): Boolean {
        val norm = normalizeGreek(text).uppercase().trim()
        return norm == "ΜΑΡΙΑ" || norm == "MARIA" || norm.startsWith("ΜΑΡΙΑ") || norm.startsWith("MARIA")
    }

    private fun isDateString(text: String): Boolean {
        val clean = text.trim()
        // "28.09", "28.9", "01.10", "1.10", "28/09", "28-09", "28.09.2026"
        val regex = Regex("""^\d{1,2}[./\-]\d{1,2}([./\-]\d{2,4})?$""")
        if (regex.matches(clean)) return true

        val num = clean.toIntOrNull()
        return num != null && num in 40000..55000
    }

    private fun isDayOfWeekString(text: String): Boolean {
        val norm = normalizeGreek(text).uppercase().trim()
        val days = listOf("ΔΕΥ", "ΤΡΙ", "ΤΕΤ", "ΠΕΜ", "ΠΑΡ", "ΣΑΒ", "ΚΥΡ", "ΔΕ", "ΤΡ", "ΤΕ", "ΠΕ", "ΠΑ", "ΣΑ", "ΚΥ", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        return days.any { norm == it || norm.startsWith(it) }
    }

    fun isScheduleString(text: String): Boolean {
        val norm = normalizeGreek(text).uppercase().trim()
        if (norm.contains("ΡΕΠΟ") || norm.contains("ΑΔΕΙΑ") || norm == "OFF" || norm == "ΚΕΝΟ" || norm == "REPO" || norm == "ADEIA") {
            return true
        }
        return norm.contains('-') && norm.any { it.isDigit() }
    }

    private fun normalizeDate(rawDate: String, sheetName: String, fallbackYear: Int): Triple<String, String, String>? {
        val clean = rawDate.trim()

        val serial = clean.toIntOrNull()
        if (serial != null && serial in 40000..55000) {
            val cal = Calendar.getInstance()
            cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            cal.add(Calendar.DAY_OF_YEAR, serial)
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val dateKey = String.format("%04d-%02d-%02d", y, m, d)
            val display = String.format("%02d.%02d", d, m)
            val dow = getGreekDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))
            return Triple(dateKey, display, dow)
        }

        val parts = clean.split('.', '/', '-')
        if (parts.size >= 2) {
            val d = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val y = if (parts.size >= 3 && parts[2].length in 2..4) {
                val parsedY = parts[2].toIntOrNull() ?: fallbackYear
                if (parsedY < 100) 2000 + parsedY else parsedY
            } else {
                fallbackYear
            }

            if (d in 1..31 && m in 1..12) {
                val dateKey = String.format("%04d-%02d-%02d", y, m, d)
                val display = String.format("%02d.%02d", d, m)

                val cal = Calendar.getInstance()
                cal.set(y, m - 1, d)
                val dow = getGreekDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))

                return Triple(dateKey, display, dow)
            }
        }
        return null
    }

    private fun inferYearFromSheetOrCalendar(sheetName: String, currentYear: Int): Int {
        val regex = Regex("""20\d{2}""")
        val match = regex.find(sheetName)
        if (match != null) {
            return match.value.toIntOrNull() ?: currentYear
        }
        return currentYear
    }

    private fun getGreekDayOfWeek(calendarDay: Int): String {
        return when (calendarDay) {
            Calendar.MONDAY -> "ΔΕΥ"
            Calendar.TUESDAY -> "ΤΡΙ"
            Calendar.WEDNESDAY -> "ΤΕΤ"
            Calendar.THURSDAY -> "ΠΕΜ"
            Calendar.FRIDAY -> "ΠΑΡ"
            Calendar.SATURDAY -> "ΣΑΒ"
            Calendar.SUNDAY -> "ΚΥΡ"
            else -> ""
        }
    }

    private fun normalizeGreek(text: String): String {
        val unaccented = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return unaccented
            .replace('Ά', 'Α').replace('ά', 'α')
            .replace('Έ', 'Ε').replace('έ', 'ε')
            .replace('Ή', 'Η').replace('ή', 'η')
            .replace('Ί', 'Ι').replace('ί', 'ι')
            .replace('Ό', 'Ο').replace('ό', 'ο')
            .replace('Ύ', 'Υ').replace('ύ', 'υ')
            .replace('Ώ', 'Ω').replace('ώ', 'ω')
    }
}
