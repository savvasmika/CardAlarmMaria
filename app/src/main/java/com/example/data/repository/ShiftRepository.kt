package com.example.data.repository

import android.content.Context
import com.example.alarm.AlarmScheduler
import com.example.data.db.AppDatabase
import com.example.data.model.AlarmRecord
import com.example.data.model.AppPreferences
import com.example.data.model.AppSettings
import com.example.data.model.ShiftDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository για το πρόγραμμα εργασίας και τα alarms της Μαρίας.
 */
class ShiftRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val shiftDao = database.shiftDao()
    private val alarmDao = database.alarmDao()
    private val preferences = AppPreferences(context)

    val allShifts: Flow<List<ShiftDay>> = shiftDao.getAllShiftsFlow()
    val allMonthKeys: Flow<List<String>> = shiftDao.getAllMonthKeysFlow()

    fun getShiftsForMonth(monthKey: String): Flow<List<ShiftDay>> {
        return shiftDao.getShiftsForMonthFlow(monthKey)
    }

    fun getPendingAlarms(): Flow<List<AlarmRecord>> {
        return alarmDao.getPendingAlarmsFlow(System.currentTimeMillis())
    }

    fun getSettings(): AppSettings {
        return preferences.getSettings()
    }

    suspend fun getShiftForDate(dateKey: String): ShiftDay? = withContext(Dispatchers.IO) {
        shiftDao.getShiftForDate(dateKey)
    }

    suspend fun saveSettings(settings: AppSettings, rescheduleAlarms: Boolean = true) = withContext(Dispatchers.IO) {
        preferences.setPreWarningEnabled(settings.preWarningEnabled)
        preferences.setPreWarningMinutes(settings.preWarningMinutes)
        preferences.setVibrationEnabled(settings.vibrationEnabled)
        preferences.setAlarmVolume(settings.alarmVolume)
        preferences.setAlarmSound(settings.alarmSound)

        if (rescheduleAlarms) {
            val shifts = shiftDao.getAllShifts()
            AlarmScheduler.rescheduleAllAlarms(context, shifts, settings)
        }
    }

    /**
     * Αποθήκευση νέου εισαχθέντος προγράμματος από το Excel και αυτόματος προγραμματισμός alarms.
     * Αντικαθιστά τα παλιά alarms και διασφαλίζει ότι δεν υπάρχουν διπλότυπα.
     */
    suspend fun importShiftsAndScheduleAlarms(
        newShifts: List<ShiftDay>,
        clearPrevious: Boolean = false
    ): List<AlarmRecord> = withContext(Dispatchers.IO) {
        if (clearPrevious) {
            shiftDao.deleteAllShifts()
        }
        shiftDao.insertShifts(newShifts)

        // Ανάκτηση όλων των ημερών και επαναπρογραμματισμός
        val allCurrentShifts = shiftDao.getAllShifts()
        val currentSettings = preferences.getSettings()
        AlarmScheduler.rescheduleAllAlarms(context, allCurrentShifts, currentSettings)
    }

    /**
     * Χειροκίνητη διόρθωση μίας ημέρας και άμεση ενημέρωση των alarms της.
     */
    suspend fun updateShiftManually(shift: ShiftDay) = withContext(Dispatchers.IO) {
        shiftDao.insertOrUpdateShift(shift)
        val currentSettings = preferences.getSettings()
        AlarmScheduler.updateAlarmsForDay(context, shift, currentSettings)
    }

    /**
     * Δοκιμαστικό alarm σε 5 δευτερόλεπτα για έλεγχο από τον χρήστη.
     */
    suspend fun triggerTestAlarm(delaySeconds: Int = 5): AlarmRecord = withContext(Dispatchers.IO) {
        AlarmScheduler.scheduleTestAlarm(context, delaySeconds)
    }

    /**
     * Διαγραφή όλων των δεδομένων (Reset).
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        val oldAlarms = alarmDao.getAllAlarms()
        if (alarmManager != null) {
            for (alarm in oldAlarms) {
                val intent = android.content.Intent(context, com.example.alarm.AlarmReceiver::class.java)
                val pi = android.app.PendingIntent.getBroadcast(
                    context,
                    alarm.id,
                    intent,
                    android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                pi?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
            }
        }
        alarmDao.deleteAllAlarms()
        shiftDao.deleteAllShifts()
    }
}
