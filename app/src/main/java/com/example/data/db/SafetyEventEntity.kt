package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "safety_events", indices = [Index(value = ["timestamp"])])
data class SafetyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val riskLevel: String, // SAFE, ATTENTION, WARNING, EMERGENCY, INFO, RECOVERY
    val riskScore: Int,
    val eventType: String,
    val title: String,
    val description: String,
    val primarySensorValuesJson: String,
    val aiRecommendation: String,
    val isVerifiedHardwareEvent: Boolean = true,
    val moduleName: String = "System",
    val severity: String = "INFORMATION", // INFORMATION, WARNING, IMPORTANT, CRITICAL, RECOVERY
    val aiConfidence: Float = 0.95f,
    val batteryPercent: Int = 0,
    val deviceTempC: Float = 0.0f,
    val processingDurationMs: Long = 15L,
    val recoveryDurationMs: Long = 0L,
    val gpsLocation: String = "Unavailable",
    val announcementStatus: String = "N/A"
)

