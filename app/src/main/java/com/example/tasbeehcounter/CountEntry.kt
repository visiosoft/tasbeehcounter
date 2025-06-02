package com.example.tasbeehcounter

import java.text.SimpleDateFormat
import java.util.*

data class CountEntry(
    val count: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
} 