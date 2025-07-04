package com.example.tasbeehcounter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.example.tasbeehcounter.databinding.FragmentNamazBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.net.URL
import org.json.JSONObject
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class NamazFragment : Fragment() {
    private var _binding: FragmentNamazBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var prayerTimes: PrayerTimes? = null
    private var currentCityName: String? = null
    private lateinit var notificationManager: NotificationManager
    
    private var quoteChangeJob: Job? = null
    private var currentQuoteIndex = 0
    private var isEnglish = true
    private var isRefreshingData = false // Flag to prevent onResume from overriding fresh data
    private var hasShownGpsNetworkError = false // Flag to track if GPS/network error notification was shown
    private var vibrator: Vibrator? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                getCurrentLocation()
                // Don't show notification for successful permission grant
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
                getCurrentLocation()
                // Don't show notification for successful permission grant
            }
            else -> {
                // No location access granted
                showLocationNotification("Location access required", "Please grant location permission for accurate prayer times")
                Toast.makeText(context, "Location permission is required for accurate prayer times", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNamazBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNotificationChannel()
        setupLocationServices()
        setupUI()
        updateIslamicQuotes()
        
        // Initialize vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // Automatically check location permission on start
        checkLocationPermission()
    }

    private fun setupNotificationChannel() {
        notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location_channel",
                "Location Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for location updates"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showLocationNotification(title: String, message: String) {
        // Check if this is a GPS/network error notification
        val isGpsNetworkError = title.contains("Location Error", ignoreCase = true) || 
                               message.contains("GPS", ignoreCase = true) ||
                               message.contains("network", ignoreCase = true) ||
                               message.contains("Unable to get location", ignoreCase = true)
        
        // If it's a GPS/network error and we've already shown one, don't show another
        if (isGpsNetworkError && hasShownGpsNetworkError) {
            return
        }
        
        // If it's a GPS/network error, mark that we've shown it
        if (isGpsNetworkError) {
            hasShownGpsNetworkError = true
        }
        
        val notification = NotificationCompat.Builder(requireContext(), "location_channel")
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun setupUI() {
        binding.locationButton.setOnClickListener {
            if (!isLocationPermissionGranted()) {
                requestLocationPermission()
            } else {
                // Use enhanced prayer times manager to refresh location and prayer times
                refreshLocationAndPrayerTimes()
            }
        }
        
        // Make quotes card clickable for tasbeeh counting
        binding.quotesCard.setOnClickListener {
            // Count tasbeeh when quotes card is tapped
            incrementTasbeehCount()
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private fun checkLocationPermission() {
        when {
            isLocationPermissionGranted() -> {
                getCurrentLocation()
            }
            else -> {
                requestLocationPermission()
            }
        }
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun getCurrentLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLocation = it
                        getCityNameFromLocation(it)
                    } ?: run {
                        // If location is null, try to get last known location
                        getLastKnownLocation()
                    }
                }
                .addOnFailureListener {
                    // If getting current location fails, try last known location
                    getLastKnownLocation()
                }
        } catch (e: SecurityException) {
            requestLocationPermission()
        }
    }

    private fun getLastKnownLocation() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLocation = it
                        getCityNameFromLocation(it)
                    } ?: run {
                        showLocationNotification("Location Error", "Unable to get location. Please check your GPS settings.")
                        Toast.makeText(context, "Unable to get location. Please check your GPS settings.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    showLocationNotification("Location Error", "Unable to get location. Please check your GPS settings.")
                    Toast.makeText(context, "Unable to get location. Please check your GPS settings.", Toast.LENGTH_LONG).show()
                }
        } catch (e: SecurityException) {
            requestLocationPermission()
        }
    }

    private fun getCityNameFromLocation(location: Location) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Round coordinates to 2 decimal places for a wider area search
                val roundedLat = Math.round(location.latitude * 100.0) / 100.0
                val roundedLon = Math.round(location.longitude * 100.0) / 100.0
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$roundedLat&lon=$roundedLon&accept-language=en&zoom=10"
                val response = URL(url).readText()
                val json = JSONObject(response)
                val address = json.getJSONObject("address")
                val city = address.optString("city") ?: address.optString("town") ?: address.optString("village")
                val state = address.optString("state", "")
                val province = address.optString("county", "")
                
                withContext(Dispatchers.Main) {
                    // Check if fragment is still active before updating UI
                    if (isAdded && !isDetached && _binding != null) {
                        currentCityName = city
                        val locationText = when {
                            city.isNotEmpty() && province.isNotEmpty() -> "$city, $province"
                            city.isNotEmpty() && state.isNotEmpty() -> "$city, $state"
                            city.isNotEmpty() -> city
                            state.isNotEmpty() -> state
                            else -> getDefaultMainCity()
                        }
                        updateLocationText(locationText)
                        // Don't show notification for successful location updates to reduce spam
                        updatePrayerTimes()
                        
                        // Reset GPS/network error flag since location was successfully obtained
                        hasShownGpsNetworkError = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Check if fragment is still active before updating UI
                    if (isAdded && !isDetached && _binding != null) {
                        updateLocationText(getDefaultMainCity())
                        updatePrayerTimes()
                    }
                }
            }
        }
    }

    private fun getDefaultMainCity(): String {
        // Get the timezone to determine the region
        val timeZone = TimeZone.getDefault()
        val timeZoneId = timeZone.id.lowercase()
        
        return when {
            timeZoneId.contains("asia") -> {
                when {
                    timeZoneId.contains("karachi") -> "Karachi, Sindh"
                    timeZoneId.contains("lahore") -> "Lahore, Punjab"
                    timeZoneId.contains("islamabad") -> "Islamabad"
                    timeZoneId.contains("dubai") -> "Dubai, UAE"
                    timeZoneId.contains("riyadh") -> "Riyadh, Saudi Arabia"
                    timeZoneId.contains("istanbul") -> "Istanbul, Turkey"
                    else -> "Mecca, Saudi Arabia"
                }
            }
            timeZoneId.contains("europe") -> {
                when {
                    timeZoneId.contains("london") -> "London, UK"
                    timeZoneId.contains("paris") -> "Paris, France"
                    timeZoneId.contains("berlin") -> "Berlin, Germany"
                    else -> "London, UK"
                }
            }
            timeZoneId.contains("america") -> {
                when {
                    timeZoneId.contains("new_york") -> "New York, USA"
                    timeZoneId.contains("los_angeles") -> "Los Angeles, USA"
                    timeZoneId.contains("chicago") -> "Chicago, USA"
                    else -> "New York, USA"
                }
            }
            else -> "Mecca, Saudi Arabia" // Default to Mecca if no specific region is detected
        }
    }

    private fun updateLocationText(text: String) {
        _binding?.locationText?.text = text
    }

    private fun updatePrayerTimes() {
        currentLocation?.let { location ->
            val coordinates = Coordinates(location.latitude, location.longitude)
            val params = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            params.madhab = com.batoulapps.adhan.Madhab.HANAFI

            val date = DateComponents.from(Date())
            prayerTimes = PrayerTimes(coordinates, date, params)
            updatePrayerTimesUI()
        }
    }

    private fun updatePrayerTimesUI() {
        prayerTimes?.let { times ->
            val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = Date()
            
            _binding?.let { binding ->
                binding.namazDateText.text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date)
                binding.fajrTime.text = dateFormat.format(times.fajr)
                binding.dhuhrTime.text = dateFormat.format(times.dhuhr)
                binding.asrTime.text = dateFormat.format(times.asr)
                binding.maghribTime.text = dateFormat.format(times.maghrib)
                binding.ishaTime.text = dateFormat.format(times.isha)

                // Show next prayer time
                val nextPrayer = when {
                    date.before(times.fajr) -> "Fajr"
                    date.before(times.dhuhr) -> "Dhuhr"
                    date.before(times.asr) -> "Asr"
                    date.before(times.maghrib) -> "Maghrib"
                    date.before(times.isha) -> "Isha"
                    else -> "Fajr (Tomorrow)"
                }
                
                binding.namazNextPrayerText.text = "Next Prayer: $nextPrayer"
            }
        }
    }

    private fun updateIslamicQuotes() {
        currentQuoteIndex = 0
        changeQuote()
        startAutoQuoteChange() // Start auto-changing quotes
    }

    private fun startAutoQuoteChange() {
        quoteChangeJob?.cancel() // Cancel any existing job
        quoteChangeJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                while (true) {
                    delay(10000) // 10 seconds delay
                    if (isActive && isAdded && !isDetached && _binding != null) {
                        changeQuote()
                    } else {
                        break // Exit the loop if fragment is no longer active
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopAutoQuoteChange() {
        quoteChangeJob?.cancel()
        quoteChangeJob = null
    }

    private fun changeQuote() {
        val englishQuotes = listOf(
            getString(R.string.quote_1),
            getString(R.string.quote_2),
            getString(R.string.quote_3),
            getString(R.string.quote_4),
            getString(R.string.quote_5),
            getString(R.string.quote_6),
            getString(R.string.quote_7),
            getString(R.string.quote_8)
        )
        
        val urduQuotes = listOf(
            getString(R.string.quote_1_urdu),
            getString(R.string.quote_2_urdu),
            getString(R.string.quote_3_urdu),
            getString(R.string.quote_4_urdu),
            getString(R.string.quote_5_urdu),
            getString(R.string.quote_6_urdu),
            getString(R.string.quote_7_urdu),
            getString(R.string.quote_8_urdu)
        )
        
        currentQuoteIndex = (currentQuoteIndex + 1) % englishQuotes.size
        _binding?.let { binding ->
            binding.quotesTextEnglish.text = englishQuotes[currentQuoteIndex]
            binding.quotesTextUrdu.text = urduQuotes[currentQuoteIndex]
        }
    }

    override fun onResume() {
        super.onResume()
        // Only refresh if we're not currently refreshing data
        if (!isRefreshingData && isLocationPermissionGranted()) {
            lifecycleScope.launch {
                try {
                    val prayerTimes = PrayerTimesManager.getPrayerTimesForToday(requireContext())
                    if (prayerTimes != null) {
                        withContext(Dispatchers.Main) {
                            if (isAdded && !isDetached && _binding != null) {
                                updateLocationText(prayerTimes.location)
                                updatePrayerTimesFromEnhanced(prayerTimes)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NamazFragment", "Error getting prayer times on resume", e)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoQuoteChange()
        _binding = null
    }

    private fun refreshLocationAndPrayerTimes() {
        // Reset GPS/network error flag when user manually refreshes
        hasShownGpsNetworkError = false
        
        // Don't show notification for starting update to reduce spam
        
        lifecycleScope.launch {
            try {
                isRefreshingData = true // Set flag to prevent onResume interference
                
                // Clear all stored data and fetch fresh online data
                val prayerTimes = PrayerTimesManager.clearAllStoredDataAndFetchFresh(requireContext())
                if (prayerTimes != null) {
                    // Update UI with new prayer times
                    withContext(Dispatchers.Main) {
                        if (isAdded && !isDetached && _binding != null) {
                            updateLocationText(prayerTimes.location)
                            updatePrayerTimesFromEnhanced(prayerTimes)
                            // Don't show notification for successful data loading to reduce spam
                            Toast.makeText(requireContext(), "Fresh prayer times loaded from online API!", Toast.LENGTH_SHORT).show()
                            
                            // Reset GPS/network error flag since location was successfully updated
                            hasShownGpsNetworkError = false
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (isAdded && !isDetached) {
                            // Don't show notification for update failure to reduce spam
                            Toast.makeText(requireContext(), "Failed to fetch fresh online data", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded && !isDetached) {
                        // Don't show notification for update error to reduce spam
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                Log.e("NamazFragment", "Error refreshing location and prayer times", e)
            } finally {
                // Reset flag after a delay to allow UI to settle
                delay(2000) // 2 seconds delay
                isRefreshingData = false
            }
        }
    }

    private fun updatePrayerTimesFromEnhanced(prayerTimes: PrayerTimesManager.PrayerTimes) {
        _binding?.let { binding ->
            binding.namazDateText.text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            binding.fajrTime.text = formatTimeForDisplay(prayerTimes.fajr)
            binding.dhuhrTime.text = formatTimeForDisplay(prayerTimes.dhuhr)
            binding.asrTime.text = formatTimeForDisplay(prayerTimes.asr)
            binding.maghribTime.text = formatTimeForDisplay(prayerTimes.maghrib)
            binding.ishaTime.text = formatTimeForDisplay(prayerTimes.isha)

            // Show next prayer time
            val nextPrayer = getNextPrayer(prayerTimes)
            binding.namazNextPrayerText.text = "Next Prayer: $nextPrayer"
        }
    }

    private fun formatTimeForDisplay(timeString: String): String {
        return try {
            val timeParts = timeString.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            String.format("%d:%02d %s", displayHour, minute, amPm)
        } catch (e: Exception) {
            timeString // Return original if parsing fails
        }
    }

    private fun getNextPrayer(prayerTimes: PrayerTimesManager.PrayerTimes): String {
        val currentTime = Calendar.getInstance()
        val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        val prayerTimeMap = mapOf(
            "Fajr" to prayerTimes.fajr,
            "Dhuhr" to prayerTimes.dhuhr,
            "Asr" to prayerTimes.asr,
            "Maghrib" to prayerTimes.maghrib,
            "Isha" to prayerTimes.isha
        )

        var nextPrayer = "Fajr (Tomorrow)"
        var minTimeDiff = Int.MAX_VALUE

        for ((prayer, timeString) in prayerTimeMap) {
            val timeParts = timeString.split(":")
            val prayerHour = timeParts[0].toInt()
            val prayerMinute = timeParts[1].toInt()
            val prayerTimeInMinutes = prayerHour * 60 + prayerMinute

            val timeDiff = if (prayerTimeInMinutes > currentTimeInMinutes) {
                prayerTimeInMinutes - currentTimeInMinutes
            } else {
                (24 * 60 - currentTimeInMinutes) + prayerTimeInMinutes
            }

            if (timeDiff < minTimeDiff) {
                minTimeDiff = timeDiff
                nextPrayer = prayer
            }
        }

        return nextPrayer
    }

    private fun incrementTasbeehCount() {
        // Get current tasbeeh count from SharedPreferences
        val sharedPreferences = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val currentCount = sharedPreferences.getInt("tasbeeh_count", 0)
        val newCount = currentCount + 1
        
        // Save updated count
        sharedPreferences.edit().putInt("tasbeeh_count", newCount).apply()
        
        // Update last tasbeeh timestamp for missed tasbeeh notifications
        NotificationService().updateLastTasbeehTimestamp(requireContext())
        
        // Add vibration when incrementing tasbeeh count
        performVibration()
        
        // Show a brief toast to confirm the count
        Toast.makeText(requireContext(), "Tasbeeh count: $newCount", Toast.LENGTH_SHORT).show()
    }

    private fun performVibration() {
        // Check vibration setting from preferences
        val vibrationEnabled = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE)
            .getBoolean("vibration", true)
        
        if (vibrationEnabled && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (vibrator?.hasVibrator() == true) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (vibrator?.hasVibrator() == true) {
                        vibrator?.vibrate(50)
                    }
                }
            } catch (e: Exception) {
                // Handle vibration error silently
                Log.e("NamazFragment", "Error during vibration: ${e.message}")
            }
        }
    }
} 