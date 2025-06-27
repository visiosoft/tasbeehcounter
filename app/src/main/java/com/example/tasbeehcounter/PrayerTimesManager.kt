package com.example.tasbeehcounter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

object PrayerTimesManager {
    private const val TAG = "PrayerTimesManager"
    private const val PRAYER_TIMES_KEY = "prayer_times"
    private const val LAST_FETCH_DATE_KEY = "last_fetch_date"
    private const val LAST_LOCATION_KEY = "last_location"
    private const val LAST_SYNC_TIME_KEY = "last_sync_time"
    private const val SYNC_INTERVAL_HOURS = 12 // Sync twice a day (every 12 hours)
    private const val API_BASE_URL = "http://api.aladhan.com/v1/timings"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val gson = Gson()

    data class PrayerTimes(
        val date: String,
        val fajr: String,
        val sunrise: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String,
        val location: String = ""
    )

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val cityName: String = ""
    )

    /**
     * Main function to get prayer times - always fetches fresh data based on current location when online
     */
    suspend fun getPrayerTimesForToday(context: Context): PrayerTimes? {
        return withContext(Dispatchers.IO) {
            try {
                val today = dateFormat.format(Calendar.getInstance().time)
                
                // Always try to fetch fresh data based on current location when online
                if (isInternetAvailable()) {
                    Log.d(TAG, "Internet available, fetching fresh prayer times based on current location")
                    return@withContext fetchFreshDataForToday(context, today)
                } else {
                    // Only use stored data when offline
                    Log.d(TAG, "No internet available, using stored data for today")
                    val storedPrayerTimes = getPrayerTimes(context)
                    val todayPrayerTimes = storedPrayerTimes.find { it.date == today }
                    return@withContext todayPrayerTimes
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prayer times for today", e)
                return@withContext null
            }
        }
    }

    /**
     * Fetch fresh data for today based on current location
     */
    private suspend fun fetchFreshDataForToday(context: Context, date: String): PrayerTimes? {
        return withContext(Dispatchers.IO) {
            try {
                // Get current location
                val currentLocation = getCurrentLocation(context)
                if (currentLocation == null) {
                    Log.e(TAG, "Failed to get current location")
                    return@withContext null
                }

                Log.d(TAG, "Fetching prayer times for location: ${currentLocation.cityName} (${currentLocation.latitude}, ${currentLocation.longitude})")

                // Fetch prayer times for current location
                val prayerTimes = fetchPrayerTimesFromAPI(currentLocation, date)
                if (prayerTimes != null) {
                    // Store the fresh data
                    savePrayerTimes(context, listOf(prayerTimes))
                    // Also save the current location
                    saveLastLocation(context, currentLocation)
                    Log.d(TAG, "Successfully fetched and stored fresh prayer times for today: ${prayerTimes.location}")
                    return@withContext prayerTimes
                } else {
                    Log.e(TAG, "Failed to fetch prayer times from API")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching fresh data for today", e)
                return@withContext null
            }
        }
    }

    /**
     * Check if we should fetch new data
     */
    private fun shouldFetchNewData(context: Context): Boolean {
        val lastFetchDate = getLastFetchDate(context)
        val today = dateFormat.format(Calendar.getInstance().time)
        
        // If we've never fetched or last fetch was not today
        if (lastFetchDate != today) {
            // Check if internet is available
            return isInternetAvailable()
        }
        
        return false
    }

    /**
     * Get current location using GPS
     */
    private suspend fun getCurrentLocation(context: Context): LocationData? {
        return withContext(Dispatchers.IO) {
            try {
                // Check location permission
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Location permission not granted")
                    val lastLocation = getLastSavedLocation(context)
                    if (lastLocation != null) {
                        Log.d(TAG, "Using last saved location due to permission: ${lastLocation.cityName}")
                        return@withContext lastLocation
                    } else {
                        Log.e(TAG, "No location permission and no saved location available")
                        return@withContext null
                    }
                }

                val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
                
                // Try to get current location using suspendCoroutine
                val location = suspendCoroutine<Location?> { continuation ->
                    try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                continuation.resume(location)
                            }
                            .addOnFailureListener { exception ->
                                Log.w(TAG, "Failed to get current location, trying last known location", exception)
                                // Try last known location
                                fusedLocationClient.lastLocation
                                    .addOnSuccessListener { lastLocation ->
                                        continuation.resume(lastLocation)
                                    }
                                    .addOnFailureListener { lastException ->
                                        Log.e(TAG, "Failed to get last known location", lastException)
                                        continuation.resume(null)
                                    }
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting location", e)
                        continuation.resume(null)
                    }
                }

                if (location != null) {
                    // Get city name from coordinates
                    val cityName = getCityNameFromCoordinates(location.latitude, location.longitude)
                    val locationData = LocationData(location.latitude, location.longitude, cityName)
                    
                    Log.d(TAG, "Successfully got current location: ${location.latitude}, ${location.longitude}, $cityName")
                    return@withContext locationData
                } else {
                    Log.w(TAG, "Current location is null, trying last saved location")
                    val lastLocation = getLastSavedLocation(context)
                    if (lastLocation != null) {
                        Log.d(TAG, "Using last saved location: ${lastLocation.cityName}")
                        return@withContext lastLocation
                    } else {
                        Log.e(TAG, "No location available (current or saved)")
                        return@withContext null
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location", e)
                val lastLocation = getLastSavedLocation(context)
                if (lastLocation != null) {
                    Log.d(TAG, "Using last saved location due to error: ${lastLocation.cityName}")
                    return@withContext lastLocation
                } else {
                    Log.e(TAG, "No location available after error")
                    return@withContext null
                }
            }
        }
    }

    /**
     * Fetch prayer times from Aladhan API
     */
    private suspend fun fetchPrayerTimesFromAPI(location: LocationData, date: String): PrayerTimes? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE_URL/$date?latitude=${location.latitude}&longitude=${location.longitude}&method=2"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonResponse = JSONObject(response.toString())
                    val data = jsonResponse.getJSONObject("data")
                    val timings = data.getJSONObject("timings")

                    val prayerTimes = PrayerTimes(
                        date = date,
                        fajr = timings.getString("Fajr"),
                        sunrise = timings.getString("Sunrise"),
                        dhuhr = timings.getString("Dhuhr"),
                        asr = timings.getString("Asr"),
                        maghrib = timings.getString("Maghrib"),
                        isha = timings.getString("Isha"),
                        location = location.cityName
                    )

                    Log.d(TAG, "Successfully fetched prayer times from API")
                    return@withContext prayerTimes
                } else {
                    Log.e(TAG, "API request failed with response code: $responseCode")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching prayer times from API", e)
                return@withContext null
            }
        }
    }

    /**
     * Get city name from coordinates using reverse geocoding
     */
    private suspend fun getCityNameFromCoordinates(latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&accept-language=en&zoom=10"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "TasbeehCounter/1.0")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val address = json.getJSONObject("address")
                    
                    // Get city name with proper null/empty string handling
                    val cityName = address.optString("city").takeIf { it.isNotEmpty() }
                        ?: address.optString("town").takeIf { it.isNotEmpty() }
                        ?: address.optString("village").takeIf { it.isNotEmpty() }
                        ?: address.optString("county").takeIf { it.isNotEmpty() }
                        ?: address.optString("state").takeIf { it.isNotEmpty() }
                        ?: "Unknown Location"
                    
                    Log.d(TAG, "Resolved city name: $cityName from coordinates: $latitude, $longitude")
                    return@withContext cityName
                } else {
                    Log.e(TAG, "Reverse geocoding failed with response code: $responseCode")
                    return@withContext "Unknown Location"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting city name from coordinates", e)
                return@withContext "Unknown Location"
            }
        }
    }

    /**
     * Check if internet is available
     */
    private fun isInternetAvailable(): Boolean {
        return try {
            val url = URL("https://www.google.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "HEAD"
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }

    // Storage methods
    private fun savePrayerTimes(context: Context, prayerTimes: List<PrayerTimes>) {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val json = gson.toJson(prayerTimes)
        sharedPreferences.edit().putString(PRAYER_TIMES_KEY, json).apply()
    }

    fun getPrayerTimes(context: Context): List<PrayerTimes> {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(PRAYER_TIMES_KEY, "[]")
        val type = object : TypeToken<List<PrayerTimes>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveLastFetchDate(context: Context, date: String) {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(LAST_FETCH_DATE_KEY, date).apply()
    }

    private fun getLastFetchDate(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString(LAST_FETCH_DATE_KEY, null)
    }

    private fun saveLastLocation(context: Context, location: LocationData) {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val json = gson.toJson(location)
        sharedPreferences.edit().putString(LAST_LOCATION_KEY, json).apply()
    }

    private fun getLastSavedLocation(context: Context): LocationData? {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(LAST_LOCATION_KEY, null)
        return if (json != null) {
            try {
                gson.fromJson(json, LocationData::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Legacy methods for backward compatibility
    fun downloadAndSavePrayerTimes(context: Context) {
        // This method is now deprecated, use getPrayerTimesForToday instead
        Log.w(TAG, "downloadAndSavePrayerTimes is deprecated, use getPrayerTimesForToday instead")
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

    /**
     * Force refresh prayer times with current accurate location
     * This ensures stored data matches online data
     */
    suspend fun forceRefreshWithAccurateLocation(context: Context): PrayerTimes? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Force refreshing prayer times with accurate location")
                
                // Clear old cached data to ensure fresh fetch
                clearCachedData(context)
                
                // Get current accurate location
                val location = getCurrentLocation(context)
                if (location == null) {
                    Log.e(TAG, "Failed to get accurate location")
                    return@withContext null
                }
                
                Log.d(TAG, "Using accurate location: ${location.latitude}, ${location.longitude}, ${location.cityName}")
                
                // Fetch fresh prayer times from API
                val today = dateFormat.format(Calendar.getInstance().time)
                val prayerTimes = fetchPrayerTimesFromAPI(location, today)
                
                if (prayerTimes != null) {
                    // Save only the fresh data
                    val freshPrayerTimes = listOf(prayerTimes)
                    savePrayerTimes(context, freshPrayerTimes)
                    saveLastFetchDate(context, today)
                    saveLastLocation(context, location)
                    
                    Log.d(TAG, "Successfully refreshed prayer times with accurate location")
                    Log.d(TAG, "New prayer times: $prayerTimes")
                    
                    return@withContext prayerTimes
                } else {
                    Log.e(TAG, "Failed to fetch prayer times with accurate location")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error force refreshing with accurate location", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Clear all cached data to ensure fresh fetch
     */
    private fun clearCachedData(context: Context) {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove(PRAYER_TIMES_KEY)
            .remove(LAST_FETCH_DATE_KEY)
            .remove(LAST_LOCATION_KEY)
            .apply()
        Log.d(TAG, "Cleared all cached prayer time data")
    }

    /**
     * Automatic background sync - stores online data once or twice a day
     * This ensures offline availability of prayer times
     */
    suspend fun performAutomaticSync(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting automatic background sync...")
                
                // Check if sync is needed
                if (!shouldPerformSync(context)) {
                    Log.d(TAG, "Sync not needed yet, using cached data")
                    return@withContext true
                }
                
                // Get current location
                val location = getCurrentLocation(context)
                if (location == null) {
                    Log.e(TAG, "Failed to get location for automatic sync")
                    return@withContext false
                }
                
                Log.d(TAG, "Automatic sync using location: ${location.latitude}, ${location.longitude}, ${location.cityName}")
                
                // Fetch prayer times for today and next few days
                val calendar = Calendar.getInstance()
                val syncSuccess = mutableListOf<Boolean>()
                val fetchedPrayerTimes = mutableListOf<PrayerTimes>()
                
                // Sync for today and next 3 days
                for (i in 0..3) {
                    val targetDate = calendar.clone() as Calendar
                    targetDate.add(Calendar.DAY_OF_MONTH, i)
                    val dateString = dateFormat.format(targetDate.time)
                    
                    val prayerTimes = fetchPrayerTimesFromAPI(location, dateString)
                    if (prayerTimes != null) {
                        fetchedPrayerTimes.add(prayerTimes)
                        syncSuccess.add(true)
                        Log.d(TAG, "Successfully synced prayer times for $dateString")
                    } else {
                        syncSuccess.add(false)
                        Log.e(TAG, "Failed to sync prayer times for $dateString")
                    }
                }
                
                // Save all fetched prayer times
                if (fetchedPrayerTimes.isNotEmpty()) {
                    val existingPrayerTimes = getPrayerTimes(context).toMutableList()
                    
                    // Add new prayer times, avoiding duplicates
                    fetchedPrayerTimes.forEach { newPrayerTime ->
                        val existingIndex = existingPrayerTimes.indexOfFirst { it.date == newPrayerTime.date }
                        if (existingIndex >= 0) {
                            existingPrayerTimes[existingIndex] = newPrayerTime
                        } else {
                            existingPrayerTimes.add(newPrayerTime)
                        }
                    }
                    
                    // Keep only last 7 days of data
                    if (existingPrayerTimes.size > 7) {
                        existingPrayerTimes.sortBy { it.date }
                        while (existingPrayerTimes.size > 7) {
                            existingPrayerTimes.removeAt(0)
                        }
                    }
                    
                    savePrayerTimes(context, existingPrayerTimes)
                    saveLastLocation(context, location)
                    Log.d(TAG, "Saved ${fetchedPrayerTimes.size} prayer times to storage")
                }
                
                // Save sync timestamp
                saveLastSyncTime(context, System.currentTimeMillis())
                
                val success = syncSuccess.any { it }
                if (success) {
                    Log.d(TAG, "Automatic sync completed successfully")
                } else {
                    Log.e(TAG, "Automatic sync failed for all dates")
                }
                
                return@withContext success
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during automatic sync", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Check if automatic sync should be performed
     */
    private fun shouldPerformSync(context: Context): Boolean {
        val lastSyncTime = getLastSyncTime(context)
        val currentTime = System.currentTimeMillis()
        
        // If never synced, perform sync
        if (lastSyncTime == 0L) {
            Log.d(TAG, "First time sync needed")
            return true
        }
        
        // Check if SYNC_INTERVAL_HOURS have passed
        val timeDiff = currentTime - lastSyncTime
        val hoursDiff = timeDiff / (1000 * 60 * 60)
        
        val shouldSync = hoursDiff >= SYNC_INTERVAL_HOURS
        Log.d(TAG, "Hours since last sync: $hoursDiff, should sync: $shouldSync")
        
        return shouldSync
    }
    
    /**
     * Get prayer times with offline fallback
     * Uses stored data when offline, syncs when online
     */
    suspend fun getPrayerTimesWithOfflineFallback(context: Context): List<PrayerTimes> {
        return withContext(Dispatchers.IO) {
            try {
                // Get current stored prayer times first
                val currentPrayerTimes = getPrayerTimes(context)
                
                // Only try automatic sync if we have no data or very old data
                if (currentPrayerTimes.isEmpty() && isInternetAvailable()) {
                    Log.d(TAG, "No stored data, attempting automatic sync")
                    performAutomaticSync(context)
                } else if (isInternetAvailable() && shouldPerformSync(context)) {
                    Log.d(TAG, "Stored data available but sync needed, performing background sync")
                    // Perform sync in background without blocking UI
                    CoroutineScope(Dispatchers.IO).launch {
                        performAutomaticSync(context)
                    }
                } else {
                    Log.d(TAG, "Using stored prayer times (online: ${isInternetAvailable()})")
                }
                
                // Return current prayer times (either fresh from sync or cached)
                val prayerTimes = getPrayerTimes(context)
                Log.d(TAG, "Returning ${prayerTimes.size} prayer times")
                return@withContext prayerTimes
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prayer times with offline fallback", e)
                return@withContext getPrayerTimes(context)
            }
        }
    }
    
    /**
     * Save last sync timestamp
     */
    private fun saveLastSyncTime(context: Context, timestamp: Long) {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putLong(LAST_SYNC_TIME_KEY, timestamp).apply()
        Log.d(TAG, "Saved last sync time: ${Date(timestamp)}")
    }
    
    /**
     * Get last sync timestamp
     */
    private fun getLastSyncTime(context: Context): Long {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getLong(LAST_SYNC_TIME_KEY, 0L)
    }

    /**
     * Clear all stored prayer times and force fresh online fetch
     * This ensures only accurate online data is used
     */
    suspend fun clearAllStoredDataAndFetchFresh(context: Context): PrayerTimes? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Clearing all stored prayer times and fetching fresh data...")
                
                // Clear all stored data
                clearCachedData(context)
                
                // Check if internet is available
                if (!isInternetAvailable()) {
                    Log.e(TAG, "No internet available, cannot fetch fresh data")
                    return@withContext null
                }
                
                // Get current accurate location
                val location = getCurrentLocation(context)
                if (location == null) {
                    Log.e(TAG, "Failed to get accurate location")
                    return@withContext null
                }
                
                Log.d(TAG, "Using accurate location: ${location.latitude}, ${location.longitude}, ${location.cityName}")
                
                // Fetch prayer times for today and remaining days
                val calendar = Calendar.getInstance()
                val fetchedPrayerTimes = mutableListOf<PrayerTimes>()
                
                // Fetch for today and next 6 days (total 7 days)
                for (i in 0..6) {
                    val targetDate = calendar.clone() as Calendar
                    targetDate.add(Calendar.DAY_OF_MONTH, i)
                    val dateString = dateFormat.format(targetDate.time)
                    
                    val prayerTimes = fetchPrayerTimesFromAPI(location, dateString)
                    if (prayerTimes != null) {
                        fetchedPrayerTimes.add(prayerTimes)
                        Log.d(TAG, "Successfully fetched prayer times for $dateString")
                    } else {
                        Log.e(TAG, "Failed to fetch prayer times for $dateString")
                    }
                }
                
                // Save only the fresh online data
                if (fetchedPrayerTimes.isNotEmpty()) {
                    savePrayerTimes(context, fetchedPrayerTimes)
                    saveLastLocation(context, location)
                    saveLastSyncTime(context, System.currentTimeMillis())
                    
                    Log.d(TAG, "Saved ${fetchedPrayerTimes.size} fresh prayer times from online API")
                    
                    // Return today's prayer times
                    val today = dateFormat.format(Calendar.getInstance().time)
                    val todayPrayerTimes = fetchedPrayerTimes.find { it.date == today }
                    return@withContext todayPrayerTimes
                } else {
                    Log.e(TAG, "No prayer times were fetched successfully")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing stored data and fetching fresh", e)
                return@withContext null
            }
        }
    }
} 