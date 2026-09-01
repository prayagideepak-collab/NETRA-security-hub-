package com.example.data.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.data.model.LocationSnapshot
import com.example.data.model.RouteEventRecord
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * MotionLocationSnapshotProvider captures discrete single-shot location fixes
 * strictly on-demand (e.g. Motion start/end, significant route events) and calculates
 * true geographic distance between coordinates.
 *
 * ABSOLUTE RULE: No continuous GPS tracking during normal motion monitoring.
 * Security Hub Motion Route Tracking must NOT depend on weather, rain, or disaster APIs.
 */
class MotionLocationSnapshotProvider(private val context: Context) {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Requests a single on-demand location snapshot.
     * Releases location hardware immediately once fix is obtained.
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

            if (location != null && isValidCoordinate(location.latitude, location.longitude)) {
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
     * Validates geographic coordinates to prevent corrupted readings.
     */
    fun isValidCoordinate(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        if (lat == 0.0 && lng == 0.0) return false
        if (lat.isNaN() || lng.isNaN()) return false
        return lat in -90.0..90.0 && lng in -180.0..180.0
    }

    /**
     * Computes straight-line distance between two location snapshots.
     * Returns null if either coordinate is invalid. Never invents distance.
     */
    fun calculateGeographicDistanceMeters(
        start: LocationSnapshot?,
        end: LocationSnapshot?
    ): Double? {
        if (start == null || end == null) return null
        if (!isValidCoordinate(start.latitude, start.longitude)) return null
        if (!isValidCoordinate(end.latitude, end.longitude)) return null

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
     * Computes cumulative distance along the path:
     * Start Point -> Route Event 1 -> Route Event 2 -> ... -> End Point
     *
     * If only start and end points exist, computes the straight-line distance.
     * Returns null if fewer than 2 valid coordinates exist. Never invents distance.
     */
    fun calculateCumulativeRouteDistanceMeters(
        start: LocationSnapshot?,
        events: List<RouteEventRecord>,
        end: LocationSnapshot?
    ): Double? {
        val validPoints = mutableListOf<Pair<Double, Double>>()

        if (start != null && isValidCoordinate(start.latitude, start.longitude)) {
            validPoints.add(Pair(start.latitude, start.longitude))
        }

        for (event in events) {
            if (isValidCoordinate(event.latitude, event.longitude)) {
                validPoints.add(Pair(event.latitude!!, event.longitude!!))
            }
        }

        if (end != null && isValidCoordinate(end.latitude, end.longitude)) {
            validPoints.add(Pair(end.latitude, end.longitude))
        }

        if (validPoints.size < 2) {
            return null
        }

        var totalDistanceMeters = 0.0
        for (i in 0 until validPoints.size - 1) {
            val p1 = validPoints[i]
            val p2 = validPoints[i + 1]
            val legDist = try {
                val results = FloatArray(1)
                Location.distanceBetween(p1.first, p1.second, p2.first, p2.second, results)
                results[0].toDouble()
            } catch (_: Throwable) {
                calculateHaversineDistance(p1.first, p1.second, p2.first, p2.second)
            }
            totalDistanceMeters += legDist
        }

        return totalDistanceMeters
    }

    /**
     * Mathematical Haversine formula (Earth radius ~ 6,371,000 meters).
     */
    fun calculateHaversineDistance(
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
}
