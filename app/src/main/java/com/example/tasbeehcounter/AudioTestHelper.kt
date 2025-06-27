package com.example.tasbeehcounter

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Helper class to test and debug audio functionality
 */
class AudioTestHelper {
    companion object {
        private const val TAG = "AudioTestHelper"
        
        /**
         * Test if audio system is working
         */
        fun testAudioSystem(context: Context) {
            Log.d(TAG, "=== Audio System Test ===")
            
            // Check audio manager
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            
            Log.d(TAG, "Notification volume: $currentVolume/$maxVolume")
            Log.d(TAG, "Audio mode: ${audioManager.mode}")
            Log.d(TAG, "Speaker on: ${audioManager.isSpeakerphoneOn}")
            
            // Test audio service
            try {
                val audioService = AudioService()
                Log.d(TAG, "AudioService created successfully")
                
                // Test if audio is playing
                val isPlaying = audioService.isPlaying()
                Log.d(TAG, "Audio currently playing: $isPlaying")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing audio service", e)
            }
            
            // Check raw resources
            try {
                val allahuAkbarExists = context.resources.getIdentifier("allahu_akbar", "raw", context.packageName) != 0
                val clickSoundExists = context.resources.getIdentifier("click_sound", "raw", context.packageName) != 0
                
                Log.d(TAG, "allahu_akbar exists: $allahuAkbarExists")
                Log.d(TAG, "click_sound exists: $clickSoundExists")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking raw resources", e)
            }
            
            Log.d(TAG, "=== Audio System Test Complete ===")
        }
        
        /**
         * Test prayer notification with audio
         */
        fun testPrayerNotificationWithAudio(context: Context) {
            Log.d(TAG, "Testing prayer notification with audio...")
            
            // First test the audio system
            testAudioSystem(context)
            
            // Then send a test notification
            PrayerNotificationExample.testPrayerNotificationWithAudio(context)
            
            Log.d(TAG, "Prayer notification test completed")
        }
        
        /**
         * Test audio only (without notification)
         */
        fun testAudioOnly(context: Context) {
            Log.d(TAG, "Testing audio only...")
            
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
                audioService.playPrayerAudio()
                
                Log.d(TAG, "Audio test started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing audio only", e)
            }
        }
    }
} 