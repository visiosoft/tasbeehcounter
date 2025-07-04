package com.example.tasbeehcounter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.tasbeehcounter.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        
        // Initialize notification service
        val notificationService = NotificationService()
        notificationService.createNotificationChannels(this)
        
        try {
            notificationService.schedulePrayerReminders(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error scheduling prayer reminders", e)
            // Continue without prayer reminders if scheduling fails
        }
        
        // Schedule daily missed tasbeeh check
        try {
            MissedTasbeehChecker.scheduleDailyCheck(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error scheduling daily tasbeeh check", e)
            // Continue without daily check if scheduling fails
        }
        
        // Auto-update prayer times when online
        autoUpdatePrayerTimesIfOnline()
        
        // Setup Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.navView.setupWithNavController(navController)

        // Set default values if not set
        if (!sharedPreferences.contains("vibration")) {
            sharedPreferences.edit().putBoolean("vibration", true).apply()
        }
        if (!sharedPreferences.contains("darkMode")) {
            sharedPreferences.edit().putBoolean("darkMode", false).apply()
        }
        if (!sharedPreferences.contains("notifications")) {
            sharedPreferences.edit().putBoolean("notifications", true).apply()
        }

        // Always enable fullscreen mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    override fun onResume() {
        super.onResume()
        // Re-enable fullscreen mode when activity resumes
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        // Auto-update prayer times when app resumes (in case internet became available)
        autoUpdatePrayerTimesIfOnline()
    }
    
    /**
     * Auto-update prayer times when online
     */
    private fun autoUpdatePrayerTimesIfOnline() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if we have stored prayer times
                val storedPrayerTimes = PrayerTimesManager.getPrayerTimes(this@MainActivity)
                
                if (storedPrayerTimes.isEmpty()) {
                    // No stored data, try to fetch fresh data
                    android.util.Log.d("MainActivity", "No stored prayer times, attempting to fetch fresh data")
                    PrayerTimesManager.autoUpdateWhenOnline(this@MainActivity)
                } else {
                    // We have stored data, check if it's recent
                    val lastFetchDate = PrayerTimesManager.getLastFetchDate(this@MainActivity)
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    if (lastFetchDate != today) {
                        // Data is old, try to update
                        android.util.Log.d("MainActivity", "Stored prayer times are old, attempting auto-update")
                        PrayerTimesManager.autoUpdateWhenOnline(this@MainActivity)
                    } else {
                        android.util.Log.d("MainActivity", "Stored prayer times are current, no update needed")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error during auto-update", e)
            }
        }
    }
}