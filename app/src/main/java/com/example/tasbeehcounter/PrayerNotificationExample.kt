package com.example.tasbeehcounter

import android.content.Context
import android.util.Log

/**
 * Simple example class demonstrating prayer notification with Arabic audio
 * This is the main function requested by the user
 */
class PrayerNotificationExample {
    companion object {
        private const val TAG = "PrayerNotificationExample"
        
        /**
         * Main function: Send prayer notification with Arabic audio "Allahu Akbar"
         * This function sends a local notification for prayer time and plays Arabic audio
         * The audio plays even if the app is in the background
         * 
         * @param context Application context
         * @param prayerName Name of the prayer (fajr, dhuhr, asr, maghrib, isha)
         */
        fun sendPrayerNotificationWithAudio(context: Context, prayerName: String) {
            try {
                Log.d(TAG, "Sending prayer notification with audio for: $prayerName")
                
                // Use the enhanced notification service
                val notificationService = NotificationService()
                notificationService.sendPrayerNotificationWithAudio(context, prayerName)
                
                Log.d(TAG, "Prayer notification with audio sent successfully for $prayerName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending prayer notification with audio", e)
            }
        }
        
        /**
         * Alternative function: Send prayer notification with custom audio file
         * 
         * @param context Application context
         * @param prayerName Name of the prayer
         * @param audioFileName Name of the audio file in raw resources (without extension)
         */
        fun sendPrayerNotificationWithCustomAudio(context: Context, prayerName: String, audioFileName: String) {
            try {
                Log.d(TAG, "Sending prayer notification with custom audio: $audioFileName for $prayerName")
                
                val notificationService = NotificationService()
                notificationService.sendPrayerNotificationWithCustomAudio(context, prayerName, audioFileName)
                
                Log.d(TAG, "Prayer notification with custom audio sent successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending prayer notification with custom audio", e)
            }
        }
        
        /**
         * Test function: Demonstrate the functionality
         */
        fun testPrayerNotificationWithAudio(context: Context) {
            // Test with Fajr prayer
            sendPrayerNotificationWithAudio(context, "fajr")
        }
        
        /**
         * Test function: Demonstrate custom audio functionality
         */
        fun testPrayerNotificationWithCustomAudio(context: Context) {
            // Test with custom audio file (if available)
            sendPrayerNotificationWithCustomAudio(context, "dhuhr", "allahu_akbar")
        }
    }
} 