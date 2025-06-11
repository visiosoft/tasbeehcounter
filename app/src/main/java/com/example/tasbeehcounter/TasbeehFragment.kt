package com.example.tasbeehcounter

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentTasbeehBinding

class TasbeehFragment : Fragment() {
    private var _binding: FragmentTasbeehBinding? = null
    private val binding get() = _binding!!
    private var count = 0
    private var isCounting = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrator: Vibrator

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
        setupSharedPreferences()
        setupVibrator()
        setupCounter()
        updateUI()
        updateStartStopButton()
    }

    private fun setupSharedPreferences() {
        sharedPreferences = requireContext().getSharedPreferences("TasbeehSettings", Context.MODE_PRIVATE)
    }

    private fun setupVibrator() {
        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun setupCounter() {
        binding.counterButton.setOnClickListener {
            if (isCounting) incrementCount()
        }

        binding.resetButton.setOnClickListener {
            count = 0
            updateUI()
        }

        binding.startStopButton.setOnClickListener {
            isCounting = !isCounting
            updateStartStopButton()
        }

        // Handle fullscreen tap
        binding.root.setOnClickListener {
            if (isCounting) {
                incrementCount()
            }
        }
    }

    private fun incrementCount() {
        count++
        if (sharedPreferences.getBoolean("vibration", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
        updateUI()
    }

    private fun updateUI() {
        binding.counterText.text = count.toString()
        binding.counterButton.visibility = View.GONE // Always hide the counter button in fullscreen mode
    }

    private fun updateStartStopButton() {
        binding.startStopButton.text = if (isCounting) "Stop" else "Start"
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 