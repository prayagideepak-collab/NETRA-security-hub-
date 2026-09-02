package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.SafetyRiskLevel

class NetraNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID_EMERGENCY = "netra_emergency_channel"
        const val NOTIFICATION_ID_EMERGENCY = 1001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Emergency Channel
            val nameEmergency = "Netra Emergency & Critical Alerts"
            val descriptionTextEmergency = "High-priority alerts that bypass Do Not Disturb for critical sensor risks"
            val importanceEmergency = NotificationManager.IMPORTANCE_HIGH
            val channelEmergency = NotificationChannel(CHANNEL_ID_EMERGENCY, nameEmergency, importanceEmergency).apply {
                description = descriptionTextEmergency
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 1000)
                setBypassDnd(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channelEmergency)
        }
    }

    fun sendEmergencyAlert(title: String, message: String, riskLevel: SafetyRiskLevel) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_EMERGENCY)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("NETRA ALERT: $title")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 1000))

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_EMERGENCY, builder.build())
        } catch (e: SecurityException) {
            // Permission might not be granted on API 33+ without runtime prompt
        }
    }
}
