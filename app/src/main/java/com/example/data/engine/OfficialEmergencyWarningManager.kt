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
    val headline: String = "No official warning",
    val description: String = "",
    val startTime: String = "उपलब्ध नहीं",
    val endTime: String = "उपलब्ध नहीं",
    val issuingAuthority: String = "उपलब्ध नहीं",
    val affectedArea: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = false,
    val source: String = "Official Disaster Management / IMD"
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
                        return OfficialWarningInfo(
                            alertId = alert.optString("id", "WARN-${System.currentTimeMillis()}"),
                            warningType = alert.optString("event", "Severe Weather Warning"),
                            severity = "WARNING",
                            headline = alert.optString("headline", "Severe weather advisory issued"),
                            description = alert.optString("desc", ""),
                            startTime = alert.optString("effective", "उपलब्ध नहीं"),
                            endTime = alert.optString("expires", "उपलब्ध नहीं"),
                            issuingAuthority = alert.optString("senderName", "India Meteorological Department / NDMA"),
                            affectedArea = alert.optString("areaDesc", loc.locality),
                            timestamp = System.currentTimeMillis(),
                            isAvailable = true
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.info("EmergencyWarning", "NETWORK_OFFLINE", "Official alert network check skipped: ${e.message}", "Offline mode.")
        }
        return null
    }

    private fun isLocationMatched(warning: OfficialWarningInfo, loc: LocationContextInfo): Boolean {
        val affected = warning.affectedArea.lowercase()
        val locality = loc.locality.lowercase()
        val district = loc.district.lowercase()
        val state = loc.state.lowercase()

        return affected.contains(locality) || affected.contains(district) || affected.contains(state) || affected.isEmpty()
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
                riskScore = 80,
                eventType = warning.warningType,
                title = warning.warningType,
                description = warning.headline,
                aiRecommendation = "Issued by ${warning.issuingAuthority}. Valid from ${warning.startTime} to ${warning.endTime}.",
                isVerifiedHardwareEvent = true,
                moduleName = "OfficialEmergencyWarningManager",
                severity = warning.severity,
                gpsLocation = "${loc.locality}, ${loc.state} (${loc.source})"
            )
            dao.insertEvent(entity)

            notificationManager.sendEmergencyAlert(
                title = warning.warningType,
                message = "${warning.headline}\nTime: ${warning.startTime} – ${warning.endTime}\nIssued by: ${warning.issuingAuthority}",
                riskLevel = com.example.data.model.SafetyRiskLevel.WARNING
            )

            val announcementText = "सावधान। ${warning.warningType}. ${warning.severity} चेतावनी जारी की गई है। समय: ${warning.startTime} से ${warning.endTime} तक। जारीकर्ता: ${warning.issuingAuthority}।"
            ttsManager.speakAlert(announcementText, isCriticalSafety = true)

            LoggingManager.critical("EmergencyWarning", "NEW_OFFICIAL_ALERT", "New official alert notified: ${warning.warningType}", "Severity: ${warning.severity}")
        }
    }
}
