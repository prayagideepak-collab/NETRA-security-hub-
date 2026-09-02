package com.example.data.pipeline

import android.content.Context
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import com.example.data.repository.NetraSafetyRepository
import com.example.util.LoggingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the single, authoritative data synchronization pipeline.
 * Coordinates App Startup Sync, Periodic Background Sync, incremental ingestion,
 * and deterministic deduplication into the central Room database and engines.
 */
class DeviceDataSyncManager(
    private val context: Context,
    private val repository: NetraSafetyRepository? = null
) {
    private val db = NetraDatabase.getInstance(context)
    private val adapter = DeviceDataAdapter(context)
    private val batteryAnomalyDetector = BatteryAnomalyDetector()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    private val _syncStatus = MutableStateFlow("IDLE")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    companion object {
        private const val PREFS_NAME = "netra_sync_prefs"
        private const val KEY_LAST_SYNC_MS = "last_sync_timestamp_ms"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val saved = prefs.getLong(KEY_LAST_SYNC_MS, 0L)
        if (saved > 0L) {
            _lastSyncTimestamp.value = saved
        }
    }

    /**
     * Executes the complete App Startup Synchronization routine.
     */
    suspend fun performStartupSync(): Result<Boolean> = withContext(Dispatchers.IO) {
        _syncStatus.value = "SYNCING_STARTUP"
        try {
            val now = System.currentTimeMillis()
            val lastSync = _lastSyncTimestamp.value ?: 0L

            LoggingManager.info(
                module = "DataPipeline",
                event = "STARTUP_SYNC_INITIATED",
                title = "Device Data Sync Initiated",
                description = "Startup sync cycle started. Last sync: ${if (lastSync > 0) SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(lastSync)) else "First Launch"}."
            )

            // 1. Sync Battery & Power State
            val batteryMetric = adapter.readBatterySnapshot()
            if (batteryMetric.isAvailable && batteryMetric.value != null) {
                val b = batteryMetric.value
                // Check anomaly
                val anomaly = batteryAnomalyDetector.evaluateReading(b.levelPercent, b.isCharging, b.plugType, b.timestamp)
                if (anomaly != null) {
                    val eventEntity = SafetyEventEntity(
                        eventId = anomaly.eventId,
                        domain = "CHARGING",
                        lifecycleState = "DETECTED",
                        timestamp = anomaly.timestamp,
                        riskLevel = "WARNING",
                        riskScore = 35,
                        eventType = "BATTERY_REPORTING_ANOMALY",
                        title = "Battery Telemetry Anomaly",
                        description = anomaly.description,
                        primarySensorValuesJson = "{\"delta\":\"${anomaly.deltaPercentage}%\",\"elapsedSec\":\"${anomaly.elapsedSeconds}\"}",
                        aiRecommendation = "Silent battery reporting telemetry. System will monitor for recurrence.",
                        isVerifiedHardwareEvent = true,
                        moduleName = "BatteryAnomalyDetector"
                    )
                    db.safetyEventDao().insertEvent(eventEntity)
                }

                // Check milestones
                val milestone = batteryAnomalyDetector.checkMilestone(b.levelPercent, b.isCharging, b.timestamp)
                if (milestone != null) {
                    // Log silent milestone telemetry record (no voice announcement)
                    val milestoneKey = "MILESTONE_${b.levelPercent}_${if (b.isCharging) "CHG" else "DIS"}"
                    val existing = db.safetyEventDao().getEventByEventId(milestoneKey)
                    if (existing == null) {
                        db.safetyEventDao().insertEvent(
                            SafetyEventEntity(
                                eventId = milestoneKey,
                                domain = "CHARGING",
                                lifecycleState = "RESOLVED",
                                timestamp = milestone.timestamp,
                                riskLevel = "SAFE",
                                riskScore = 0,
                                eventType = "BATTERY_MILESTONE_REACHED",
                                title = "Battery Milestone ${b.levelPercent}%",
                                description = "Battery reached ${b.levelPercent}% milestone during ${if (b.isCharging) "charging" else "discharge"}.",
                                primarySensorValuesJson = "{\"milestone\":\"${b.levelPercent}%\",\"state\":\"${if (b.isCharging) "CHARGING" else "DISCHARGING"}\"}",
                                aiRecommendation = "Milestone logged silently.",
                                isVerifiedHardwareEvent = true,
                                moduleName = "BatteryAnomalyDetector"
                            )
                        )
                    }
                }
            }

            // 2. Perform 7-day retention pruning across Safety stores
            val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
            db.safetyEventDao().pruneOldResolvedEvents(sevenDaysAgo)

            // 3. Mark last sync timestamp
            _lastSyncTimestamp.value = now
            prefs.edit().putLong(KEY_LAST_SYNC_MS, now).apply()

            _syncStatus.value = "SYNC_SUCCESS"
            LoggingManager.info(
                module = "DataPipeline",
                event = "STARTUP_SYNC_COMPLETED",
                title = "Device Data Sync Completed",
                description = "Startup data sync cycle completed successfully. Central stores updated."
            )
            Result.success(true)
        } catch (e: Exception) {
            _syncStatus.value = "SYNC_FAILED"
            LoggingManager.warning(
                module = "DataPipeline",
                event = "STARTUP_SYNC_FAILED",
                title = "Data Sync Failed",
                description = "Sync cycle encountered error: ${e.message ?: "Unknown error"}"
            )
            Result.failure(e)
        }
    }

    /**
     * Periodic incremental sync routine for WorkManager execution.
     */
    suspend fun performPeriodicSync(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            // 7-day retention cleanup
            val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
            db.safetyEventDao().pruneOldResolvedEvents(sevenDaysAgo)

            _lastSyncTimestamp.value = now
            prefs.edit().putLong(KEY_LAST_SYNC_MS, now).apply()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
