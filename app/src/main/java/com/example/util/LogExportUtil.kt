package com.example.util

import com.example.data.model.SensorFusionState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Contextual

@Serializable
data class SensorLog(
    val timestamp: Long,
    val fusionState: SensorFusionState,
    val drivingEvent: String?
)

object LogExportUtil {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun serializeLog(fusionState: SensorFusionState, drivingEvent: String?): String {
        val log = SensorLog(System.currentTimeMillis(), fusionState, drivingEvent)
        return json.encodeToString(log)
    }
}
