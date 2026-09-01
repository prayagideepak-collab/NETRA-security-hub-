package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_audits")
data class SystemAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val totalServicesChecked: Int,
    val healthyServices: Int,
    val restartedServices: Int,
    val failedServices: Int,
    val unsupportedComponents: Int,
    val recoveryActionsPerformed: String,
    val overallSystemHealthScore: Int,
    val servicesDetailsJson: String // Detailed JSON representation of each audited service/component
)
