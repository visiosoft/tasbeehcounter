package com.example.tasbeehcounter

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PrayerTimesManager {
    private const val PRAYER_TIMES_KEY = "prayer_times"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class PrayerTimes(
        val date: String,
        val fajr: String,
        val sunrise: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String
    )

    fun downloadAndSavePrayerTimes(context: Context) {
        // TODO: Implement actual API call to get prayer times
        // For now, using dummy data
        val today = dateFormat.format(Calendar.getInstance().time)
        val prayerTimes = PrayerTimes(
            date = today,
            fajr = "05:00",
            sunrise = "06:00",
            dhuhr = "12:00",
            asr = "15:00",
            maghrib = "18:00",
            isha = "19:00"
        )
        savePrayerTimes(context, listOf(prayerTimes))
    }

    fun savePrayerTimes(context: Context, prayerTimes: List<PrayerTimes>) {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(prayerTimes)
        sharedPreferences.edit().putString(PRAYER_TIMES_KEY, json).apply()
    }

    fun getPrayerTimes(context: Context): List<PrayerTimes> {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(PRAYER_TIMES_KEY, "[]")
        val type = object : TypeToken<List<PrayerTimes>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }

    fun getNextFajrTime(context: Context): Long {
        val prayerTimes = getPrayerTimes(context)
        val today = dateFormat.format(Calendar.getInstance().time)
        val todayPrayerTimes = prayerTimes.find { it.date == today }

        if (todayPrayerTimes != null) {
            val calendar = Calendar.getInstance()
            val fajrTime = todayPrayerTimes.fajr.split(":")
            calendar.set(Calendar.HOUR_OF_DAY, fajrTime[0].toInt())
            calendar.set(Calendar.MINUTE, fajrTime[1].toInt())
            calendar.set(Calendar.SECOND, 0)

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            return calendar.timeInMillis
        }

        // If no prayer times found, return next day at 5 AM
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 5)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        return calendar.timeInMillis
    }

    fun getCurrentPrayerTime(context: Context): String {
        val prayerTimes = getPrayerTimes(context)
        val today = dateFormat.format(Calendar.getInstance().time)
        val todayPrayerTimes = prayerTimes.find { it.date == today } ?: return ""

        val currentTime = Calendar.getInstance()
        val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(Calendar.MINUTE)
        val currentTimeString = String.format("%02d:%02d", currentHour, currentMinute)

        return when {
            currentTimeString < todayPrayerTimes.fajr -> "Fajr"
            currentTimeString < todayPrayerTimes.sunrise -> "Sunrise"
            currentTimeString < todayPrayerTimes.dhuhr -> "Dhuhr"
            currentTimeString < todayPrayerTimes.asr -> "Asr"
            currentTimeString < todayPrayerTimes.maghrib -> "Maghrib"
            currentTimeString < todayPrayerTimes.isha -> "Isha"
            else -> "Fajr"
        }
    }
} 