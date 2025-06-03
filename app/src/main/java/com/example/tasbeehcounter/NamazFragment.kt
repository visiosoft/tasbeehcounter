package com.example.tasbeehcounter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentNamazBinding
import java.text.SimpleDateFormat
import java.util.*

class NamazFragment : Fragment() {
    private var _binding: FragmentNamazBinding? = null
    private val binding get() = _binding!!
    private lateinit var locationManager: LocationManager

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
        updateDate()
    }

    private fun setupLocation() {
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                location?.let { updatePrayerTimes(it) }
            } catch (e: Exception) {
                Toast.makeText(context, "Error getting location", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun updateDate() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        binding.dateText.text = dateFormat.format(Date())
    }

    private fun updatePrayerTimes(location: Location) {
        // TODO: Implement actual prayer time calculation based on location
        // For now, using placeholder times
        binding.fajrTime.text = "05:30"
        binding.dhuhrTime.text = "12:30"
        binding.asrTime.text = "15:45"
        binding.maghribTime.text = "18:30"
        binding.ishaTime.text = "20:00"

        // Update location text
        binding.locationText.text = "Current Location" // TODO: Get actual location name
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupLocation()
                } else {
                    Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
} 