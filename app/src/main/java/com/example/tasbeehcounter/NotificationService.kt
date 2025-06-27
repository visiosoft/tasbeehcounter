package com.example.tasbeehcounter

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationService {
    companion object {
        private const val TAG = "NotificationService"
        
        // Notification channel IDs
        const val CHANNEL_MISSED_TASBEEH = "missed_tasbeeh"
        const val CHANNEL_PRAYER_REMINDERS = "prayer_reminders"
        
        // Notification IDs
        const val NOTIFICATION_MISSED_TASBEEH = 1000
        const val NOTIFICATION_FAJR = 2001
        const val NOTIFICATION_DHUHR = 2002
        const val NOTIFICATION_ASR = 2003
        const val NOTIFICATION_MAGHRIB = 2004
        const val NOTIFICATION_ISHA = 2005
        
        // Action constants
        const val ACTION_CHECK_MISSED_TASBEEH = "com.example.tasbeehcounter.CHECK_MISSED_TASBEEH"
        const val ACTION_PRAYER_REMINDER = "com.example.tasbeehcounter.PRAYER_REMINDER"
        
        // Prayer names
        val PRAYER_NAMES = mapOf(
            "fajr" to "Fajr",
            "dhuhr" to "Dhuhr", 
            "asr" to "Asr",
            "maghrib" to "Maghrib",
            "isha" to "Isha"
        )
        
        // Islamic quotes for missed tasbeeh - Bilingual (Pure English + Pure Urdu) for English-speaking Muslims
        val MISSED_TASBEEH_QUOTES = listOf(
            "You missed your tasbeeh yesterday. Come back and reconnect with dhikr.\nاللہ کی یاد میں دلوں کو سکون ملتا ہے۔",
            
            "Remember Allah in abundance, for He is the source of peace.\nتسبیح اللہ کی یاد کا بہترین ذریعہ ہے۔",
            
            "Every day is a new opportunity to worship Allah.\nہر دن نیا موقع ہے اللہ کی عبادت کا۔",
            
            "Turn to Allah, He will help you.\nاللہ کی طرف رجوع کرو، وہ تمہاری مدد کرے گا۔",
            
            "Make tasbeeh a habit, it's the best for you.\nتسبیح کی عادت بناؤ، یہ تمہارے لیے بہترین ہے۔",
            
            "Spending time in Allah's remembrance is better than everything in this world.\nاللہ کی یاد میں وقت گزارنا دنیا کی ہر چیز سے بہتر ہے۔",
            
            "Indeed, Allah is with those who are patient.\nبےشک اللہ صبر کرنے والوں کے ساتھ ہے۔",
            
            "After every difficulty comes ease.\nہر مشکل کے بعد آسانی ہے۔",
            
            "Don't lose hope in Allah's mercy.\nاللہ کی رحمت سے مایوس نہ ہو۔",
            
            "The best of deeds is to remember Allah.\nسب سے بہترین عمل اللہ کی یاد ہے۔",
            
            "Be patient with Allah, for Allah is with the patient.\nاللہ تعالیٰ کے ساتھ صبر کرو، بےشک اللہ صبر کرنے والوں کے ساتھ ہے۔",
            
            "Surely with hardship comes ease.\nبےشک مشکل کے ساتھ آسانی ہے۔",
            
            "The best among you are those with the best character.\nتم میں سے بہترین وہ ہیں جو اخلاق میں بہترین ہیں۔",
            
            "Whoever believes in Allah and the Last Day, let him speak good or remain silent.\nجو اللہ اور آخرت پر ایمان رکھتا ہے وہ اچھی بات کہے یا خاموش رہے۔",
            
            "Allah does not burden a soul beyond its capacity.\nاللہ کسی جان پر اس کی طاقت سے زیادہ بوجھ نہیں ڈالتا۔",
            
            "The most beloved to Allah is the one who is most beneficial to people.\nاللہ کے نزدیک سب سے محبوب وہ شخص ہے جو لوگوں کو سب سے زیادہ فائدہ پہنچائے۔",
            
            "Indeed, We have created man in the best form.\nبےشک ہم نے انسان کو بہترین ساخت میں پیدا کیا۔",
            
            "The strong person is not the one who can wrestle someone down, but the strong person is the one who can control himself when he is angry.\nطاقتور وہ نہیں جو کسی کو پچھاڑ دے، بلکہ طاقتور وہ ہے جو غصے میں اپنے آپ پر قابو رکھے۔",
            
            "Prayer is the ascension of the believer.\nنماز مومن کی معراج ہے۔",
            
            "The fruit of patience is sweet.\nصبر کا پھل میٹھا ہوتا ہے۔"
        )
        
        // Prayer reminder messages
        val PRAYER_MESSAGES = mapOf(
            "fajr" to "Time for Fajr prayer",
            "dhuhr" to "Time for Dhuhr prayer",
            "asr" to "Time for Asr prayer",
            "maghrib" to "Time for Maghrib prayer",
            "isha" to "Time for Isha prayer"
        )
        
        /**
         * Check if exact alarm permissions are available
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true // On older versions, exact alarms are always allowed
            }
        }
        
        /**
         * Request exact alarm permissions by opening system settings
         */
        fun requestExactAlarmPermission(context: Context) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general alarm settings
                    val fallbackIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    fallbackIntent.data = android.net.Uri.fromParts("package", context.packageName, null)
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallbackIntent)
                }
            }
        }
    }
    
    /**
     * Initialize notification channels for Android 8.0+
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Missed Tasbeeh Channel
            val missedTasbeehChannel = NotificationChannel(
                CHANNEL_MISSED_TASBEEH,
                "Missed Tasbeeh Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders when you miss your daily tasbeeh"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Prayer Reminders Channel
            val prayerRemindersChannel = NotificationChannel(
                CHANNEL_PRAYER_REMINDERS,
                "Prayer Time Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily prayer time reminders"
                enableVibration(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannels(listOf(missedTasbeehChannel, prayerRemindersChannel))
            Log.d(TAG, "Notification channels created successfully")
        }
    }
    
    /**
     * Check if user missed tasbeeh for one full day and show notification
     */
    fun checkMissedTasbeeh(context: Context) {
        val sharedPreferences = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val lastTasbeehTimestamp = sharedPreferences.getLong("last_tasbeeh_timestamp", 0L)
        val notificationsEnabled = sharedPreferences.getBoolean("notifications", true)
        
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications are disabled")
            return
        }
        
        if (lastTasbeehTimestamp == 0L) {
            Log.d(TAG, "No previous tasbeeh timestamp found")
            return
        }
        
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis
        
        // Check if more than 24 hours have passed since last tasbeeh
        val timeDifference = today - lastTasbeehTimestamp
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        
        if (timeDifference >= oneDayInMillis) {
            showMissedTasbeehNotification(context)
            Log.d(TAG, "Missed tasbeeh notification shown")
        } else {
            Log.d(TAG, "No missed tasbeeh detected")
        }
    }
    
    /**
     * Show missed tasbeeh notification with random Islamic quote
     */
    private fun showMissedTasbeehNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Select random quote
        val randomQuote = MISSED_TASBEEH_QUOTES.random()
        
        // Split the quote into English and Urdu parts for better display
        val quoteParts = randomQuote.split("\n")
        val englishPart = quoteParts.firstOrNull() ?: randomQuote
        val urduPart = if (quoteParts.size > 1) quoteParts[1] else ""
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED_TASBEEH)
            .setSmallIcon(R.drawable.ic_tasbeeh)
            .setContentTitle("Missed Tasbeeh Reminder")
            .setContentText(englishPart)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(if (urduPart.isNotEmpty()) "$englishPart\n\n$urduPart" else englishPart))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_MISSED_TASBEEH, notification)
    }
    
    /**
     * Schedule daily prayer reminders using AlarmManager
     */
    fun schedulePrayerReminders(context: Context) {
        val sharedPreferences = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPreferences.getBoolean("notifications", true)
        
        if (!notificationsEnabled) {
            Log.d(TAG, "Prayer notifications are disabled")
            return
        }
        
        try {
            // Cancel existing prayer reminders
            cancelExistingPrayerReminders(context)
            
            // Try to get actual prayer times from PrayerTimesManager
            val prayerTimes = PrayerTimesManager.getPrayerTimes(context)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayPrayerTimes = prayerTimes.find { it.date == today }
            
            if (todayPrayerTimes != null) {
                // Use actual prayer times
                schedulePrayerReminderFromTime(context, "fajr", todayPrayerTimes.fajr)
                schedulePrayerReminderFromTime(context, "dhuhr", todayPrayerTimes.dhuhr)
                schedulePrayerReminderFromTime(context, "asr", todayPrayerTimes.asr)
                schedulePrayerReminderFromTime(context, "maghrib", todayPrayerTimes.maghrib)
                schedulePrayerReminderFromTime(context, "isha", todayPrayerTimes.isha)
                Log.d(TAG, "Prayer reminders scheduled using actual prayer times")
            } else {
                // Use default times if no prayer times available
                schedulePrayerReminder(context, "fajr", 5, 30) // 5:30 AM
                schedulePrayerReminder(context, "dhuhr", 12, 30) // 12:30 PM
                schedulePrayerReminder(context, "asr", 15, 30) // 3:30 PM
                schedulePrayerReminder(context, "maghrib", 18, 30) // 6:30 PM
                schedulePrayerReminder(context, "isha", 19, 30) // 7:30 PM
                Log.d(TAG, "Prayer reminders scheduled using default times")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling prayer reminders", e)
        }
    }
    
    /**
     * Cancel existing prayer reminders
     */
    private fun cancelExistingPrayerReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prayers = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        
        for (prayer in prayers) {
            val intent = Intent(context, PrayerReminderReceiver::class.java).apply {
                putExtra("prayer", prayer)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                prayer.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }
    
    /**
     * Schedule prayer reminder from time string (HH:MM format)
     */
    private fun schedulePrayerReminderFromTime(context: Context, prayerName: String, timeString: String) {
        try {
            val timeParts = timeString.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            
            // Add 5 minutes to the prayer time for the reminder
            val reminderMinute = minute + 5
            val reminderHour = if (reminderMinute >= 60) hour + 1 else hour
            val finalMinute = if (reminderMinute >= 60) reminderMinute - 60 else reminderMinute
            
            schedulePrayerReminder(context, prayerName, reminderHour, finalMinute)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing prayer time: $timeString", e)
            // Fallback to default time
            val defaultHour = when (prayerName) {
                "fajr" -> 5
                "dhuhr" -> 12
                "asr" -> 15
                "maghrib" -> 18
                "isha" -> 19
                else -> 12
            }
            schedulePrayerReminder(context, prayerName, defaultHour, 30)
        }
    }
    
    /**
     * Schedule individual prayer reminder
     */
    private fun schedulePrayerReminder(context: Context, prayerName: String, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        
        // If the time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        val delay = calendar.timeInMillis - System.currentTimeMillis()
        
        val intent = Intent(context, PrayerReminderReceiver::class.java).apply {
            putExtra("prayer", prayerName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check if we can schedule exact alarms
        if (canScheduleExactAlarms(context)) {
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
            Log.d(TAG, "Scheduled exact $prayerName reminder for ${calendar.time}")
        } else {
            // Fallback to inexact alarm
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            Log.d(TAG, "Scheduled inexact $prayerName reminder for ${calendar.time} (exact alarms not permitted)")
        }
    }
    
    /**
     * Update last tasbeeh timestamp when user performs tasbeeh
     */
    fun updateLastTasbeehTimestamp(context: Context) {
        val sharedPreferences = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        sharedPreferences.edit().putLong("last_tasbeeh_timestamp", System.currentTimeMillis()).apply()
        Log.d(TAG, "Last tasbeeh timestamp updated")
    }
    
    /**
     * Cancel all scheduled notifications
     */
    fun cancelAllNotifications(context: Context) {
        try {
            cancelExistingPrayerReminders(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling existing prayer reminders", e)
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        
        Log.d(TAG, "All notifications cancelled")
    }
    
    /**
     * Test method to show the next prayer notification based on current time
     */
    fun testPrayerNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        try {
            // Get today's prayer times from database
            val prayerTimes = PrayerTimesManager.getPrayerTimes(context)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayPrayerTimes = prayerTimes.find { it.date == today }
            
            if (todayPrayerTimes != null) {
                // Find the next prayer based on current time
                val currentTime = Calendar.getInstance()
                val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
                val currentMinute = currentTime.get(Calendar.MINUTE)
                val currentTimeInMinutes = currentHour * 60 + currentMinute
                
                // Parse prayer times and find the next one
                val prayerTimeMap = mapOf(
                    "fajr" to todayPrayerTimes.fajr,
                    "dhuhr" to todayPrayerTimes.dhuhr,
                    "asr" to todayPrayerTimes.asr,
                    "maghrib" to todayPrayerTimes.maghrib,
                    "isha" to todayPrayerTimes.isha
                )
                
                var nextPrayer: String? = null
                var nextPrayerTime: String? = null
                var minTimeDiff = Int.MAX_VALUE
                
                for ((prayer, timeString) in prayerTimeMap) {
                    val timeParts = timeString.split(":")
                    val prayerHour = timeParts[0].toInt()
                    val prayerMinute = timeParts[1].toInt()
                    val prayerTimeInMinutes = prayerHour * 60 + prayerMinute
                    
                    // Calculate time difference (handle next day for prayers after current time)
                    val timeDiff = if (prayerTimeInMinutes > currentTimeInMinutes) {
                        prayerTimeInMinutes - currentTimeInMinutes
                    } else {
                        // Prayer time has passed today, check for tomorrow
                        (24 * 60 - currentTimeInMinutes) + prayerTimeInMinutes
                    }
                    
                    if (timeDiff < minTimeDiff) {
                        minTimeDiff = timeDiff
                        nextPrayer = prayer
                        nextPrayerTime = timeString
                    }
                }
                
                // Show notification for the next prayer
                nextPrayer?.let { prayer ->
                    val prayerDisplayName = PRAYER_NAMES[prayer] ?: prayer
                    val message = PRAYER_MESSAGES[prayer] ?: "It's time for prayer"
                    
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    val notification = NotificationCompat.Builder(context, CHANNEL_PRAYER_REMINDERS)
                        .setSmallIcon(R.drawable.ic_prayer)
                        .setContentTitle("TEST: $prayerDisplayName Prayer Time")
                        .setContentText("$message at $nextPrayerTime (Test Notification)")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build()
                    
                    val notificationId = when (prayer) {
                        "fajr" -> NOTIFICATION_FAJR
                        "dhuhr" -> NOTIFICATION_DHUHR
                        "asr" -> NOTIFICATION_ASR
                        "maghrib" -> NOTIFICATION_MAGHRIB
                        "isha" -> NOTIFICATION_ISHA
                        else -> 2000
                    }
                    
                    notificationManager.notify(notificationId, notification)
                    Log.d(TAG, "Test notification sent for next prayer: $prayer at $nextPrayerTime")
                }
            } else {
                // Fallback: show a default notification if no prayer times available
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val notification = NotificationCompat.Builder(context, CHANNEL_PRAYER_REMINDERS)
                    .setSmallIcon(R.drawable.ic_prayer)
                    .setContentTitle("TEST: Prayer Time")
                    .setContentText("Prayer reminder test (no prayer times available)")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
                
                notificationManager.notify(2000, notification)
                Log.d(TAG, "Test notification sent (fallback - no prayer times available)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing test prayer notification", e)
        }
    }
    
    /**
     * Test method to show missed tasbeeh notification with bilingual quotes (English + Urdu)
     */
    fun testMissedTasbeehNotification(context: Context) {
        showMissedTasbeehNotification(context)
        Log.d(TAG, "Test missed tasbeeh notification sent with bilingual quote")
    }
}

/**
 * Broadcast receiver for missed tasbeeh checks
 */
class MissedTasbeehReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationService.ACTION_CHECK_MISSED_TASBEEH -> {
                NotificationService().checkMissedTasbeeh(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                try {
                    // Re-schedule notifications after device reboot
                    NotificationService().createNotificationChannels(context)
                    NotificationService().schedulePrayerReminders(context)
                } catch (e: Exception) {
                    // Log the error but don't crash
                    android.util.Log.e("MissedTasbeehReceiver", "Error scheduling notifications after boot", e)
                }
            }
        }
    }
}

/**
 * Broadcast receiver for prayer reminders
 */
class PrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("prayer") ?: return
        
        showPrayerReminderNotification(context, prayerName)
        
        // Schedule next day's reminder
        scheduleNextDayReminder(context, prayerName)
    }
    
    private fun showPrayerReminderNotification(context: Context, prayerName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prayerDisplayName = NotificationService.PRAYER_NAMES[prayerName] ?: prayerName
        val message = NotificationService.PRAYER_MESSAGES[prayerName] ?: "It's time for prayer"
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, NotificationService.CHANNEL_PRAYER_REMINDERS)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentTitle("$prayerDisplayName Prayer Time")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationId = when (prayerName) {
            "fajr" -> NotificationService.NOTIFICATION_FAJR
            "dhuhr" -> NotificationService.NOTIFICATION_DHUHR
            "asr" -> NotificationService.NOTIFICATION_ASR
            "maghrib" -> NotificationService.NOTIFICATION_MAGHRIB
            "isha" -> NotificationService.NOTIFICATION_ISHA
            else -> 2000
        }
        
        notificationManager.notify(notificationId, notification)
        Log.d("PrayerReminderReceiver", "Prayer reminder notification shown for $prayerName")
    }
    
    private fun scheduleNextDayReminder(context: Context, prayerName: String) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        
        val hour = when (prayerName) {
            "fajr" -> 5
            "dhuhr" -> 12
            "asr" -> 15
            "maghrib" -> 18
            "isha" -> 19
            else -> 12
        }
        
        val minute = when (prayerName) {
            "fajr" -> 30
            "dhuhr" -> 30
            "asr" -> 30
            "maghrib" -> 30
            "isha" -> 30
            else -> 30
        }
        
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        
        val intent = Intent(context, PrayerReminderReceiver::class.java).apply {
            putExtra("prayer", prayerName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check if we can schedule exact alarms
        if (NotificationService.canScheduleExactAlarms(context)) {
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
            Log.d("PrayerReminderReceiver", "Scheduled exact next day reminder for $prayerName")
        } else {
            // Fallback to inexact alarm
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            Log.d("PrayerReminderReceiver", "Scheduled inexact next day reminder for $prayerName (exact alarms not permitted)")
        }
    }
} 