package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.model.SafetyRiskLevel

class NetraHapticsManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun triggerHapticForRisk(riskLevel: SafetyRiskLevel) {
        if (!vibrator.hasVibrator()) return

        when (riskLevel) {
            SafetyRiskLevel.EMERGENCY -> {
                // Repeating long pattern: 3 heavy pulses
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 400, 200, 400, 200, 800)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 400, 200, 400, 200, 800), -1)
                }
            }
            SafetyRiskLevel.WARNING -> {
                // Moderate double pulse
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
            }
            SafetyRiskLevel.ATTENTION -> {
                // Short attention tap
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(150, 150)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
            }
            SafetyRiskLevel.SAFE -> {
                // No vibration on safe state
            }
        }
    }
}
