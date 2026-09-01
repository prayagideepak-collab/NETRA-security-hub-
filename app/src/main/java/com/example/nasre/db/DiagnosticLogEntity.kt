package com.example.nasre.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val severity: String,
    val module: String,
    val event: String,
    val description: String,
    val recoveryAction: String?,
    val result: String?,
    val sessionId: String,
    val buildVersion: String
)
