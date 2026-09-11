package com.example.data.engine

import android.content.Context
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import com.example.data.service.OfficialLocationContextManager
import com.example.data.service.LocationContextInfo
import com.example.util.LoggingManager
import com.example.util.NetraNotificationManager
import com.example.util.NetraTtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OfficialWarningInfo(
    val alertId: String = "",
    val warningType: String = "No Active Warning",
    val severity: String = "UNKNOWN", // INFO, WATCH, WARNING, CRITICAL, UNKNOWN
    val headline: String = "",
    val description: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val issuingAuthority: String = "Source unavailable",
    val sourceType: String = "THIRD_PARTY", // OFFICIAL_VERIFIED, THIRD_PARTY, UNVERIFIED
    val verificationStatus: String = "VERIFIED_THIRD_PARTY", // OFFICIAL_VERIFIED, VERIFIED_THIRD_PARTY, UNVERIFIED
    val locationRelevance: String = "UNKNOWN_RELEVANCE", // VERIFIED_MATCH, NO_MATCH, UNKNOWN_RELEVANCE
    val lifecycleState: String = "INFORMATIONAL", // ACTIVE, UPDATED, EXPIRED, CANCELLED, INFORMATIONAL
    val checkStatusState: String = "CHECK_SUCCESS_NO_ACTIVE_WARNING", // LOCATION_UNAVAILABLE, LOCATION_STALE, SOURCE_UNAVAILABLE, CHECK_FAILED, CHECK_SUCCESS_NO_ACTIVE_WARNING, VERIFIED_ACTIVE_WARNING, UNVERIFIED_INFORMATION
    val affectedArea: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = false,
    val source: String = "Weather Provider (Third-Party)"
)

class OfficialEmergencyWarningManager(private val context: Context) {
    private val db = NetraDatabase.getInstance(context)
    private val locationManager = OfficialLocationContextManager(context)
    private val notificationManager = NetraNotificationManager(context)
    private val ttsManager = NetraTtsManager(context)

    private val _activeWarning = MutableStateFlow(OfficialWarningInfo())
    val activeWarning: StateFlow<OfficialWarningInfo> = _activeWarning.asStateFlow()

    private val _lastCheckTimestamp = MutableStateFlow(0L)
    val lastCheckTimestamp: StateFlow<Long> = _lastCheckTimestamp.asStateFlow()

    private val _checkStatus = MutableStateFlow("Idle")
    val checkStatus: StateFlow<String> = _checkStatus.asStateFlow()

    suspend fun checkOfficialWarnings(): OfficialWarningInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 30-minute TTL cache check for ultra-low battery / network conservation
        if (now - _lastCheckTimestamp.value < 30 * 60 * 1000L && _activeWarning.value.checkStatusState.isNotEmpty() && _activeWarning.value.checkStatusState != "Idle") {
            return@withContext _activeWarning.value
        }

        _checkStatus.value = "Checking Warning Sources..."
        val loc = locationManager.refreshLocation()
        _lastCheckTimestamp.value = now

        val hasValidCoords = loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0 &&
                             loc.locationStatus != "LOCATION_UNAVAILABLE" &&
                             loc.locationStatus != "PERMISSION_NOT_GRANTED" &&
                             loc.locationStatus != "PROVIDER_DISABLED"

        if (!hasValidCoords) {
            val stateInfo = OfficialWarningInfo(
                warningType = "Location Unavailable",
                headline = "Location unavailable for warning check",
                checkStatusState = "LOCATION_UNAVAILABLE",
                source = "System Location"
            )
            _activeWarning.value = stateInfo
            _checkStatus.value = "Location unavailable"
            return@withContext stateInfo
        }

        try {
            val warning = fetchWarning(loc)
            if (warning == null) {
                val stateInfo = OfficialWarningInfo(
                    warningType = "Warning Source Unavailable",
                    headline = "Warning source unavailable",
                    checkStatusState = "SOURCE_UNAVAILABLE",
                    issuingAuthority = "Source unavailable"
                )
                _activeWarning.value = stateInfo
                _checkStatus.value = "Warning source unavailable"
                return@withContext stateInfo
            }

            if (warning.isAvailable) {
                val relevance = determineLocationRelevance(warning, loc)
                val updatedWarning = warning.copy(locationRelevance = relevance)

                // UNIFIED ELIGIBILITY: Official + Reliable Third-Party data collected & displayed if location matches
                val isEligible = updatedWarning.isAvailable &&
                                 updatedWarning.locationRelevance == "VERIFIED_MATCH" &&
                                 updatedWarning.lifecycleState == "ACTIVE"

                if (isEligible) {
                    processMatchedWarning(updatedWarning, loc)
                    val finalState = updatedWarning.copy(checkStatusState = "VERIFIED_ACTIVE_WARNING")
                    _activeWarning.value = finalState
                    _checkStatus.value = "Active Warning Detected (${updatedWarning.source})"
                    return@withContext finalState
                } else {
                    val infoState = updatedWarning.copy(checkStatusState = "UNVERIFIED_INFORMATION")
                    _activeWarning.value = infoState
                    _checkStatus.value = "Advisory recorded (No local match)"
                    return@withContext infoState
                }
            }

            val clearState = OfficialWarningInfo(
                isAvailable = false,
                warningType = "No Active Warning",
                headline = "No active warning for ${loc.locality}",
                checkStatusState = "CHECK_SUCCESS_NO_ACTIVE_WARNING"
            )
            _activeWarning.value = clearState
            _checkStatus.value = "Checked - No active warnings"
            return@withContext clearState
        } catch (e: Exception) {
            LoggingManager.critical("EmergencyWarning", "CHECK_FAILED", "Warning check failed: ${e.message}", "Preserving status.")
            val failState = OfficialWarningInfo(
                warningType = "Check Failed",
                headline = "Latest warning check failed",
                checkStatusState = "CHECK_FAILED"
            )
            _activeWarning.value = failState
            _checkStatus.value = "Latest warning check failed"
            return@withContext failState
        }
    }

    private suspend fun fetchWarning(loc: LocationContextInfo): OfficialWarningInfo? {
        try {
            val url = URL("https://api.weatherapi.com/v1/current.json?key=public&q=${loc.latitude},${loc.longitude}&aqi=no")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                if (root.has("alerts")) {
                    val alertsObj = root.getJSONObject("alerts")
                    val alertArray = alertsObj.getJSONArray("alert")
                    if (alertArray.length() > 0) {
                        val alert = alertArray.getJSONObject(0)
                        val sender = alert.optString("senderName", "").trim()
                        val authority = if (sender.isNotEmpty()) sender else "Reliable Weather Provider"
                        
                        val event = alert.optString("event", "Weather Advisory")
                        val severityRaw = alert.optString("severity", "UNKNOWN")
                        val mappedSeverity = mapSeverity(severityRaw, event)

                        return OfficialWarningInfo(
                            alertId = alert.optString("id", "WARN-${System.currentTimeMillis()}"),
                            warningType = event,
                            severity = mappedSeverity,
                            headline = alert.optString("headline", "Weather advisory issued"),
                            description = alert.optString("desc", ""),
                            startTime = alert.optString("effective", ""),
                            endTime = alert.optString("expires", ""),
                            issuingAuthority = authority,
                            sourceType = "THIRD_PARTY",
                            verificationStatus = "VERIFIED_THIRD_PARTY",
                            locationRelevance = "UNKNOWN_RELEVANCE",
                            lifecycleState = "ACTIVE",
                            checkStatusState = "VERIFIED_ACTIVE_WARNING",
                            affectedArea = alert.optString("areaDesc", ""),
                            timestamp = System.currentTimeMillis(),
                            isAvailable = true,
                            source = "Weather Provider (Third-Party)"
                        )
                    }
                }
                return OfficialWarningInfo(isAvailable = false)
            }
        } catch (e: Exception) {
            LoggingManager.info("EmergencyWarning", "NETWORK_OFFLINE", "Warning network check failed: ${e.message}", "Source unavailable.")
        }
        return null
    }

    private fun mapSeverity(severityStr: String, eventStr: String): String {
        val s = severityStr.uppercase()
        val e = eventStr.uppercase()
        return when {
            s.contains("CRITICAL") || s.contains("SEVERE") || e.contains("EXTREME") || e.contains("RED") -> "CRITICAL"
            s.contains("WARNING") || s.contains("ORANGE") || e.contains("WARNING") -> "WARNING"
            s.contains("WATCH") || s.contains("YELLOW") || e.contains("WATCH") -> "WATCH"
            s.contains("INFO") || s.contains("ADVISORY") || e.contains("ADVISORY") -> "INFO"
            else -> "UNKNOWN"
        }
    }

    private fun determineLocationRelevance(warning: OfficialWarningInfo, loc: LocationContextInfo): String {
        val affected = warning.affectedArea.lowercase().trim()
        val locality = loc.locality.lowercase().trim()
        val district = loc.district.lowercase().trim()
        val state = loc.state.lowercase().trim()

        if (affected.isEmpty()) {
            return "UNKNOWN_RELEVANCE"
        }

        val matchesCity = locality.isNotEmpty() && locality != "location unavailable" && affected.contains(locality)
        val matchesDistrict = district.isNotEmpty() && district != "location unavailable" && affected.contains(district)
        val matchesState = state.isNotEmpty() && state != "location unavailable" && affected.contains(state) &&
            (affected.contains("state") || affected.contains("all") || affected.length < 100)
        val matchesCoordsFallback = loc.locationStatus == "COORDINATES_AVAILABLE" && (affected.contains("country") || affected.contains("national") || affected.contains("region") || affected.isEmpty() || affected.contains("all"))

        return if (matchesCity || matchesDistrict || matchesState || matchesCoordsFallback) {
            "VERIFIED_MATCH"
        } else {
            "NO_MATCH"
        }
    }

    private suspend fun processMatchedWarning(warning: OfficialWarningInfo, loc: LocationContextInfo) {
        val dao = db.safetyEventDao()
        val existing = dao.getEventByEventId(warning.alertId)

        if (existing == null) {
            val entity = SafetyEventEntity(
                eventId = warning.alertId,
                domain = "OFFICIAL_WARNING",
                lifecycleState = "ACTIVE",
                timestamp = warning.timestamp,
                riskLevel = when (warning.severity) {
                    "CRITICAL" -> "EMERGENCY"
                    "WARNING" -> "WARNING"
                    else -> "ATTENTION"
                },
                riskScore = if (warning.severity == "CRITICAL") 90 else if (warning.severity == "WARNING") 75 else 50,
                eventType = warning.warningType,
                title = warning.warningType,
                description = warning.headline,
                aiRecommendation = "Source: ${warning.source} (${warning.issuingAuthority}). Valid: ${warning.startTime} to ${warning.endTime}.",
                isVerifiedHardwareEvent = false,
                moduleName = "OfficialEmergencyWarningManager",
                severity = warning.severity,
                gpsLocation = "${loc.locality}, ${loc.state} (${warning.source})"
            )
            dao.insertEvent(entity)

            notificationManager.sendEmergencyAlert(
                title = "${warning.warningType} (${warning.source})",
                message = "${warning.headline}\nValid: ${warning.startTime} – ${warning.endTime}\nSource: ${warning.issuingAuthority}",
                riskLevel = when (warning.severity) {
                    "CRITICAL" -> com.example.data.model.SafetyRiskLevel.EMERGENCY
                    "WARNING" -> com.example.data.model.SafetyRiskLevel.WARNING
                    else -> com.example.data.model.SafetyRiskLevel.ATTENTION
                }
            )

            val announcementText = "आपके क्षेत्र के लिए एक विश्वसनीय weather service (${warning.source}) ने ${warning.warningType} की चेतावनी रिपोर्ट की है। कृपया application में विवरण देखें।"
            ttsManager.speakAlert(announcementText, isCriticalSafety = warning.severity == "CRITICAL" || warning.severity == "WARNING")

            LoggingManager.critical("EmergencyWarning", "NEW_WARNING_DETECTED", "New warning detected: ${warning.warningType}", "Source: ${warning.source}, Severity: ${warning.severity}")
        }
    }
}
