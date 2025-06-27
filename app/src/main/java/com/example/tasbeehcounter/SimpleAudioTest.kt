package com.example.tasbeehcounter

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * Simple audio test class to directly test Allahu Akbar audio
 */
class SimpleAudioTest {
    companion object {
        private const val TAG = "SimpleAudioTest"
        
        /**
         * Direct test of Allahu Akbar audio file
         */
        fun testAllahuAkbarAudio(context: Context) {
            try {
                Log.d(TAG, "Testing Allahu Akbar audio directly...")
                
                val mediaPlayer = MediaPlayer()
                
                // Try to load the audio file
                val resourceId = context.resources.getIdentifier("allahu_akbar", "raw", context.packageName)
                if (resourceId != 0) {
                    Log.d(TAG, "Found allahu_akbar resource with ID: $resourceId")
                    
                    val assetFileDescriptor = context.resources.openRawResourceFd(resourceId)
                    mediaPlayer.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
                    assetFileDescriptor.close()
                    
                    mediaPlayer.setOnPreparedListener {
                        Log.d(TAG, "MediaPlayer prepared successfully")
                        mediaPlayer.start()
                        Log.d(TAG, "Allahu Akbar audio started")
                    }
                    
                    mediaPlayer.setOnCompletionListener {
                        Log.d(TAG, "Allahu Akbar audio completed")
                        mediaPlayer.release()
                    }
                    
                    mediaPlayer.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        mediaPlayer.release()
                        true
                    }
                    
                    mediaPlayer.prepareAsync()
                    
                } else {
                    Log.e(TAG, "allahu_akbar resource not found!")
                    mediaPlayer.release()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing Allahu Akbar audio", e)
            }
        }
        
        /**
         * Test with fallback options
         */
        fun testAllahuAkbarWithFallbacks(context: Context) {
            Log.d(TAG, "=== Testing Allahu Akbar with Fallbacks ===")
            
            // Test 1: Direct MP3 file
            testAllahuAkbarAudio(context)
            
            // Test 2: Generated audio (after delay)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Testing generated audio fallback...")
                AudioGenerator.generateAllahuAkbarAudio(context)
            }, 3000) // 3 second delay
            
            // Test 3: TTS (after longer delay)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Testing TTS fallback...")
                val ttsHelper = TextToSpeechHelper(context)
                if (ttsHelper.isAvailable()) {
                    ttsHelper.speakAllahuAkbar()
                } else {
                    Log.w(TAG, "TTS not available")
                }
            }, 6000) // 6 second delay
        }
        
        /**
         * Check audio file status
         */
        fun checkAudioFileStatus(context: Context) {
            Log.d(TAG, "=== Audio File Status Check ===")
            
            try {
                val resourceId = context.resources.getIdentifier("allahu_akbar", "raw", context.packageName)
                Log.d(TAG, "allahu_akbar resource ID: $resourceId")
                
                if (resourceId != 0) {
                    val assetFileDescriptor = context.resources.openRawResourceFd(resourceId)
                    Log.d(TAG, "File descriptor: ${assetFileDescriptor.fileDescriptor}")
                    Log.d(TAG, "Start offset: ${assetFileDescriptor.startOffset}")
                    Log.d(TAG, "Declared length: ${assetFileDescriptor.declaredLength}")
                    assetFileDescriptor.close()
                } else {
                    Log.e(TAG, "allahu_akbar resource not found!")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking audio file status", e)
            }
        }
        
        /**
         * Play Allahu Akbar audio (10 seconds) with auto-stop
         */
        fun playAllahuAkbarAudio(context: Context) {
            try {
                Log.d(TAG, "Playing Allahu Akbar audio...")
                
                val mediaPlayer = MediaPlayer()
                
                // Try to load the audio file
                val resourceId = context.resources.getIdentifier("allahu_akbar", "raw", context.packageName)
                if (resourceId != 0) {
                    Log.d(TAG, "Found allahu_akbar resource with ID: $resourceId")
                    
                    val assetFileDescriptor = context.resources.openRawResourceFd(resourceId)
                    mediaPlayer.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
                    assetFileDescriptor.close()
                    
                    mediaPlayer.setOnPreparedListener {
                        Log.d(TAG, "MediaPlayer prepared successfully")
                        mediaPlayer.start()
                        Log.d(TAG, "Allahu Akbar audio started")
                        
                        // Stop after 10 seconds automatically
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.stop()
                                    Log.d(TAG, "Allahu Akbar audio stopped automatically")
                                }
                                mediaPlayer.release()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error stopping audio", e)
                            }
                        }, 10000) // 10 seconds
                    }
                    
                    mediaPlayer.setOnCompletionListener {
                        Log.d(TAG, "Allahu Akbar audio completed")
                        mediaPlayer.release()
                    }
                    
                    mediaPlayer.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        mediaPlayer.release()
                        true
                    }
                    
                    mediaPlayer.prepareAsync()
                    
                } else {
                    Log.e(TAG, "allahu_akbar resource not found!")
                    mediaPlayer.release()
                    // Fallback to generated short audio
                    playShortGeneratedAudio(context)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error playing Allahu Akbar audio", e)
                // Fallback to generated short audio
                playShortGeneratedAudio(context)
            }
        }
        
        /**
         * Play short generated audio (2-4 seconds)
         */
        private fun playShortGeneratedAudio(context: Context) {
            try {
                Log.d(TAG, "Playing short generated audio...")
                
                val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                
                // Short Allahu Akbar pattern (2-3 seconds total)
                toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        toneGenerator.release()
                        Log.d(TAG, "Short generated audio completed")
                    }, 900)
                }, 600)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error playing short generated audio", e)
            }
        }
    }
} 