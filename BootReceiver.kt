package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver που ενεργοποιείται αυτόματα μετά από επανεκκίνηση του τηλεφώνου (BOOT_COMPLETED)
 * ή αλλαγή ώρας/ζώνης ώρας (TIME_SET, TIMEZONE_CHANGED).
 * Επαναπρογραμματίζει όλα τα ενεργά alarms ώστε να μην χαθεί κανένα ωράριο εργασίας!
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Λήφθηκε broadcast: $action. Επαναπρογραμματισμός alarms...")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val shifts = db.shiftDao().getAllShifts()
                    val prefs = AppPreferences(context)
                    val settings = prefs.getSettings()

                    Log.d("BootReceiver", "Ανάκτηση ${shifts.size} ημερών για επαναπρογραμματισμό.")
                    AlarmScheduler.rescheduleAllAlarms(context, shifts, settings)
                    Log.d("BootReceiver", "Ο επαναπρογραμματισμός ολοκληρώθηκε επιτυχώς.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Σφάλμα κατά τον επαναπρογραμματισμό alarms μετά το boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
