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
import com.example.tasbeehcounter.databinding.FragmentNamazBinding
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.Madhab
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*
import com.batoulapps.adhan.data.DateComponents

class NamazFragment : Fragment() {
    private var _binding: FragmentNamazBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getLocation()
            }
            else -> {
                Toast.makeText(context, "Location permission is required for prayer times", Toast.LENGTH_LONG).show()
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
        
        setupLocation()
        setupPrayerGuide()
    }
    
    private fun setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        checkLocationPermission()
    }
    
    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getLocation()
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
    
    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        calculatePrayerTimes(it)
                    } ?: run {
                        Toast.makeText(context, "Unable to get location", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error getting location: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun calculatePrayerTimes(location: Location) {
        val coordinates = Coordinates(location.latitude, location.longitude)
        val params = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        params.madhab = Madhab.SHAFI
        
        val prayerTimes = PrayerTimes(
            coordinates,
            DateComponents.from(Date()),
            params
        )
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        binding.dateText.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        binding.fajrTime.text = timeFormat.format(prayerTimes.fajr)
        binding.dhuhrTime.text = timeFormat.format(prayerTimes.dhuhr)
        binding.asrTime.text = timeFormat.format(prayerTimes.asr)
        binding.maghribTime.text = timeFormat.format(prayerTimes.maghrib)
        binding.ishaTime.text = timeFormat.format(prayerTimes.isha)
        
        // Show next prayer time
        val now = Date()
        val nextPrayer = when {
            now.before(prayerTimes.fajr) -> "Fajr"
            now.before(prayerTimes.dhuhr) -> "Dhuhr"
            now.before(prayerTimes.asr) -> "Asr"
            now.before(prayerTimes.maghrib) -> "Maghrib"
            now.before(prayerTimes.isha) -> "Isha"
            else -> "Fajr (Tomorrow)"
        }
        
        binding.nextPrayerText.text = "Next Prayer: $nextPrayer"
    }
    
    private fun setupPrayerGuide() {
        binding.prayerGuideButton.setOnClickListener {
            showPrayerGuide()
        }
    }
    
    private fun showPrayerGuide() {
        val prayerGuide = """
            Fajr Prayer:
            1. Make intention
            2. Raise hands and say Allahu Akbar
            3. Recite Surah Al-Fatiha
            4. Perform Ruku (bowing)
            5. Stand up straight
            6. Perform Sujood (prostration)
            7. Sit between prostrations
            8. Complete the prayer with Tasleem
            
            Note: This is a basic guide. Please consult with a qualified Islamic scholar for detailed instructions.
        """.trimIndent()
        
        binding.prayerGuideText.text = prayerGuide
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 