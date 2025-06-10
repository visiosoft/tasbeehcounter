package com.example.tasbeehcounter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentTasbeehBinding
import java.text.SimpleDateFormat
import java.util.*

class TasbeehFragment : Fragment() {
    private var _binding: FragmentTasbeehBinding? = null
    private val binding get() = _binding!!

    private var count = 0
    private var isCounting = false
    private lateinit var vibrator: Vibrator
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasbeehBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVibrator()
        setupSharedPreferences()
        setupClickListeners()
        updateFullscreenMode()

        // Enable tap anywhere to count
        binding.root.setOnClickListener {
            if (isCounting) {
                count++
                binding.counterText.text = count.toString()
                if (isVibrationEnabled()) {
                    vibrate(50)
                }
            }
        }
    }

    private fun setupSharedPreferences() {
        sharedPreferences = requireContext().getSharedPreferences("TasbeehSettings", 0)
    }

    private fun isVibrationEnabled(): Boolean {
        return sharedPreferences.getBoolean("vibration", true)
    }

    private fun updateFullscreenMode() {
        (requireActivity() as MainActivity).updateFullscreenMode()
    }

    private fun setupVibrator() {
        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun setupClickListeners() {
        binding.counterButton.setOnClickListener {
            if (isCounting) {
                count++
                binding.counterText.text = count.toString()
                if (isVibrationEnabled()) {
                    vibrate(50)
                }
            }
        }

        binding.resetButton.setOnClickListener {
            count = 0
            binding.counterText.text = "0"
            if (isVibrationEnabled()) {
                vibrate(100)
            }
        }

        binding.startStopButton.setOnClickListener {
            isCounting = !isCounting
            binding.startStopButton.text = if (isCounting) "Stop" else "Start"
            if (isVibrationEnabled()) {
                vibrate(150)
            }
        }

        binding.saveButton.setOnClickListener {
            if (count > 0) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                // Here you can implement saving functionality
                // For now, just reset the counter
                count = 0
                binding.counterText.text = "0"
                isCounting = false
                binding.startStopButton.text = "Start"
            }
        }
    }

    private fun vibrate(duration: Long) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    override fun onResume() {
        super.onResume()
        updateFullscreenMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 