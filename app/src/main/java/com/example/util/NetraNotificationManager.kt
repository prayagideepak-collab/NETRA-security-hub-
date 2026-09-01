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
        const val CHANNEL_ID_MOTION = "netra_motion_channel"
        const val NOTIFICATION_ID_EMERGENCY = 1001
        const val NOTIFICATION_ID_MOTION = 1002
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

            // Motion & Daily Activity Channel
            val nameMotion = "Netra Daily Motion & Activity"
            val descriptionTextMotion = "Displays daily activity and standing progress from validated telemetry"
            val importanceMotion = NotificationManager.IMPORTANCE_LOW
            val channelMotion = NotificationChannel(CHANNEL_ID_MOTION, nameMotion, importanceMotion).apply {
                description = descriptionTextMotion
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channelMotion)
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

    fun updateMotionNotification(state: com.example.data.model.DailyMotionDashboardState) {
        val stepTarget = state.targetProgress.targetSteps
        val stepsDone = state.totalActivity.totalSteps
        val standTargetSec = state.targetProgress.targetStandingSec
        val standDoneSec = state.totalActivity.standingDurationSec

        val stepsText = if (stepsDone != null && stepTarget != null) {
            "Steps: $stepsDone / $stepTarget"
        } else if (stepsDone != null) {
            "Steps: $stepsDone"
        } else {
            "Steps: Initializing"
        }

        val standingText = if (standTargetSec != null && standTargetSec > 0) {
            "Standing: ${com.example.data.model.MotionTimeFormatter.formatDuration(standDoneSec)} / ${com.example.data.model.MotionTimeFormatter.formatDuration(standTargetSec)}"
        } else {
            "Standing: ${com.example.data.model.MotionTimeFormatter.formatDuration(standDoneSec)}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_MOTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Daily Motion: $stepsText")
            .setContentText(standingText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MOTION, builder.build())
        } catch (e: SecurityException) {
            // Ignored if permission not yet granted
        }
    }
}
