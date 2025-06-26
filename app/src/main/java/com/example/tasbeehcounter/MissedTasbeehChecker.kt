package com.example.tasbeehcounter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.*

class MissedTasbeehChecker {
    companion object {
        private const val TAG = "MissedTasbeehChecker"
        private const val REQUEST_CODE_MISSED_TASBEEH = 1001
        
        /**
         * Check if exact alarm permissions are available
         */
        private fun canScheduleExactAlarms(context: Context): Boolean {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true // On older versions, exact alarms are always allowed
            }
        }
        
        /**
         * Schedule daily missed tasbeeh check at 9:00 AM
         */
        fun scheduleDailyCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MissedTasbeehReceiver::class.java).apply {
                action = NotificationService.ACTION_CHECK_MISSED_TASBEEH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_MISSED_TASBEEH,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Cancel any existing alarm
            alarmManager.cancel(pendingIntent)
            
            // Set alarm for 9:00 AM daily
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            
            // If it's already past 9:00 AM today, schedule for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            // Check if we can schedule exact alarms
            if (canScheduleExactAlarms(context)) {
                // Schedule the exact alarm
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    @Suppress("DEPRECATION")
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Daily missed tasbeeh check scheduled (exact) for ${calendar.time}")
            } else {
                // Fallback to inexact alarm
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
                Log.d(TAG, "Daily missed tasbeeh check scheduled (inexact) for ${calendar.time} (exact alarms not permitted)")
            }
        }
        
        /**
         * Cancel daily missed tasbeeh check
         */
        fun cancelDailyCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MissedTasbeehReceiver::class.java).apply {
                action = NotificationService.ACTION_CHECK_MISSED_TASBEEH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_MISSED_TASBEEH,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Daily missed tasbeeh check cancelled")
        }
        
        /**
         * Check if daily check is scheduled
         */
        fun isDailyCheckScheduled(context: Context): Boolean {
            val intent = Intent(context, MissedTasbeehReceiver::class.java).apply {
                action = NotificationService.ACTION_CHECK_MISSED_TASBEEH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_MISSED_TASBEEH,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            return pendingIntent != null
        }
    }
} 