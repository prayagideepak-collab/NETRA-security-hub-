package com.example.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.util.LoggingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

data class LocationContextInfo(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val locality: String = "Location Unavailable",
    val district: String = "Location Unavailable",
    val state: String = "Location Unavailable",
    val country: String = "Location Unavailable",
    val source: String = "LOCATION_UNAVAILABLE" // GPS / NETWORK / COARSE / LAST_KNOWN / LOCATION_UNAVAILABLE
)

class OfficialLocationContextManager(private val context: Context) {

    private val _locationContext = MutableStateFlow(LocationContextInfo())
    val locationContext: StateFlow<LocationContextInfo> = _locationContext.asStateFlow()

    private var lastFetchTime = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0

    suspend fun refreshLocation(): LocationContextInfo = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE")
            _locationContext.value = info
            return@withContext info
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE")
            _locationContext.value = info
            return@withContext info
        }

        var bestLocation: Location? = null
        var sourceUsed = "LOCATION_UNAVAILABLE"

        try {
            if (hasFine) {
                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLoc != null) {
                    bestLocation = gpsLoc
                    sourceUsed = "GPS"
                }
            }

            if (bestLocation == null && (hasFine || hasCoarse)) {
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (netLoc != null) {
                    bestLocation = netLoc
                    sourceUsed = "NETWORK"
                }
            }

            if (bestLocation == null) {
                val passLoc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (passLoc != null) {
                    bestLocation = passLoc
                    sourceUsed = "LAST_KNOWN"
                }
            }
        } catch (e: Exception) {
            LoggingManager.critical("LocationManager", "LOCATION_FETCH_ERROR", "Error fetching location: ${e.message}", "Using fallback.")
        }

        if (bestLocation == null) {
            val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE")
            _locationContext.value = info
            return@withContext info
        }

        val now = System.currentTimeMillis()
        val lat = bestLocation.latitude
        val lon = bestLocation.longitude
        val distance = floatArrayOf(0f)
        if (lastLat != 0.0 && lastLon != 0.0) {
            Location.distanceBetween(lastLat, lastLon, lat, lon, distance)
        }

        if (distance[0] < 1000f && (now - lastFetchTime) < 900000L && _locationContext.value.locality != "Location Unavailable") {
            return@withContext _locationContext.value
        }

        lastLat = lat
        lastLon = lon
        lastFetchTime = now

        var locality = "Location Unavailable"
        var district = "Location Unavailable"
        var state = "Location Unavailable"
        var country = "Location Unavailable"

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Location Unavailable"
                    district = addr.subAdminArea ?: locality
                    state = addr.adminArea ?: "Location Unavailable"
                    country = addr.countryName ?: "Location Unavailable"
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Location Unavailable"
                    district = addr.subAdminArea ?: locality
                    state = addr.adminArea ?: "Location Unavailable"
                    country = addr.countryName ?: "Location Unavailable"
                }
            }
        } catch (e: Exception) {
            LoggingManager.info("LocationManager", "GEOCODE_ERROR", "Reverse geocoding failed: ${e.message}", "Using coordinates/fallback.")
        }

        val info = LocationContextInfo(
            latitude = lat,
            longitude = lon,
            accuracy = bestLocation.accuracy,
            timestamp = bestLocation.time,
            locality = locality,
            district = district,
            state = state,
            country = country,
            source = sourceUsed
        )

        _locationContext.value = info
        LoggingManager.info("LocationManager", "LOCATION_ACQUIRED", "Location updated: $locality, $state ($sourceUsed)", "Lat: $lat, Lon: $lon")
        return@withContext info
    }
}
