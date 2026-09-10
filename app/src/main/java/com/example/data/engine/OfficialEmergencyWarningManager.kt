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
    val severity: String = "INFO", // INFO, WATCH, WARNING, CRITICAL
    val headline: String = "",
    val description: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val issuingAuthority: String = "Source unavailable",
    val sourceType: String = "THIRD_PARTY", // OFFICIAL_VERIFIED, THIRD_PARTY, UNVERIFIED
    val verificationStatus: String = "UNVERIFIED", // VERIFIED, UNVERIFIED
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
        _checkStatus.value = "Checking Official Sources..."
        val loc = locationManager.refreshLocation()
        _lastCheckTimestamp.value = System.currentTimeMillis()

        try {
            val warning = fetchAuthoritativeWarning(loc)
            if (warning != null && warning.isAvailable) {
                val matchesLocation = isLocationMatched(warning, loc)
                if (matchesLocation) {
                    processMatchedWarning(warning, loc)
                    _activeWarning.value = warning
                    _checkStatus.value = "Active Warning Detected"
                    return@withContext warning
                }
            }

            _activeWarning.value = OfficialWarningInfo(isAvailable = false, warningType = "No Active Warning", headline = "No official warning for ${loc.locality}")
            _checkStatus.value = "Checked - All Clear"
            return@withContext _activeWarning.value
        } catch (e: Exception) {
            LoggingManager.critical("EmergencyWarning", "ALERT_CHECK_FAILURE", "Failed to check official warnings: ${e.message}", "Preserving status.")
            _checkStatus.value = "Latest official alert check unavailable"
            return@withContext _activeWarning.value
        }
    }

    private suspend fun fetchAuthoritativeWarning(loc: LocationContextInfo): OfficialWarningInfo? {
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
                        
                        val event = alert.optString("event", "Severe Weather Advisory")
                        val severityRaw = alert.optString("severity", "INFO")
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
                            verificationStatus = "UNVERIFIED",
                            affectedArea = alert.optString("areaDesc", ""),
                            timestamp = System.currentTimeMillis(),
                            isAvailable = true,
                            source = "WeatherAPI (Third-Party)"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.info("EmergencyWarning", "NETWORK_OFFLINE", "Official alert network check skipped: ${e.message}", "Offline mode.")
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
            else -> "INFO"
        }
    }

    private fun isLocationMatched(warning: OfficialWarningInfo, loc: LocationContextInfo): Boolean {
        val affected = warning.affectedArea.lowercase().trim()
        val locality = loc.locality.lowercase().trim()
        val district = loc.district.lowercase().trim()
        val state = loc.state.lowercase().trim()

        // STRICT RULE: affectedArea.isEmpty() != location matched!
        if (affected.isEmpty()) {
            return false
        }

        val matchesCity = locality.isNotEmpty() && locality != "location unavailable" && affected.contains(locality)
        val matchesDistrict = district.isNotEmpty() && district != "location unavailable" && affected.contains(district)
        val matchesState = state.isNotEmpty() && state != "location unavailable" && affected.contains(state) &&
            (affected.contains("state") || affected.contains("all") || affected.length < 100)

        return matchesCity || matchesDistrict || matchesState
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
                riskLevel = warning.severity,
                riskScore = if (warning.severity == "CRITICAL") 90 else if (warning.severity == "WARNING") 75 else 50,
                eventType = warning.warningType,
                title = warning.warningType,
                description = warning.headline,
                aiRecommendation = "Source: ${warning.source} (${warning.issuingAuthority}). Valid: ${warning.startTime} to ${warning.endTime}.",
                isVerifiedHardwareEvent = true,
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

            LoggingManager.critical("EmergencyWarning", "NEW_OFFICIAL_ALERT", "New official alert notified: ${warning.warningType}", "Severity: ${warning.severity}")
        }
    }
}
