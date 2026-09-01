package com.example.nasre.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resource_optimizer")
data class ResourceOptimizerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val cpu: Double,
    val ram: Long,
    val workerCount: Int,
    val optimizationApplied: String,
    val beforeState: String,
    val afterState: String,
    val result: String
)
