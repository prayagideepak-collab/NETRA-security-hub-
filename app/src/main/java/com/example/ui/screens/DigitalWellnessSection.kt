package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DigitalWellnessSection(
    digitalMetrics: DigitalWellnessMetrics,
    appUsageList: List<AppUsageInfo>,
    deviceEvents: List<DeviceUsageEvent>,
    combinedTimeline: List<CombinedHealthTimelineItem>,
    dwreSettings: DwreSettings = DwreSettings(),
    activeAppSession: AppSessionTracker = AppSessionTracker("com.instagram.android", "Instagram", AppCategory.SOCIAL_MEDIA),
    dwreNotifications: List<DwreNotificationEvent> = emptyList(),
    dwreDailySummary: DwreDailySummary = DwreDailySummary(),
    onUpdateDwreSettings: (DwreSettings) -> Unit = {},
    onSwitchAppSession: (String, String, AppCategory) -> Unit = { _, _, _ -> },
    onSimulateAddSessionTime: (Long) -> Unit = {},
    onTriggerTestNotification: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Digital Wellness Score & Overview Header
        DigitalWellnessScoreCard(metrics = digitalMetrics)

        // 2. DWRE Active Session & Real-time Reminder Control Engine
        DwreControlAndActiveSessionCard(
            settings = dwreSettings,
            session = activeAppSession,
            onUpdateSettings = onUpdateDwreSettings,
            onSwitchApp = onSwitchAppSession,
            onSimulateTime = onSimulateAddSessionTime,
            onTriggerTestNotif = onTriggerTestNotification
        )

        // 3. DWRE Notification History & Tray Activity Log
        DwreNotificationHistoryCard(
            notifications = dwreNotifications,
            onTriggerTest = onTriggerTestNotification
        )

        // 4. NETRA Platform Production Hardening Roadmap Status
        PlatformHardeningStatusCard()

        // 4. Permission Warning if Usage Access is Denied
        if (!digitalMetrics.isUsageAccessGranted) {
            UsageAccessPermissionBanner(
                onGrantClick = {
                    try {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            )
        }

        // 5. DWRE Daily Summary & Break Analysis
        DwreDailySummaryCard(summary = dwreDailySummary)

        // 6. Screen Statistics Grid
        ScreenStatisticsGridCard(metrics = digitalMetrics)

        // 7. DWRE Settings & Engine Configuration
        DwreSettingsPanelCard(
            settings = dwreSettings,
            onUpdateSettings = onUpdateDwreSettings
        )

        // 8. Productivity Analysis & Digital Balance Breakdown
        DigitalBalanceBreakdownCard(metrics = digitalMetrics)

        // 9. Application Usage & Categories
        ApplicationUsageListCard(
            apps = appUsageList,
            isGranted = digitalMetrics.isUsageAccessGranted
        )

        // 10. Focus Sessions Metric
        FocusSessionsCard(metrics = digitalMetrics)

        // 11. Battery & Thermal Correlation
        BatteryCorrelationCard(metrics = digitalMetrics)

        // 12. AI Insights Card
        AiWellnessInsightsCard(metrics = digitalMetrics)

        // 13. Combined Physical & Digital Timeline
        CombinedHealthTimelineCard(timelineItems = combinedTimeline)

        // 14. Privacy & Local Storage Guarantee
        PrivacyGuaranteeCard()
    }
}

@Composable
private fun DigitalWellnessScoreCard(metrics: DigitalWellnessMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("digital_wellness_score_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BentoGreenPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhonelinkSetup,
                            contentDescription = null,
                            tint = BentoGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DIGITAL WELLNESS SCORE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoGreenPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = metrics.wellnessStatusLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BentoGreenPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${metrics.digitalWellnessScore}/100",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BentoGreenPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Score Progress Bar
            LinearProgressIndicator(
                progress = { metrics.digitalWellnessScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BentoGreenPrimary,
                trackColor = BentoBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Behavioral digital activity index based on session lengths, screen breaks, and focus time ratios.",
                fontSize = 11.sp,
                color = BentoTextMuted
            )
        }
    }
}

@Composable
private fun UsageAccessPermissionBanner(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usage_access_permission_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF332000)),
        border = BorderStroke(1.dp, Color(0xFFFFB000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB000),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Usage Access Permission Required",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB000)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Application usage unavailable. Grant Usage Access to enable automatic app category stats, foreground session tracking, and productivity reports.",
                fontSize = 12.sp,
                color = BentoTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Grant Usage Access", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScreenStatisticsGridCard(metrics: DigitalWellnessMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("screen_statistics_grid_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SCREEN STATISTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoGreenPrimary,
                letterSpacing = 1.sp
            )

            // Primary Total Screen Time Header Stat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BentoBackground, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Total Screen Time Today", fontSize = 12.sp, color = BentoTextMuted)
                    Text(
                        text = formatDurationHoursMin(metrics.totalScreenTimeSec),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = BentoGreenPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Grid Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Screen ON Count",
                    value = "${metrics.screenOnCount} times",
                    icon = Icons.Default.Visibility
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Screen OFF Count",
                    value = "${metrics.screenOffCount} times",
                    icon = Icons.Default.VisibilityOff
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Screen Unlocks",
                    value = "${metrics.screenUnlockCount} unlocks",
                    icon = Icons.Default.LockOpen
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Screen Locks",
                    value = "${metrics.screenLockCount} locks",
                    icon = Icons.Default.Lock
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Longest Continuous",
                    value = formatDurationHoursMin(metrics.longestContinuousSessionSec),
                    icon = Icons.Default.Timer
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Average Session",
                    value = "${metrics.avgSessionDurationSec / 60} min",
                    icon = Icons.Default.Timelapse
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "First Screen ON",
                    value = metrics.firstScreenOnTime,
                    icon = Icons.Default.WbSunny
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Last Screen OFF",
                    value = metrics.lastScreenOffTime,
                    icon = Icons.Default.NightsStay
                )
            }
        }
    }
}

@Composable
private fun MetricMiniTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = BentoBackground
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BentoGreenPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, fontSize = 10.sp, color = BentoTextMuted)
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
            }
        }
    }
}

@Composable
private fun DigitalBalanceBreakdownCard(metrics: DigitalWellnessMetrics) {
    val total = maxOf(metrics.totalScreenTimeSec, 1L)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("digital_balance_breakdown_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "DIGITAL BALANCE & CATEGORY REPORT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoGreenPrimary,
                letterSpacing = 1.sp
            )

            CategoryProgressItem(
                categoryName = "Productive & Education",
                durationSec = metrics.productiveAppTimeSec,
                totalSec = total,
                color = BentoGreenPrimary,
                icon = Icons.Default.CheckCircle
            )

            CategoryProgressItem(
                categoryName = "Communication & Messengers",
                durationSec = metrics.communicationTimeSec,
                totalSec = total,
                color = Color(0xFF2196F3),
                icon = Icons.Default.Chat
            )

            CategoryProgressItem(
                categoryName = "Entertainment & Media",
                durationSec = metrics.entertainmentTimeSec,
                totalSec = total,
                color = Color(0xFFFF9800),
                icon = Icons.Default.Movie
            )

            CategoryProgressItem(
                categoryName = "Social Media",
                durationSec = metrics.socialMediaTimeSec,
                totalSec = total,
                color = Color(0xFFE91E63),
                icon = Icons.Default.People
            )

            CategoryProgressItem(
                categoryName = "Gaming",
                durationSec = metrics.gamingTimeSec,
                totalSec = total,
                color = Color(0xFF9C27B0),
                icon = Icons.Default.SportsEsports
            )

            CategoryProgressItem(
                categoryName = "Idle Screen & Others",
                durationSec = metrics.idleScreenTimeSec,
                totalSec = total,
                color = BentoTextMuted,
                icon = Icons.Default.HourglassEmpty
            )
        }
    }
}

@Composable
private fun CategoryProgressItem(
    categoryName: String,
    durationSec: Long,
    totalSec: Long,
    color: Color,
    icon: ImageVector
) {
    val pct = (durationSec.toFloat() / totalSec).coerceIn(0f, 1f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = categoryName, fontSize = 12.sp, color = BentoTextPrimary)
            }
            Text(
                text = "${formatDurationHoursMin(durationSec)} (${(pct * 100).toInt()}%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTextSecondary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = BentoBackground
        )
    }
}

@Composable
private fun ApplicationUsageListCard(
    apps: List<AppUsageInfo>,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("application_usage_list_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "APPLICATION USAGE STATISTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGreenPrimary,
                    letterSpacing = 1.sp
                )
                if (!isGranted) {
                    Text(
                        text = "Sample Mode",
                        fontSize = 10.sp,
                        color = Color(0xFFFFB000),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (apps.isEmpty()) {
                Text(
                    text = "No application statistics logged.",
                    fontSize = 12.sp,
                    color = BentoTextMuted
                )
            } else {
                apps.take(6).forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BentoBackground, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (app.category.isProductive) BentoGreenPrimary.copy(alpha = 0.2f)
                                        else BentoTextMuted.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (app.category.isProductive) Icons.Default.Work else Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = if (app.category.isProductive) BentoGreenPrimary else BentoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = app.appName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                                Text(
                                    text = "${app.category.displayName} • ${app.openCount} opens",
                                    fontSize = 10.sp,
                                    color = BentoTextMuted
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BentoCardBg
                        ) {
                            Text(
                                text = formatDurationHoursMin(app.foregroundDurationSec),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoGreenPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusSessionsCard(metrics: DigitalWellnessMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("focus_sessions_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = BentoGreenPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "UNINTERRUPTED FOCUS SESSIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGreenPrimary,
                    letterSpacing = 1.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Total Focus Time",
                    value = formatDurationHoursMin(metrics.totalFocusTimeSec),
                    icon = Icons.Default.Timer
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Longest Focus Streak",
                    value = formatDurationHoursMin(metrics.longestFocusSessionSec),
                    icon = Icons.Default.Bolt
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Focus Sessions",
                    value = "${metrics.focusSessionCount} sessions",
                    icon = Icons.Default.CenterFocusStrong
                )
            }
        }
    }
}

@Composable
private fun BatteryCorrelationCard(metrics: DigitalWellnessMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_correlation_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = BentoGreenPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BATTERY & THERMAL CORRELATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGreenPrimary,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = metrics.batteryInsight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Screen Drain Est.",
                    value = "%.1f%%".format(metrics.screenBatteryConsumptionPct),
                    icon = Icons.Default.BatteryAlert
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Avg Screen-On Temp",
                    value = "%.1f°C".format(metrics.avgThermalDuringScreenOnC),
                    icon = Icons.Default.Thermostat
                )
            }
        }
    }
}

@Composable
private fun AiWellnessInsightsCard(metrics: DigitalWellnessMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_wellness_insights_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = BentoGreenPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI DIGITAL WELLNESS INSIGHTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGreenPrimary,
                    letterSpacing = 1.sp
                )
            }

            InsightBullet(text = "Screen time is consistent with your daily 4-hour target (+12m vs yesterday).")
            InsightBullet(text = "Peak screen activity detected in the evening (18:30 - 20:45).")
            InsightBullet(text = "Physical walking and active screen usage overlapped for 18 minutes.")
            InsightBullet(text = "Longest continuous screen session was 45 min — regular screen breaks recommended.")
        }
    }
}

@Composable
private fun InsightBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text = "• ", color = BentoGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = text, fontSize = 12.sp, color = BentoTextSecondary)
    }
}

@Composable
private fun CombinedHealthTimelineCard(timelineItems: List<CombinedHealthTimelineItem>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("combined_health_timeline_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "COMBINED PHYSICAL & DIGITAL TIMELINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoGreenPrimary,
                letterSpacing = 1.sp
            )

            if (timelineItems.isEmpty()) {
                Text(text = "No timeline activity logged.", fontSize = 12.sp, color = BentoTextMuted)
            } else {
                timelineItems.take(8).forEach { item ->
                    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BentoBackground, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (item.category == "PHYSICAL ACTIVITY") BentoGreenPrimary.copy(alpha = 0.2f)
                                        else Color(0xFF2196F3).copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (item.iconType) {
                                        "WALKING" -> Icons.Default.DirectionsWalk
                                        "VEHICLE", "DRIVING" -> Icons.Default.DirectionsCar
                                        "SCREEN_ON" -> Icons.Default.Visibility
                                        "SCREEN_OFF" -> Icons.Default.VisibilityOff
                                        "UNLOCKED" -> Icons.Default.LockOpen
                                        else -> Icons.Default.Smartphone
                                    },
                                    contentDescription = null,
                                    tint = if (item.category == "PHYSICAL ACTIVITY") BentoGreenPrimary else Color(0xFF2196F3),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                                Text(
                                    text = "${item.category} • ${item.durationOrDetail}",
                                    fontSize = 10.sp,
                                    color = BentoTextMuted
                                )
                            }
                        }

                        Text(
                            text = timeStr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyGuaranteeCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("privacy_guarantee_card"),
        shape = RoundedCornerShape(12.dp),
        color = BentoBackground
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = BentoGreenPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Privacy Guarantee: All digital wellness & usage history remains stored strictly on-device. No app contents, messages, or notifications are monitored or collected.",
                fontSize = 10.sp,
                color = BentoTextMuted
            )
        }
    }
}

private fun formatDurationHoursMin(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun DwreControlAndActiveSessionCard(
    settings: DwreSettings,
    session: AppSessionTracker,
    onUpdateSettings: (DwreSettings) -> Unit,
    onSwitchApp: (String, String, AppCategory) -> Unit,
    onSimulateTime: (Long) -> Unit,
    onTriggerTestNotif: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dwre_control_active_session_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = BorderStroke(1.dp, BentoGreenPrimary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: DWRE Engine Status & Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BentoGreenPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = BentoGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "UNIVERSAL SCREEN TIME REMINDER ENGINE v2.0",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoGreenPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (settings.isEnabled) "Default ON • Zero-Config Foreground Monitor" else "Engine Suspended",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    }
                }

                Switch(
                    checked = settings.isEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(isEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = BentoGreenPrimary
                    )
                )
            }

            // Status Banner & Key Architectural Guarantees
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    !settings.isEnabled -> BentoBackground
                    session.isPaused -> Color(0xFF332000)
                    else -> BentoGreenPrimary.copy(alpha = 0.12f)
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        !settings.isEnabled -> BentoTextMuted.copy(alpha = 0.3f)
                        session.isPaused -> Color(0xFFFFB000)
                        else -> BentoGreenPrimary.copy(alpha = 0.4f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                !settings.isEnabled -> Icons.Default.PauseCircle
                                session.isPaused -> Icons.Default.Pause
                                else -> Icons.Default.PlayCircle
                            },
                            contentDescription = null,
                            tint = if (settings.isEnabled && !session.isPaused) BentoGreenPrimary else Color(0xFFFFB000),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !settings.isEnabled -> "ENGINE DISABLED"
                                session.isPaused -> "PAUSED: ${session.pauseReason ?: "Break Active"}"
                                else -> "MONITORING ALL APPS • Silent Tray Mode • 45s Grace Period"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.isEnabled && !session.isPaused) BentoGreenPrimary else Color(0xFFFFB000)
                        )
                    }

                    Text(
                        text = "Zero Overhead",
                        fontSize = 10.sp,
                        color = BentoTextMuted
                    )
                }
            }

            // Active App Session Focus Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = BentoBackground
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CURRENT ACTIVE APP FOCUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextMuted
                            )
                            Text(
                                text = session.appName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BentoTextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (session.isPaused) Color(0xFFFF9800).copy(alpha = 0.2f)
                            else if (session.category.isProductive) BentoGreenPrimary.copy(alpha = 0.2f)
                            else Color(0xFF2196F3).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (session.isPaused) "EXCLUDED / PAUSED" else session.category.displayName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (session.isPaused) Color(0xFFFF9800)
                                else if (session.category.isProductive) BentoGreenPrimary
                                else Color(0xFF2196F3),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(text = "Continuous Session Time", fontSize = 11.sp, color = BentoTextMuted)
                            Text(
                                text = if (session.isPaused && session.pauseReason?.contains("Exclusion") == true) "0 Minutes (Excluded)"
                                else formatDwreDuration(session.continuousDurationSec),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (session.isPaused) BentoTextMuted else BentoGreenPrimary
                            )
                        }

                        Text(
                            text = "Reminders Sent: ${session.totalRemindersSent}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary
                        )
                    }

                    // Milestone Progress Bar (15 min interval = 900s)
                    val baseIntervalSec = settings.baseIntervalMinutes * 60L
                    val currentMilestoneSec = maxOf(baseIntervalSec, ((session.continuousDurationSec / baseIntervalSec) + 1) * baseIntervalSec)
                    val progressInMilestone = (session.continuousDurationSec % baseIntervalSec).toFloat() / baseIntervalSec.toFloat()

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Next Reminder Window (${currentMilestoneSec / 60}m)",
                                fontSize = 10.sp,
                                color = BentoTextMuted
                            )
                            Text(
                                text = if (session.isPaused) "Bypassed" else "${((1f - progressInMilestone) * (baseIntervalSec / 60)).toInt()} min remaining",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (session.isPaused) BentoTextMuted else BentoGreenPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (session.isPaused) 0f else progressInMilestone },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BentoGreenPrimary,
                            trackColor = BentoCardBg
                        )
                    }
                }
            }

            // Interactive App Switcher Pills (Including Intelligent Exclusion Test)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SIMULATE FOREGROUND APP SWITCHING:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppSelectorChip(
                        appName = "Instagram",
                        isSelected = session.appName == "Instagram",
                        onClick = { onSwitchApp("com.instagram.android", "Instagram", AppCategory.SOCIAL_MEDIA) },
                        modifier = Modifier.weight(1f)
                    )
                    AppSelectorChip(
                        appName = "YouTube",
                        isSelected = session.appName == "YouTube",
                        onClick = { onSwitchApp("com.google.android.youtube", "YouTube", AppCategory.ENTERTAINMENT) },
                        modifier = Modifier.weight(1f)
                    )
                    AppSelectorChip(
                        appName = "Docs",
                        isSelected = session.appName == "Google Docs",
                        onClick = { onSwitchApp("com.google.android.apps.docs", "Google Docs", AppCategory.PRODUCTIVITY) },
                        modifier = Modifier.weight(1f)
                    )
                    AppSelectorChip(
                        appName = "Phone (Dialer)",
                        isSelected = session.appName == "Phone Dialer",
                        onClick = { onSwitchApp("com.android.dialer", "Phone Dialer", AppCategory.COMMUNICATION) },
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }

            // Intelligent Exclusion Badge List
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = BentoBackground,
                border = BorderStroke(1.dp, BentoTextMuted.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = BentoGreenPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AUTOMATIC INTELLIGENT EXCLUSIONS (System Handled):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoGreenPrimary
                        )
                    }
                    Text(
                        text = "• Emergency Dialer • In-Call UI • System Setup • Keyguard/Biometrics • Lockscreen • Netra Hub",
                        fontSize = 10.sp,
                        color = BentoTextSecondary
                    )
                }
            }

            // Fast Simulator Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSimulateTime(900L) }, // +15 min
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BentoGreenPrimary)
                ) {
                    Text("+15 Min", fontSize = 11.sp, color = BentoGreenPrimary, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onSimulateTime(1800L) }, // +30 min
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BentoGreenPrimary)
                ) {
                    Text("+30 Min", fontSize = 11.sp, color = BentoGreenPrimary, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTriggerTestNotif,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Tray Alert", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectorChip(
    appName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) BentoGreenPrimary else BentoBackground,
        border = BorderStroke(1.dp, if (isSelected) BentoGreenPrimary else BentoTextMuted.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else BentoTextPrimary
            )
        }
    }
}

@Composable
private fun DwreNotificationHistoryCard(
    notifications: List<DwreNotificationEvent>,
    onTriggerTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dwre_notification_history_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ListAlt,
                        contentDescription = null,
                        tint = BentoGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DWRE NOTIFICATION TRAY HISTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoGreenPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BentoGreenPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${notifications.size} Logged",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoGreenPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (notifications.isEmpty()) {
                Text(
                    text = "No notification tray reminders posted yet. Use '+15 Min' or 'Test Tray Alert' above to simulate.",
                    fontSize = 12.sp,
                    color = BentoTextMuted
                )
            } else {
                notifications.take(5).forEach { notif ->
                    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notif.timestamp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = BentoBackground
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (notif.isFocusProtectionTone) Icons.Default.School else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = if (notif.isFocusProtectionTone) BentoGreenPrimary else Color(0xFFFF9800),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = notif.appName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BentoTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${notif.formattedDuration}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BentoGreenPrimary
                                    )
                                }

                                Text(text = timeStr, fontSize = 10.sp, color = BentoTextMuted)
                            }

                            Text(
                                text = notif.messageText,
                                fontSize = 11.sp,
                                color = BentoTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DwreDailySummaryCard(summary: DwreDailySummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dwre_daily_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "DAILY DIGITAL WELLNESS SUMMARY & BREAKS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoGreenPrimary,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Longest Continuous Session",
                    value = formatDwreDuration(summary.longestAppSessionSec),
                    icon = Icons.Default.HourglassFull
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Breaks Detected (>2m)",
                    value = "${summary.totalBreaksDetected} breaks",
                    icon = Icons.Default.FreeBreakfast
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Most Used App",
                    value = summary.mostUsedApp,
                    icon = Icons.Default.Star
                )
                MetricMiniTile(
                    modifier = Modifier.weight(1f),
                    title = "Adaptive Window Triggers",
                    value = "${summary.adaptiveIntervalTriggeredCount} times",
                    icon = Icons.Default.AutoMode
                )
            }
        }
    }
}

@Composable
private fun DwreSettingsPanelCard(
    settings: DwreSettings,
    onUpdateSettings: (DwreSettings) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dwre_settings_panel_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "DWRE v2.0 REFINEMENTS & POLICY CONFIGURATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoGreenPrimary,
                letterSpacing = 1.sp
            )

            SettingSwitchRow(
                title = "Zero-Configuration (Monitor All Apps)",
                subtitle = "Automatically track all foreground application continuous sessions without manual per-app setup.",
                checked = settings.isEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(isEnabled = it)) }
            )

            SettingSwitchRow(
                title = "Smart Grace Period (45 Seconds)",
                subtitle = "Prevent continuous session resets when user quickly peeks at recent apps or task-switches < 45s.",
                checked = settings.gracePeriodSeconds == 45,
                onCheckedChange = { onUpdateSettings(settings.copy(gracePeriodSeconds = if (it) 45 else 0)) }
            )

            SettingSwitchRow(
                title = "Silent Notification Tray Only",
                subtitle = "Zero vibration, zero audio ring, zero full-screen popups. Non-intrusive notification bar awareness.",
                checked = settings.silentNotificationsOnly,
                onCheckedChange = { onUpdateSettings(settings.copy(silentNotificationsOnly = it)) }
            )

            SettingSwitchRow(
                title = "Driving & Emergency Suppression",
                subtitle = "Automatically suppress DWRE reminders during high-speed driving or active security alerts.",
                checked = settings.drivingProtectionEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(drivingProtectionEnabled = it)) }
            )

            SettingSwitchRow(
                title = "Adaptive Reminder Progression",
                subtitle = "15m → 30m → 45m → 1h → 1h 30m → 2h... Continuous session milestone formatting.",
                checked = settings.adaptiveIntervalEnabled,
                onCheckedChange = { onUpdateSettings(settings.copy(adaptiveIntervalEnabled = it)) }
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
            Text(text = subtitle, fontSize = 10.sp, color = BentoTextMuted)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = BentoGreenPrimary
            )
        )
    }
}

@Composable
private fun PlatformHardeningStatusCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("platform_hardening_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = BorderStroke(1.dp, BentoGreenPrimary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BentoGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NETRA PLATFORM HARDENING (10 PHASES)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoGreenPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BentoGreenPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Phases 1-10 Integrated",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoGreenPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "• Phase 1: EngineCoordinator & EventBus Foundation\n" +
                        "• Phase 2: Unified Activity Recognition (Walk, Run, Stand, Drive)\n" +
                        "• Phase 3: Global Power Budget Manager & Adaptive Sensor Sampling\n" +
                        "• Phase 4: Netra AI Fusion Insight Engine & Verified Correlations\n" +
                        "• Phase 5: Event-Driven Usage & Motion Callbacks\n" +
                        "• Phase 6: Crash Isolation & Recovery Coordinator\n" +
                        "• Phase 7: Performance, Privacy & Local Encryption Guarantees\n" +
                        "• Phase 8: Netra Simulator Framework & Scenario Suite\n" +
                        "• Phase 9: Automated Release Quality Benchmarks\n" +
                        "• Phase 10: Production Hardening & Final Validation",
                fontSize = 11.sp,
                color = BentoTextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

