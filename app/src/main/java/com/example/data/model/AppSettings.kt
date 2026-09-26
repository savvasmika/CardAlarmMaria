package com.example.data.model

import android.content.Context
import android.content.SharedPreferences

/**
 * Ρυθμίσεις εφαρμογής (προειδοποίηση πριν τη δουλειά, δόνηση, ήχος, ένταση).
 */
data class AppSettings(
    val preWarningEnabled: Boolean = true,
    val preWarningMinutes: Int = 15, // 5, 10, 15, 20, 30 λεπτά
    val vibrationEnabled: Boolean = true,
    val alarmVolume: Float = 0.9f,
    val alarmSound: String = "default" // "default", "gentle", "radar"
)

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("maria_work_alarm_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRE_WARNING_ENABLED = "pre_warning_enabled"
        private const val KEY_PRE_WARNING_MINUTES = "pre_warning_minutes"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_ALARM_VOLUME = "alarm_volume"
        private const val KEY_ALARM_SOUND = "alarm_sound"
    }

    fun getSettings(): AppSettings {
        return AppSettings(
            preWarningEnabled = prefs.getBoolean(KEY_PRE_WARNING_ENABLED, true),
            preWarningMinutes = prefs.getInt(KEY_PRE_WARNING_MINUTES, 15),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
            alarmVolume = prefs.getFloat(KEY_ALARM_VOLUME, 0.9f),
            alarmSound = prefs.getString(KEY_ALARM_SOUND, "default") ?: "default"
        )
    }

    fun setPreWarningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRE_WARNING_ENABLED, enabled).apply()
    }

    fun setPreWarningMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_PRE_WARNING_MINUTES, minutes).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun setAlarmVolume(volume: Float) {
        prefs.edit().putFloat(KEY_ALARM_VOLUME, volume).apply()
    }

    fun setAlarmSound(sound: String) {
        prefs.edit().putString(KEY_ALARM_SOUND, sound).apply()
    }
}
