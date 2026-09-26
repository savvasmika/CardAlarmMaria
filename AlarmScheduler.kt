package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.model.AlarmRecord
import com.example.data.model.AppSettings
import com.example.data.model.ShiftDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Διαχειριστής προγραμματισμού των Alarms μέσω του AlarmManager του Android.
 *
 * Σημαντικές λειτουργίες:
 * 1. setAlarmClock: Εξασφαλίζει ακρίβεια δευτερολέπτου ακόμα και σε Doze Mode και κλειδωμένη οθόνη.
 * 2. Αποτροπή διπλότυπων: Ακυρώνει όλα τα παλιά alarms πριν δημιουργήσει νέα.
 * 3. Δημιουργία START, END, PRE_WARNING alarms για κάθε εργάσιμη ημέρα.
 */
object AlarmScheduler {

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ALARM_TYPE = "extra_alarm_type"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"

    const val TYPE_START = "START"
    const val TYPE_END = "END"
    const val TYPE_PRE_WARNING = "PRE_WARNING"
    const val TYPE_TEST = "TEST"

    /**
     * Επαναπρογραμματίζει όλα τα alarms από την αρχή για τα δοθέντα shifts.
     * Ακυρώνει πλήρως όλα τα προηγούμενα alarms.
     */
    suspend fun rescheduleAllAlarms(
        context: Context,
        shifts: List<ShiftDay>,
        settings: AppSettings
    ): List<AlarmRecord> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val alarmDao = db.alarmDao()

        // 1. Ακύρωση παλιών alarms από το σύστημα
        val oldAlarms = alarmDao.getAllAlarms()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager != null) {
            for (oldAlarm in oldAlarms) {
                cancelPendingIntent(context, alarmManager, oldAlarm.id)
            }
        }
        alarmDao.deleteAllAlarms()

        // 2. Δημιουργία νέων alarms
        val now = System.currentTimeMillis()
        val newAlarms = mutableListOf<AlarmRecord>()

        for (shift in shifts) {
            if (shift.isOff) continue // ΡΕΠΟ ή ΑΔΕΙΑ: κανένα alarm

            // A. START ALARM & PRE_WARNING
            if (!shift.firstStartTime.isNullOrBlank()) {
                val startTimestamp = calculateTimestamp(shift.dateKey, shift.firstStartTime)

                // Προειδοποίηση πριν τη δουλειά (π.χ. 15 λεπτά πριν)
                if (settings.preWarningEnabled && settings.preWarningMinutes > 0) {
                    val preWarningTimestamp = startTimestamp - (settings.preWarningMinutes * 60 * 1000L)
                    if (preWarningTimestamp > now) {
                        newAlarms.add(
                            AlarmRecord(
                                dateKey = shift.dateKey,
                                alarmType = TYPE_PRE_WARNING,
                                triggerTimestamp = preWarningTimestamp,
                                scheduledTime = formatTimestampToTime(preWarningTimestamp),
                                title = "Προειδοποίηση δουλειάς",
                                message = "Σε ${settings.preWarningMinutes} λεπτά ξεκινάς δουλειά (${shift.firstStartTime})"
                            )
                        )
                    }
                }

                // Κύριο Alarm Έναρξης
                if (startTimestamp > now) {
                    newAlarms.add(
                        AlarmRecord(
                            dateKey = shift.dateKey,
                            alarmType = TYPE_START,
                            triggerTimestamp = startTimestamp,
                            scheduledTime = shift.firstStartTime,
                            title = "Ώρα για δουλειά",
                            message = "Έχεις δουλειά σήμερα στις ${shift.firstStartTime}"
                        )
                    )
                }
            }

            // B. END ALARM (Σχολάς!)
            if (!shift.lastEndTime.isNullOrBlank()) {
                val endTimestamp = calculateTimestamp(shift.dateKey, shift.lastEndTime)
                if (endTimestamp > now) {
                    newAlarms.add(
                        AlarmRecord(
                            dateKey = shift.dateKey,
                            alarmType = TYPE_END,
                            triggerTimestamp = endTimestamp,
                            scheduledTime = shift.lastEndTime,
                            title = "Σχολάς!",
                            message = "Η δουλειά τελείωσε στις ${shift.lastEndTime} 🎉"
                        )
                    )
                }
            }
        }

        // 3. Αποθήκευση στη βάση για να αποκτήσουν μοναδικά IDs
        val insertedIds = alarmDao.insertAlarms(newAlarms)
        val finalAlarms = mutableListOf<AlarmRecord>()

        for (i in newAlarms.indices) {
            val assignedId = insertedIds.getOrNull(i)?.toInt() ?: (i + 1)
            val fullRecord = newAlarms[i].copy(id = assignedId)
            finalAlarms.add(fullRecord)

            // Προγραμματισμός στο Android AlarmManager
            scheduleInSystem(context, fullRecord)
        }

        finalAlarms
    }

    /**
     * Ενημερώνει τα alarms μόνο για μία συγκεκριμένη ημέρα (π.χ. μετά από χειροκίνητη διόρθωση).
     */
    suspend fun updateAlarmsForDay(
        context: Context,
        shift: ShiftDay,
        settings: AppSettings
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val alarmDao = db.alarmDao()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        // Ακύρωση παλιών alarms αυτής της ημέρας
        val oldDayAlarms = alarmDao.getAlarmsForDate(shift.dateKey)
        if (alarmManager != null) {
            for (old in oldDayAlarms) {
                cancelPendingIntent(context, alarmManager, old.id)
            }
        }
        alarmDao.deleteAlarmsForDate(shift.dateKey)

        if (shift.isOff) return@withContext

        val now = System.currentTimeMillis()
        val newAlarms = mutableListOf<AlarmRecord>()

        if (!shift.firstStartTime.isNullOrBlank()) {
            val startTimestamp = calculateTimestamp(shift.dateKey, shift.firstStartTime)

            if (settings.preWarningEnabled && settings.preWarningMinutes > 0) {
                val preTimestamp = startTimestamp - (settings.preWarningMinutes * 60 * 1000L)
                if (preTimestamp > now) {
                    newAlarms.add(
                        AlarmRecord(
                            dateKey = shift.dateKey,
                            alarmType = TYPE_PRE_WARNING,
                            triggerTimestamp = preTimestamp,
                            scheduledTime = formatTimestampToTime(preTimestamp),
                            title = "Προειδοποίηση δουλειάς",
                            message = "Σε ${settings.preWarningMinutes} λεπτά ξεκινάς δουλειά (${shift.firstStartTime})"
                        )
                    )
                }
            }

            if (startTimestamp > now) {
                newAlarms.add(
                    AlarmRecord(
                        dateKey = shift.dateKey,
                        alarmType = TYPE_START,
                        triggerTimestamp = startTimestamp,
                        scheduledTime = shift.firstStartTime,
                        title = "Ώρα για δουλειά",
                        message = "Έχεις δουλειά σήμερα στις ${shift.firstStartTime}"
                    )
                )
            }
        }

        if (!shift.lastEndTime.isNullOrBlank()) {
            val endTimestamp = calculateTimestamp(shift.dateKey, shift.lastEndTime)
            if (endTimestamp > now) {
                newAlarms.add(
                    AlarmRecord(
                        dateKey = shift.dateKey,
                        alarmType = TYPE_END,
                        triggerTimestamp = endTimestamp,
                        scheduledTime = shift.lastEndTime,
                        title = "Σχολάς!",
                        message = "Η δουλειά τελείωσε στις ${shift.lastEndTime} 🎉"
                    )
                )
            }
        }

        val insertedIds = alarmDao.insertAlarms(newAlarms)
        for (i in newAlarms.indices) {
            val assignedId = insertedIds.getOrNull(i)?.toInt() ?: (i + 1000)
            val fullRecord = newAlarms[i].copy(id = assignedId)
            scheduleInSystem(context, fullRecord)
        }
    }

    /**
     * Προγραμματίζει δοκιμαστικό alarm σε X δευτερόλεπτα για άμεσο έλεγχο.
     */
    suspend fun scheduleTestAlarm(context: Context, delaySeconds: Int = 5): AlarmRecord = withContext(Dispatchers.IO) {
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)
        val cal = Calendar.getInstance()
        cal.timeInMillis = triggerTime
        val timeStr = String.format("%02d:%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))

        val db = AppDatabase.getInstance(context)
        val testAlarm = AlarmRecord(
            dateKey = "TEST",
            alarmType = TYPE_TEST,
            triggerTimestamp = triggerTime,
            scheduledTime = timeStr,
            title = "Δοκιμαστικό Alarm (Έλεγχος)",
            message = "Το σύστημα alarm της Μαρίας λειτουργεί άψογα! ⏰"
        )
        val id = db.alarmDao().insertAlarm(testAlarm).toInt()
        val fullRecord = testAlarm.copy(id = id)
        scheduleInSystem(context, fullRecord)
        fullRecord
    }

    fun scheduleInSystem(context: Context, alarm: AlarmRecord) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_TYPE, alarm.alarmType)
            putExtra(EXTRA_TITLE, alarm.title)
            putExtra(EXTRA_MESSAGE, alarm.message)
            putExtra(EXTRA_SCHEDULED_TIME, alarm.scheduledTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showAppIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id + 100000,
            showAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Χρήση setAlarmClock για μέγιστη αξιοπιστία, εμφάνιση εικονιδίου ρολογιού στο status bar
            // και παράκαμψη οποιουδήποτε doze mode/battery restriction
            val alarmClockInfo = AlarmManager.AlarmClockInfo(alarm.triggerTimestamp, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("AlarmScheduler", "Προγραμματίστηκε alarm [${alarm.id}] για ${alarm.scheduledTime} (${alarm.title})")
        } catch (e: SecurityException) {
            Log.w("AlarmScheduler", "SecurityException με setAlarmClock, δοκιμή με setExactAndAllowWhileIdle", e)
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerTimestamp,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e("AlarmScheduler", "Αποτυχία προγραμματισμού alarm", e2)
            }
        }
    }

    private fun cancelPendingIntent(context: Context, alarmManager: AlarmManager, alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun calculateTimestamp(dateKey: String, timeStr: String): Long {
        val dateParts = dateKey.split('-')
        val timeParts = timeStr.split(':')

        val year = dateParts[0].toInt()
        val month = dateParts[1].toInt() - 1 // Calendar months 0-11
        val day = dateParts[2].toInt()

        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()

        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatTimestampToTime(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}
