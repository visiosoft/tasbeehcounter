package com.example.tasbeehcounter

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private var isUsingManualLocation = false
    private var currentCityName: String? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
                getCurrentLocation()
            }
            else -> {
                // No location access granted
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
        setupLocationServices()
        setupUI()
        // Automatically check location permission on start
        checkLocationPermission()
    }

    private fun setupUI() {
        binding.locationButton.setOnClickListener {
            // Only show manual input if GPS is not available
            if (!isLocationPermissionGranted()) {
                showManualLocationDialog()
            } else {
                getCurrentLocation()
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
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun getCurrentLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLocation = it
                        isUsingManualLocation = false
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
            showManualLocationDialog()
        }
    }

    private fun getLastKnownLocation() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        currentLocation = it
                        isUsingManualLocation = false
                        getCityNameFromLocation(it)
                    } ?: run {
                        showManualLocationDialog()
                    }
                }
                .addOnFailureListener {
                    showManualLocationDialog()
                }
        } catch (e: SecurityException) {
            showManualLocationDialog()
        }
    }

    private fun getCityNameFromLocation(location: Location) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=${location.latitude}&lon=${location.longitude}"
                val response = URL(url).readText()
                val json = JSONObject(response)
                val address = json.getJSONObject("address")
                val city = address.optString("city") ?: address.optString("town") ?: address.optString("village")
                
                withContext(Dispatchers.Main) {
                    currentCityName = city
                    updateLocationText("Current Location: $city")
                    updatePrayerTimes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateLocationText("Current Location")
                    updatePrayerTimes()
                }
            }
        }
    }

    private fun showManualLocationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_location_input, null)
        val cityInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.cityInput)
        val searchButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.searchButton)
        val latitudeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.latitudeInput)
        val longitudeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.longitudeInput)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enter Location")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                try {
                    val latitude = latitudeInput.text.toString().toDouble()
                    val longitude = longitudeInput.text.toString().toDouble()
                    if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                        currentLocation = Location("manual").apply {
                            this.latitude = latitude
                            this.longitude = longitude
                        }
                        isUsingManualLocation = true
                        currentCityName = cityInput.text.toString()
                        updateLocationText("Manual Location: ${currentCityName ?: "Custom Location"}")
                        updatePrayerTimes()
                    } else {
                        Toast.makeText(context, "Invalid coordinates", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        searchButton.setOnClickListener {
            val cityName = cityInput.text.toString()
            if (cityName.isNotEmpty()) {
                searchCity(cityName) { lat, lon ->
                    latitudeInput.setText(lat.toString())
                    longitudeInput.setText(lon.toString())
                }
            } else {
                Toast.makeText(context, "Please enter a city name", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun searchCity(cityName: String, onResult: (Double, Double) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encodedCity = java.net.URLEncoder.encode(cityName, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedCity"
                val response = URL(url).readText()
                val jsonArray = org.json.JSONArray(response)
                
                if (jsonArray.length() > 0) {
                    val firstResult = jsonArray.getJSONObject(0)
                    val lat = firstResult.getDouble("lat")
                    val lon = firstResult.getDouble("lon")
                    
                    withContext(Dispatchers.Main) {
                        onResult(lat, lon)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "City not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error searching city: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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