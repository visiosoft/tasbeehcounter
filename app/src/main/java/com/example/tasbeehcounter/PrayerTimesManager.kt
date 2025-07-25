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
                    // Use stored data when offline
                    Log.d(TAG, "No internet available, using stored data for today")
                    val storedPrayerTimes = getPrayerTimes(context)
                    val todayPrayerTimes = storedPrayerTimes.find { it.date == today }
                    
                    if (todayPrayerTimes != null) {
                        Log.d(TAG, "Using stored prayer times for today: ${todayPrayerTimes.location}")
                        return@withContext todayPrayerTimes
                    } else {
                        Log.d(TAG, "No stored prayer times found for today")
                        return@withContext null
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prayer times for today", e)
                return@withContext null
            }
        }
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
                    // Clear old data and store the fresh data
                    clearCachedData(context)
                    savePrayerTimes(context, listOf(prayerTimes))
                    // Also save the current location
                    saveLastLocation(context, currentLocation)
                    // Save the fetch date to prevent immediate re-fetching
                    saveLastFetchDate(context, date)
                    Log.d(TAG, "Successfully fetched and stored fresh prayer times for today: ${prayerTimes.location}")
                    Log.d(TAG, "Prayer times for ${currentLocation.cityName}: Fajr=${prayerTimes.fajr}, Sunrise=${prayerTimes.sunrise}, Dhuhr=${prayerTimes.dhuhr}, Asr=${prayerTimes.asr}, Maghrib=${prayerTimes.maghrib}, Isha=${prayerTimes.isha}")
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
                        // Use a shorter timeout for faster response
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                continuation.resume(location)
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
                // Use method=1 (University Of Islamic Sciences, Karachi) for more accurate times
                // Add school=1 for Hanafi Asr calculation (shadow factor = 2)
                val url = "$API_BASE_URL/$date?latitude=${location.latitude}&longitude=${location.longitude}&method=1&school=1"
                Log.d(TAG, "Fetching prayer times from: $url")
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
                    Log.d(TAG, "Prayer times for ${location.cityName}: Fajr=${prayerTimes.fajr}, Sunrise=${prayerTimes.sunrise}, Dhuhr=${prayerTimes.dhuhr}, Asr=${prayerTimes.asr}, Maghrib=${prayerTimes.maghrib}, Isha=${prayerTimes.isha}")
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
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&accept-language=en&zoom=10&addressdetails=1"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "TasbeehCounter/1.0")
                connection.connectTimeout = 5000  // Increased for better accuracy
                connection.readTimeout = 5000     // Increased for better accuracy

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
                    
                    // Enhanced city name resolution with better fallback hierarchy
                    val cityName = when {
                        // Primary city names
                        address.optString("city").isNotEmpty() -> {
                            val city = address.optString("city")
                            val state = address.optString("state", "")
                            if (state.isNotEmpty()) "$city, $state" else city
                        }
                        address.optString("town").isNotEmpty() -> {
                            val town = address.optString("town")
                            val state = address.optString("state", "")
                            if (state.isNotEmpty()) "$town, $state" else town
                        }
                        address.optString("village").isNotEmpty() -> {
                            val village = address.optString("village")
                            val state = address.optString("state", "")
                            if (state.isNotEmpty()) "$village, $state" else village
                        }
                        // Suburban areas
                        address.optString("suburb").isNotEmpty() -> {
                            val suburb = address.optString("suburb")
                            val city = address.optString("city", "")
                            val state = address.optString("state", "")
                            when {
                                city.isNotEmpty() && state.isNotEmpty() -> "$suburb, $city, $state"
                                city.isNotEmpty() -> "$suburb, $city"
                                state.isNotEmpty() -> "$suburb, $state"
                                else -> suburb
                            }
                        }
                        // County/Province level
                        address.optString("county").isNotEmpty() -> {
                            val county = address.optString("county")
                            val state = address.optString("state", "")
                            if (state.isNotEmpty()) "$county, $state" else county
                        }
                        // State/Province level
                        address.optString("state").isNotEmpty() -> {
                            val state = address.optString("state")
                            val country = address.optString("country", "")
                            if (country.isNotEmpty()) "$state, $country" else state
                        }
                        // Country level
                        address.optString("country").isNotEmpty() -> {
                            address.optString("country")
                        }
                        // Display name from Nominatim as last resort
                        json.optString("display_name").isNotEmpty() -> {
                            val displayName = json.optString("display_name")
                            // Extract the most relevant part (usually first part before comma)
                            displayName.split(",").firstOrNull()?.trim() ?: displayName
                        }
                        else -> "Unknown Location"
                    }
                    
                    // Cache the result
                    geocodingCache[cacheKey] = cityName
                    
                    Log.d(TAG, "Resolved city name: $cityName from coordinates: $latitude, $longitude")
                    Log.d(TAG, "Full address data: ${address.toString()}")
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
        Log.d(TAG, "Saved last accurate location: ${location.cityName} (${location.latitude}, ${location.longitude})")
    }

    private fun getLastSavedLocation(context: Context): LocationData? {
        val sharedPreferences = context.getSharedPreferences("TasbeehPrefs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(LAST_LOCATION_KEY, null)
        return if (json != null) {
            try {
                val location = gson.fromJson(json, LocationData::class.java)
                Log.d(TAG, "Retrieved last saved location: ${location.cityName} (${location.latitude}, ${location.longitude})")
                location
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing last saved location", e)
                null
            }
        } else {
            Log.d(TAG, "No last saved location found")
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
        
        // Clear geocoding cache as well
        geocodingCache.clear()
        
        Log.d(TAG, "Cleared all cached prayer time data and geocoding cache")
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
                
                // Sync for today and next 2 days (total 3 days for offline use)
                for (i in 0..2) {
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
                    
                    // Keep only last 3 days of data for offline use
                    if (existingPrayerTimes.size > 3) {
                        existingPrayerTimes.sortBy { it.date }
                        while (existingPrayerTimes.size > 3) {
                            existingPrayerTimes.removeAt(0)
                        }
                    }
                    
                    savePrayerTimes(context, existingPrayerTimes)
                    saveLastLocation(context, location)
                    
                    // Save the fetch date to prevent immediate re-fetching
                    val today = dateFormat.format(Calendar.getInstance().time)
                    saveLastFetchDate(context, today)
                    
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
                
                // Fetch for today and next 2 days (total 3 days for offline use)
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
                
                // Save only the fresh online data
                if (fetchedPrayerTimes.isNotEmpty()) {
                    savePrayerTimes(context, fetchedPrayerTimes)
                    saveLastLocation(context, location)
                    saveLastSyncTime(context, System.currentTimeMillis())
                    
                    // Save the fetch date to prevent immediate re-fetching
                    val today = dateFormat.format(Calendar.getInstance().time)
                    saveLastFetchDate(context, today)
                    
                    Log.d(TAG, "Saved ${fetchedPrayerTimes.size} fresh prayer times from online API")
                    
                    // Return today's prayer times
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

    /**
     * Test different prayer time calculation methods to find the most accurate one
     * Methods: 1=Karachi, 2=Egypt, 3=North America, 4=Muslim World League, 5=Umm Al-Qura, 6=Fixed Isha
     */
    suspend fun testPrayerTimeMethods(context: Context): Map<Int, PrayerTimes?> {
        return withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, PrayerTimes?>()
            val today = dateFormat.format(Calendar.getInstance().time)
            
            // Get current location
            val currentLocation = getCurrentLocation(context)
            if (currentLocation == null) {
                Log.e(TAG, "Failed to get current location for method testing")
                return@withContext results
            }
            
            Log.d(TAG, "Testing prayer time methods for location: ${currentLocation.cityName}")
            
            // Test methods 1-6
            for (method in 1..6) {
                try {
                    val url = "$API_BASE_URL/$today?latitude=${currentLocation.latitude}&longitude=${currentLocation.longitude}&method=$method&school=1"
                    Log.d(TAG, "Testing method $method: $url")
                    
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

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
                            date = today,
                            fajr = timings.getString("Fajr"),
                            sunrise = timings.getString("Sunrise"),
                            dhuhr = timings.getString("Dhuhr"),
                            asr = timings.getString("Asr"),
                            maghrib = timings.getString("Maghrib"),
                            isha = timings.getString("Isha"),
                            location = "${currentLocation.cityName} (Method $method)"
                        )
                        
                        results[method] = prayerTimes
                        Log.d(TAG, "Method $method: Fajr=${prayerTimes.fajr}, Dhuhr=${prayerTimes.dhuhr}, Maghrib=${prayerTimes.maghrib}")
                    } else {
                        Log.e(TAG, "Method $method failed with response code: $responseCode")
                        results[method] = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error testing method $method", e)
                    results[method] = null
                }
            }
            
            Log.d(TAG, "Method testing completed. Results: ${results.keys.size} successful")
            return@withContext results
        }
    }

    /**
     * Test both Asr calculation methods to find the correct one
     * school=0: Shafi'i method (shadow factor = 1) - Standard
     * school=1: Hanafi method (shadow factor = 2) - Later Asr time
     */
    suspend fun testAsrCalculationMethods(context: Context): Map<String, PrayerTimes?> {
        return withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, PrayerTimes?>()
            val today = dateFormat.format(Calendar.getInstance().time)
            
            // Get current location
            val currentLocation = getCurrentLocation(context)
            if (currentLocation == null) {
                Log.e(TAG, "Failed to get current location for Asr method testing")
                return@withContext results
            }
            
            Log.d(TAG, "Testing Asr calculation methods for location: ${currentLocation.cityName}")
            
            // Test both school methods
            val schools = mapOf(
                "Shafi'i (Standard)" to 0,
                "Hanafi (Later Asr)" to 1
            )
            
            for ((schoolName, schoolValue) in schools) {
                try {
                    val url = "$API_BASE_URL/$today?latitude=${currentLocation.latitude}&longitude=${currentLocation.longitude}&method=1&school=$schoolValue"
                    Log.d(TAG, "Testing $schoolName: $url")
                    
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

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
                            date = today,
                            fajr = timings.getString("Fajr"),
                            sunrise = timings.getString("Sunrise"),
                            dhuhr = timings.getString("Dhuhr"),
                            asr = timings.getString("Asr"),
                            maghrib = timings.getString("Maghrib"),
                            isha = timings.getString("Isha"),
                            location = "${currentLocation.cityName} ($schoolName)"
                        )
                        
                        results[schoolName] = prayerTimes
                        Log.d(TAG, "$schoolName: Asr=${prayerTimes.asr}")
                    } else {
                        Log.e(TAG, "$schoolName failed with response code: $responseCode")
                        results[schoolName] = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error testing $schoolName", e)
                    results[schoolName] = null
                }
            }
            
            Log.d(TAG, "Asr method testing completed. Results: ${results.keys.size} successful")
            return@withContext results
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