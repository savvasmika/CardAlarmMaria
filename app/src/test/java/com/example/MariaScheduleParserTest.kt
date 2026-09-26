package com.example

import com.example.excel.MariaScheduleParser
import com.example.excel.SampleScheduleData
import com.example.excel.XlsxWorkbookParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MariaScheduleParserTest {

    private val parser = MariaScheduleParser()

    @Test
    fun testSingleShiftParsing() {
        val timing1 = parser.parseShiftTiming("9-15")
        assertFalse(timing1.isOff)
        assertEquals("09:00", timing1.firstStartTime)
        assertEquals("15:00", timing1.lastEndTime)
        assertEquals(6.0, timing1.estimatedHours, 0.01)

        val timing2 = parser.parseShiftTiming("12-20")
        assertFalse(timing2.isOff)
        assertEquals("12:00", timing2.firstStartTime)
        assertEquals("20:00", timing2.lastEndTime)
        assertEquals(8.0, timing2.estimatedHours, 0.01)

        val timing3 = parser.parseShiftTiming("15-21")
        assertFalse(timing3.isOff)
        assertEquals("15:00", timing3.firstStartTime)
        assertEquals("21:00", timing3.lastEndTime)
        assertEquals(6.0, timing3.estimatedHours, 0.01)

        val timing4 = parser.parseShiftTiming("10-18")
        assertFalse(timing4.isOff)
        assertEquals("10:00", timing4.firstStartTime)
        assertEquals("18:00", timing4.lastEndTime)
        assertEquals(8.0, timing4.estimatedHours, 0.01)
    }

    @Test
    fun testSplitShiftParsing() {
        val timing = parser.parseShiftTiming("9-13/19-21")
        assertFalse(timing.isOff)
        assertEquals("09:00", timing.firstStartTime)
        assertEquals("21:00", timing.lastEndTime)
        assertEquals(6.0, timing.estimatedHours, 0.01)

        val timingWithSpaces = parser.parseShiftTiming("09:00 - 13:00 / 19:00 - 21:00")
        assertFalse(timingWithSpaces.isOff)
        assertEquals("09:00", timingWithSpaces.firstStartTime)
        assertEquals("21:00", timingWithSpaces.lastEndTime)
    }

    @Test
    fun testRepoAndAdeiaParsing() {
        val repo = parser.parseShiftTiming("ΡΕΠΟ")
        assertTrue(repo.isOff)
        assertEquals("ΡΕΠΟ", repo.offType)
        assertNull(repo.firstStartTime)
        assertNull(repo.lastEndTime)

        val adeia = parser.parseShiftTiming("ΑΔΕΙΑ")
        assertTrue(adeia.isOff)
        assertEquals("ΑΔΕΙΑ", adeia.offType)
        assertNull(adeia.firstStartTime)
        assertNull(adeia.lastEndTime)

        val empty = parser.parseShiftTiming("")
        assertTrue(empty.isOff)
    }

    @Test
    fun testExactPhotoSpreadsheetStructure() {
        // Ακριβής προσομοίωση του Excel από τη φωτογραφία της οθόνης του χρήστη:
        // Φύλλο OCTO:
        // Col A (0): Ημερομηνία
        // Col B (1): ΔΕΣΠΟΙΝΑ, Col C (2): ΩΡΑΡΙΟ, Col D (3): ΩΡΕΣ/ΗΜΕΡΑ
        // Col E (4): ΜΑΡΙΑ, Col F (5): ΩΡΑΡΙΟ, Col G (6): ΩΡΕΣ/ΗΜΕΡΑ
        // Col H (7): ΜΥΡΤΩ, Col I (8): ΩΡΑΡΙΟ, Col J (9): ΩΡΕΣ/ΗΜΕΡΑ
        // Col K (10): ΝΕΚΤΑΡΙΑ, Col L (11): ΩΡΑΡΙΟ, Col M (12): ΩΡΕΣ/ΗΜΕΡΑ
        val rows = mapOf(
            // Row 1: Header εβδομάδας 1
            1 to mapOf(1 to "ΔΕΣΠΟΙΝΑ", 2 to "ΩΡΑΡΙΟ", 3 to "ΩΡΕΣ/ΗΜΕΡΑ", 4 to "ΜΑΡΙΑ", 5 to "ΩΡΑΡΙΟ", 6 to "ΩΡΕΣ/ΗΜΕΡΑ", 7 to "ΜΥΡΤΩ", 8 to "ΩΡΑΡΙΟ", 9 to "ΩΡΕΣ/ΗΜΕΡΑ", 10 to "ΝΕΚΤΑΡΙΑ", 11 to "ΩΡΑΡΙΟ", 12 to "ΩΡΕΣ/ΗΜΕΡΑ"),
            // Row 2..8: Εβδομάδα 1
            2 to mapOf(0 to "28.09", 1 to "ΔΕΥ", 2 to "15-21", 3 to "6.0", 4 to "ΔΕΥ", 5 to "9-15", 6 to "6", 7 to "ΔΕΥ", 8 to "9-15", 9 to "6", 10 to "ΔΕΥ", 11 to "13-21", 12 to "8"),
            3 to mapOf(0 to "29.09", 1 to "ΤΡ", 2 to "9-15", 3 to "6.0", 4 to "ΤΡ", 5 to "12-20", 6 to "8", 7 to "ΤΡ", 8 to "9-15", 9 to "6", 10 to "ΤΡ", 11 to "13-21", 12 to "8"),
            4 to mapOf(0 to "30.09", 1 to "ΤΕΤ", 2 to "14-20", 3 to "6.0", 4 to "ΤΕΤ", 5 to "15-21", 6 to "6", 7 to "ΤΕΤ", 8 to "9-15", 9 to "6", 10 to "ΤΕΤ", 11 to "9-17", 12 to "8"),
            5 to mapOf(0 to "01.10", 1 to "ΠΕΜ", 2 to "15-21", 3 to "6.0", 4 to "ΠΕΜ", 5 to "9-13/19-21", 6 to "6", 7 to "ΠΕΜ", 8 to "ΑΔΕΙΑ", 9 to "7", 10 to "ΠΕΜ", 11 to "9-17", 12 to "8"),
            6 to mapOf(0 to "02.10", 1 to "ΠΑΡ", 2 to "9-15", 3 to "6.0", 4 to "ΠΑΡ", 5 to "9-13/19-21", 6 to "6", 7 to "ΠΑΡ", 8 to "ΑΔΕΙΑ", 9 to "7", 10 to "ΠΑΡ", 11 to "11-19", 12 to "8"),
            7 to mapOf(0 to "03.10", 1 to "ΣΑΒ", 2 to "ΡΕΠΟ", 4 to "ΣΑΒ", 5 to "10-18", 6 to "8", 7 to "ΣΑΒ", 8 to "ΑΔΕΙΑ", 9 to "8", 10 to "ΣΑΒ", 11 to "ΡΕΠΟ"),
            8 to mapOf(0 to "04.10", 1 to "ΚΥΡ", 2 to "ΡΕΠΟ", 4 to "ΚΥΡ", 5 to "ΡΕΠΟ", 7 to "ΚΥΡ", 8 to "ΡΕΠΟ", 10 to "ΚΥΡ", 11 to "ΡΕΠΟ"),
            // Row 9: Σύνολο εβδομάδας 1
            9 to mapOf(3 to "30.0", 6 to "40", 9 to "40", 12 to "40"),
            // Row 10: Header εβδομάδας 2
            10 to mapOf(1 to "ΔΕΣΠΟΙΝΑ", 2 to "ΩΡΑΡΙΟ", 3 to "ΩΡΕΣ/ΗΜΕΡΑ", 4 to "ΜΑΡΙΑ", 5 to "ΩΡΑΡΙΟ", 6 to "ΩΡΕΣ/ΗΜΕΡΑ", 7 to "ΜΥΡΤΩ", 8 to "ΩΡΑΡΙΟ", 9 to "ΩΡΕΣ/ΗΜΕΡΑ", 10 to "ΝΕΚΤΑΡΙΑ", 11 to "ΩΡΑΡΙΟ", 12 to "ΩΡΕΣ/ΗΜΕΡΑ"),
            // Row 11..17: Εβδομάδα 2
            11 to mapOf(0 to "05.10", 1 to "ΔΕΥ", 4 to "ΔΕΥ", 6 to "8", 7 to "ΔΕΥ", 9 to "8", 10 to "ΔΕΥ"),
            12 to mapOf(0 to "06.10", 1 to "ΤΡ", 4 to "ΤΡ", 6 to "8", 7 to "ΤΡ", 9 to "8", 10 to "ΤΡ"),
            13 to mapOf(0 to "07.10", 1 to "ΤΕΤ", 4 to "ΤΕΤ", 6 to "8", 7 to "ΤΕΤ", 9 to "8", 10 to "ΤΕΤ"),
            14 to mapOf(0 to "08.10", 1 to "ΠΕΜ", 4 to "ΠΕΜ", 6 to "8", 7 to "ΠΕΜ", 9 to "8", 10 to "ΠΕΜ"),
            15 to mapOf(0 to "09.10", 1 to "ΠΑΡ", 4 to "ΠΑΡ", 6 to "8", 7 to "ΠΑΡ", 9 to "8", 10 to "ΠΑΡ"),
            16 to mapOf(0 to "10.10", 1 to "ΣΑΒ", 4 to "ΣΑΒ", 5 to "ΡΕΠΟ", 7 to "ΣΑΒ", 8 to "ΡΕΠΟ", 10 to "ΣΑΒ"),
            17 to mapOf(0 to "11.10", 1 to "ΚΥΡ", 4 to "ΚΥΡ", 5 to "ΡΕΠΟ", 7 to "ΚΥΡ", 8 to "ΡΕΠΟ", 10 to "ΚΥΡ")
        )

        val sheet = XlsxWorkbookParser.Sheet(
            id = "rId11",
            name = "OCTO",
            target = "worksheets/sheet11.xml",
            rows = rows
        )
        val workbook = XlsxWorkbookParser.Workbook(listOf(sheet))
        val result = parser.parseWorkbook(workbook)

        // 14 ημέρες συνολικά (2 εβδομάδες)
        assertEquals(14, result.shifts.size)

        // Έλεγχος Εβδομάδας 1 για τη ΜΑΡΙΑ:
        val d28 = result.shifts.first { it.displayDate == "28.09" }
        assertEquals("ΔΕΥ", d28.dayOfWeek)
        assertEquals("09:00", d28.firstStartTime)
        assertEquals("15:00", d28.lastEndTime)
        assertEquals(6.0, d28.hoursWorked, 0.01)
        assertFalse(d28.isOff)

        val d29 = result.shifts.first { it.displayDate == "29.09" }
        assertEquals("ΤΡ", d29.dayOfWeek)
        assertEquals("12:00", d29.firstStartTime)
        assertEquals("20:00", d29.lastEndTime)
        assertEquals(8.0, d29.hoursWorked, 0.01)

        val d30 = result.shifts.first { it.displayDate == "30.09" }
        assertEquals("15:00", d30.firstStartTime)
        assertEquals("21:00", d30.lastEndTime)

        // Σπαστό 01.10 (9-13/19-21)
        val d01 = result.shifts.first { it.displayDate == "01.10" }
        assertEquals("ΠΕΜ", d01.dayOfWeek)
        assertEquals("09:00", d01.firstStartTime)
        assertEquals("21:00", d01.lastEndTime)
        assertEquals(6.0, d01.hoursWorked, 0.01)

        // 03.10 (10-18)
        val d03 = result.shifts.first { it.displayDate == "03.10" }
        assertEquals("10:00", d03.firstStartTime)
        assertEquals("18:00", d03.lastEndTime)
        assertEquals(8.0, d03.hoursWorked, 0.01)

        // 04.10 (ΡΕΠΟ)
        val d04 = result.shifts.first { it.displayDate == "04.10" }
        assertTrue(d04.isOff)
        assertEquals("ΡΕΠΟ", d04.offType)

        // Έλεγχος Εβδομάδας 2: 10.10 & 11.10 ΡΕΠΟ
        val d10 = result.shifts.first { it.displayDate == "10.10" }
        assertTrue(d10.isOff)
        assertEquals("ΡΕΠΟ", d10.offType)

        val d11 = result.shifts.first { it.displayDate == "11.10" }
        assertTrue(d11.isOff)
        assertEquals("ΡΕΠΟ", d11.offType)
    }
}
