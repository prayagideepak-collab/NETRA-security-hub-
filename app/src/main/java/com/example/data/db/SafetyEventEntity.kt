package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "safety_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["eventId"]),
        Index(value = ["lifecycleState"])
    ]
)
data class SafetyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String = "",                         // e.g. THERMAL-20260901-001
    val domain: String = "SYSTEM",                    // THERMAL, MAGNETIC, MOTION, IMPACT, CHARGING, SYSTEM
    val lifecycleState: String = "ACTIVE",            // DETECTED, CONFIRMED, ACTIVE, ESCALATED, RECOVERING, RESOLVED
    val timestamp: Long = System.currentTimeMillis(),
    val startTime: Long = timestamp,
    val lastUpdateTime: Long = timestamp,
    val endTime: Long? = null,
    val riskLevel: String = "SAFE",                  // SAFE, ATTENTION, WARNING, CRITICAL, EMERGENCY, INFO
    val riskScore: Int = 0,
    val eventType: String = "",
    val title: String = "",
    val description: String = "",
    val peakValue: String? = null,
    val currentValue: String? = null,
    val thresholdValue: String? = null,
    val primarySensorValuesJson: String = "{}",
    val aiRecommendation: String = "",
    val isVerifiedHardwareEvent: Boolean = true,
    val moduleName: String = "SafetyEngine",
    val severity: String = "INFORMATION",            // NORMAL, ATTENTION, WARNING, CRITICAL
    val aiConfidence: Float = 0.95f,
    val evidence: String = "",
    val resolution: String? = null,
    val batteryPercent: Int = 0,
    val deviceTempC: Float = 0.0f,
    val processingDurationMs: Long = 15L,
    val recoveryDurationMs: Long = 0L,
    val gpsLocation: String = "Unavailable",
    val announcementStatus: String = "N/A"
)
