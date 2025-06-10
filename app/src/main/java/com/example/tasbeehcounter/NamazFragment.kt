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
import java.text.SimpleDateFormat
import java.util.*

class NamazFragment : Fragment() {
    private var _binding: FragmentNamazBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var prayerTimes: PrayerTimes? = null

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
        checkLocationPermission()
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
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
                        updatePrayerTimes()
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePrayerTimes() {
        currentLocation?.let { location ->
            val coordinates = Coordinates(location.latitude, location.longitude)
            val params = CalculationMethod.KARACHI.parameters
            params.madhab = com.batoulapps.adhan.Madhab.HANAFI

            val date = DateComponents.from(Date())
            prayerTimes = PrayerTimes(coordinates, date, params)
            updatePrayerTimesUI()
        }
    }

    private fun updatePrayerTimesUI() {
        prayerTimes?.let { times ->
            val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            
            binding.fajrTime.text = dateFormat.format(times.fajr)
            binding.sunriseTime.text = dateFormat.format(times.sunrise)
            binding.dhuhrTime.text = dateFormat.format(times.dhuhr)
            binding.asrTime.text = dateFormat.format(times.asr)
            binding.maghribTime.text = dateFormat.format(times.maghrib)
            binding.ishaTime.text = dateFormat.format(times.isha)
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentLocation == null) {
            checkLocationPermission()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 