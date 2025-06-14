package com.example.tasbeehcounter

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class PrayerTimeUpdateService : Service() {
    private val TAG = "PrayerTimeUpdateService"
    private val handler = Handler(Looper.getMainLooper())
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        setupNetworkCallback()
        scheduleNextUpdate()
    }

    private fun setupNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                downloadPrayerTimes()
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun scheduleNextUpdate() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val delay = calendar.timeInMillis - System.currentTimeMillis()
        handler.postDelayed({
            resetCounts()
            downloadPrayerTimes()
            scheduleNextUpdate()
        }, delay)
    }

    private fun downloadPrayerTimes() {
        // TODO: Implement actual API call to get prayer times
        // For now, using dummy data
        val today = dateFormat.format(Calendar.getInstance().time)
        val prayerTimes = PrayerTimesManager.PrayerTimes(
            date = today,
            fajr = "05:00",
            sunrise = "06:00",
            dhuhr = "12:00",
            asr = "15:00",
            maghrib = "18:00",
            isha = "19:00"
        )
        PrayerTimesManager.downloadAndSavePrayerTimes(this)
    }

    private fun resetCounts() {
        val sharedPreferences = getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("saved_counts", "[]").apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        handler.removeCallbacksAndMessages(null)
    }
} 