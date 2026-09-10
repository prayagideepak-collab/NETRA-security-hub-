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
    val verificationStatus: String = "UNVERIFIED", // VERIFIED, UNVERIFIED
    val locationRelevance: String = "UNKNOWN_RELEVANCE", // VERIFIED_MATCH, NO_MATCH, UNKNOWN_RELEVANCE
    val lifecycleState: String = "INFORMATIONAL", // ACTIVE, UPDATED, EXPIRED, CANCELLED, INFORMATIONAL
    val checkStatusState: String = "CHECK_SUCCESS_NO_ACTIVE_WARNING", // LOCATION_UNAVAILABLE, LOCATION_STALE, SOURCE_UNAVAILABLE, CHECK_FAILED, CHECK_SUCCESS_NO_ACTIVE_WARNING, VERIFIED_ACTIVE_WARNING, UNVERIFIED_INFORMATION
    val affectedArea: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = false,
    val source: String = "WeatherAPI (Third-Party)"
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
        _checkStatus.value = "Checking Warning Sources..."
        val loc = locationManager.refreshLocation()
        _lastCheckTimestamp.value = System.currentTimeMillis()

        if (loc.locality == "Location Unavailable" || loc.latitude == 0.0) {
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
                    warningType = "Source Unavailable",
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

                // STRICT ELIGIBILITY GATE:
                // Only VERIFIED and locationRelevance == VERIFIED_MATCH can trigger emergency pipeline
                val isEligible = (updatedWarning.verificationStatus == "VERIFIED" || updatedWarning.sourceType == "OFFICIAL_VERIFIED") &&
                                 (updatedWarning.locationRelevance == "VERIFIED_MATCH") &&
                                 updatedWarning.lifecycleState == "ACTIVE"

                if (isEligible) {
                    processVerifiedMatchedWarning(updatedWarning, loc)
                    val finalState = updatedWarning.copy(checkStatusState = "VERIFIED_ACTIVE_WARNING")
                    _activeWarning.value = finalState
                    _checkStatus.value = "Active Verified Warning Detected"
                    return@withContext finalState
                } else {
                    // Unverified or third-party data: stored only as informational, never official emergency
                    val infoState = updatedWarning.copy(checkStatusState = "UNVERIFIED_INFORMATION")
                    _activeWarning.value = infoState
                    _checkStatus.value = "Unverified advisory (Not official warning)"
                    return@withContext infoState
                }
            }

            val clearState = OfficialWarningInfo(
                isAvailable = false,
                warningType = "No Active Warning",
                headline = "No active verified warning for ${loc.locality}",
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
                        val authority = if (sender.isNotEmpty()) sender else "Source unavailable"
                        
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
                            sourceType = "THIRD_PARTY", // Explicitly third-party
                            verificationStatus = "UNVERIFIED", // Unverified
                            locationRelevance = "UNKNOWN_RELEVANCE",
                            lifecycleState = "INFORMATIONAL",
                            checkStatusState = "UNVERIFIED_INFORMATION",
                            affectedArea = alert.optString("areaDesc", ""),
                            timestamp = System.currentTimeMillis(),
                            isAvailable = true,
                            source = "WeatherAPI (Third-Party)"
                        )
                    }
                }
                // Successfully checked, no alerts
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
            else -> "UNKNOWN" // Never default to INFO if unknown
        }
    }

    private fun determineLocationRelevance(warning: OfficialWarningInfo, loc: LocationContextInfo): String {
        val affected = warning.affectedArea.lowercase().trim()
        val locality = loc.locality.lowercase().trim()
        val district = loc.district.lowercase().trim()
        val state = loc.state.lowercase().trim()

        // STRICT RULE: affected.isEmpty() NEVER equals location match!
        if (affected.isEmpty()) {
            return "UNKNOWN_RELEVANCE"
        }

        val matchesCity = locality.isNotEmpty() && locality != "location unavailable" && affected.contains(locality)
        val matchesDistrict = district.isNotEmpty() && district != "location unavailable" && affected.contains(district)
        val matchesState = state.isNotEmpty() && state != "location unavailable" && affected.contains(state) &&
            (affected.contains("state") || affected.contains("all") || affected.length < 100)

        return if (matchesCity || matchesDistrict || matchesState) {
            "VERIFIED_MATCH"
        } else {
            "NO_MATCH"
        }
    }

    private suspend fun processVerifiedMatchedWarning(warning: OfficialWarningInfo, loc: LocationContextInfo) {
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
                isVerifiedHardwareEvent = false, // Never mark unverified/third-party as hardware event
                moduleName = "OfficialEmergencyWarningManager",
                severity = warning.severity,
                gpsLocation = "${loc.locality}, ${loc.state} (${loc.source})"
            )
            dao.insertEvent(entity)

            notificationManager.sendEmergencyAlert(
                title = warning.warningType,
                message = "${warning.headline}\nValid: ${warning.startTime} – ${warning.endTime}\nSource: ${warning.issuingAuthority}",
                riskLevel = when (warning.severity) {
                    "CRITICAL" -> com.example.data.model.SafetyRiskLevel.EMERGENCY
                    "WARNING" -> com.example.data.model.SafetyRiskLevel.WARNING
                    else -> com.example.data.model.SafetyRiskLevel.ATTENTION
                }
            )

            val authText = if (warning.issuingAuthority != "Source unavailable") warning.issuingAuthority else "Source unavailable"
            val announcementText = "Safety alert. ${warning.warningType}. Severity ${warning.severity}. Valid from ${warning.startTime} to ${warning.endTime}. Issued by $authText."
            ttsManager.speakAlert(announcementText, isCriticalSafety = warning.severity == "CRITICAL" || warning.severity == "WARNING")

            LoggingManager.critical("EmergencyWarning", "NEW_VERIFIED_ALERT", "New verified official alert notified: ${warning.warningType}", "Severity: ${warning.severity}")
        }
    }
}
