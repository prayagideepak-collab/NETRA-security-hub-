package com.example.util

import java.util.Locale

object TimeManager {
    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationMs(millis: Long): String {
        return formatDuration(millis / 1000)
    }
}
