package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.util.Calendar

@Composable
fun MotionIntelligenceScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val motionState by viewModel.motionDashboardState.collectAsState()
    val availableDates by viewModel.availableMotionHistoryDates.collectAsState()
    val selectedDate by viewModel.selectedMotionDate.collectAsState()

    val dobEpochMs by viewModel.userDobEpochMs.collectAsState()
    val heightCm by viewModel.userHeightCm.collectAsState()
    val heightUnit by viewModel.userHeightUnit.collectAsState()
    val gender by viewModel.userGender.collectAsState()
    val customSteps by viewModel.customStepTarget.collectAsState()
    val customStandingSec by viewModel.customStandingTargetSec.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }

    val userProfile = remember(dobEpochMs, heightCm, heightUnit, gender) {
        UserProfile(dobEpochMs, heightCm, heightUnit, gender)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BentoBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Top: Motion Header Card & Profile Configuration
        item {
            MotionHeaderCard(
                state = motionState,
                userProfile = userProfile,
                onProfileClick = { showProfileDialog = true }
            )
        }

        // 7-Day History Date Selector
        item {
            HistoryDateSelector(
                selectedDate = selectedDate,
                availableDates = availableDates,
                onSelectDate = { viewModel.selectMotionHistoryDate(it) }
            )
        }

        // Historical Archive Banner if viewing past date
        if (motionState.isHistorical) {
            item {
                Surface(
                    color = BentoCardBg,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoGreenPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = BentoGreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Viewing Historical Archive", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
                            Text("Date: ${motionState.displayDate}", style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { viewModel.selectMotionHistoryDate(null) },
                            colors = ButtonDefaults.textButtonColors(contentColor = BentoGreenPrimary)
                        ) {
                            Text("Back to Today", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 1. Subsection: STANDING
        item {
            StandingStatsCard(standingStats = motionState.standingStats)
        }

        // 2. Subsection: WALKING
        item {
            WalkingStatsCard(
                walkingStats = motionState.walkingStats,
                routeSessions = motionState.routeSessions,
                activeSession = motionState.activeRouteSession
            )
        }

        // 3. Subsection: RUNNING
        item {
            RunningStatsCard(runningStats = motionState.runningStats)
        }

        // 4. Subsection: DRIVING
        item {
            DrivingStatsCard(
                drivingStats = motionState.drivingStats,
                routeSessions = motionState.routeSessions,
                activeSession = motionState.activeRouteSession
            )
        }

        // Motion Timeline (Confirmed Sessions & Start/End Snapshots)
        item {
            MotionTimelineCard(
                routeSessions = motionState.routeSessions,
                events = motionState.recentEvents
            )
        }

        // At the bottom:
        // Daily Activity Summary: Total Steps, Total Distance, Total Standing Time, Activity-wise details
        item {
            TotalActivitySummaryCard(
                total = motionState.totalActivity,
                walkingStats = motionState.walkingStats,
                runningStats = motionState.runningStats,
                standingStats = motionState.standingStats,
                drivingStats = motionState.drivingStats
            )
        }

        // Daily Targets / Progress
        item {
            ActivityTargetProgressCard(
                progress = motionState.targetProgress,
                onConfigureClick = { showProfileDialog = true }
            )
        }
    }

    if (showProfileDialog) {
        UserProfileEditDialog(
            currentProfile = userProfile,
            currentCustomSteps = customSteps,
            currentCustomStandingSec = customStandingSec,
            onDismiss = { showProfileDialog = false },
            onSave = { dob, hCm, hUnit, g, cSteps, cStand ->
                viewModel.saveUserProfile(dob, hCm, hUnit, g)
                viewModel.saveCustomTargets(cSteps, cStand)
                showProfileDialog = false
            }
        )
    }
}

@Composable
fun MotionHeaderCard(
    state: DailyMotionDashboardState,
    userProfile: UserProfile,
    onProfileClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth().testTag("motion_header_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MOTION",
                        style = MaterialTheme.typography.titleMedium,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Date: ${state.displayDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoTextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (state.currentMotionCategory) {
                            MotionCategory.WALKING, MotionCategory.RUNNING -> BentoGreenPrimary.copy(alpha = 0.2f)
                            MotionCategory.DRIVING -> BentoYellow.copy(alpha = 0.2f)
                            MotionCategory.STANDING -> BentoBlue.copy(alpha = 0.2f)
                            MotionCategory.UNKNOWN -> BentoHeroCardBg
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (state.currentMotionCategory) {
                                MotionCategory.WALKING, MotionCategory.RUNNING -> BentoGreenPrimary
                                MotionCategory.DRIVING -> BentoYellow
                                MotionCategory.STANDING -> BentoBlue
                                MotionCategory.UNKNOWN -> BentoBorder
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (state.currentMotionCategory) {
                                            MotionCategory.WALKING, MotionCategory.RUNNING -> BentoGreenPrimary
                                            MotionCategory.DRIVING -> BentoYellow
                                            MotionCategory.STANDING -> BentoBlue
                                            MotionCategory.UNKNOWN -> BentoTextMuted
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isHistorical) "Archived" else state.currentMotionCategory.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = BentoTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "User Profile & Biometrics",
                            tint = if (userProfile.isConfigured) BentoGreenPrimary else BentoTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileCard(
    userProfile: UserProfile,
    onEditClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth().testTag("user_profile_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "User Profile & Biometrics",
                        style = MaterialTheme.typography.titleSmall,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onEditClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = BentoGreenPrimary)
                ) {
                    Text(if (userProfile.isConfigured) "Edit" else "Set Up", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (userProfile.isConfigured) {
                val age = userProfile.calculateAge()
                val dobStr = userProfile.dobEpochMs?.let { MotionTimeFormatter.formatDisplayDate(it) } ?: "Not Set"
                val heightStr = if (userProfile.heightUnit == "ft/in") {
                    val totalInches = (userProfile.heightCm ?: 0f) / 2.54f
                    val feet = (totalInches / 12).toInt()
                    val inches = (totalInches % 12).toInt()
                    "$feet ft $inches in (${userProfile.heightCm?.toInt()} cm)"
                } else {
                    "${userProfile.heightCm?.toInt()} cm"
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Date of Birth", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
                        Text(dobStr, style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Column {
                        Text("Calculated Age", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
                        Text(if (age != null) "$age years" else "N/A", style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Column {
                        Text("Height", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
                        Text(heightStr, style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Column {
                        Text("Gender", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
                        Text(userProfile.gender.ifBlank { "Not Specified" }, style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Text(
                    text = "Profile details not configured. Setup your birth date and height to calculate accurate stride length and distance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BentoTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "These details are used only to improve activity/step calculations and related safety metrics.",
                style = MaterialTheme.typography.labelSmall,
                color = BentoTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ActivityTargetProgressCard(
    progress: ActivityTargetProgress,
    onConfigureClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth().testTag("target_progress_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Activity Targets & Goals",
                        style = MaterialTheme.typography.titleSmall,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (progress.targetConfigured) {
                    Surface(
                        color = BentoHeroCardBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = progress.ageGroupLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoGreenPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (progress.targetConfigured) {
                // Steps Progress
                val stepTarget = progress.targetSteps ?: 10000
                val stepsDone = progress.todaySteps
                val stepPct = progress.stepProgressPct

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Daily Steps Target", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                        if (stepsDone != null && stepPct != null) {
                            Text("$stepsDone / $stepTarget steps ($stepPct%)", style = MaterialTheme.typography.bodySmall, color = BentoGreenPrimary, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Unavailable / $stepTarget steps", style = MaterialTheme.typography.bodySmall, color = BentoTextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (stepPct != null) (stepPct / 100f).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (stepPct != null) BentoGreenPrimary else BentoTextMuted.copy(alpha = 0.3f),
                        trackColor = BentoHeroCardBg
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Standing Target
                val standTargetSec = progress.targetStandingSec ?: 10800L
                val standDoneSec = progress.todayStandingSec
                val standPct = progress.standingProgressPct ?: 0

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Daily Standing Target", style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                        Text(
                            "${MotionTimeFormatter.formatDuration(standDoneSec)} / ${MotionTimeFormatter.formatDuration(standTargetSec)} ($standPct%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (standPct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = BentoBlue,
                        trackColor = BentoHeroCardBg
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Target: Not configured. Set your birth date to enable age-appropriate activity targets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfigureClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary)
                    ) {
                        Text("Configure", fontSize = 11.sp, color = BentoBackground)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDateSelector(
    selectedDate: String?,
    availableDates: List<String>,
    onSelectDate: (String?) -> Unit
) {
    val scrollState = rememberScrollState()
    Column {
        Text("Activity History (Last 7 Days)", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = (selectedDate == null),
                onClick = { onSelectDate(null) },
                label = { Text("Today (Live)", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BentoGreenPrimary,
                    selectedLabelColor = BentoBackground,
                    containerColor = BentoHeroCardBg,
                    labelColor = BentoTextSecondary
                )
            )

            availableDates.forEach { dateKey ->
                val isSelected = (selectedDate == dateKey)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectDate(dateKey) },
                    label = { Text(MotionTimeFormatter.parseDateKeyToDisplay(dateKey), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BentoGreenPrimary,
                        selectedLabelColor = BentoBackground,
                        containerColor = BentoHeroCardBg,
                        labelColor = BentoTextSecondary
                    )
                )
            }
        }
    }
}

@Composable
fun StandingStatsCard(standingStats: SubActivityStats) {
    ActivityCard(
        title = "Standing",
        icon = Icons.Default.Accessibility,
        iconTint = BentoBlue,
        testTag = "standing_stats_card"
    ) {
        if (standingStats.durationSec <= 0L && !standingStats.isAvailable) {
            Text(
                text = "No standing activity recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = BentoTextMuted
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "Duration", value = MotionTimeFormatter.formatDuration(standingStats.durationSec))
                MetricColumn(label = "Session Status", value = standingStats.statusDescription.ifBlank { "Stationary" })
                MetricColumn(label = "Safety State", value = "Nominal")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Accurately recorded during active user interaction. Prolonged unattended desk placement is filtered.",
                style = MaterialTheme.typography.labelSmall,
                color = BentoTextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun WalkingStatsCard(
    walkingStats: SubActivityStats,
    routeSessions: List<MotionRouteSession> = emptyList(),
    activeSession: MotionRouteSession? = null
) {
    ActivityCard(
        title = "Walking",
        icon = Icons.Default.DirectionsWalk,
        iconTint = BentoGreenPrimary,
        testTag = "walking_stats_card"
    ) {
        val hasWalkingData = walkingStats.durationSec > 0L || (walkingStats.steps != null && walkingStats.steps > 0) || walkingStats.distanceMeters != null
        if (!hasWalkingData) {
            Text(
                text = "No walking data available yet.",
                style = MaterialTheme.typography.bodySmall,
                color = BentoTextMuted
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "Duration", value = MotionTimeFormatter.formatDuration(walkingStats.durationSec))
                MetricColumn(label = "Steps", value = walkingStats.steps?.toString() ?: "Steps unavailable")
                MetricColumn(label = "Distance", value = MotionTimeFormatter.formatDistance(walkingStats.distanceMeters))
            }
            if (walkingStats.cadenceStepsPerMin != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Cadence: ${walkingStats.cadenceStepsPerMin} steps/min", style = MaterialTheme.typography.labelSmall, color = BentoGreenPrimary)
            }
        }

        // Route Snapshots (Walking Start & End Single-Shot Location Fixes)
        val walkingSessions = routeSessions.filter { it.activityCategory == MotionCategory.WALKING }
        val currentWalkingSession = if (activeSession?.activityCategory == MotionCategory.WALKING) activeSession else null

        if (currentWalkingSession != null || walkingSessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BentoBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

            Text("Route Snapshots & Accuracy", style = MaterialTheme.typography.labelSmall, color = BentoGreenPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            if (currentWalkingSession != null) {
                Surface(
                    color = BentoHeroCardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(BentoGreenPrimary, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Active Session Snapshot", style = MaterialTheme.typography.labelSmall, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val startLoc = currentWalkingSession.startLocation
                        if (startLoc != null) {
                            Text("Start Point: %.5f, %.5f (±%.1f m)".format(startLoc.latitude, startLoc.longitude, startLoc.accuracyMeters ?: 0f), style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary, fontSize = 11.sp)
                        } else {
                            Text("Start Point: Location unavailable for this activity", style = MaterialTheme.typography.bodySmall, color = BentoTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            walkingSessions.take(2).forEach { session ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = BentoHeroCardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Completed Walk (${MotionTimeFormatter.formatDuration(((session.endTimeMs ?: session.startTimeMs) - session.startTimeMs) / 1000L)})", style = MaterialTheme.typography.labelSmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium)
                            if (session.snapshotDistanceMeters != null) {
                                val distLabel = if (session.isCumulativeDistance) "Route Distance" else "Geodetic"
                                Text("$distLabel: %.0f m".format(session.snapshotDistanceMeters), style = MaterialTheme.typography.labelSmall, color = BentoGreenPrimary, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Distance unavailable", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        if (session.startLocation != null) {
                            Text("Start: %.5f, %.5f".format(session.startLocation.latitude, session.startLocation.longitude), style = MaterialTheme.typography.bodySmall, color = BentoTextMuted, fontSize = 10.sp)
                        } else {
                            Text("Start: Location unavailable", style = MaterialTheme.typography.bodySmall, color = BentoTextMuted, fontSize = 10.sp)
                        }
                        if (session.endLocation != null) {
                            Text("End: %.5f, %.5f".format(session.endLocation.latitude, session.endLocation.longitude), style = MaterialTheme.typography.bodySmall, color = BentoTextMuted, fontSize = 10.sp)
                        }
                        if (session.locationAccuracyMeters != null) {
                            Text("Accuracy: ±%.1f m".format(session.locationAccuracyMeters), style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RunningStatsCard(runningStats: SubActivityStats) {
    ActivityCard(
        title = "Running",
        icon = Icons.Default.DirectionsRun,
        iconTint = BentoYellow,
        testTag = "running_stats_card"
    ) {
        val hasRunningData = runningStats.durationSec > 0L || (runningStats.steps != null && runningStats.steps > 0) || runningStats.distanceMeters != null
        if (!hasRunningData) {
            Text(
                text = "No running activity detected.",
                style = MaterialTheme.typography.bodySmall,
                color = BentoTextMuted
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "Duration", value = MotionTimeFormatter.formatDuration(runningStats.durationSec))
                MetricColumn(label = "Steps", value = runningStats.steps?.toString() ?: "Steps unavailable")
                MetricColumn(label = "Distance", value = MotionTimeFormatter.formatDistance(runningStats.distanceMeters))
            }
            if (runningStats.cadenceStepsPerMin != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Cadence: ${runningStats.cadenceStepsPerMin} steps/min (High Intensity)", style = MaterialTheme.typography.labelSmall, color = BentoYellow)
            }
        }
    }
}

@Composable
fun DrivingStatsCard(
    drivingStats: SubActivityStats,
    routeSessions: List<MotionRouteSession> = emptyList(),
    activeSession: MotionRouteSession? = null
) {
    ActivityCard(
        title = "Driving",
        icon = Icons.Default.DirectionsCar,
        iconTint = BentoPurple,
        testTag = "driving_stats_card"
    ) {
        val hasDrivingData = drivingStats.durationSec > 0L || drivingStats.distanceMeters != null || drivingStats.currentSpeedKmH != null
        if (!hasDrivingData) {
            Text(
                text = "Driving data unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = BentoTextMuted
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "Duration", value = MotionTimeFormatter.formatDuration(drivingStats.durationSec))
                MetricColumn(label = "Distance", value = MotionTimeFormatter.formatDistance(drivingStats.distanceMeters))
                MetricColumn(label = "Speed Evidence", value = drivingStats.currentSpeedKmH?.let { "%.1f km/h".format(it) } ?: "Unavailable")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Driving classification requires sustained GNSS speed ≥ 20.0 km/h for confirmation period.",
                style = MaterialTheme.typography.labelSmall,
                color = BentoTextMuted,
                fontSize = 10.sp
            )
        }

        // Route Events (Smart Speed-Drop Snapshots)
        val allEvents = buildList {
            if (activeSession != null && activeSession.activityCategory == MotionCategory.DRIVING) {
                addAll(activeSession.intermediateEvents)
            }
            routeSessions.filter { it.activityCategory == MotionCategory.DRIVING }.forEach {
                addAll(it.intermediateEvents)
            }
        }.take(3)

        if (allEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BentoBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Route Events & Speed Drops", style = MaterialTheme.typography.labelSmall, color = BentoPurple, fontWeight = FontWeight.Bold)

            allEvents.forEach { ev ->
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = BentoHeroCardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(ev.classification.displayName, style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                            Text("Δ -%.1f km/h (from %.1f km/h)".format(ev.speedDeltaKmH, ev.previousSpeedKmH), style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                        }
                        if (ev.latitude != null && ev.longitude != null) {
                            Text("%.4f, %.4f".format(ev.latitude, ev.longitude), style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary, fontSize = 10.sp)
                        } else {
                            Text("Location unavailable", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MotionTimelineCard(
    routeSessions: List<MotionRouteSession>,
    events: List<MotionEvent>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth().testTag("motion_timeline_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Motion Timeline",
                    style = MaterialTheme.typography.titleSmall,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (routeSessions.isEmpty() && events.isEmpty()) {
                Text(
                    text = "No confirmed motion sessions recorded for this date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BentoTextMuted
                )
            } else {
                routeSessions.forEach { session ->
                    val sDate = MotionTimeFormatter.formatTimelineDate(session.startTimeMs)
                    val sTime = MotionTimeFormatter.formatDisplayTime(session.startTimeMs)
                    val eTimeMs = session.endTimeMs ?: session.startTimeMs
                    val eDate = MotionTimeFormatter.formatTimelineDate(eTimeMs)
                    val eTime = MotionTimeFormatter.formatDisplayTime(eTimeMs)
                    val durSec = ((eTimeMs - session.startTimeMs) / 1000L).coerceAtLeast(0L)

                    Surface(
                        color = BentoHeroCardBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val catColor = when (session.activityCategory) {
                                        MotionCategory.WALKING -> BentoGreenPrimary
                                        MotionCategory.RUNNING -> BentoYellow
                                        MotionCategory.DRIVING -> BentoPurple
                                        MotionCategory.STANDING -> BentoBlue
                                        MotionCategory.UNKNOWN -> BentoTextMuted
                                    }
                                    Box(modifier = Modifier.size(8.dp).background(catColor, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = session.activityCategory.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = BentoTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "ID: ${session.sessionId.take(8)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BentoTextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Start: Date: $sDate, Time: $sTime", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary, fontSize = 11.sp)
                                    Text("End:   Date: $eDate, Time: $eTime", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Duration: ${MotionTimeFormatter.formatDuration(durSec)}", style = MaterialTheme.typography.labelSmall, color = BentoTextPrimary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                                    if (session.snapshotDistanceMeters != null) {
                                        Text("Distance: ${MotionTimeFormatter.formatDistance(session.snapshotDistanceMeters)}", style = MaterialTheme.typography.labelSmall, color = BentoGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    } else {
                                        Text("Distance unavailable", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                                    }
                                }
                            }

                            if (session.startLocation != null || session.endLocation != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    if (session.startLocation != null) {
                                        Text("Start GPS: %.4f, %.4f".format(session.startLocation.latitude, session.startLocation.longitude), style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                                    } else {
                                        Text("Start GPS: Unavailable", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                                    }
                                    if (session.endLocation != null) {
                                        Text("End GPS: %.4f, %.4f".format(session.endLocation.latitude, session.endLocation.longitude), style = MaterialTheme.typography.labelSmall, color = BentoTextMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TotalActivitySummaryCard(
    total: TotalActivityStats,
    walkingStats: SubActivityStats,
    runningStats: SubActivityStats,
    standingStats: SubActivityStats,
    drivingStats: SubActivityStats
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth().testTag("total_activity_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Assessment, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Daily Activity Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Metrics: Total Steps, Total Distance, Total Standing Time
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "TOTAL STEPS", value = total.totalSteps?.toString() ?: "Unavailable", isPrimary = true)
                MetricColumn(label = "TOTAL DISTANCE", value = MotionTimeFormatter.formatDistance(total.totalDistanceMeters), isPrimary = true)
                MetricColumn(label = "STANDING TIME", value = MotionTimeFormatter.formatDuration(total.standingDurationSec), isPrimary = true)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BentoBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Text("Activity-Wise Breakdown", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Walking: steps, duration, distance
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "Walking Steps", value = walkingStats.steps?.toString() ?: "Unavailable")
                MetricColumn(label = "Walking Duration", value = MotionTimeFormatter.formatDuration(walkingStats.durationSec))
                MetricColumn(label = "Walking Distance", value = MotionTimeFormatter.formatDistance(walkingStats.distanceMeters))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Running: steps, duration, distance
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "Running Steps", value = runningStats.steps?.toString() ?: "Unavailable")
                MetricColumn(label = "Running Duration", value = MotionTimeFormatter.formatDuration(runningStats.durationSec))
                MetricColumn(label = "Running Distance", value = MotionTimeFormatter.formatDistance(runningStats.distanceMeters))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Standing: duration & Driving: duration, distance
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn(label = "Standing Duration", value = MotionTimeFormatter.formatDuration(standingStats.durationSec))
                MetricColumn(label = "Driving Duration", value = MotionTimeFormatter.formatDuration(drivingStats.durationSec))
                MetricColumn(label = "Driving Distance", value = MotionTimeFormatter.formatDistance(drivingStats.distanceMeters))
            }
        }
    }
}

@Composable
fun ActivityCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun MetricColumn(
    label: String,
    value: String,
    isPrimary: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isPrimary) BentoGreenPrimary else BentoTextMuted,
            fontSize = if (isPrimary) 11.sp else 10.sp,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = BentoTextPrimary,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (isPrimary) 15.sp else 13.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileEditDialog(
    currentProfile: UserProfile,
    currentCustomSteps: Int?,
    currentCustomStandingSec: Long?,
    onDismiss: () -> Unit,
    onSave: (dobEpochMs: Long?, heightCm: Float?, heightUnit: String, gender: String, customSteps: Int?, customStandingSec: Long?) -> Unit
) {
    val context = LocalContext.current

    var selectedDobMs by remember { mutableStateOf(currentProfile.dobEpochMs) }
    var heightInput by remember {
        mutableStateOf(
            if (currentProfile.heightUnit == "ft/in" && currentProfile.heightCm != null) {
                val totalInches = currentProfile.heightCm / 2.54f
                val ft = (totalInches / 12).toInt()
                val inc = (totalInches % 12).toInt()
                "$ft' $inc\""
            } else {
                currentProfile.heightCm?.toInt()?.toString() ?: ""
            }
        )
    }
    var heightUnit by remember { mutableStateOf(currentProfile.heightUnit) }
    var selectedGender by remember { mutableStateOf(currentProfile.gender.ifBlank { "Male" }) }
    var customStepInput by remember { mutableStateOf(currentCustomSteps?.toString() ?: "") }
    var customStandingHrsInput by remember { mutableStateOf(currentCustomStandingSec?.let { (it / 3600L).toString() } ?: "") }

    val openDatePicker = {
        val cal = Calendar.getInstance()
        if (selectedDobMs != null && selectedDobMs!! > 0) {
            cal.timeInMillis = selectedDobMs!!
        }
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // Enforce past/present date
                if (!picked.after(Calendar.getInstance())) {
                    selectedDobMs = picked.timeInMillis
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.maxDate = System.currentTimeMillis() // Prevents future dates
        datePicker.show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BentoCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "User Profile & Biometrics",
                    style = MaterialTheme.typography.titleMedium,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                // Date of Birth Selector
                Column {
                    Text("Date of Birth (enforces past/today)", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openDatePicker() },
                        shape = RoundedCornerShape(8.dp),
                        color = BentoHeroCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedDobMs?.let { MotionTimeFormatter.formatDisplayDate(it) } ?: "Select Date of Birth",
                                color = if (selectedDobMs != null) BentoTextPrimary else BentoTextMuted,
                                fontSize = 14.sp
                            )
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BentoGreenPrimary)
                        }
                    }
                }

                // Height & Unit Selector
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Height", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                        Row {
                            FilterChip(
                                selected = (heightUnit == "cm"),
                                onClick = { heightUnit = "cm" },
                                label = { Text("cm", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = (heightUnit == "ft/in"),
                                onClick = { heightUnit = "ft/in" },
                                label = { Text("ft/in", fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        placeholder = { Text(if (heightUnit == "cm") "e.g. 175" else "e.g. 5.9 (feet.inches)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Gender Selector
                Column {
                    Text("Gender (for biomechanical stride length)", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Male", "Female", "Other").forEach { g ->
                            FilterChip(
                                selected = (selectedGender == g),
                                onClick = { selectedGender = g },
                                label = { Text(g, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Custom Targets (Optional)
                Column {
                    Text("Custom Targets (Optional override)", style = MaterialTheme.typography.labelSmall, color = BentoTextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customStepInput,
                            onValueChange = { customStepInput = it },
                            label = { Text("Steps Target", fontSize = 11.sp) },
                            placeholder = { Text("10000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customStandingHrsInput,
                            onValueChange = { customStandingHrsInput = it },
                            label = { Text("Standing (hrs)", fontSize = 11.sp) },
                            placeholder = { Text("3") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = BentoTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedCm: Float? = if (heightUnit == "ft/in") {
                                val raw = heightInput.replace("'", ".").replace("\"", "").trim()
                                val parts = raw.split(".")
                                if (parts.isNotEmpty()) {
                                    val ft = parts[0].toFloatOrNull() ?: 0f
                                    val inc = if (parts.size > 1) parts[1].toFloatOrNull() ?: 0f else 0f
                                    ((ft * 12f) + inc) * 2.54f
                                } else null
                            } else {
                                heightInput.toFloatOrNull()
                            }

                            val stepTarget = customStepInput.toIntOrNull()
                            val standSec = customStandingHrsInput.toLongOrNull()?.let { it * 3600L }

                            onSave(
                                selectedDobMs,
                                parsedCm,
                                heightUnit,
                                selectedGender,
                                stepTarget,
                                standSec
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary)
                    ) {
                        Text("Save Profile", color = BentoBackground)
                    }
                }
            }
        }
    }
}
