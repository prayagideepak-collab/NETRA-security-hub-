package com.aistudio.netrasensorhub.data.intelligence.sync

import android.content.Context
import com.aistudio.netrasensorhub.data.intelligence.engine.LocalImpactEngine
import com.aistudio.netrasensorhub.data.intelligence.location.DynamicLocationManager
import com.aistudio.netrasensorhub.data.intelligence.models.*
import com.aistudio.netrasensorhub.data.intelligence.network.AuthoritativeIntelligenceClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalIntelligenceSnapshot(
    val location: LocationRecord?,
    val locationStatus: LocationStatus,
    val motionState: MotionState,
    val weather: WeatherIntelligence?,
    val weatherStatus: WeatherStatus,
    val activeAlerts: List<DisasterAlert>,
    val seismicEvents: List<SeismicEvent>,
    val nearbyImpactAreas: List<NearbyArea>,
    val isRefreshing: Boolean,
    val lastSyncTimeMillis: Long
)

class IntelligenceSyncManager(private val context: Context) {

    private val locationManager = DynamicLocationManager(context)
    private val intelligenceClient = AuthoritativeIntelligenceClient()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _snapshot = MutableStateFlow(
        LocalIntelligenceSnapshot(
            location = null,
            locationStatus = LocationStatus.ACQUIRING,
            motionState = MotionState.STATIONARY,
            weather = null,
            weatherStatus = WeatherStatus.FETCHING,
            activeAlerts = emptyList(),
            seismicEvents = emptyList(),
            nearbyImpactAreas = emptyList(),
            isRefreshing = false,
            lastSyncTimeMillis = 0L
        )
    )
    val snapshot: StateFlow<LocalIntelligenceSnapshot> = _snapshot.asStateFlow()

    // Alert deduplication cache: EventId -> Severity
    private val processedAlerts = mutableMapOf<String, AlertSeverity>()
    private var lastLocationQueried: Pair<Double, Double>? = null

    init {
        // Collect location updates and sync intelligence automatically
        scope.launch {
            locationManager.locationRecord.collect { locRecord ->
                if (locRecord != null) {
                    val currentLoc = locRecord.latitude to locRecord.longitude
                    val shouldRefresh = lastLocationQueried == null ||
                            LocalImpactEngine.calculateDistanceKm(
                                lastLocationQueried!!.first, lastLocationQueried!!.second,
                                currentLoc.first, currentLoc.second
                            ) > 5.0

                    _snapshot.value = _snapshot.value.copy(
                        location = locRecord,
                        locationStatus = LocationStatus.VERIFIED,
                        nearbyImpactAreas = LocalImpactEngine.computeDynamicNearbyAreas(
                            locRecord.latitude,
                            locRecord.longitude,
                            locRecord.city
                        )
                    )

                    if (shouldRefresh) {
                        lastLocationQueried = currentLoc
                        syncIntelligenceData(locRecord)
                    }
                }
            }
        }

        scope.launch {
            locationManager.locationStatus.collect { status ->
                _snapshot.value = _snapshot.value.copy(locationStatus = status)
            }
        }
    }

    fun start() {
        locationManager.startLowPowerPeriodicTracking()
    }

    fun stop() {
        locationManager.stopAll()
    }

    fun updateMotionState(motion: MotionState) {
        _snapshot.value = _snapshot.value.copy(motionState = motion)
        locationManager.updateMotionState(motion)
    }

    fun triggerManualRefresh() {
        locationManager.requestImmediateLocation()
        val loc = _snapshot.value.location
        if (loc != null) {
            syncIntelligenceData(loc)
        }
    }

    fun syncIntelligenceData(location: LocationRecord) {
        scope.launch {
            _snapshot.value = _snapshot.value.copy(isRefreshing = true)

            // 1. Fetch real-time weather
            val weatherResult = intelligenceClient.fetchRealtimeWeather(location.latitude, location.longitude)
            val weather = weatherResult.getOrNull()
            val weatherStatus = if (weather != null) WeatherStatus.LIVE else WeatherStatus.NETWORK_ERROR

            // 2. Fetch authoritative seismic events
            val seismicResult = intelligenceClient.fetchAuthoritativeSeismicEvents(location.latitude, location.longitude)
            val seismicEvents = seismicResult.getOrDefault(emptyList())

            // 3. Evaluate weather hazard alerts
            val weatherAlerts = if (weather != null) {
                LocalImpactEngine.evaluateWeatherHazards(
                    currentAreaName = location.city ?: "Local Sector",
                    currentLat = location.latitude,
                    currentLon = location.longitude,
                    weather = weather
                )
            } else {
                emptyList()
            }

            // 4. Evaluate seismic hazard alerts (only if local impact is WATCH, WARNING, or CRITICAL)
            val seismicAlerts = seismicEvents
                .filter { it.potentialLocalImpact != AlertSeverity.INFO }
                .map { event ->
                    DisasterAlert(
                        eventId = event.eventId,
                        title = "Seismic Activity Advisory (M%.1f)".format(event.magnitude),
                        category = DisasterCategory.SEISMIC,
                        severity = event.potentialLocalImpact,
                        locationName = event.place,
                        latitude = event.latitude,
                        longitude = event.longitude,
                        distanceKmFromCurrent = event.distanceKmFromCurrent,
                        impactRadiusKm = (10.0 * event.magnitude),
                        localImpactLevel = event.potentialLocalImpact,
                        description = "Earthquake of magnitude %.1f detected at depth %.1f km (%s, %.0f km away). %s".format(
                            event.magnitude,
                            event.depthKm,
                            event.place,
                            event.distanceKmFromCurrent,
                            if (event.potentialLocalImpact == AlertSeverity.CRITICAL) "Strong ground shaking hazard! Drop, Cover, and Hold on."
                            else "Mild/moderate tremors possible."
                        ),
                        officialSource = event.officialConfirmation,
                        confidence = 0.95f,
                        timestamp = event.originTimeMillis,
                        isUnconfirmed = false
                    )
                }

            // Deduplicate and combine
            val allNewAlerts = weatherAlerts + seismicAlerts
            val deduplicatedAlerts = mutableListOf<DisasterAlert>()

            for (alert in allNewAlerts) {
                val previousSeverity = processedAlerts[alert.eventId]
                if (previousSeverity == null || previousSeverity != alert.severity) {
                    processedAlerts[alert.eventId] = alert.severity
                    deduplicatedAlerts.add(alert)
                } else {
                    deduplicatedAlerts.add(alert) // Retain in active view
                }
            }

            _snapshot.value = _snapshot.value.copy(
                weather = weather ?: _snapshot.value.weather,
                weatherStatus = if (weather != null) WeatherStatus.LIVE else if (_snapshot.value.weather != null) WeatherStatus.CACHED else WeatherStatus.NETWORK_ERROR,
                activeAlerts = deduplicatedAlerts,
                seismicEvents = seismicEvents,
                isRefreshing = false,
                lastSyncTimeMillis = System.currentTimeMillis()
            )
        }
    }
}
