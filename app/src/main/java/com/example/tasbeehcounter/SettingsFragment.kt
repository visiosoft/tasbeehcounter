package com.example.tasbeehcounter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentSettingsBinding
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var vibrationSwitch: SwitchMaterial
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var autoLocationSwitch: SwitchMaterial

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
        initializeSwitches()
        setupListeners()
    }

    private fun initializeSwitches() {
        vibrationSwitch = binding.vibrationSwitch
        darkModeSwitch = binding.darkModeSwitch
        notificationSwitch = binding.notificationSwitch
        autoLocationSwitch = binding.autoLocationSwitch

        // Load saved preferences
        val prefs = requireContext().getSharedPreferences("Settings", 0)
        vibrationSwitch.isChecked = prefs.getBoolean("vibration", true)
        darkModeSwitch.isChecked = prefs.getBoolean("darkMode", false)
        notificationSwitch.isChecked = prefs.getBoolean("notifications", true)
        autoLocationSwitch.isChecked = prefs.getBoolean("autoLocation", true)
    }

    private fun setupListeners() {
        val prefs = requireContext().getSharedPreferences("Settings", 0)
        val editor = prefs.edit()

        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("vibration", isChecked)
            editor.apply()
        }

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("darkMode", isChecked)
            editor.apply()
            // Apply theme change
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("notifications", isChecked)
            editor.apply()
        }

        autoLocationSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("autoLocation", isChecked)
            editor.apply()
        }

        binding.rateButton.setOnClickListener {
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

        binding.feedbackButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // Add your support email here
                putExtra(Intent.EXTRA_SUBJECT, "Tasbeeh Counter Feedback")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Handle case where no email app is available
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 