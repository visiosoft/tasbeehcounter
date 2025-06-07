package com.example.tasbeehcounter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tasbeehcounter.databinding.FragmentNamazBinding
import java.text.SimpleDateFormat
import java.util.*

class NamazFragment : Fragment() {
    private var _binding: FragmentNamazBinding? = null
    private val binding get() = _binding!!
    
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
        
        setupPrayerTimes()
        setupPrayerGuide()
    }
    
    private fun setupPrayerTimes() {
        // This is a placeholder. In a real app, you would calculate prayer times
        // based on location and date using a prayer times calculation library
        val currentDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        binding.dateText.text = currentDate
        
        // Example prayer times (replace with actual calculations)
        binding.fajrTime.text = "05:30"
        binding.dhuhrTime.text = "13:00"
        binding.asrTime.text = "16:30"
        binding.maghribTime.text = "19:00"
        binding.ishaTime.text = "20:30"
    }
    
    private fun setupPrayerGuide() {
        binding.prayerGuideButton.setOnClickListener {
            // Show prayer guide dialog or navigate to prayer guide screen
            showPrayerGuide()
        }
    }
    
    private fun showPrayerGuide() {
        // Create and show a dialog with prayer guide
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