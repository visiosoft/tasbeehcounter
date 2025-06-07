package com.example.tasbeehcounter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentQiblaBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.roundToInt

class QiblaFragment : Fragment(), SensorEventListener {
    private var _binding: FragmentQiblaBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var sensorManager: SensorManager
    private var compass: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    
    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    
    private var currentDegree = 0f
    private var qiblaDegree = 0f
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                getLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
                getLocation()
            }
            else -> {
                // No location access granted
                Toast.makeText(context, "Location permission is required for Qibla direction", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQiblaBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSensors()
        setupLocation()
    }
    
    private fun setupSensors() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        compass = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
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
                        calculateQiblaDirection(it)
                    } ?: run {
                        Toast.makeText(context, "Unable to get location", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error getting location: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun calculateQiblaDirection(location: Location) {
        // Kaaba coordinates
        val kaabaLat = 21.4225
        val kaabaLng = 39.8262
        
        val lat1 = Math.toRadians(location.latitude)
        val lng1 = Math.toRadians(location.longitude)
        val lat2 = Math.toRadians(kaabaLat)
        val lng2 = Math.toRadians(kaabaLng)
        
        val y = Math.sin(lng2 - lng1) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lng2 - lng1)
        var qiblaAngle = Math.toDegrees(Math.atan2(y, x))
        
        if (qiblaAngle < 0) {
            qiblaAngle += 360
        }
        
        qiblaDegree = qiblaAngle.toFloat()
        updateCompass()
    }
    
    private fun updateCompass() {
        val rotation = RotateAnimation(
            currentDegree,
            -qiblaDegree,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        
        rotation.duration = 250
        rotation.fillAfter = true
        
        binding.compassImage.startAnimation(rotation)
        currentDegree = -qiblaDegree
        
        binding.qiblaDirectionText.text = "Qibla Direction: ${qiblaDegree.roundToInt()}°"
    }
    
    override fun onResume() {
        super.onResume()
        compass?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }
        
        if (lastAccelerometerSet && lastMagnetometerSet) {
            SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                lastAccelerometer,
                lastMagnetometer
            )
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            val azimuthInRadians = orientation[0]
            val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
            
            val rotation = RotateAnimation(
                currentDegree,
                -azimuthInDegrees,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            
            rotation.duration = 250
            rotation.fillAfter = true
            
            binding.compassImage.startAnimation(rotation)
            currentDegree = -azimuthInDegrees
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 