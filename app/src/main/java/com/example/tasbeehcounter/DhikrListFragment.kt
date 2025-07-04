package com.example.tasbeehcounter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.SharedPreferences
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class DhikrListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DhikrAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private var dhikrList = mutableListOf<Dhikr>()
    private var vibrator: Vibrator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dhikr_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireContext().getSharedPreferences("DhikrPrefs", Context.MODE_PRIVATE)
        
        // Initialize vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        recyclerView = view.findViewById(R.id.dhikrRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Load default dhikr list
        val defaultDhikrList = listOf(
            Dhikr("سُبْحَانَ اللَّهِ", "Glory be to Allah", 33, false),
            Dhikr("الْحَمْدُ لِلَّهِ", "All praise is due to Allah", 33, false),
            Dhikr("اللَّهُ أَكْبَرُ", "Allah is the Greatest", 33, false),
            Dhikr("لَا إِلَٰهَ إِلَّا اللَّهُ", "There is no god but Allah", 100, false),
            Dhikr("أَسْتَغْفِرُ اللَّهَ", "I seek forgiveness from Allah", 100, false),
            Dhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "Glory be to Allah and His is the praise", 100, false),
            Dhikr("سُبْحَانَ اللَّهِ الْعَظِيمِ", "Glory be to Allah, the Most Great", 100, false),
            Dhikr("لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "There is no power and no strength except with Allah", 100, false),
            Dhikr("اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ", "O Allah, send prayers upon Muhammad", 100, false),
            Dhikr("سُبْحَانَ اللَّهِ وَالْحَمْدُ لِلَّهِ وَلَا إِلَٰهَ إِلَّا اللَّهُ وَاللَّهُ أَكْبَرُ", "Glory be to Allah, and praise be to Allah, and there is no god but Allah, and Allah is the Greatest", 33, false)
        )

        // Load saved custom dhikr
        val savedDhikrJson = sharedPreferences.getString("custom_dhikr", null)
        val customDhikrList: List<Dhikr> = if (savedDhikrJson != null) {
            val type = object : TypeToken<List<Dhikr>>() {}.type
            gson.fromJson(savedDhikrJson, type)
        } else {
            emptyList()
        }

        dhikrList = (defaultDhikrList + customDhikrList).toMutableList()
        adapter = DhikrAdapter(dhikrList)
        recyclerView.adapter = adapter

        // Setup FAB
        view.findViewById<FloatingActionButton>(R.id.addDhikrFab).setOnClickListener {
            showAddDhikrDialog()
        }
    }

    private fun showAddDhikrDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialog_add_dhikr)
            .create()

        dialog.show()

        val arabicInput = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.arabicTextInput)
        val targetCountInput = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.targetCountInput)
        val saveButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveButton)
        val cancelButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)

        saveButton?.setOnClickListener {
            val arabic = arabicInput?.text?.toString()
            val targetCount = targetCountInput?.text?.toString()?.toIntOrNull()

            if (!arabic.isNullOrBlank() && targetCount != null) {
                val newDhikr = Dhikr(arabic, "", targetCount, true)
                dhikrList.add(newDhikr)
                adapter.notifyItemInserted(dhikrList.size - 1)
                saveCustomDhikr()
                dialog.dismiss()
            }
        }

        cancelButton?.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showEditDhikrDialog(position: Int) {
        val dhikr = dhikrList[position]
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialog_add_dhikr)
            .create()

        dialog.show()

        val arabicInput = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.arabicTextInput)
        val targetCountInput = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.targetCountInput)
        val saveButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveButton)
        val cancelButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)

        arabicInput?.setText(dhikr.arabic)
        targetCountInput?.setText(dhikr.targetCount.toString())

        saveButton?.setOnClickListener {
            val arabic = arabicInput?.text?.toString()
            val targetCount = targetCountInput?.text?.toString()?.toIntOrNull()

            if (!arabic.isNullOrBlank() && targetCount != null) {
                dhikr.arabic = arabic
                dhikr.targetCount = targetCount
                adapter.notifyItemChanged(position)
                saveCustomDhikr()
                dialog.dismiss()
            }
        }

        cancelButton?.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun saveCustomDhikr() {
        val customDhikr = dhikrList.filter { it.isCustom }
        val json = gson.toJson(customDhikr)
        sharedPreferences.edit().putString("custom_dhikr", json).apply()
    }

    private fun performVibration() {
        // Check vibration setting from preferences
        val vibrationEnabled = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE)
            .getBoolean("vibration", true)
        
        if (vibrationEnabled && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (vibrator?.hasVibrator() == true) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (vibrator?.hasVibrator() == true) {
                        vibrator?.vibrate(50)
                    }
                }
            } catch (e: Exception) {
                // Handle vibration error silently
                android.util.Log.e("DhikrListFragment", "Error during vibration: ${e.message}")
            }
        }
    }

    data class Dhikr(
        var arabic: String,
        val translation: String,
        var targetCount: Int,
        val isCustom: Boolean,
        var currentCount: Int = 0
    )

    private inner class DhikrAdapter(private val dhikrList: MutableList<Dhikr>) :
        RecyclerView.Adapter<DhikrAdapter.DhikrViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DhikrViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dhikr_list_item, parent, false)
            return DhikrViewHolder(view)
        }

        override fun onBindViewHolder(holder: DhikrViewHolder, position: Int) {
            val dhikr = dhikrList[position]
            holder.arabicText.text = dhikr.arabic
            holder.translationText.text = dhikr.translation
            holder.countText.text = "${dhikr.currentCount}/${dhikr.targetCount}"

            // Show edit and delete buttons only for custom dhikr
            holder.editButton.visibility = if (dhikr.isCustom) View.VISIBLE else View.GONE
            holder.deleteButton.visibility = if (dhikr.isCustom) View.VISIBLE else View.GONE

            holder.incrementButton.setOnClickListener {
                if (dhikr.currentCount < dhikr.targetCount) {
                    dhikr.currentCount++
                    holder.countText.text = "${dhikr.currentCount}/${dhikr.targetCount}"
                    
                    // Add vibration when incrementing dhikr count
                    performVibration()
                    
                    if (dhikr.currentCount == dhikr.targetCount) {
                        holder.completeText.visibility = View.VISIBLE
                        holder.incrementButton.isEnabled = false
                    }
                }
            }

            holder.resetButton.setOnClickListener {
                dhikr.currentCount = 0
                holder.countText.text = "${dhikr.currentCount}/${dhikr.targetCount}"
                holder.completeText.visibility = View.GONE
                holder.incrementButton.isEnabled = true
            }

            holder.editButton.setOnClickListener {
                showEditDhikrDialog(position)
            }

            holder.deleteButton.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Dhikr")
                    .setMessage("Are you sure you want to delete this dhikr?")
                    .setPositiveButton("Delete") { _, _ ->
                        dhikrList.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        saveCustomDhikr()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount() = dhikrList.size

        inner class DhikrViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val arabicText: TextView = itemView.findViewById(R.id.arabicText)
            val translationText: TextView = itemView.findViewById(R.id.translationText)
            val countText: TextView = itemView.findViewById(R.id.countText)
            val completeText: TextView = itemView.findViewById(R.id.completeText)
            val incrementButton: Button = itemView.findViewById(R.id.incrementButton)
            val resetButton: Button = itemView.findViewById(R.id.resetButton)
            val editButton: Button = itemView.findViewById(R.id.editButton)
            val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        }
    }
} 