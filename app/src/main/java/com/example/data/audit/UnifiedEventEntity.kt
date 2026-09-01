package com.example.data.audit

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unified_event_history")
data class UnifiedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // Safety, Driving, Weather, Service, etc.
    val severity: String, // Information, Warning, High Risk, Critical
    val eventName: String,
    val sourceModule: String,
    val description: String,
    val status: String,
    val resolutionStatus: String? = null, // Optional
    val metadataJson: String? = null, // For structured safety evidence trail
    val occurrences: Int = 1,
    val totalDurationMs: Long = 0
)
