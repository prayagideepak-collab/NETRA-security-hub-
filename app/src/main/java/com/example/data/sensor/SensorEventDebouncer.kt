package com.example.data.sensor

import com.example.data.model.RawSensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.math.abs

object SensorEventDebouncer {

    /**
     * Debounces a sensor readings map flow, ignoring rapid insignificant state changes
     * and minor value fluctuations below a noise tolerance threshold.
     * Ensures processing engine only triggers for meaningful safety-related events.
     */
    fun Flow<Map<String, RawSensorReading>>.debounceSensorStream(
        debounceTimeoutMs: Long = 500L,
        noiseTolerance: Float = 0.05f
    ): Flow<Map<String, RawSensorReading>> = flow {
        var lastEmittedValues: Map<String, Float> = emptyMap()

        collect { currentMap ->
            val hasSignificantChange = currentMap.any { (key, reading) ->
                val lastVal = lastEmittedValues[key] ?: return@any true
                val currentVal = reading.values.firstOrNull() ?: 0f
                abs(currentVal - lastVal) >= noiseTolerance
            }

            if (hasSignificantChange || lastEmittedValues.isEmpty()) {
                lastEmittedValues = currentMap.mapValues { it.value.values.firstOrNull() ?: 0f }
                emit(currentMap)
            }
        }
    }.debounce(debounceTimeoutMs)

    /**
     * General extension to debounce any generic sensor metric flow.
     */
    fun <T> Flow<T>.debounceEvent(timeoutMs: Long = 400L): Flow<T> {
        return this.distinctUntilChanged().debounce(timeoutMs)
    }
}
