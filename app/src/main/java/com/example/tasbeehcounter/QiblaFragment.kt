package com.example.tasbeehcounter

import android.content.Context
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
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentQiblaBinding
import kotlin.math.abs
import kotlin.math.roundToInt

class QiblaFragment : Fragment(), SensorEventListener {
    private var _binding: FragmentQiblaBinding? = null
    private val binding get() = _binding!!

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var currentLocation: Location? = null

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var azimuth = 0f
    private var qiblaDirection = 0f
    private var currentRotation = 0f

    // Kaaba coordinates
    private val KAABA_LATITUDE = 21.422487
    private val KAABA_LONGITUDE = 39.826206

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
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer == null || magnetometer == null) {
            Toast.makeText(context, "Required sensors are not available", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupLocation() {
        // Get location from shared preferences or use default
        val sharedPreferences = requireContext().getSharedPreferences("TasbeehSettings", Context.MODE_PRIVATE)
        val latitude = sharedPreferences.getFloat("latitude", 0f)
        val longitude = sharedPreferences.getFloat("longitude", 0f)

        if (latitude != 0f && longitude != 0f) {
            currentLocation = Location("").apply {
                this.latitude = latitude.toDouble()
                this.longitude = longitude.toDouble()
            }
            calculateQiblaDirection()
        } else {
            Toast.makeText(context, "Please set your location in Namaz Times first", Toast.LENGTH_LONG).show()
        }
    }

    private fun calculateQiblaDirection() {
        currentLocation?.let { location ->
            // Calculate Qibla direction using great circle formula
            val lat1 = Math.toRadians(location.latitude)
            val lon1 = Math.toRadians(location.longitude)
            val lat2 = Math.toRadians(KAABA_LATITUDE)
            val lon2 = Math.toRadians(KAABA_LONGITUDE)

            val y = Math.sin(lon2 - lon1) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) -
                    Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1)
            qiblaDirection = Math.toDegrees(Math.atan2(y, x)).toFloat()
            
            // Normalize to 0-360
            qiblaDirection = (qiblaDirection + 360) % 360
        }
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
            SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // Convert radians to degrees
            azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            // Normalize to 0-360
            azimuth = (azimuth + 360) % 360

            // Calculate rotation needed
            val rotation = (qiblaDirection - azimuth + 360) % 360
            
            // Only rotate if the difference is significant
            if (abs(rotation - currentRotation) > 1) {
                rotateCompass(rotation)
                currentRotation = rotation
            }

            // Update direction text
            updateDirectionText(rotation)
        }
    }

    private fun rotateCompass(degrees: Float) {
        val rotateAnimation = RotateAnimation(
            currentRotation,
            degrees,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation.duration = 250
        rotateAnimation.fillAfter = true
        binding.compassImage.startAnimation(rotateAnimation)
    }

    private fun updateDirectionText(degrees: Float) {
        val direction = when {
            degrees in 337.5f..360f || degrees in 0f..22.5f -> "North"
            degrees in 22.5f..67.5f -> "Northeast"
            degrees in 67.5f..112.5f -> "East"
            degrees in 112.5f..157.5f -> "Southeast"
            degrees in 157.5f..202.5f -> "South"
            degrees in 202.5f..247.5f -> "Southwest"
            degrees in 247.5f..292.5f -> "West"
            degrees in 292.5f..337.5f -> "Northwest"
            else -> ""
        }

        binding.directionText.text = "Qibla Direction: $direction (${degrees.roundToInt()}°)"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    override fun onResume() {
        super.onResume()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 