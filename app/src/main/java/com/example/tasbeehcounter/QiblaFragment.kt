package com.example.tasbeehcounter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentQiblaBinding

class QiblaFragment : Fragment(), SensorEventListener {
    private var _binding: FragmentQiblaBinding? = null
    private val binding get() = _binding!!

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var currentDegree = 0f

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
    }

    private fun setupSensors() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
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

            val azimuthInRadians = orientation[0]
            val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

            // Calculate Qibla direction (simplified - actual calculation would need location data)
            val qiblaDirection = 0f // This should be calculated based on user's location

            rotateCompass(azimuthInDegrees)
            updateQiblaDirection(azimuthInDegrees, qiblaDirection)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    private fun rotateCompass(degree: Float) {
        val rotateAnimation = RotateAnimation(
            currentDegree,
            -degree,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )

        rotateAnimation.duration = 250
        rotateAnimation.fillAfter = true

        binding.compassImage.startAnimation(rotateAnimation)
        currentDegree = -degree
    }

    private fun updateQiblaDirection(azimuth: Float, qiblaDirection: Float) {
        val angle = (qiblaDirection - azimuth + 360) % 360
        binding.directionText.text = String.format("%.1f°", angle)
    }
} 