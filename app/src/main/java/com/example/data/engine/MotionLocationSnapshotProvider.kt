package com.example.data.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.data.model.LocationSnapshot
import com.example.data.model.RainContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * MotionLocationSnapshotProvider captures discrete single-shot location fixes
 * strictly on-demand (e.g. Walking start/end, speed-drop event) and calculates
 * true geographic distance between coordinates.
 *
 * ABSOLUTE RULE: No continuous GPS tracking during normal motion monitoring.
 */
class MotionLocationSnapshotProvider(private val context: Context) {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Lightweight in-memory weather cache (30-min TTL)
    private var cachedWeatherLat: Double? = null
    private var cachedWeatherLng: Double? = null
    private var cachedRainContext: RainContext = RainContext.UNAVAILABLE
    private var cachedWeatherTimeMs: Long = 0L

    /**
     * Requests a single on-demand location snapshot.
     * Stops location hardware immediately once fix is obtained.
     */
    @SuppressLint("MissingPermission")
    suspend fun requestSingleLocationFix(
        isStartingPoint: Boolean = false,
        isEndingPoint: Boolean = false
    ): LocationSnapshot? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return@withContext null
        }

        try {
            val cts = CancellationTokenSource()
            val location: Location? = try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
            } catch (_: Exception) {
                fusedLocationClient.lastLocation.await()
            }

            if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
                LocationSnapshot(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                    timestamp = location.time.let { if (it > 0) it else System.currentTimeMillis() },
                    isStartingPoint = isStartingPoint,
                    isEndingPoint = isEndingPoint
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes straight-line / snapshot distance between two coordinates using
     * geographic Haversine / Android Location geodesic formulas.
     *
     * Never invents distance.
     */
    fun calculateGeographicDistanceMeters(
        start: LocationSnapshot?,
        end: LocationSnapshot?
    ): Double? {
        if (start == null || end == null) return null
        if (start.latitude == 0.0 && start.longitude == 0.0) return null
        if (end.latitude == 0.0 && end.longitude == 0.0) return null

        return try {
            val results = FloatArray(1)
            Location.distanceBetween(
                start.latitude,
                start.longitude,
                end.latitude,
                end.longitude,
                results
            )
            results[0].toDouble()
        } catch (_: Throwable) {
            // Fallback mathematically exact haversine calculation for robust local test environments
            calculateHaversineDistance(start.latitude, start.longitude, end.latitude, end.longitude)
        }
    }

    /**
     * Mathematical Haversine formula (Earth radius ~ 6,371,000 meters).
     */
    private fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Obtains weather context for a location snapshot.
     * Uses 30-minute cache to avoid battery/network drain.
     * Returns UNAVAILABLE if no data can be verified.
     */
    suspend fun fetchWeatherContext(
        latitude: Double?,
        longitude: Double?
    ): RainContext = withContext(Dispatchers.IO) {
        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
            return@withContext RainContext.UNAVAILABLE
        }

        val now = System.currentTimeMillis()
        val cLat = cachedWeatherLat
        val cLng = cachedWeatherLng

        // Check if cache is still valid (< 30 minutes and < 5 km away)
        if (cLat != null && cLng != null && (now - cachedWeatherTimeMs < 1800000L)) {
            val dist = calculateHaversineDistance(cLat, cLng, latitude, longitude)
            if (dist < 5000.0) {
                return@withContext cachedRainContext
            }
        }

        try {
            // Non-blocking query to Open-Meteo free API for precipitation check
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=precipitation,rain".format(latitude, longitude)
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val currentObj = json.optJSONObject("current")
                val precipitation = currentObj?.optDouble("precipitation", 0.0) ?: 0.0
                val rain = currentObj?.optDouble("rain", 0.0) ?: 0.0

                val result = if (precipitation > 0.1 || rain > 0.1) {
                    RainContext.RAIN_DETECTED
                } else {
                    RainContext.NO_RAIN
                }

                // Update cache
                cachedWeatherLat = latitude
                cachedWeatherLng = longitude
                cachedWeatherTimeMs = now
                cachedRainContext = result

                return@withContext result
            }
        } catch (_: Exception) {
            // Zero fabrication: network or API failure defaults strictly to UNAVAILABLE
        }

        RainContext.UNAVAILABLE
    }
}
