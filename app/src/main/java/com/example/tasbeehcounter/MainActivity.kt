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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        sharedPreferences = getSharedPreferences("TasbeehSettings", Context.MODE_PRIVATE)
        
        // Setup Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.navView.setupWithNavController(navController)

        // Set default values if not set
        if (!sharedPreferences.contains("vibration")) {
            sharedPreferences.edit().putBoolean("vibration", false).apply()
        }
        if (!sharedPreferences.contains("dark_mode")) {
            sharedPreferences.edit().putBoolean("dark_mode", false).apply()
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
    }
}