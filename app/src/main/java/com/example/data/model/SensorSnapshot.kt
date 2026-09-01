package com.example.data.model

/**
 * An immutable snapshot of all sensor and system states at a specific point in time.
 * This is the central "Single Source of Truth" for all Netra components.
 */
data class SensorSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val monotonicTimestamp: Long = System.nanoTime(),
    
    // Physical Sensors
    val batteryPercent: Int = 0,
    val batteryTempC: Float = 0f,
    val deviceTempC: Float = 0f,
    val ambientTempC: Float = 0f,
    val magneticFieldUT: Float = 0f,
    val ambientLightLux: Float = 0f,
    
    // System Status
    val isCharging: Boolean = false,
    val isScreenOn: Boolean = false,
    val isNetworkActive: Boolean = false,
    val isGpsActive: Boolean = false,
    
    // Metadata/Context
    val metadata: Map<String, String> = emptyMap()
)
