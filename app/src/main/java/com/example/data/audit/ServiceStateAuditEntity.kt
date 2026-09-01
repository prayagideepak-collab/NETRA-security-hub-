package com.example.data.audit

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_state_audit")
data class ServiceStateAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceName: String,
    val previousState: String, // Enabled / Disabled
    val newState: String,      // Enabled / Disabled
    val timestamp: Long,
    val triggerSource: String, // User Action, System Recovery, etc.
    val reason: String,
    val status: String,        // Success / Failed
    val startTime: Long? = null,
    val endTime: Long? = null,
    val durationMs: Long? = null
)
