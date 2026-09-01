package com.example.data.engine

import com.example.data.model.ActivityTarget
import com.example.data.model.ActivityTargetProgress
import com.example.data.model.UserProfile
import com.example.data.model.MotionTimeFormatter

object ActivityTargetEngine {

    fun determineTarget(
        userProfile: UserProfile,
        customStepTarget: Int? = null,
        customStandingTargetSec: Long? = null,
        currentEpochMs: Long = System.currentTimeMillis()
    ): ActivityTarget {
        val effectiveDateStr = MotionTimeFormatter.formatDisplayDate(currentEpochMs)
        val age = userProfile.calculateAge(currentEpochMs)

        if (customStepTarget != null || customStandingTargetSec != null) {
            val stepT = customStepTarget ?: 10000
            val standT = customStandingTargetSec ?: 10800L
            val ageLabel = if (age != null) "Age $age" else "Custom"
            return ActivityTarget(
                ageGroup = ageLabel,
                stepTarget = stepT,
                standingTargetSec = standT,
                targetSource = "User Configured Target",
                effectiveDate = effectiveDateStr
            )
        }

        if (age == null) {
            return ActivityTarget(
                ageGroup = "Not configured",
                stepTarget = null,
                standingTargetSec = null,
                targetSource = "Not configured",
                effectiveDate = effectiveDateStr
            )
        }

        return when {
            age < 18 -> ActivityTarget(
                ageGroup = "Youth (< 18 yrs)",
                stepTarget = 12000,
                standingTargetSec = 10800L, // 3 hours
                targetSource = "Dynamic (Age-Group: Youth)",
                effectiveDate = effectiveDateStr
            )
            age <= 64 -> ActivityTarget(
                ageGroup = "Adult (18–64 yrs)",
                stepTarget = 10000,
                standingTargetSec = 10800L, // 3 hours
                targetSource = "Dynamic (Age-Group: Adult)",
                effectiveDate = effectiveDateStr
            )
            else -> ActivityTarget(
                ageGroup = "Senior (65+ yrs)",
                stepTarget = 7000,
                standingTargetSec = 7200L, // 2 hours
                targetSource = "Dynamic (Age-Group: Senior)",
                effectiveDate = effectiveDateStr
            )
        }
    }

    fun calculateProgress(
        target: ActivityTarget,
        todaySteps: Int?,
        todayStandingSec: Long
    ): ActivityTargetProgress {
        val isConfigured = target.stepTarget != null && target.standingTargetSec != null

        val stepProgress = if (isConfigured && todaySteps != null && target.stepTarget != null && target.stepTarget > 0) {
            ((todaySteps.toFloat() / target.stepTarget) * 100f).toInt().coerceIn(0, 100)
        } else null

        val standingProgress = if (isConfigured && target.standingTargetSec != null && target.standingTargetSec > 0) {
            ((todayStandingSec.toFloat() / target.standingTargetSec) * 100f).toInt().coerceIn(0, 100)
        } else null

        return ActivityTargetProgress(
            todaySteps = todaySteps,
            targetSteps = target.stepTarget,
            stepProgressPct = stepProgress,
            todayStandingSec = todayStandingSec,
            targetStandingSec = target.standingTargetSec,
            standingProgressPct = standingProgress,
            targetConfigured = isConfigured,
            ageGroupLabel = target.ageGroup
        )
    }
}
