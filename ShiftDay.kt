package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Αναπαριστά το πρόγραμμα εργασίας της Μαρίας για μια συγκεκριμένη ημέρα.
 * Αποθηκεύεται στη Room database.
 */
@Entity(tableName = "shift_days")
data class ShiftDay(
    @PrimaryKey
    val dateKey: String, // Μοναδικό κλειδί ISO: "2026-09-28"
    val displayDate: String, // π.χ. "28.09"
    val dayOfWeek: String, // π.χ. "ΔΕΥ"
    val rawSchedule: String, // π.χ. "9-15", "9-13/19-21", "ΡΕΠΟ", "ΑΔΕΙΑ"
    val isOff: Boolean, // true αν είναι ΡΕΠΟ, ΑΔΕΙΑ ή κενό (χωρίς alarms)
    val offType: String? = null, // "ΡΕΠΟ", "ΑΔΕΙΑ", κτλ.
    val firstStartTime: String? = null, // ώρα πρώτης έναρξης (π.χ. "09:00")
    val lastEndTime: String? = null, // ώρα τελικής λήξης (π.χ. "21:00")
    val hoursWorked: Double = 0.0, // εκτιμώμενες ώρες εργασίας
    val monthKey: String = "", // π.χ. "2026-09" ή "SEP" για φιλτράρισμα μήνα
    val sheetName: String = "", // όνομα φύλλου Excel (π.χ. "SEP", "OCT")
    val updatedAt: Long = System.currentTimeMillis()
)
