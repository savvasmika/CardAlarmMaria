package com.example.excel

import com.example.data.model.ShiftDay
import java.util.Calendar

/**
 * Δοκιμαστικό πρόγραμμα εργασίας με βάση το παράδειγμα του Excel της Μαρίας:
 * 28.09 | ΔΕΥ | 9-15 | 6
 * 29.09 | ΤΡΙ | 12-20 | 8
 * 30.09 | ΤΕΤ | 15-21 | 6
 * 01.10 | ΠΕΜ | 9-13/19-21 | 6
 * 02.10 | ΠΑΡ | 9-13/19-21 | 6
 * 03.10 | ΣΑΒ | 10-18 | 8
 * 04.10 | ΚΥΡ | ΡΕΠΟ
 */
object SampleScheduleData {

    fun generateSampleShifts(year: Int = Calendar.getInstance().get(Calendar.YEAR)): List<ShiftDay> {
        val parser = MariaScheduleParser()
        val rawData = listOf(
            Triple("28.09", "ΔΕΥ", "9-15"),
            Triple("29.09", "ΤΡ", "12-20"),
            Triple("30.09", "ΤΕΤ", "15-21"),
            Triple("01.10", "ΠΕΜ", "9-13/19-21"),
            Triple("02.10", "ΠΑΡ", "9-13/19-21"),
            Triple("03.10", "ΣΑΒ", "10-18"),
            Triple("04.10", "ΚΥΡ", "ΡΕΠΟ"),
            Triple("05.10", "ΔΕΥ", "9-17"),
            Triple("06.10", "ΤΡ", "9-17"),
            Triple("07.10", "ΤΕΤ", "9-17"),
            Triple("08.10", "ΠΕΜ", "9-17"),
            Triple("09.10", "ΠΑΡ", "9-17"),
            Triple("10.10", "ΣΑΒ", "ΡΕΠΟ"),
            Triple("11.10", "ΚΥΡ", "ΡΕΠΟ"),
            Triple("12.10", "ΔΕΥ", "9-15"),
            Triple("13.10", "ΤΡ", "12-20"),
            Triple("14.10", "ΤΕΤ", "15-21"),
            Triple("15.10", "ΠΕΜ", "9-13/19-21"),
            Triple("16.10", "ΠΑΡ", "9-13/19-21"),
            Triple("17.10", "ΣΑΒ", "10-18"),
            Triple("18.10", "ΚΥΡ", "ΡΕΠΟ"),
            Triple("19.10", "ΔΕΥ", "9-17"),
            Triple("20.10", "ΤΡ", "12-20"),
            Triple("21.10", "ΤΕΤ", "9-13/19-21"),
            Triple("22.10", "ΠΕΜ", "15-21"),
            Triple("23.10", "ΠΑΡ", "10-18"),
            Triple("24.10", "ΣΑΒ", "10-18"),
            Triple("25.10", "ΚΥΡ", "ΡΕΠΟ"),
            Triple("26.10", "ΔΕΥ", "9-15"),
            Triple("27.10", "ΤΡ", "12-20"),
            Triple("28.10", "ΤΕΤ", "ΡΕΠΟ"),
            Triple("29.10", "ΠΕΜ", "9-13/19-21"),
            Triple("30.10", "ΠΑΡ", "15-21"),
            Triple("31.10", "ΣΑΒ", "10-18")
        )

        return rawData.map { (dispDate, dow, sched) ->
            val timing = parser.parseShiftTiming(sched)
            val parts = dispDate.split('.')
            val d = parts[0].toInt()
            val m = parts[1].toInt()
            val dateKey = String.format("%04d-%02d-%02d", year, m, d)
            val monthKey = String.format("%04d-%02d", year, m)
            val sheetName = "OCTO"

            ShiftDay(
                dateKey = dateKey,
                displayDate = dispDate,
                dayOfWeek = dow,
                rawSchedule = sched,
                isOff = timing.isOff,
                offType = timing.offType,
                firstStartTime = timing.firstStartTime,
                lastEndTime = timing.lastEndTime,
                hoursWorked = timing.estimatedHours,
                monthKey = monthKey,
                sheetName = sheetName
            )
        }
    }
}
