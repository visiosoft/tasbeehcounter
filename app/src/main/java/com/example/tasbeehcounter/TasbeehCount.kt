package com.example.tasbeehcounter

import java.util.Date

data class TasbeehCount(
    val count: Int,
    val date: Date
)

enum class CountType {
    DAILY,
    WEEKLY
} 