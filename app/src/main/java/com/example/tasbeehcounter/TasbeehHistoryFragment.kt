package com.example.tasbeehcounter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TasbeehHistoryFragment : Fragment() {
    private lateinit var todayCount: TextView
    private lateinit var yesterdayCount: TextView
    private lateinit var historyRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tasbeeh_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        todayCount = view.findViewById(R.id.todayCount)
        yesterdayCount = view.findViewById(R.id.yesterdayCount)
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        
        loadHistory()
    }

    private fun loadHistory() {
        val sharedPreferences = requireContext().getSharedPreferences("TasbeehSettings", android.content.Context.MODE_PRIVATE)
        val savedCounts = sharedPreferences.getString("saved_counts", "[]")
        val countsList = mutableListOf<TasbeehCount>()
        
        if (savedCounts != null && savedCounts != "[]") {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = dateFormat.format(calendar.time)
            
            var todayTotal = 0
            var yesterdayTotal = 0
            
            savedCounts.split(",").forEach { countString ->
                val parts = countString.split("|")
                if (parts.size >= 2) {
                    val count = parts[0].toIntOrNull() ?: 0
                    val date = dateFormat.parse(parts[1])
                    if (date != null) {
                        val tasbeehCount = TasbeehCount(count, date)
                        countsList.add(tasbeehCount)
                        
                        val countDate = dateFormat.format(date)
                        when (countDate) {
                            today -> todayTotal += count
                            yesterday -> yesterdayTotal += count
                        }
                    }
                }
            }
            
            todayCount.text = todayTotal.toString()
            yesterdayCount.text = yesterdayTotal.toString()
            
            // Sort by date and get last 7 days
            val sortedCounts = countsList.sortedByDescending { it.date }
            val last7Days = sortedCounts.take(7)
            
            historyRecyclerView.layoutManager = LinearLayoutManager(context)
            historyRecyclerView.adapter = HistoryAdapter(last7Days)
        } else {
            todayCount.text = "0"
            yesterdayCount.text = "0"
            historyRecyclerView.layoutManager = LinearLayoutManager(context)
            historyRecyclerView.adapter = HistoryAdapter(emptyList())
        }
    }

    private inner class HistoryAdapter(private val counts: List<TasbeehCount>) :
        RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val count = counts[position]
            val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
            holder.dateText.text = dateFormat.format(count.date)
            holder.countText.text = count.count.toString()
        }

        override fun getItemCount() = counts.size

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val dateText: TextView = itemView.findViewById(R.id.dateText)
            val countText: TextView = itemView.findViewById(R.id.countText)
        }
    }
} 