package com.example.nasre.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "root_cause")
data class RootCauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val module: String,
    val failureType: String,
    val rootCause: String,
    val threadDump: String?,
    val exception: String?,
    val memorySnapshot: String?,
    val cpuSnapshot: String?,
    val recommendedRecovery: String,
    val recoveryExecuted: String,
    val recoveryResult: String
)
