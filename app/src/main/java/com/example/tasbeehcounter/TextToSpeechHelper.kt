package com.example.tasbeehcounter

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * Helper class for Text-to-Speech functionality
 * This can speak "Allahu Akbar" in Arabic
 */
class TextToSpeechHelper(private val context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private val TAG = "TextToSpeechHelper"
    
    init {
        initializeTextToSpeech()
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TextToSpeech initialized successfully")
                
                // Try to set Arabic language
                val result = textToSpeech?.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Arabic language not supported, using default")
                    textToSpeech?.setLanguage(Locale.getDefault())
                }
                
                // Set speech rate and pitch
                textToSpeech?.setSpeechRate(0.8f)
                textToSpeech?.setPitch(1.0f)
                
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }
    
    /**
     * Speak "Allahu Akbar" in Arabic
     */
    fun speakAllahuAkbar() {
        try {
            val text = "الله أكبر" // Allahu Akbar in Arabic
            val utteranceId = "allahu_akbar_${System.currentTimeMillis()}"
            
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            Log.d(TAG, "Speaking: $text")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking Allahu Akbar", e)
        }
    }
    
    /**
     * Speak custom text in Arabic
     */
    fun speakArabicText(text: String) {
        try {
            val utteranceId = "arabic_text_${System.currentTimeMillis()}"
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            Log.d(TAG, "Speaking Arabic text: $text")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking Arabic text", e)
        }
    }
    
    /**
     * Speak prayer time notification
     */
    fun speakPrayerTime(prayerName: String) {
        try {
            val prayerText = when (prayerName.lowercase()) {
                "fajr" -> "حان وقت صلاة الفجر"
                "dhuhr" -> "حان وقت صلاة الظهر"
                "asr" -> "حان وقت صلاة العصر"
                "maghrib" -> "حان وقت صلاة المغرب"
                "isha" -> "حان وقت صلاة العشاء"
                else -> "حان وقت الصلاة"
            }
            
            val utteranceId = "prayer_time_${System.currentTimeMillis()}"
            textToSpeech?.speak(prayerText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            Log.d(TAG, "Speaking prayer time: $prayerText")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking prayer time", e)
        }
    }
    
    /**
     * Check if TTS is available
     */
    fun isAvailable(): Boolean {
        return textToSpeech?.isLanguageAvailable(Locale("ar")) == TextToSpeech.LANG_AVAILABLE ||
               textToSpeech?.isLanguageAvailable(Locale.getDefault()) == TextToSpeech.LANG_AVAILABLE
    }
    
    /**
     * Get available languages
     */
    fun getAvailableLanguages(): List<Locale> {
        val languages = mutableListOf<Locale>()
        
        // Check common languages
        val commonLanguages = listOf(
            Locale("ar"), // Arabic
            Locale("en"), // English
            Locale("ur"), // Urdu
            Locale("tr"), // Turkish
            Locale("ms"), // Malay
            Locale.getDefault() // System default
        )
        
        for (locale in commonLanguages) {
            if (textToSpeech?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE) {
                languages.add(locale)
            }
        }
        
        return languages
    }
    
    /**
     * Release resources
     */
    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        Log.d(TAG, "TextToSpeech released")
    }
} 