package com.example.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.util.LoggingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationContextInfo(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val locality: String = "Location Unavailable",
    val district: String = "Location Unavailable",
    val state: String = "Location Unavailable",
    val country: String = "Location Unavailable",
    val source: String = "LOCATION_UNAVAILABLE", // GPS, NETWORK, PASSIVE, CACHED, CURRENT_LOCATION, LOCATION_UNAVAILABLE
    val locationStatus: String = "LOCATION_UNAVAILABLE" // LOCATION_AVAILABLE, COORDINATES_AVAILABLE, GEOCODER_FAILED, LOCATION_STALE, LOCATION_TIMEOUT, PERMISSION_NOT_GRANTED, PROVIDER_DISABLED, LOCATION_UNAVAILABLE
)

class OfficialLocationContextManager(private val context: Context) {

    private val _locationContext = MutableStateFlow(LocationContextInfo())
    val locationContext: StateFlow<LocationContextInfo> = _locationContext.asStateFlow()

    private var lastFetchTime = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0

    suspend fun refreshLocation(): LocationContextInfo = withContext(Dispatchers.IO) {
        // Stage A: Permission Check
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            LoggingManager.info("LocationManager", "LOCATION_PERMISSION_STATE", "Permission not granted", "Status: PERMISSION_NOT_GRANTED")
            val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE", locationStatus = "PERMISSION_NOT_GRANTED")
            _locationContext.value = info
            return@withContext info
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE", locationStatus = "LOCATION_UNAVAILABLE")
            _locationContext.value = info
            return@withContext info
        }

        // Stage B: Provider Availability Check
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        LoggingManager.info("LocationManager", "LOCATION_PROVIDER_STATE", "GPS: $isGpsEnabled, Network: $isNetworkEnabled", "")

        if (!isGpsEnabled && !isNetworkEnabled) {
            val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE", locationStatus = "PROVIDER_DISABLED")
            _locationContext.value = info
            return@withContext info
        }

        val now = System.currentTimeMillis()
        var bestLocation: Location? = null
        var sourceUsed = "LOCATION_UNAVAILABLE"

        // Stage C: Cached Location Validation (GPS / Network / Passive)
        try {
            if (hasFine && isGpsEnabled) {
                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLoc != null && isValidCoordinate(gpsLoc.latitude, gpsLoc.longitude)) {
                    val age = now - gpsLoc.time
                    if (age < 900000L) { // 15 mins fresh
                        bestLocation = gpsLoc
                        sourceUsed = "GPS"
                        LoggingManager.info("LocationManager", "CACHED_LOCATION_FOUND", "Found fresh GPS cache", "Age: ${age}ms")
                    } else {
                        LoggingManager.info("LocationManager", "CACHED_LOCATION_STALE", "GPS cache stale", "Age: ${age}ms")
                    }
                }
            }

            if (bestLocation == null && isNetworkEnabled) {
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (netLoc != null && isValidCoordinate(netLoc.latitude, netLoc.longitude)) {
                    val age = now - netLoc.time
                    if (age < 900000L) {
                        bestLocation = netLoc
                        sourceUsed = "NETWORK"
                        LoggingManager.info("LocationManager", "CACHED_LOCATION_FOUND", "Found fresh Network cache", "Age: ${age}ms")
                    }
                }
            }

            if (bestLocation == null) {
                val passLoc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (passLoc != null && isValidCoordinate(passLoc.latitude, passLoc.longitude)) {
                    val age = now - passLoc.time
                    if (age < 1800000L) { // 30 mins for passive
                        bestLocation = passLoc
                        sourceUsed = "PASSIVE"
                        LoggingManager.info("LocationManager", "CACHED_LOCATION_FOUND", "Found Passive cache", "Age: ${age}ms")
                    }
                }
            }

            if (bestLocation == null) {
                if (hasFine && isGpsEnabled) {
                    val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (gpsLoc != null && isValidCoordinate(gpsLoc.latitude, gpsLoc.longitude)) {
                        bestLocation = gpsLoc
                        sourceUsed = "GPS"
                    }
                }
                if (bestLocation == null && isNetworkEnabled) {
                    val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (netLoc != null && isValidCoordinate(netLoc.latitude, netLoc.longitude)) {
                        bestLocation = netLoc
                        sourceUsed = "NETWORK"
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.critical("LocationManager", "LOCATION_FETCH_ERROR", "Error reading cached location: ${e.message}", "")
        }

        // Stage D & E: One-Shot Current Location if cache is null or unusable
        if (bestLocation == null) {
            LoggingManager.info("LocationManager", "ONE_SHOT_LOCATION_REQUEST", "Requesting one-shot fresh location", "")
            try {
                val providerToTry = if (isNetworkEnabled && hasCoarse) LocationManager.NETWORK_PROVIDER else if (isGpsEnabled && hasFine) LocationManager.GPS_PROVIDER else null
                if (providerToTry != null) {
                    val fetchedLoc = requestOneShotLocation(locationManager, providerToTry)
                    if (fetchedLoc != null && isValidCoordinate(fetchedLoc.latitude, fetchedLoc.longitude)) {
                        bestLocation = fetchedLoc
                        sourceUsed = if (providerToTry == LocationManager.GPS_PROVIDER) "GPS" else "NETWORK"
                        LoggingManager.info("LocationManager", "ONE_SHOT_LOCATION_SUCCESS", "One-shot location obtained via $sourceUsed", "")
                    } else {
                        LoggingManager.info("LocationManager", "ONE_SHOT_LOCATION_TIMEOUT", "One-shot location timed out or invalid on $providerToTry", "")
                        if (providerToTry == LocationManager.NETWORK_PROVIDER && isGpsEnabled && hasFine) {
                            val gpsFetched = requestOneShotLocation(locationManager, LocationManager.GPS_PROVIDER)
                            if (gpsFetched != null && isValidCoordinate(gpsFetched.latitude, gpsFetched.longitude)) {
                                bestLocation = gpsFetched
                                sourceUsed = "GPS"
                                LoggingManager.info("LocationManager", "ONE_SHOT_LOCATION_SUCCESS", "One-shot fallback GPS obtained", "")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LoggingManager.critical("LocationManager", "ONE_SHOT_ERROR", "One-shot location error: ${e.message}", "")
            }
        }

        if (bestLocation == null || !isValidCoordinate(bestLocation.latitude, bestLocation.longitude)) {
            val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE", locationStatus = "LOCATION_UNAVAILABLE")
            _locationContext.value = info
            return@withContext info
        }

        val lat = bestLocation.latitude
        val lon = bestLocation.longitude

        val distance = floatArrayOf(0f)
        if (lastLat != 0.0 && lastLon != 0.0) {
            Location.distanceBetween(lastLat, lastLon, lat, lon, distance)
        }
        if (distance[0] < 1000f && (now - lastFetchTime) < 900000L && _locationContext.value.locationStatus != "LOCATION_UNAVAILABLE" && _locationContext.value.locationStatus != "PERMISSION_NOT_GRANTED") {
            return@withContext _locationContext.value
        }

        lastLat = lat
        lastLon = lon
        lastFetchTime = now

        var locality = "Location Unavailable"
        var district = "Location Unavailable"
        var state = "Location Unavailable"
        var country = "Location Unavailable"
        var geocoderSuccess = false

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Location Unavailable"
                district = addr.subAdminArea ?: locality
                state = addr.adminArea ?: "Location Unavailable"
                country = addr.countryName ?: "Location Unavailable"
                geocoderSuccess = true
                LoggingManager.info("LocationManager", "GEOCODER_SUCCESS", "Geocoded successfully: $locality, $state", "")
            } else {
                LoggingManager.info("LocationManager", "GEOCODER_FAILED", "Geocoder returned empty addresses", "")
            }
        } catch (e: Exception) {
            LoggingManager.info("LocationManager", "GEOCODER_FAILED", "Reverse geocoding failed: ${e.message}", "Retaining valid coordinates.")
        }

        val locationStatus = if (geocoderSuccess && locality != "Location Unavailable") "LOCATION_AVAILABLE" else "COORDINATES_AVAILABLE"

        val info = LocationContextInfo(
            latitude = lat,
            longitude = lon,
            accuracy = bestLocation.accuracy,
            timestamp = bestLocation.time,
            locality = locality,
            district = district,
            state = state,
            country = country,
            source = sourceUsed,
            locationStatus = locationStatus
        )

        _locationContext.value = info
        LoggingManager.info("LocationManager", "FINAL_LOCATION_STATUS", "Final status: $locationStatus ($sourceUsed)", "Lat: $lat, Lon: $lon")
        return@withContext info
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (lat.isNaN() || lat.isInfinite() || lon.isNaN() || lon.isInfinite()) return false
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return false
        return true
    }

    private suspend fun requestOneShotLocation(locationManager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = android.os.CancellationSignal()
                locationManager.getCurrentLocation(provider, cancellationSignal, context.mainExecutor, { location ->
                    if (!continuation.isCompleted) {
                        continuation.resume(location)
                    }
                })
                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (e: Exception) {}
                        if (!continuation.isCompleted) {
                            continuation.resume(location)
                        }
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, listener, context.mainLooper)
                continuation.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            if (!continuation.isCompleted) {
                continuation.resume(null)
            }
        }
    }
}
