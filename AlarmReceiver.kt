package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.db.AppDatabase
import com.example.ui.alarm.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver που καλείται από το λειτουργικό Android κατά την ώρα ενός alarm.
 * Λειτουργεί ακόμα και αν η εφαρμογή είναι κλειστή και η οθόνη κλειδωμένη.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: "START"
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Maria Work Alarm"
        val message = intent.getStringExtra(AlarmScheduler.EXTRA_MESSAGE) ?: "Ώρα για δουλειά!"
        val scheduledTime = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) ?: ""

        Log.d("AlarmReceiver", "Ενεργοποίηση Alarm: id=$alarmId, type=$alarmType, title=$title, time=$scheduledTime")

        // 1. Προσωρινό WakeLock για να διασφαλιστεί ότι η CPU δεν θα κοιμηθεί πριν εκκινήσει το service
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MariaWorkAlarm:AlarmReceiverWakeLock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 λεπτά μέγιστο

        // 2. Εκκίνηση Foreground Service για συνεχή αναπαραγωγή ήχου και δόνησης
        val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_START_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
            putExtra(AlarmScheduler.EXTRA_MESSAGE, message)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Αποτυχία εκκίνησης AlarmSoundService", e)
        }

        // 3. Άνοιγμα της οθόνης συναγερμού (AlarmRingingActivity)
        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
            putExtra(AlarmScheduler.EXTRA_MESSAGE, message)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Αποτυχία έναρξης AlarmRingingActivity (θα εμφανιστεί μέσω του full-screen intent της ειδοποίησης)", e)
        }

        // 4. Ενημέρωση βάσης δεδομένων
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                db.alarmDao().markAlarmFired(alarmId)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Σφάλμα ενημέρωσης κατάστασης alarm στη βάση", e)
            }
        }
    }
}
