package com.example.tasbeehcounter

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sharedPreferences = requireContext().getSharedPreferences("TasbeehSettings", 0)
        
        setupSwitches()
        setupButtons()
    }

    private fun setupSwitches() {
        // Full Screen Switch
        binding.switchFullscreen.isChecked = sharedPreferences.getBoolean("fullscreen", false)
        binding.switchFullscreen.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("fullscreen", isChecked).apply()
        }

        // Vibration Switch
        binding.switchVibration.isChecked = sharedPreferences.getBoolean("vibration", true)
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("vibration", isChecked).apply()
        }

        // Dark Mode Switch
        binding.switchDarkMode.isChecked = sharedPreferences.getBoolean("dark_mode", false)
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    private fun setupButtons() {
        // Rate App Button
        binding.buttonRate.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=${requireContext().packageName}")
                })
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
                })
            }
        }

        // Share App Button
        binding.buttonShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Tasbeeh Counter App")
                putExtra(Intent.EXTRA_TEXT, "Check out this amazing Tasbeeh Counter app: https://play.google.com/store/apps/details?id=${requireContext().packageName}")
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 