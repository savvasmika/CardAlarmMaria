package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Αναπαριστά ένα προγραμματισμένο Alarm στο AlarmManager του Android.
 * Αποθηκεύει το μοναδικό PendingIntent ID για αξιόπιστη ακύρωση/ανανέωση.
 */
@Entity(tableName = "alarm_records")
data class AlarmRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val dateKey: String, // "2026-09-28"
    val alarmType: String, // "START", "END", "PRE_WARNING", "TEST"
    val triggerTimestamp: Long, // Ώρα ειδοποίησης σε milliseconds
    val scheduledTime: String, // "08:45", "09:00", "15:00"
    val title: String, // "Ώρα για δουλειά", "Σχολάς!"
    val message: String, // "Έχεις δουλειά σήμερα στις 09:00"
    val isEnabled: Boolean = true,
    val isFired: Boolean = false
)
