package com.example.tasbeehcounter

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tasbeehcounter.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {
    private var count = 0
    private var isCounting = false
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private val SAVED_COUNTS_KEY = "saved_counts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("TasbeehCounter", MODE_PRIVATE)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.counterButton.setOnClickListener {
            if (isCounting) {
                count++
                updateCounterText()
            }
        }

        binding.resetButton.setOnClickListener {
            count = 0
            updateCounterText()
        }

        binding.startStopButton.setOnClickListener {
            isCounting = !isCounting
            binding.startStopButton.text = if (isCounting) "Stop" else "Start"
            binding.startStopButton.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (isCounting) R.color.stop_color else R.color.start_color
                )
            )
        }

        binding.saveButton.setOnClickListener {
            if (count > 0) {
                saveCount()
            } else {
                Toast.makeText(this, "Please count something first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCounterText() {
        binding.counterButton.text = count.toString()
    }

    private fun saveCount() {
        val savedCounts = getSavedCounts().toMutableList()
        savedCounts.add(0, CountEntry(count))
        
        val json = gson.toJson(savedCounts)
        sharedPreferences.edit().putString(SAVED_COUNTS_KEY, json).apply()
        
        Toast.makeText(this, "Count saved successfully", Toast.LENGTH_SHORT).show()
        showSavedCountsDialog()
    }

    private fun getSavedCounts(): List<CountEntry> {
        val json = sharedPreferences.getString(SAVED_COUNTS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<CountEntry>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun showSavedCountsDialog() {
        val savedCounts = getSavedCounts()
        if (savedCounts.isEmpty()) {
            Toast.makeText(this, "No saved counts yet", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_saved_counts, null)
        val countsContainer = dialogView.findViewById<android.widget.TextView>(R.id.countsContainer)
        
        val countsText = StringBuilder()
        savedCounts.forEach { entry ->
            countsText.append("${entry.count} - ${entry.getFormattedDate()}\n\n")
        }
        
        countsContainer.text = countsText.toString()

        AlertDialog.Builder(this)
            .setTitle("Saved Counts")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }
}