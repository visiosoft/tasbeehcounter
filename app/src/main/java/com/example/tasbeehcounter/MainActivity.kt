package com.example.tasbeehcounter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
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
                else -> false
            }
        }

        // Set default selection
        binding.bottomNavigation.selectedItemId = R.id.navigation_tasbeeh
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}