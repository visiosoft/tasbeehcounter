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
import java.net.HttpURLConnection
import java.io.BufferedReader
import java.io.InputStreamReader

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
        
        // Show initial location status
        showLocationStatus()
        
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
        
        // Add a long press on location button for testing
        binding.locationButton.setOnLongClickListener {
            testLocationFunctionality()
            true
        }
        
        // Removed tasbeeh counting functionality from prayer timing page
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
        Log.d("NamazFragment", "Starting city name lookup for coordinates: ${location.latitude}, ${location.longitude}")
        
        // First, show coordinates immediately as a fallback
        CoroutineScope(Dispatchers.Main).launch {
            if (isAdded && !isDetached && _binding != null) {
                val coordinateText = "📍 ${location.latitude.toFloat()}, ${location.longitude.toFloat()}"
                binding.locationText.text = coordinateText
                Log.d("NamazFragment", "Showing coordinates as fallback: $coordinateText")
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Use more precise coordinates for better accuracy
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=${location.latitude}&lon=${location.longitude}&accept-language=en&zoom=10&addressdetails=1"
                Log.d("NamazFragment", "Making API request to: $url")
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "TasbeehCounter/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                Log.d("NamazFragment", "API response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val address = json.getJSONObject("address")
                    
                    Log.d("NamazFragment", "Full API response: ${response.toString()}")
                    Log.d("NamazFragment", "Address object: ${address.toString()}")
                    
                    // Enhanced city name resolution with better fallback hierarchy
                    val cityName = when {
                        // Primary city names with state/province
                        address.optString("city").isNotEmpty() -> {
                            val city = address.optString("city")
                            val state = address.optString("state", "")
                            val country = address.optString("country", "")
                            when {
                                state.isNotEmpty() && country.isNotEmpty() -> "$city, $state, $country"
                                state.isNotEmpty() -> "$city, $state"
                                country.isNotEmpty() -> "$city, $country"
                                else -> city
                            }
                        }
                        address.optString("town").isNotEmpty() -> {
                            val town = address.optString("town")
                            val state = address.optString("state", "")
                            val country = address.optString("country", "")
                            when {
                                state.isNotEmpty() && country.isNotEmpty() -> "$town, $state, $country"
                                state.isNotEmpty() -> "$town, $state"
                                country.isNotEmpty() -> "$town, $country"
                                else -> town
                            }
                        }
                        address.optString("village").isNotEmpty() -> {
                            val village = address.optString("village")
                            val state = address.optString("state", "")
                            val country = address.optString("country", "")
                            when {
                                state.isNotEmpty() && country.isNotEmpty() -> "$village, $state, $country"
                                state.isNotEmpty() -> "$village, $state"
                                country.isNotEmpty() -> "$village, $country"
                                else -> village
                            }
                        }
                        // Suburban areas with city context
                        address.optString("suburb").isNotEmpty() -> {
                            val suburb = address.optString("suburb")
                            val city = address.optString("city", "")
                            val state = address.optString("state", "")
                            val country = address.optString("country", "")
                            when {
                                city.isNotEmpty() && state.isNotEmpty() && country.isNotEmpty() -> "$suburb, $city, $state, $country"
                                city.isNotEmpty() && state.isNotEmpty() -> "$suburb, $city, $state"
                                city.isNotEmpty() -> "$suburb, $city"
                                state.isNotEmpty() && country.isNotEmpty() -> "$suburb, $state, $country"
                                state.isNotEmpty() -> "$suburb, $state"
                                else -> suburb
                            }
                        }
                        // County/Province level
                        address.optString("county").isNotEmpty() -> {
                            val county = address.optString("county")
                            val state = address.optString("state", "")
                            val country = address.optString("country", "")
                            when {
                                state.isNotEmpty() && country.isNotEmpty() -> "$county, $state, $country"
                                state.isNotEmpty() -> "$county, $state"
                                country.isNotEmpty() -> "$county, $country"
                                else -> county
                            }
                        }
                        // State/Province level
                        address.optString("state").isNotEmpty() -> {
                            val state = address.optString("state")
                            val country = address.optString("country", "")
                            if (country.isNotEmpty()) "$state, $country" else state
                        }
                        // Country level
                        address.optString("country").isNotEmpty() -> {
                            address.optString("country")
                        }
                        // Display name from Nominatim as last resort
                        json.optString("display_name").isNotEmpty() -> {
                            val displayName = json.optString("display_name")
                            // Extract the most relevant parts (first two parts before comma)
                            val parts = displayName.split(",").take(2).map { it.trim() }
                            parts.joinToString(", ")
                        }
                        else -> getDefaultMainCity()
                    }
                    
                    Log.d("NamazFragment", "Resolved city name: $cityName")
                
                withContext(Dispatchers.Main) {
                    // Check if fragment is still active before updating UI
                    if (isAdded && !isDetached && _binding != null) {
                            currentCityName = cityName
                            updateLocationText(cityName)
                        // Don't show notification for successful location updates to reduce spam
                        updatePrayerTimes()
                        
                        // Reset GPS/network error flag since location was successfully obtained
                        hasShownGpsNetworkError = false
                            
                            Log.d("NamazFragment", "Updated location to: $cityName")
                        } else {
                            Log.w("NamazFragment", "Fragment not active, skipping UI update")
                        }
                    }
                } else {
                    Log.e("NamazFragment", "Reverse geocoding failed with response code: $responseCode")
                    withContext(Dispatchers.Main) {
                        if (isAdded && !isDetached && _binding != null) {
                            updateLocationText(getDefaultMainCity())
                            updatePrayerTimes()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NamazFragment", "Error getting city name from location", e)
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
        _binding?.locationText?.text = "📍 $text"
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
        lifecycleScope.launch {
            try {
                // Show loading state with visual feedback
                binding.locationText.text = "📍 Updating location..."
                binding.locationButton.isEnabled = false // Disable button during update
                
                // Use the enhanced prayer times manager to get fresh location and prayer times
                val prayerTimes = PrayerTimesManager.getPrayerTimesForToday(requireContext())
                
                if (prayerTimes != null) {
                    // Update the location text with the city name from prayer times
                    val locationText = prayerTimes.location.ifEmpty { "Current Location" }
                    binding.locationText.text = "📍 $locationText"
                    currentCityName = locationText
                    
                    // Update prayer times UI with the fresh data
                    updatePrayerTimesFromManager(prayerTimes)
                    
                    Toast.makeText(context, "✅ Location updated: $locationText", Toast.LENGTH_SHORT).show()
                    Log.d("NamazFragment", "Successfully refreshed location and prayer times: $locationText")
                } else {
                    // Fallback to GPS location if prayer times manager fails
                    binding.locationText.text = "📍 Getting GPS location..."
                    getCurrentLocation()
                    Toast.makeText(context, "📍 Using GPS location", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NamazFragment", "Error refreshing location and prayer times", e)
                // Fallback to GPS location
                binding.locationText.text = "📍 Getting GPS location..."
                getCurrentLocation()
                Toast.makeText(context, "📍 Using GPS location", Toast.LENGTH_SHORT).show()
            } finally {
                // Re-enable the button after update
                binding.locationButton.isEnabled = true
            }
        }
    }

    private fun updatePrayerTimesFromManager(prayerTimes: PrayerTimesManager.PrayerTimes) {
        try {
            val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = Date()
            
            _binding?.let { binding ->
                binding.namazDateText.text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date)
                
                // Parse prayer times from string format (HH:MM) to Date objects
                val fajrTime = parseTimeString(prayerTimes.fajr)
                val dhuhrTime = parseTimeString(prayerTimes.dhuhr)
                val asrTime = parseTimeString(prayerTimes.asr)
                val maghribTime = parseTimeString(prayerTimes.maghrib)
                val ishaTime = parseTimeString(prayerTimes.isha)
                
                binding.fajrTime.text = dateFormat.format(fajrTime)
                binding.dhuhrTime.text = dateFormat.format(dhuhrTime)
                binding.asrTime.text = dateFormat.format(asrTime)
                binding.maghribTime.text = dateFormat.format(maghribTime)
                binding.ishaTime.text = dateFormat.format(ishaTime)

                // Show next prayer time
                val currentTime = Calendar.getInstance()
                val nextPrayer = when {
                    currentTime.before(fajrTime) -> "Fajr"
                    currentTime.before(dhuhrTime) -> "Dhuhr"
                    currentTime.before(asrTime) -> "Asr"
                    currentTime.before(maghribTime) -> "Maghrib"
                    currentTime.before(ishaTime) -> "Isha"
                    else -> "Fajr (Tomorrow)"
                }
                
                binding.namazNextPrayerText.text = "Next Prayer: $nextPrayer"
            }
        } catch (e: Exception) {
            Log.e("NamazFragment", "Error updating prayer times from manager", e)
            // Fallback to existing prayer times update method
            updatePrayerTimes()
        }
    }

    private fun parseTimeString(timeString: String): Calendar {
        val parts = timeString.split(":")
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        calendar.set(Calendar.MINUTE, parts[1].toInt())
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
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

    private fun testLocationFunctionality() {
        Log.d("NamazFragment", "Testing location functionality...")
        
        // Test if location permission is granted
        val hasLocationPermission = isLocationPermissionGranted()
        Log.d("NamazFragment", "Location permission granted: $hasLocationPermission")
        
        // Test if we can get current location
        if (hasLocationPermission) {
            try {
                // Check if we have the required permission before making the call
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location: Location? ->
                            if (location != null) {
                                Log.d("NamazFragment", "Test: Got location - ${location.latitude}, ${location.longitude}")
                                // Show coordinates immediately
                                CoroutineScope(Dispatchers.Main).launch {
                                    if (isAdded && !isDetached && _binding != null) {
                                        val coordinateText = "📍 Test: ${location.latitude.toFloat()}, ${location.longitude.toFloat()}"
                                        binding.locationText.text = coordinateText
                                        Toast.makeText(context, "Location test successful", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Log.d("NamazFragment", "Test: Location is null")
                                Toast.makeText(context, "Location test: Location is null", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("NamazFragment", "Test: Failed to get location", exception)
                            Toast.makeText(context, "Location test failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("NamazFragment", "Test: No fine location permission")
                    Toast.makeText(context, "Location test: No fine location permission", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NamazFragment", "Test: Exception getting location", e)
                Toast.makeText(context, "Location test exception: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("NamazFragment", "Test: No location permission")
            Toast.makeText(context, "Location test: No permission", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationStatus() {
        val hasPermission = isLocationPermissionGranted()
        val statusText = if (hasPermission) {
            "📍 Location permission granted"
        } else {
            "📍 Location permission needed"
        }
        
        binding.locationText.text = statusText
        Log.d("NamazFragment", "Location status: $statusText")
    }
} 