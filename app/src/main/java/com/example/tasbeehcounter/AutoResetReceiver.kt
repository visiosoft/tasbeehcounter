package com.example.tasbeehcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.util.Log
import androidx.core.content.edit

class AutoResetReceiver : BroadcastReceiver() {
    companion object {
        private const val tag = "AutoResetReceiver"
        private const val ACTION_RESET = "com.example.tasbeehcounter.ACTION_RESET"

        fun scheduleNextReset(context: Context) {
            val intent = Intent(context, AutoResetReceiver::class.java).apply {
                action = ACTION_RESET
            }
            context.sendBroadcast(intent)
        }

        fun cancelReset(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AutoResetReceiver::class.java).apply {
                action = ACTION_RESET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            ACTION_RESET -> {
                // Reset all counts
                context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE).edit {
                    putString("saved_counts", "[]")
                }
                
                // Schedule next reset
                scheduleNextResetInternal(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // Schedule reset after device reboot
                scheduleNextResetInternal(context)
            }
        }
    }

    private fun scheduleNextResetInternal(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val resetIntent = Intent(context, AutoResetReceiver::class.java).apply {
            action = ACTION_RESET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Get next Fajr time
        val nextFajrTime = PrayerTimesManager.getNextFajrTime(context)
        
        // Schedule the alarm
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextFajrTime,
            pendingIntent
        )
        
        Log.d(tag, "Next reset scheduled for: ${java.util.Date(nextFajrTime)}")
    }
} 