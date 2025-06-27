package com.example.tasbeehcounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.tasbeehcounter.databinding.FragmentSettingsBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var autoLocationSwitch: SwitchMaterial
    private lateinit var vibrationSwitch: SwitchMaterial
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var exactAlarmButton: com.google.android.material.button.MaterialButton

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, enable notifications
            val prefs = requireContext().getSharedPreferences("Settings", 0)
            prefs.edit().putBoolean("notifications", true).apply()
            notificationSwitch.isChecked = true
            
            // Schedule prayer reminders and daily missed tasbeeh check
            val notificationService = NotificationService()
            notificationService.schedulePrayerReminders(requireContext())
            notificationService.scheduleAutomaticSync(requireContext())
            MissedTasbeehChecker.scheduleDailyCheck(requireContext())
            
            Toast.makeText(requireContext(), "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied, disable notifications
            val prefs = requireContext().getSharedPreferences("Settings", 0)
            prefs.edit().putBoolean("notifications", false).apply()
            notificationSwitch.isChecked = false
            
            // Cancel all notifications and daily checks
            val notificationService = NotificationService()
            notificationService.cancelAllNotifications(requireContext())
            notificationService.cancelAutomaticSync(requireContext())
            MissedTasbeehChecker.cancelDailyCheck(requireContext())
            
            Toast.makeText(requireContext(), "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

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
        notificationSwitch = binding.notificationSwitch
        autoLocationSwitch = binding.autoLocationSwitch
        vibrationSwitch = binding.vibrationSwitch
        darkModeSwitch = binding.darkModeSwitch
        exactAlarmButton = binding.exactAlarmButton

        // Load saved preferences
        val prefs = requireContext().getSharedPreferences("Settings", 0)
        notificationSwitch.isChecked = prefs.getBoolean("notifications", true)
        autoLocationSwitch.isChecked = prefs.getBoolean("autoLocation", true)
        vibrationSwitch.isChecked = prefs.getBoolean("vibration", true)
        darkModeSwitch.isChecked = prefs.getBoolean("darkMode", false)
        
        // Update exact alarm button text based on permission status
        updateExactAlarmButtonText()
    }

    private fun updateExactAlarmButtonText() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (NotificationService.canScheduleExactAlarms(requireContext())) {
                exactAlarmButton.text = "Exact Alarms Enabled ✓"
                exactAlarmButton.isEnabled = false
            } else {
                exactAlarmButton.text = "Enable Exact Alarms"
                exactAlarmButton.isEnabled = true
            }
        } else {
            exactAlarmButton.text = "Exact Alarms Not Required"
            exactAlarmButton.isEnabled = false
        }
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
            if (isChecked) {
                // Check if notification permission is granted (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // Permission already granted
                        editor.putBoolean("notifications", true)
                        editor.apply()
                        
                        // Schedule prayer reminders and daily missed tasbeeh check
                        val notificationService = NotificationService()
                        notificationService.schedulePrayerReminders(requireContext())
                        notificationService.scheduleAutomaticSync(requireContext())
                        MissedTasbeehChecker.scheduleDailyCheck(requireContext())
                    } else {
                        // Request permission
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@setOnCheckedChangeListener
                    }
                } else {
                    // Android 12 and below don't need explicit permission
                    editor.putBoolean("notifications", true)
                    editor.apply()
                    
                    // Schedule prayer reminders and daily missed tasbeeh check
                    val notificationService = NotificationService()
                    notificationService.schedulePrayerReminders(requireContext())
                    notificationService.scheduleAutomaticSync(requireContext())
                    MissedTasbeehChecker.scheduleDailyCheck(requireContext())
                }
            } else {
                // Disable notifications
                editor.putBoolean("notifications", false)
                editor.apply()
                
                // Cancel all notifications and daily checks
                val notificationService = NotificationService()
                notificationService.cancelAllNotifications(requireContext())
                notificationService.cancelAutomaticSync(requireContext())
                MissedTasbeehChecker.cancelDailyCheck(requireContext())
            }
        }

        autoLocationSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("autoLocation", isChecked)
            editor.apply()
        }

        exactAlarmButton.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                NotificationService.requestExactAlarmPermission(requireContext())
                Toast.makeText(requireContext(), "Please enable exact alarms in system settings", Toast.LENGTH_LONG).show()
            }
        }

        binding.testPrayerNotificationsButton.setOnClickListener {
            testPrayerNotifications()
        }

        binding.testMissedTasbeehButton.setOnClickListener {
            testMissedTasbeehAlert()
        }

        binding.refreshAccurateLocationButton.setOnClickListener {
            refreshPrayerTimesWithAccurateLocation()
        }

        binding.rateButton.setOnClickListener {
            openPlayStore()
        }

        binding.shareButton.setOnClickListener {
            shareApp()
        }

        binding.feedbackButton.setOnClickListener {
            sendFeedback()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Update exact alarm button text when returning from settings
        updateExactAlarmButtonText()
    }

    private fun refreshPrayerTimesWithAccurateLocation() {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Clearing old data and fetching fresh online prayer times...", Toast.LENGTH_SHORT).show()
                val prayerTimes = PrayerTimesManager.clearAllStoredDataAndFetchFresh(requireContext())
                if (prayerTimes != null) {
                    Toast.makeText(requireContext(), "Fresh prayer times loaded for ${prayerTimes.location}!", Toast.LENGTH_LONG).show()
                    // Add a small delay to ensure data is properly saved
                    delay(1000)
                } else {
                    Toast.makeText(requireContext(), "Failed to fetch fresh online data", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun testPrayerNotifications() {
        val notificationService = NotificationService()
        notificationService.sendPrayerNotificationWithAudio(requireContext(), "fajr")
        Toast.makeText(requireContext(), "Test prayer notification with audio sent!", Toast.LENGTH_SHORT).show()
    }

    private fun testMissedTasbeehAlert() {
        val notificationService = NotificationService()
        notificationService.testMissedTasbeehNotification(requireContext())
        Toast.makeText(requireContext(), "Test missed tasbeeh notification with bilingual quote sent!", Toast.LENGTH_SHORT).show()
    }

    private fun openPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("market://details?id=${requireContext().packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to web browser if Play Store app is not available
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
            startActivity(intent)
        }
    }

    private fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Tasbeeh Counter App")
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this amazing Tasbeeh Counter app with prayer times and daily dhikr tracking!\n\nDownload it from: https://play.google.com/store/apps/details?id=${requireContext().packageName}")
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error sharing app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendFeedback() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:infoniaziseo@gmail.com")
            intent.putExtra(Intent.EXTRA_SUBJECT, "Tasbeeh Counter App Feedback")
            intent.putExtra(Intent.EXTRA_TEXT, "Dear Developer,\n\nI would like to provide feedback about the Tasbeeh Counter app:\n\n")
            startActivity(Intent.createChooser(intent, "Send Feedback"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error sending feedback: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 