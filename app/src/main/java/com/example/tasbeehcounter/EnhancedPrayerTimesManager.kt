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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object EnhancedPrayerTimesManager {
    private const val TAG = "EnhancedPrayerTimesManager"
    private const val PRAYER_TIMES_KEY = "prayer_times"
    private const val LAST_FETCH_DATE_KEY = "last_fetch_date"
    private const val LAST_LOCATION_KEY = "last_location"
    private const val API_BASE_URL = "http://api.aladhan.com/v1/timings"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val gson = Gson()
    
    // Simple cache for reverse geocoding results
    private val geocodingCache = mutableMapOf<String, String>()

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
     * Main function to get prayer times - fetches only once per day
     */
    suspend fun getPrayerTimesForToday(context: Context): PrayerTimes? {
        return withContext(Dispatchers.IO) {
            try {
                val today = dateFormat.format(Calendar.getInstance().time)
                
                // Check if we already have prayer times for today
                val existingPrayerTimes = getPrayerTimes(context)
                val todayPrayerTimes = existingPrayerTimes.find { it.date == today }
                
                if (todayPrayerTimes != null) {
                    Log.d(TAG, "Using cached prayer times for today: $today")
                    return@withContext todayPrayerTimes
                }
                
                // Check if we should fetch new data
                if (!shouldFetchNewData(context)) {
                    Log.d(TAG, "Skipping fetch - using existing data or no internet")
                    return@withContext existingPrayerTimes.lastOrNull()
                }
                
                // Get current location
                val location = getCurrentLocation(context)
                if (location == null) {
                    Log.e(TAG, "Failed to get location")
                    return@withContext existingPrayerTimes.lastOrNull()
                }
                
                // Fetch prayer times from API
                val prayerTimes = fetchPrayerTimesFromAPI(location, today)
                if (prayerTimes != null) {
                    // Save new prayer times
                    val updatedPrayerTimes = existingPrayerTimes.toMutableList()
                    updatedPrayerTimes.add(prayerTimes)
                    
                    // Keep only last 3 days of data for offline use
                    if (updatedPrayerTimes.size > 3) {
                        updatedPrayerTimes.removeAt(0)
                    }
                    
                    savePrayerTimes(context, updatedPrayerTimes)
                    saveLastFetchDate(context, today)
                    saveLastLocation(context, location)
                    
                    Log.d(TAG, "Successfully fetched and saved prayer times for $today")
                    return@withContext prayerTimes
                } else {
                    Log.e(TAG, "Failed to fetch prayer times from API")
                    return@withContext existingPrayerTimes.lastOrNull()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prayer times for today", e)
                return@withContext getPrayerTimes(context).lastOrNull()
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
     * Get current location using GPS with optimized performance
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
                    return@withContext getLastSavedLocation(context)
                }

                val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
                
                // First, try to get last known location immediately (fastest)
                val lastKnownLocation = suspendCoroutine<Location?> { continuation ->
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location ->
                            continuation.resume(location)
                        }
                        .addOnFailureListener { exception ->
                            Log.w(TAG, "Failed to get last known location", exception)
                            continuation.resume(null)
                        }
                }
                
                if (lastKnownLocation != null && isLocationAccurate(lastKnownLocation)) {
                    Log.d(TAG, "Using fast last known location: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                    val cityName = getCityNameFromCoordinates(lastKnownLocation.latitude, lastKnownLocation.longitude)
                    val locationData = LocationData(lastKnownLocation.latitude, lastKnownLocation.longitude, cityName)
                    return@withContext locationData
                }
                
                // If last known location is not accurate, try current location with timeout
                val location = suspendCoroutine<Location?> { continuation ->
                    try {
                        // Use balanced power accuracy for faster response
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                if (location != null && isLocationAccurate(location)) {
                                    Log.d(TAG, "Got accurate location: ${location.latitude}, ${location.longitude}")
                                    continuation.resume(location)
                                } else {
                                    Log.w(TAG, "Location not accurate enough, using last known")
                                    continuation.resume(lastKnownLocation)
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.w(TAG, "Failed to get current location, using last known", exception)
                                continuation.resume(lastKnownLocation)
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting location", e)
                        continuation.resume(lastKnownLocation)
                    }
                }

                if (location != null) {
                    // Get city name from coordinates with better error handling
                    val cityName = getCityNameFromCoordinates(location.latitude, location.longitude)
                    val locationData = LocationData(location.latitude, location.longitude, cityName)
                    
                    Log.d(TAG, "Final location: ${location.latitude}, ${location.longitude}, $cityName")
                    Log.d(TAG, "Location accuracy: ${location.accuracy} meters")
                    Log.d(TAG, "Location provider: ${location.provider}")
                    
                    return@withContext locationData
                } else {
                    Log.w(TAG, "Location is null, using last saved location")
                    return@withContext getLastSavedLocation(context)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location", e)
                return@withContext getLastSavedLocation(context)
            }
        }
    }

    /**
     * Check if location is accurate enough (within 500 meters for faster response)
     */
    private fun isLocationAccurate(location: Location): Boolean {
        return location.accuracy <= 500f && location.latitude != 0.0 && location.longitude != 0.0
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
                connection.connectTimeout = 5000  // Reduced from 10000
                connection.readTimeout = 5000     // Reduced from 10000

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
                    val dateInfo = data.getJSONObject("date")

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
     * Get city name from coordinates using reverse geocoding with optimized performance
     */
    private suspend fun getCityNameFromCoordinates(latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                // Create cache key from coordinates (rounded to reduce cache size)
                val cacheKey = "${(latitude * 100).toInt() / 100.0},${(longitude * 100).toInt() / 100.0}"
                
                // Check cache first
                geocodingCache[cacheKey]?.let { cachedCityName ->
                    Log.d(TAG, "Using cached city name: $cachedCityName for coordinates: $latitude, $longitude")
                    return@withContext cachedCityName
                }
                
                // Use faster timeout and simpler parameters
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&accept-language=en&zoom=8"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "TasbeehCounter/1.0")
                connection.connectTimeout = 3000  // Reduced from 10000
                connection.readTimeout = 3000     // Reduced from 10000

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
                    
                    // Try multiple address fields in order of preference
                    val cityName = address.optString("city") 
                        ?: address.optString("town") 
                        ?: address.optString("village")
                        ?: address.optString("suburb")
                        ?: address.optString("county")
                        ?: address.optString("state")
                        ?: "Unknown Location"
                    
                    // Cache the result
                    geocodingCache[cacheKey] = cityName
                    
                    Log.d(TAG, "Reverse geocoding result: $cityName")
                    
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

    fun getLastFetchDate(context: Context): String? {
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

    /**
     * Force refresh prayer times (useful for testing)
     */
    suspend fun forceRefreshPrayerTimes(context: Context): PrayerTimes? {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().remove(LAST_FETCH_DATE_KEY).apply()
        return getPrayerTimesForToday(context)
    }

    /**
     * Force refresh location and prayer times
     */
    suspend fun forceRefreshLocationAndPrayerTimes(context: Context): PrayerTimes? {
        Log.d(TAG, "Force refreshing location and prayer times")
        
        // Clear cached location to force new location fetch
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove(LAST_FETCH_DATE_KEY)
            .remove(LAST_LOCATION_KEY)
            .apply()
        
        return getPrayerTimesForToday(context)
    }

    /**
     * Get debug information about current location and prayer times
     */
    fun getDebugInfo(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val lastFetchDate = sharedPreferences.getString(LAST_FETCH_DATE_KEY, "Never")
        val lastLocationJson = sharedPreferences.getString(LAST_LOCATION_KEY, "None")
        val prayerTimesJson = sharedPreferences.getString(PRAYER_TIMES_KEY, "[]")
        
        val prayerTimes = getPrayerTimes(context)
        val today = dateFormat.format(Calendar.getInstance().time)
        val todayPrayerTimes = prayerTimes.find { it.date == today }
        
        return """
            Debug Information:
            Last Fetch Date: $lastFetchDate
            Last Location: $lastLocationJson
            Today's Date: $today
            Today's Prayer Times: ${todayPrayerTimes ?: "Not found"}
            Total Cached Prayer Times: ${prayerTimes.size}
            Internet Available: ${isInternetAvailable()}
        """.trimIndent()
    }

    /**
     * Clear all cached prayer times
     */
    fun clearCachedPrayerTimes(context: Context) {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove(PRAYER_TIMES_KEY)
            .remove(LAST_FETCH_DATE_KEY)
            .remove(LAST_LOCATION_KEY)
            .apply()
        
        // Clear geocoding cache as well
        geocodingCache.clear()
        
        Log.d(TAG, "Cleared all cached prayer times and geocoding cache")
    }

    /**
     * Get prayer times for offline use - returns stored data for 3 days
     */
    suspend fun getPrayerTimesForOffline(context: Context): List<PrayerTimes> {
        return withContext(Dispatchers.IO) {
            try {
                val storedPrayerTimes = getPrayerTimes(context)
                
                if (storedPrayerTimes.isNotEmpty()) {
                    Log.d(TAG, "Returning ${storedPrayerTimes.size} stored prayer times for offline use")
                    return@withContext storedPrayerTimes
                } else {
                    Log.d(TAG, "No stored prayer times available for offline use")
                    return@withContext emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting offline prayer times", e)
                return@withContext emptyList()
            }
        }
    }

    /**
     * Auto-update prayer times when going online
     */
    suspend fun autoUpdateWhenOnline(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInternetAvailable()) {
                    Log.d(TAG, "No internet available, skipping auto-update")
                    return@withContext false
                }
                
                Log.d(TAG, "Internet available, performing auto-update")
                
                // Get current location
                val location = getCurrentLocation(context)
                if (location == null) {
                    Log.e(TAG, "Failed to get location for auto-update")
                    return@withContext false
                }
                
                // Fetch prayer times for 3 days (today + next 2 days)
                val calendar = Calendar.getInstance()
                val fetchedPrayerTimes = mutableListOf<PrayerTimes>()
                
                for (i in 0..2) {
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
                
                // Save the fetched data for offline use
                if (fetchedPrayerTimes.isNotEmpty()) {
                    savePrayerTimes(context, fetchedPrayerTimes)
                    saveLastLocation(context, location)
                    saveLastFetchDate(context, dateFormat.format(Calendar.getInstance().time))
                    
                    Log.d(TAG, "Auto-update completed: saved ${fetchedPrayerTimes.size} prayer times")
                    return@withContext true
                } else {
                    Log.e(TAG, "Auto-update failed: no prayer times fetched")
                    return@withContext false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-update", e)
                return@withContext false
            }
        }
    }

    /**
     * Check if offline prayer times are available for today
     */
    fun hasOfflinePrayerTimes(context: Context): Boolean {
        val storedPrayerTimes = getPrayerTimes(context)
        val today = dateFormat.format(Calendar.getInstance().time)
        return storedPrayerTimes.any { it.date == today }
    }

    /**
     * Get today's prayer times for offline use
     */
    fun getTodayPrayerTimesOffline(context: Context): PrayerTimes? {
        val storedPrayerTimes = getPrayerTimes(context)
        val today = dateFormat.format(Calendar.getInstance().time)
        return storedPrayerTimes.find { it.date == today }
    }
} 