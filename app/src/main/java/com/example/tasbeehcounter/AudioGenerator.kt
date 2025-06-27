package com.example.tasbeehcounter

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class to generate simple audio for "Allahu Akbar"
 * This creates a basic audio file using Android's built-in tone generation
 */
class AudioGenerator {
    companion object {
        private const val TAG = "AudioGenerator"
        
        /**
         * Generate a simple "Allahu Akbar" audio file
         * This creates a basic audio pattern that can be used as a placeholder
         */
        fun generateAllahuAkbarAudio(context: Context): Boolean {
            try {
                Log.d(TAG, "Generating Allahu Akbar audio...")
                
                // Create a simple audio pattern using ToneGenerator
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                
                // Generate a sequence of tones to represent "Allahu Akbar"
                // This is a simplified version - in a real app you'd want actual voice recording
                
                // First tone (Allah)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                Thread.sleep(600)
                
                // Second tone (u)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                Thread.sleep(400)
                
                // Third tone (Akbar)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
                Thread.sleep(900)
                
                toneGenerator.release()
                
                Log.d(TAG, "Allahu Akbar audio generated successfully")
                return true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating Allahu Akbar audio", e)
                return false
            }
        }
        
        /**
         * Generate a more complex takbeer pattern
         */
        fun generateTakbeerPattern(context: Context): Boolean {
            try {
                Log.d(TAG, "Generating takbeer pattern...")
                
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                
                // Takbeer pattern: Allahu Akbar (repeated)
                for (i in 1..3) {
                    // Allahu
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    Thread.sleep(500)
                    
                    // Akbar
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                    Thread.sleep(700)
                    
                    // Pause between repetitions
                    if (i < 3) {
                        Thread.sleep(300)
                    }
                }
                
                toneGenerator.release()
                
                Log.d(TAG, "Takbeer pattern generated successfully")
                return true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating takbeer pattern", e)
                return false
            }
        }
        
        /**
         * Generate prayer time notification sound
         */
        fun generatePrayerTimeSound(context: Context): Boolean {
            try {
                Log.d(TAG, "Generating prayer time notification sound...")
                
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                
                // Prayer time notification pattern
                // Short alert followed by longer tone
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                Thread.sleep(300)
                
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                Thread.sleep(1100)
                
                toneGenerator.release()
                
                Log.d(TAG, "Prayer time sound generated successfully")
                return true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating prayer time sound", e)
                return false
            }
        }
        
        /**
         * Test all audio generation functions
         */
        fun testAllAudioGeneration(context: Context) {
            Log.d(TAG, "=== Testing All Audio Generation ===")
            
            val results = mutableListOf<Boolean>()
            
            // Test 1: Simple Allahu Akbar
            results.add(generateAllahuAkbarAudio(context))
            
            // Wait between tests
            Thread.sleep(1000)
            
            // Test 2: Takbeer pattern
            results.add(generateTakbeerPattern(context))
            
            // Wait between tests
            Thread.sleep(1000)
            
            // Test 3: Prayer time sound
            results.add(generatePrayerTimeSound(context))
            
            // Log results
            Log.d(TAG, "Audio generation test results:")
            Log.d(TAG, "Allahu Akbar: ${if (results[0]) "SUCCESS" else "FAILED"}")
            Log.d(TAG, "Takbeer Pattern: ${if (results[1]) "SUCCESS" else "FAILED"}")
            Log.d(TAG, "Prayer Time Sound: ${if (results[2]) "SUCCESS" else "FAILED"}")
            
            Log.d(TAG, "=== Audio Generation Test Complete ===")
        }
    }
} 