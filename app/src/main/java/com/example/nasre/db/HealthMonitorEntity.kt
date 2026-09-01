package com.example.nasre.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_monitor")
data class HealthMonitorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val moduleName: String,
    val status: String,
    val memoryUsage: Long,
    val cpuUsage: Double,
    val threadCount: Int,
    val activeWorkers: Int,
    val healthScore: Int,
    val lastHeartbeat: Long,
    val failureCount: Int
)
