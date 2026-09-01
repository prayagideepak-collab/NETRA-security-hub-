package com.aistudio.netrasensorhub.data.intelligence.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.aistudio.netrasensorhub.data.intelligence.models.LocationRecord
import com.aistudio.netrasensorhub.data.intelligence.models.LocationStatus
import com.aistudio.netrasensorhub.data.intelligence.models.MotionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class DynamicLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _locationRecord = MutableStateFlow<LocationRecord?>(null)
    val locationRecord: StateFlow<LocationRecord?> = _locationRecord.asStateFlow()

    private val _locationStatus = MutableStateFlow(LocationStatus.ACQUIRING)
    val locationStatus: StateFlow<LocationStatus> = _locationStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var periodicJob: Job? = null
    private var acquisitionTimeoutJob: Job? = null

    private var currentMotionState = MotionState.STATIONARY
    private var isListening = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /**
     * Updates motion state to dynamically adjust acquisition strategy.
     */
    fun updateMotionState(motion: MotionState) {
        if (currentMotionState != motion) {
            currentMotionState = motion
            // Restart periodic cycle with new adaptive interval
            startLowPowerPeriodicTracking()
        }
    }

    /**
     * Starts low-power periodic location acquisition.
     * In Stationary mode: 15-minute interval with max ~5 second acquisition window.
     * In Active mode: Adaptive 3-5 minute interval.
     */
    fun startLowPowerPeriodicTracking() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                acquireLocationFixWithTimeout(maxTimeoutMs = 5000L)
                val intervalMs = when (currentMotionState) {
                    MotionState.STATIONARY -> 15 * 60 * 1000L // 15 minutes low-power
                    MotionState.WALKING -> 5 * 60 * 1000L     // 5 minutes
                    MotionState.RUNNING, MotionState.VEHICLE -> 2 * 60 * 1000L // 2 minutes
                }
                delay(intervalMs)
            }
        }
    }

    /**
     * Trigger immediate single location acquisition (max 5-second window).
     */
    fun requestImmediateLocation() {
        scope.launch {
            acquireLocationFixWithTimeout(maxTimeoutMs = 5000L)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun acquireLocationFixWithTimeout(maxTimeoutMs: Long) {
        if (locationManager == null) {
            _locationStatus.value = LocationStatus.UNAVAILABLE
            return
        }

        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!hasGps && !hasNetwork) {
            _locationStatus.value = LocationStatus.UNAVAILABLE
            return
        }

        withContext(Dispatchers.Main) {
            try {
                // Check cached last known location first for immediate availability
                var bestLastKnown: Location? = null
                if (hasGps) {
                    val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (loc != null && (bestLastKnown == null || loc.accuracy < bestLastKnown!!.accuracy)) {
                        bestLastKnown = loc
                    }
                }
                if (hasNetwork) {
                    val loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null && (bestLastKnown == null || loc.accuracy < bestLastKnown!!.accuracy)) {
                        bestLastKnown = loc
                    }
                }

                if (bestLastKnown != null && (System.currentTimeMillis() - bestLastKnown.time) < 10 * 60 * 1000L) {
                    handleNewLocation(bestLastKnown)
                }

                // Register one-shot listener for precise fresh fix
                if (!isListening) {
                    if (hasGps) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            0L,
                            0f,
                            locationListener,
                            Looper.getMainLooper()
                        )
                    }
                    if (hasNetwork) {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            0L,
                            0f,
                            locationListener,
                            Looper.getMainLooper()
                        )
                    }
                    isListening = true
                }
            } catch (e: SecurityException) {
                _locationStatus.value = LocationStatus.PERMISSION_REQUIRED
                stopListening()
                return@withContext
            } catch (e: Exception) {
                _locationStatus.value = LocationStatus.UNAVAILABLE
                stopListening()
                return@withContext
            }
        }

        // Bound acquisition window to max ~5 seconds to prevent battery drain
        acquisitionTimeoutJob?.cancel()
        acquisitionTimeoutJob = scope.launch {
            delay(maxTimeoutMs)
            withContext(Dispatchers.Main) {
                stopListening()
                if (_locationRecord.value == null) {
                    _locationStatus.value = LocationStatus.UNAVAILABLE
                }
            }
        }
    }

    private fun handleNewLocation(location: Location) {
        // Stop listening immediately to enforce low-power rule!
        stopListening()
        acquisitionTimeoutJob?.cancel()

        scope.launch(Dispatchers.IO) {
            val confidence = calculateConfidence(location)
            val geocoded = reverseGeocode(location.latitude, location.longitude)

            val record = LocationRecord(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                provider = location.provider ?: "system",
                timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                city = geocoded?.locality ?: geocoded?.subAdminArea ?: "Current Sector",
                district = geocoded?.subAdminArea ?: geocoded?.adminArea,
                state = geocoded?.adminArea,
                country = geocoded?.countryName ?: "India",
                locationConfidence = confidence,
                isVerified = true
            )

            _locationRecord.value = record
            _locationStatus.value = LocationStatus.VERIFIED
        }
    }

    private fun stopListening() {
        if (isListening && locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener)
            } catch (_: Exception) {}
            isListening = false
        }
    }

    private fun calculateConfidence(location: Location): Float {
        var conf = when {
            location.accuracy <= 15f -> 0.98f
            location.accuracy <= 50f -> 0.88f
            location.accuracy <= 100f -> 0.75f
            location.accuracy <= 500f -> 0.60f
            else -> 0.40f
        }
        if (location.provider == LocationManager.GPS_PROVIDER) {
            conf = (conf + 0.05f).coerceAtMost(1.0f)
        }
        return conf
    }

    private fun reverseGeocode(lat: Double, lon: Double): Address? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var result: Address? = null
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    result = addresses.firstOrNull()
                }
                result
            } else {
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(lat, lon, 1)
                list?.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun stopAll() {
        periodicJob?.cancel()
        acquisitionTimeoutJob?.cancel()
        stopListening()
    }
}
