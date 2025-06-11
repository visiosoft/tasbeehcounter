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

class DhikrListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DhikrAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dhikr_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.dhikrRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val dhikrList = listOf(
            Dhikr("سُبْحَانَ اللَّهِ", "Glory be to Allah", 33),
            Dhikr("الْحَمْدُ لِلَّهِ", "All praise is due to Allah", 33),
            Dhikr("اللَّهُ أَكْبَرُ", "Allah is the Greatest", 33),
            Dhikr("لَا إِلَٰهَ إِلَّا اللَّهُ", "There is no god but Allah", 100)
        )

        adapter = DhikrAdapter(dhikrList)
        recyclerView.adapter = adapter
    }

    data class Dhikr(
        val arabic: String,
        val translation: String,
        val targetCount: Int,
        var currentCount: Int = 0
    )

    private inner class DhikrAdapter(private val dhikrList: List<Dhikr>) :
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

            holder.incrementButton.setOnClickListener {
                if (dhikr.currentCount < dhikr.targetCount) {
                    dhikr.currentCount++
                    holder.countText.text = "${dhikr.currentCount}/${dhikr.targetCount}"
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
        }

        override fun getItemCount() = dhikrList.size

        inner class DhikrViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val arabicText: TextView = itemView.findViewById(R.id.arabicText)
            val translationText: TextView = itemView.findViewById(R.id.translationText)
            val countText: TextView = itemView.findViewById(R.id.countText)
            val completeText: TextView = itemView.findViewById(R.id.completeText)
            val incrementButton: Button = itemView.findViewById(R.id.incrementButton)
            val resetButton: Button = itemView.findViewById(R.id.resetButton)
        }
    }
} 