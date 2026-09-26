package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.model.AppPreferences
import com.example.ui.alarm.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground Service που αναπαράγει τον συνεχή ήχο του alarm και τη δόνηση.
 *
 * Σημαντικές προδιαγραφές χρήστη:
 * - Ο ήχος συνεχίζει μέχρι ο χρήστης να πατήσει «ΑΠΕΝΕΡΓΟΠΟΙΗΣΗ» ή «OK».
 * - Λειτουργεί σαν πραγματικό alarm και όχι σαν απλή notification.
 * - Εμφανίζει ειδοποίηση μέγιστης προτεραιότητας με Full Screen Intent.
 */
class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val CHANNEL_ID = "maria_work_alarms_channel"
        const val CHANNEL_NAME = "Ειδοποιήσεις Εργασίας Μαρίας"
        const val NOTIFICATION_ID = 9999

        const val ACTION_START_ALARM = "com.example.action.START_ALARM"
        const val ACTION_DISMISS = "com.example.action.DISMISS_ALARM"
        const val ACTION_SNOOZE = "com.example.action.SNOOZE_ALARM"

        var isRinging = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM

        when (action) {
            ACTION_DISMISS -> {
                Log.d("AlarmSoundService", "Λήφθηκε ενέργεια ΑΠΕΝΕΡΓΟΠΟΙΗΣΗΣ")
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                Log.d("AlarmSoundService", "Λήφθηκε ενέργεια ΑΝΑΒΟΛΗΣ (5 λεπτά)")
                snoozeAlarm()
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM -> {
                val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0) ?: 0
                val title = intent?.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Ώρα για δουλειά"
                val message = intent?.getStringExtra(AlarmScheduler.EXTRA_MESSAGE) ?: "Έχεις δουλειά σήμερα"
                val scheduledTime = intent?.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) ?: ""

                startForegroundWithNotification(alarmId, title, message, scheduledTime)
                startAlarmMediaAndVibration()
                isRinging = true
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification(
        alarmId: Int,
        title: String,
        message: String,
        scheduledTime: String
    ) {
        // Full screen intent προς την AlarmRingingActivity
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
            putExtra(AlarmScheduler.EXTRA_MESSAGE, message)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId + 200000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action button
        val dismissIntent = Intent(this, AlarmSoundService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            alarmId + 300000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action button
        val snoozeIntent = Intent(this, AlarmSoundService::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            alarmId + 400000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_maria_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("Maria Work Alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_lock_power_off, "ΑΠΕΝΕΡΓΟΠΟΙΗΣΗ", dismissPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "ΑΝΑΒΟΛΗ (5')", snoozePendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startAlarmMediaAndVibration() {
        val prefs = AppPreferences(this)
        val settings = prefs.getSettings()

        // 1. Ήχος Alarm (Επαναλαμβανόμενος / Looping)
        try {
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                isLooping = true // Συνεχής αναπαραγωγή μέχρι να πατηθεί ΑΠΕΝΕΡΓΟΠΟΙΗΣΗ
                val vol = settings.alarmVolume.coerceIn(0.1f, 1.0f)
                setVolume(vol, vol)
                prepare()
                start()
            }
            Log.d("AlarmSoundService", "Ο ήχος του alarm ξεκίνησε επιτυχώς")
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Σφάλμα εκκίνησης MediaPlayer", e)
        }

        // 2. Δόνηση
        if (settings.vibrationEnabled) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat from index 0
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                Log.e("AlarmSoundService", "Σφάλμα εκκίνησης δόνησης", e)
            }
        }
    }

    private fun snoozeAlarm() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.scheduleTestAlarm(applicationContext, delaySeconds = 5 * 60)
            } catch (e: Exception) {
                Log.e("AlarmSoundService", "Σφάλμα προγραμματισμού snooze", e)
            }
        }
    }

    private fun stopAlarmAndSelf() {
        isRinging = false
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Σφάλμα τερματισμού MediaPlayer", e)
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Σφάλμα ακύρωσης δόνησης", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarmAndSelf()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Κανάλι ειδοποιήσεων ωραρίου εργασίας και alarms της Μαρίας"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
