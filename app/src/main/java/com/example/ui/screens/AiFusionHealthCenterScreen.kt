package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.HealthCenterEngine
import com.example.data.model.*
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import com.example.util.TimeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HealthSubSection(val title: String, val icon: ImageVector) {
    LIVE_HEALTH("Live Health", Icons.Default.Favorite),
    DAILY_ACTIVITY("Daily Activity", Icons.Default.DirectionsWalk),
    DIGITAL_WELLNESS("Digital Wellness", Icons.Default.PhonelinkSetup),
    WALKING_STATS("Walking Stats", Icons.Default.Speed),
    STANDING_STATS("Standing Stats", Icons.Default.Accessibility),
    TIMELINE("Movement Timeline", Icons.Default.Timeline),
    SAFETY_EVENTS("Safety / Hits", Icons.Default.Security),
    POWER_IMPACT("Power Impact", Icons.Default.BatteryChargingFull),
    REPORTS("Reports & Trends", Icons.Default.Assessment)
}

@Composable
fun AiFusionHealthCenterScreen(
    viewModel: MainViewModel,
    capabilities: List<SensorCapabilityInfo>,
    modifier: Modifier = Modifier
) {
    val healthEngine = viewModel.repository.sensorManager.healthCenterEngine

    val score by healthEngine.healthScore.collectAsState()
    val carryState by healthEngine.carryState.collectAsState()
    val intensity by healthEngine.movementIntensity.collectAsState()
    val dailyMetrics by healthEngine.dailyMetrics.collectAsState()
    val walkingStats by healthEngine.walkingStats.collectAsState()
    val standingStats by healthEngine.standingStats.collectAsState()
    val timeline by healthEngine.movementTimeline.collectAsState()
    val impacts by healthEngine.impactEvents.collectAsState()
    val powerImpacts by healthEngine.powerImpactList.collectAsState()
    val report by healthEngine.healthReport.collectAsState()

    val digitalMetrics by healthEngine.digitalMetrics.collectAsState()
    val appUsageList by healthEngine.appUsageList.collectAsState()
    val deviceEvents by healthEngine.deviceUsageEvents.collectAsState()
    val combinedTimeline by healthEngine.combinedTimeline.collectAsState()

    val dwreSettings by healthEngine.dwreSettings.collectAsState()
    val activeAppSession by healthEngine.activeAppSession.collectAsState()
    val dwreNotifications by healthEngine.dwreNotifications.collectAsState()
    val dwreDailySummary by healthEngine.dwreDailySummary.collectAsState()

    var selectedSection by remember { mutableStateOf(HealthSubSection.LIVE_HEALTH) }

    val deduplicatedRegistry = remember(capabilities) {
        healthEngine.getDeduplicatedSensorRegistry(capabilities)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("ai_fusion_health_center_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Screen Title Banner
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = BentoGreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI FUSION HEALTH CENTER",
                        color = BentoGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Activity & Movement Intelligence",
                    color = BentoTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Non-medical movement quality, posture balance, carry state & power context",
                    color = BentoTextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Section Tabs Navigation Scrollable Row
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedSection.ordinal,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = BentoGreenPrimary,
                divider = {}
            ) {
                HealthSubSection.values().forEach { tab ->
                    FilterChip(
                        selected = (selectedSection == tab),
                        onClick = { selectedSection = tab },
                        label = { Text(tab.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BentoGreenPrimary,
                            selectedLabelColor = BentoBackground,
                            containerColor = BentoCardBg,
                            labelColor = BentoTextSecondary
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        // Section Content Switcher
        when (selectedSection) {
            HealthSubSection.LIVE_HEALTH -> {
                item {
                    LiveHealthHeroCard(
                        score = score,
                        carryState = carryState,
                        intensity = intensity
                    )
                }
                item {
                    DeduplicatedSensorRegistryCard(sensors = deduplicatedRegistry)
                }
            }

            HealthSubSection.DAILY_ACTIVITY -> {
                item {
                    DailyActivityOverviewCard(metrics = dailyMetrics)
                }
            }

            HealthSubSection.DIGITAL_WELLNESS -> {
                item {
                    DigitalWellnessSection(
                        digitalMetrics = digitalMetrics,
                        appUsageList = appUsageList,
                        deviceEvents = deviceEvents,
                        combinedTimeline = combinedTimeline,
                        dwreSettings = dwreSettings,
                        activeAppSession = activeAppSession,
                        dwreNotifications = dwreNotifications,
                        dwreDailySummary = dwreDailySummary,
                        onUpdateDwreSettings = { healthEngine.updateDwreSettings(it) },
                        onSwitchAppSession = { pkg, name, cat -> healthEngine.switchActiveAppSession(pkg, name, cat) },
                        onSimulateAddSessionTime = { sec -> healthEngine.simulateAddSessionTime(sec) },
                        onTriggerTestNotification = { healthEngine.triggerTestDwreNotification() }
                    )
                }
            }

            HealthSubSection.WALKING_STATS -> {
                item {
                    WalkingStatisticsCard(stats = walkingStats, distanceKm = dailyMetrics.walkingDistanceKm)
                }
            }

            HealthSubSection.STANDING_STATS -> {
                item {
                    StandingStatisticsCard(stats = standingStats, standingSec = dailyMetrics.standingDurationSec)
                }
            }

            HealthSubSection.TIMELINE -> {
                item {
                    Text(
                        text = "TODAY'S MOVEMENT TIMELINE",
                        color = BentoTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                items(items = timeline, key = { it.id }) { item ->
                    MovementTimelineCard(item = item)
                }
            }

            HealthSubSection.SAFETY_EVENTS -> {
                item {
                    Text(
                        text = "IMPACT & PHYSICAL FORCE LOG",
                        color = BentoTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                if (impacts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(BentoCardBg)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🟢 No physical impact spikes recorded today.",
                                color = BentoGreenPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    items(items = impacts, key = { it.id }) { event ->
                        ImpactEventCard(event = event)
                    }
                }
            }

            HealthSubSection.POWER_IMPACT -> {
                item {
                    PowerImpactOverviewCard(metrics = dailyMetrics, powerList = powerImpacts)
                }
            }

            HealthSubSection.REPORTS -> {
                item {
                    ReportsAndTrendsCard(report = report)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun LiveHealthHeroCard(
    score: ActivityHealthScore,
    carryState: CarryState,
    intensity: MovementIntensity
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoHeroCardBg)
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = BentoGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "LIVE MOVEMENT HEALTH SCORE",
                        color = BentoGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = score.statusLabel,
                        color = BentoTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${score.score}",
                    color = BentoGreenPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score Progress Bar
            LinearProgressIndicator(
                progress = { score.score / 100f },
                color = BentoGreenPrimary,
                trackColor = BentoHeroCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Carry State Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoHeroCardBg)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Carry State", color = BentoTextMuted, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = carryState.displayName,
                            color = BentoTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Movement Intensity Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoHeroCardBg)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Movement Intensity", color = BentoTextMuted, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "%.1f / 10".format(intensity.value),
                            color = BentoTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Sensor Attribution Footer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = BentoGreenVibrant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Source: ${score.primarySensorSource} (${score.confidencePct}% Confidence)",
                    color = BentoTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun DeduplicatedSensorRegistryCard(sensors: List<SensorCapabilityInfo>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CANONICAL DEDUPLICATED SENSORS",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            sensors.take(6).forEach { sensor ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sensor.name, color = BentoTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(sensor.description, color = BentoTextMuted, fontSize = 10.sp, maxLines = 1)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BentoHeroCardBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Canonical Active", color = BentoGreenPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Divider(color = BentoBorder.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
fun DailyActivityOverviewCard(metrics: DailyActivityMetrics) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "DAILY ACTIVITY BREAKDOWN",
                color = BentoGreenPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Steps & Distance Hero Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    title = "Steps Today",
                    value = "${metrics.stepsToday}",
                    subtitle = "Goal: 8,000",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Walking Distance",
                    value = "%.2f km".format(metrics.walkingDistanceKm),
                    subtitle = "Stride Estimate",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Durations Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DurationPill("Walking", TimeManager.formatDuration(metrics.walkingDurationSec), Modifier.weight(1f))
                DurationPill("Standing", TimeManager.formatDuration(metrics.standingDurationSec), Modifier.weight(1f))
                DurationPill("Idle / Sitting", TimeManager.formatDuration(metrics.idleDurationSec), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Context Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Thermal State: ${metrics.thermalLevelLabel}", color = BentoTextMuted, fontSize = 11.sp)
                Text("Activity Battery Cost: %.1f%%/hr".format(metrics.batteryDrainRatePctHr), color = BentoTextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun WalkingStatisticsCard(stats: WalkingStats, distanceKm: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WALKING CADENCE & METRICS",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile("Cadence", "${stats.cadenceStepsPerMin} steps/min", "Pace Quality", Modifier.weight(1f))
                MetricTile("Avg Stride", "${stats.avgStrideLengthCm} cm", "Hardware Calibrated", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile("Avg Speed", "%.1f km/h".format(stats.avgWalkingSpeedKmH), "GPS + Motion", Modifier.weight(1f))
                MetricTile("Energy Burn", "${stats.estimatedCaloriesKcal} kcal", "Estimated", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StandingStatisticsCard(stats: StandingStats, standingSec: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Accessibility, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STANDING & POSTURE BALANCING",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile("Standing Ratio", "${stats.standingRatioPct}%", "Active vs Idle", Modifier.weight(1f))
                MetricTile("Longest Stretch", "${stats.longestStandingStretchMin} min", "Continuous", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            MetricTile("Posture Stability Score", "${stats.postureStabilityScore}%", "Balance Index", Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun MovementTimelineCard(item: MovementTimelineItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BentoHeroCardBg)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = when (item.activityType) {
                        "WALKING" -> Icons.Default.DirectionsWalk
                        "STANDING" -> Icons.Default.Accessibility
                        "VEHICLE" -> Icons.Default.DirectionsCar
                        else -> Icons.Default.Bed
                    },
                    contentDescription = null,
                    tint = BentoGreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.activityType,
                    color = BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Duration: ${TimeManager.formatDuration(item.durationSec)} • Distance: %.0f m".format(item.distanceMeters),
                    color = BentoTextMuted,
                    fontSize = 11.sp
                )
            }

            Text(
                text = "${item.confidencePct}% Conf",
                color = BentoGreenVibrant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ImpactEventCard(event: ImpactEventItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoRed.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = BentoRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.title,
                    color = BentoRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "%.2f G".format(event.gForceMagnitude),
                    color = BentoRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = event.description,
                color = BentoTextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PowerImpactOverviewCard(metrics: DailyActivityMetrics, powerList: List<PowerImpactMetrics>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "POWER & THERMAL IMPACT PER ACTIVITY",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            powerList.forEach { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.activityType, color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Battery Cost: %.1f%%/hr • Temp Rise: +%.1f°C/hr".format(p.batteryDrainRatePctHr, p.thermalRiseCPerHr),
                            color = BentoTextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BentoHeroCardBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(p.thermalStatus, color = BentoGreenPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Divider(color = BentoBorder.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun ReportsAndTrendsCard(report: HealthCenterReport) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Assessment, contentDescription = null, tint = BentoGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACTIVITY TRENDS & REPORTS",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile("Today vs Yesterday", "${report.todaySteps} / ${report.yesterdaySteps}", "Steps", Modifier.weight(1f))
                MetricTile("7-Day Avg Steps", "${report.weeklyAvgSteps}", report.healthScoreTrend, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AI MOVEMENT INSIGHTS",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            report.insightsSummary.forEach { insight ->
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Text("• ", color = BentoGreenPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(insight, color = BentoTextPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MetricTile(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BentoHeroCardBg)
            .padding(14.dp)
    ) {
        Column {
            Text(title, color = BentoTextMuted, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = BentoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = BentoGreenVibrant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DurationPill(label: String, formattedDuration: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BentoHeroCardBg)
            .padding(10.dp)
    ) {
        Column {
            Text(label, color = BentoTextMuted, fontSize = 9.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(formattedDuration, color = BentoTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
