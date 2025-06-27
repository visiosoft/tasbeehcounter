package com.example.tasbeehcounter

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.io.IOException

class AudioService : Service() {
    private val TAG = "AudioService"
    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioService created")
    }
    
    /**
     * Play Arabic audio saying "Allahu Akbar" for prayer notifications
     * This method handles both built-in audio and external audio files
     */
    fun playPrayerAudio() {
        try {
            // Release any existing MediaPlayer
            releaseMediaPlayer()
            
            // Create new MediaPlayer
            mediaPlayer = MediaPlayer()
            
            // Set audio attributes for notification usage
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                mediaPlayer?.setAudioAttributes(audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                mediaPlayer?.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
            }
            
            // Try to play the allahu_akbar audio first
            try {
                // Check if the resource exists first
                val resourceId = resources.getIdentifier("allahu_akbar", "raw", packageName)
                if (resourceId != 0) {
                    val assetFileDescriptor = resources.openRawResourceFd(resourceId)
                    mediaPlayer?.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
                    assetFileDescriptor.close()
                    Log.d(TAG, "Playing allahu_akbar audio file")
                } else {
                    throw Exception("allahu_akbar audio file not found")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load allahu_akbar audio file, trying click_sound", e)
                // Fallback to click sound
                try {
                    val clickSoundDescriptor = resources.openRawResourceFd(R.raw.click_sound)
                    mediaPlayer?.setDataSource(clickSoundDescriptor.fileDescriptor, clickSoundDescriptor.startOffset, clickSoundDescriptor.declaredLength)
                    clickSoundDescriptor.close()
                    Log.d(TAG, "Playing click_sound as fallback")
                } catch (e2: Exception) {
                    Log.w(TAG, "Could not load click_sound, using generated audio", e2)
                    // Final fallback: use generated audio
                    playGeneratedAllahuAkbarAudio()
                    return
                }
            }
            
            // Set up completion listener
            mediaPlayer?.setOnCompletionListener {
                Log.d(TAG, "Prayer audio completed")
                releaseMediaPlayer()
            }
            
            // Set up error listener
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                releaseMediaPlayer()
                // Try generated audio as last resort
                playGeneratedAllahuAkbarAudio()
                true
            }
            
            // Prepare and play
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            
            Log.d(TAG, "Prayer audio started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing prayer audio", e)
            releaseMediaPlayer()
            // Try generated audio as last resort
            playGeneratedAllahuAkbarAudio()
        }
    }
    
    /**
     * Play generated Allahu Akbar audio using ToneGenerator
     */
    private fun playGeneratedAllahuAkbarAudio() {
        try {
            Log.d(TAG, "Playing generated Allahu Akbar audio")
            
            // Use AudioGenerator to create Allahu Akbar sound
            AudioGenerator.generateAllahuAkbarAudio(this)
            
            Log.d(TAG, "Generated Allahu Akbar audio played successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing generated Allahu Akbar audio", e)
        }
    }
    
    /**
     * Play custom audio file from assets or raw resources
     */
    fun playCustomAudio(audioFileName: String) {
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer()
            
            // Set audio attributes
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                mediaPlayer?.setAudioAttributes(audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                mediaPlayer?.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
            }
            
            // Try to load from assets first
            try {
                val assetFileDescriptor = assets.openFd(audioFileName)
                mediaPlayer?.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
                assetFileDescriptor.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not load audio from assets: $audioFileName", e)
                // Try to load from raw resources
                try {
                    val resourceId = resources.getIdentifier(audioFileName, "raw", packageName)
                    if (resourceId != 0) {
                        val rawFileDescriptor = resources.openRawResourceFd(resourceId)
                        mediaPlayer?.setDataSource(rawFileDescriptor.fileDescriptor, rawFileDescriptor.startOffset, rawFileDescriptor.declaredLength)
                        rawFileDescriptor.close()
                    } else {
                        throw Exception("Audio file not found in raw resources")
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Could not load audio from raw resources: $audioFileName", e2)
                    // Fallback to default prayer audio
                    playPrayerAudio()
                    return
                }
            }
            
            // Set up listeners
            mediaPlayer?.setOnCompletionListener {
                Log.d(TAG, "Custom audio completed: $audioFileName")
                releaseMediaPlayer()
            }
            
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error for $audioFileName: what=$what, extra=$extra")
                releaseMediaPlayer()
                true
            }
            
            // Prepare and play
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            
            Log.d(TAG, "Custom audio started successfully: $audioFileName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing custom audio: $audioFileName", e)
            releaseMediaPlayer()
        }
    }
    
    /**
     * Stop current audio playback
     */
    fun stopAudio() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        } finally {
            releaseMediaPlayer()
        }
    }
    
    /**
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
    
    /**
     * Release MediaPlayer resources
     */
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
        Log.d(TAG, "AudioService destroyed")
    }
} 