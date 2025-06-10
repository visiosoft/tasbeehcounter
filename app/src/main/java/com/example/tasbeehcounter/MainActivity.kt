package com.example.tasbeehcounter

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.View

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set Tasbeeh as default selected item
        binding.bottomNavigation.selectedItemId = R.id.navigation_tasbeeh
        
        setupBottomNavigation()
        
        // Show TasbeehFragment by default
        if (savedInstanceState == null) {
            showFragment(TasbeehFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_tasbeeh -> {
                    showFragment(TasbeehFragment())
                    true
                }
                R.id.navigation_qibla -> {
                    showFragment(QiblaFragment())
                    true
                }
                R.id.navigation_namaz -> {
                    showFragment(NamazFragment())
                    true
                }
                R.id.navigation_settings -> {
                    showFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        updateFullscreenMode()
    }

    fun updateFullscreenMode() {
        val sharedPreferences = getSharedPreferences("TasbeehSettings", 0)
        if (sharedPreferences.getBoolean("fullscreen", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}