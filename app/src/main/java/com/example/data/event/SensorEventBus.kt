package com.example.data.event

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.PriorityBlockingQueue

enum class EventPriority {
    EMERGENCY, CRITICAL, WARNING, INFO, DEBUG
}

data class SensorEvent(
    val priority: EventPriority,
    val data: Any,
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<SensorEvent> {
    override fun compareTo(other: SensorEvent): Int {
        return priority.ordinal.compareTo(other.priority.ordinal)
    }
}

object SensorEventBus {
    private val queue = PriorityBlockingQueue<SensorEvent>()
    private val _persistentAlert = MutableStateFlow<SensorEvent?>(null)
    val persistentAlert: StateFlow<SensorEvent?> = _persistentAlert.asStateFlow()

    fun post(event: SensorEvent) {
        queue.put(event)
        if (event.priority == EventPriority.EMERGENCY || event.priority == EventPriority.CRITICAL) {
            _persistentAlert.value = event
        }
    }

    fun take(): SensorEvent {
        return queue.take()
    }

    fun clearAlert() {
        _persistentAlert.value = null
    }
}
