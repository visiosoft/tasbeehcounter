package com.example.tasbeehcounter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.net.URL
import org.json.JSONObject

class NamazFragment : Fragment() {
    private var _binding: FragmentNamazBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var prayerTimes: PrayerTimes? = null
    private var currentCityName: String? = null
    private lateinit var notificationManager: NotificationManager

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                getCurrentLocation()
                showLocationNotification("Location access granted", "Getting your current location...")
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
                getCurrentLocation()
                showLocationNotification("Location access granted", "Getting your current location...")
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
                getCurrentLocation()
                showLocationNotification("Updating Location", "Getting your current location...")
            }
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
                    currentCityName = city
                    val locationText = when {
                        city.isNotEmpty() && province.isNotEmpty() -> "$city, $province"
                        city.isNotEmpty() && state.isNotEmpty() -> "$city, $state"
                        city.isNotEmpty() -> city
                        state.isNotEmpty() -> state
                        else -> getDefaultMainCity()
                    }
                    updateLocationText(locationText)
                    showLocationNotification("Location Updated", "Prayer times updated for $locationText")
                    updatePrayerTimes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateLocationText(getDefaultMainCity())
                    updatePrayerTimes()
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
        binding.locationText.text = text
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
            
            binding.dateText.text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date)
            binding.fajrTime.text = dateFormat.format(times.fajr)
            binding.sunriseTime.text = dateFormat.format(times.sunrise)
            binding.dhuhrTime.text = dateFormat.format(times.dhuhr)
            binding.asrTime.text = dateFormat.format(times.asr)
            binding.maghribTime.text = dateFormat.format(times.maghrib)
            binding.ishaTime.text = dateFormat.format(times.isha)

            // Show next prayer time
            val nextPrayer = when {
                date.before(times.fajr) -> "Fajr"
                date.before(times.sunrise) -> "Sunrise"
                date.before(times.dhuhr) -> "Dhuhr"
                date.before(times.asr) -> "Asr"
                date.before(times.maghrib) -> "Maghrib"
                date.before(times.isha) -> "Isha"
                else -> "Fajr (Tomorrow)"
            }
            
            binding.nextPrayerText.text = "Next Prayer: $nextPrayer"
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically refresh location when fragment resumes
        if (isLocationPermissionGranted()) {
            getCurrentLocation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 