package com.example.tasbeehcounter

import android.content.Context
import android.util.Log

/**
 * Helper class that provides comprehensive examples of how to use
 * the audio-enhanced prayer notification functionality
 */
class PrayerNotificationHelper {
    companion object {
        private const val TAG = "PrayerNotificationHelper"
        
        /**
         * Example 1: Send a basic prayer notification with default audio
         * This will show a notification and play the built-in "Allahu Akbar" audio
         */
        fun sendBasicPrayerNotification(context: Context, prayerName: String) {
            try {
                val notificationService = NotificationService()
                notificationService.sendPrayerNotificationWithAudio(context, prayerName)
                Log.d(TAG, "Basic prayer notification sent for $prayerName")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending basic prayer notification", e)
            }
        }
        
        /**
         * Example 2: Send prayer notification with custom audio file
         * This will show a notification and play a custom audio file
         * @param audioFileName Name of the audio file in raw resources (without extension)
         */
        fun sendPrayerNotificationWithCustomAudio(context: Context, prayerName: String, audioFileName: String) {
            try {
                val notificationService = NotificationService()
                notificationService.sendPrayerNotificationWithCustomAudio(context, prayerName, audioFileName)
                Log.d(TAG, "Prayer notification with custom audio sent for $prayerName using $audioFileName")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending prayer notification with custom audio", e)
            }
        }
        
        /**
         * Example 3: Send notifications for all prayers at once (for testing)
         */
        fun sendAllPrayerNotifications(context: Context) {
            val prayers = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
            
            prayers.forEachIndexed { index, prayer ->
                // Add a small delay between notifications to avoid overwhelming the user
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    sendBasicPrayerNotification(context, prayer)
                }, (index * 2000).toLong()) // 2 second delay between each
            }
            
            Log.d(TAG, "All prayer notifications scheduled")
        }
        
        /**
         * Example 4: Send prayer notification with specific timing
         * This is useful for testing or manual triggering
         */
        fun sendPrayerNotificationAtTime(context: Context, prayerName: String, delayMillis: Long = 0) {
            if (delayMillis > 0) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    sendBasicPrayerNotification(context, prayerName)
                }, delayMillis)
                Log.d(TAG, "Prayer notification scheduled for $prayerName in ${delayMillis}ms")
            } else {
                sendBasicPrayerNotification(context, prayerName)
            }
        }
        
        /**
         * Example 5: Send prayer notification with custom message and audio
         * This demonstrates how to customize the notification content
         */
        fun sendCustomPrayerNotification(context: Context, prayerName: String, customMessage: String, audioFileName: String? = null) {
            try {
                if (audioFileName != null) {
                    sendPrayerNotificationWithCustomAudio(context, prayerName, audioFileName)
                } else {
                    sendBasicPrayerNotification(context, prayerName)
                }
                
                Log.d(TAG, "Custom prayer notification sent for $prayerName with message: $customMessage")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending custom prayer notification", e)
            }
        }
        
        /**
         * Example 6: Test audio service directly
         * This allows testing the audio functionality without notifications
         */
        fun testAudioOnly(context: Context, audioFileName: String? = null) {
            try {
                val audioIntent = android.content.Intent(context, AudioService::class.java)
                
                // Start the audio service
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(audioIntent)
                } else {
                    context.startService(audioIntent)
                }
                
                // Play audio
                val audioService = AudioService()
                if (audioFileName != null) {
                    audioService.playCustomAudio(audioFileName)
                } else {
                    audioService.playPrayerAudio()
                }
                
                Log.d(TAG, "Audio test started")
            } catch (e: Exception) {
                Log.e(TAG, "Error testing audio", e)
            }
        }
        
        /**
         * Example 7: Stop current audio playback
         */
        fun stopAudio(_context: Context) {
            try {
                val audioService = AudioService()
                audioService.stopAudio()
                Log.d(TAG, "Audio stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio", e)
            }
        }
        
        /**
         * Example 8: Check if audio is currently playing
         */
        fun isAudioPlaying(_context: Context): Boolean {
            return try {
                val audioService = AudioService()
                audioService.isPlaying()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking audio status", e)
                false
            }
        }
        
        /**
         * Example 9: Send prayer notification with different audio files for different prayers
         * This demonstrates how to use different audio files for different prayer times
         */
        fun sendPrayerNotificationWithPrayerSpecificAudio(context: Context, prayerName: String) {
            val audioFileMap = mapOf(
                "fajr" to "fajr_adhan",
                "dhuhr" to "dhuhr_adhan", 
                "asr" to "asr_adhan",
                "maghrib" to "maghrib_adhan",
                "isha" to "isha_adhan"
            )
            
            val audioFileName = audioFileMap[prayerName]
            
            if (audioFileName != null) {
                sendPrayerNotificationWithCustomAudio(context, prayerName, audioFileName)
            } else {
                sendBasicPrayerNotification(context, prayerName)
            }
        }
        
        /**
         * Example 10: Comprehensive prayer notification with all features
         * This demonstrates the full functionality including notification, audio, and vibration
         */
        fun sendComprehensivePrayerNotification(context: Context, prayerName: String) {
            try {
                // Check if notifications are enabled
                val sharedPreferences = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
                val notificationsEnabled = sharedPreferences.getBoolean("notifications", true)
                
                if (!notificationsEnabled) {
                    Log.d(TAG, "Notifications are disabled")
                    return
                }
                
                // Send the notification with audio
                sendBasicPrayerNotification(context, prayerName)
                
                // Log the action
                Log.d(TAG, "Comprehensive prayer notification sent for $prayerName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending comprehensive prayer notification", e)
            }
        }
    }
} 