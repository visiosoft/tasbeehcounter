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
    private const val PRAYER_TIMES_KEY = "enhanced_prayer_times"
    private const val LAST_FETCH_DATE_KEY = "last_fetch_date"
    private const val LAST_LOCATION_KEY = "last_location"
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
                    
                    // Keep only last 7 days of data
                    if (updatedPrayerTimes.size > 7) {
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
     * Get current location using GPS with improved accuracy
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
                
                // Try to get current location with better accuracy settings
                val location = suspendCoroutine<Location?> { continuation ->
                    try {
                        // First try to get high accuracy location
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                if (location != null && isLocationAccurate(location)) {
                                    Log.d(TAG, "Got accurate location: ${location.latitude}, ${location.longitude}")
                                    continuation.resume(location)
                                } else {
                                    Log.w(TAG, "Location not accurate enough, trying last known location")
                                    // Try last known location
                                    fusedLocationClient.lastLocation
                                        .addOnSuccessListener { lastLocation ->
                                            if (lastLocation != null && isLocationAccurate(lastLocation)) {
                                                Log.d(TAG, "Using accurate last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                                                continuation.resume(lastLocation)
                                            } else {
                                                Log.w(TAG, "Last known location not accurate, using any available location")
                                                continuation.resume(location ?: lastLocation)
                                            }
                                        }
                                        .addOnFailureListener { lastException ->
                                            Log.e(TAG, "Failed to get last known location", lastException)
                                            continuation.resume(location)
                                        }
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.w(TAG, "Failed to get current location, trying last known location", exception)
                                // Try last known location
                                fusedLocationClient.lastLocation
                                    .addOnSuccessListener { lastLocation ->
                                        if (lastLocation != null && isLocationAccurate(lastLocation)) {
                                            Log.d(TAG, "Using last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                                            continuation.resume(lastLocation)
                                        } else {
                                            Log.w(TAG, "Last known location not accurate")
                                            continuation.resume(lastLocation)
                                        }
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
     * Check if location is accurate enough (within 100 meters)
     */
    private fun isLocationAccurate(location: Location): Boolean {
        return location.accuracy <= 100f && location.latitude != 0.0 && location.longitude != 0.0
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
     * Get city name from coordinates using reverse geocoding with improved accuracy
     */
    private suspend fun getCityNameFromCoordinates(latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                // Use a more detailed zoom level for better accuracy
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&accept-language=en&zoom=12&addressdetails=1"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "TasbeehCounter/1.0")
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
                    
                    Log.d(TAG, "Reverse geocoding result: $cityName")
                    Log.d(TAG, "Full address: ${json.optString("display_name", "Unknown")}")
                    
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
        Log.d(TAG, "Cleared all cached prayer times")
    }
} 