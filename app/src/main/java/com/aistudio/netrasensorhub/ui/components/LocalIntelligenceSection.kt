package com.aistudio.netrasensorhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.netrasensorhub.data.intelligence.models.*
import com.aistudio.netrasensorhub.data.intelligence.sync.LocalIntelligenceSnapshot
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LocalIntelligenceSection(
    snapshot: LocalIntelligenceSnapshot,
    onRequestLocationPermission: () -> Unit,
    onManualRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF0D1117) else Color(0xFFF6F8FA)
    val borderColor = if (isDark) Color(0xFF30363D) else Color(0xFFD0D7DE)
    val textColor = if (isDark) Color(0xFFC9D1D9) else Color(0xFF24292F)
    val mutedColor = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A)

    val timeFormatter = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location & Disaster Intelligence",
                    tint = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "LOCAL DISASTER & WEATHER INTELLIGENCE",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            IconButton(
                onClick = onManualRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Intelligence",
                    tint = if (snapshot.isRefreshing) Color(0xFF58A6FF) else mutedColor
                )
            }
        }

        // 1. Current Verified Location Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT MONITORING CENTER",
                            color = mutedColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val loc = snapshot.location
                        if (loc != null) {
                            Text(
                                text = "${loc.city ?: "Local Region"}, ${loc.state ?: loc.country ?: ""}",
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Coordinates: %.4f° N, %.4f° E (Provider: %s)".format(loc.latitude, loc.longitude, loc.provider.uppercase()),
                                color = mutedColor,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                text = when (snapshot.locationStatus) {
                                    LocationStatus.PERMISSION_REQUIRED -> "Location Permission Required"
                                    LocationStatus.ACQUIRING -> "Acquiring GPS / Network Fix (~5s window)..."
                                    LocationStatus.UNAVAILABLE -> "Location Fix Unavailable (GPS / Network Off)"
                                    LocationStatus.VERIFIED -> "Position Verified"
                                },
                                color = if (snapshot.locationStatus == LocationStatus.PERMISSION_REQUIRED) Color(0xFFDA3633) else textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Status Badge
                    val (badgeText, badgeBg, badgeColor) = when (snapshot.locationStatus) {
                        LocationStatus.VERIFIED -> Triple("VERIFIED", if (isDark) Color(0xFF033A16) else Color(0xFFDAFBE1), Color(0xFF3FB950))
                        LocationStatus.ACQUIRING -> Triple("ACQUIRING", if (isDark) Color(0xFF3B2300) else Color(0xFFFFF8C5), Color(0xFFD29922))
                        LocationStatus.PERMISSION_REQUIRED -> Triple("NO ACCESS", if (isDark) Color(0xFF490202) else Color(0xFFFFEBE9), Color(0xFFDA3633))
                        LocationStatus.UNAVAILABLE -> Triple("UNAVAILABLE", if (isDark) Color(0xFF21262D) else Color(0xFFEAEEF2), mutedColor)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (snapshot.locationStatus == LocationStatus.PERMISSION_REQUIRED) {
                    Button(
                        onClick = onRequestLocationPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF1F6FEB) else Color(0xFF0969DA)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Grant Location Permission for Local Intelligence", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (snapshot.location != null) {
                    val loc = snapshot.location
                    Divider(color = borderColor, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IntelligenceStat(label = "Accuracy", value = "±%.0f m".format(loc.accuracy), color = textColor, isDark = isDark)
                        IntelligenceStat(label = "Confidence", value = "%.0f%%".format(loc.locationConfidence * 100), color = if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37), isDark = isDark)
                        IntelligenceStat(label = "Motion", value = snapshot.motionState.name, color = textColor, isDark = isDark)
                        IntelligenceStat(label = "Updated", value = timeFormatter.format(Date(loc.timestamp)), color = mutedColor, isDark = isDark)
                    }
                }
            }
        }

        // 2. Dynamic Nearby Impact Area (Geo-Radius)
        if (snapshot.nearbyImpactAreas.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DYNAMIC SURROUNDING IMPACT ZONES",
                            color = mutedColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Geo-Radius 100 km",
                            color = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        snapshot.nearbyImpactAreas.take(3).forEach { area ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF161B22) else Color(0xFFEAEEF2))
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = area.name,
                                        color = textColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${area.direction} • ${area.distanceKm.toInt()} km",
                                        color = mutedColor,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Real-Time Verified Local Weather Intelligence
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA), modifier = Modifier.size(18.dp))
                        Text(
                            text = "METEOROLOGICAL TELEMETRY",
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    val (weatherBadgeText, weatherBadgeColor) = when (snapshot.weatherStatus) {
                        WeatherStatus.LIVE -> "LIVE (OPEN-METEO)" to (if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37))
                        WeatherStatus.CACHED -> "CACHED" to Color(0xFFD29922)
                        WeatherStatus.FETCHING -> "SYNCING..." to (if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA))
                        WeatherStatus.NETWORK_ERROR, WeatherStatus.UNAVAILABLE -> "NETWORK OFFLINE" to mutedColor
                    }

                    Text(
                        text = weatherBadgeText,
                        color = weatherBadgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val weather = snapshot.weather
                if (weather != null && weather.temperatureC != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "%.1f°C".format(weather.temperatureC),
                                color = textColor,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${weather.weatherCondition ?: "Clear"} • Feels like %.1f°C".format(weather.apparentTemperatureC ?: weather.temperatureC),
                                color = mutedColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Wind: %.1f km/h".format(weather.windSpeedKmh ?: 0f),
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Humidity: %.0f%% • Precip: %.1f mm".format(weather.relativeHumidity ?: 0f, weather.precipitationMm ?: 0f),
                                color = mutedColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Divider(color = borderColor, thickness = 0.5.dp)

                    Text(
                        text = "Last Updated: %s (Authoritative Feed: %s)".format(timeFormatter.format(Date(weather.lastUpdatedMillis)), weather.source),
                        color = mutedColor,
                        fontSize = 10.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (snapshot.weatherStatus == WeatherStatus.FETCHING) "Synchronizing authoritative meteorological data..." else "Meteorological telemetry unavailable. Awaiting location/network connection.",
                            color = mutedColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 4. Active Local Disaster & Seismic Alerts
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "LOCAL DISASTER & SEISMIC HAZARD ASSESSMENTS",
                color = mutedColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            if (snapshot.activeAlerts.isEmpty() && snapshot.seismicEvents.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF3FB950), modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = "All Monitored Regional Zones Stable",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "No active severe weather anomalies or significant seismic hazards within local impact radius.",
                                color = mutedColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                snapshot.activeAlerts.forEach { alert ->
                    AlertItemCard(alert = alert, isDark = isDark, borderColor = borderColor, textColor = textColor, mutedColor = mutedColor)
                }

                // Recent Seismic Events List
                if (snapshot.seismicEvents.isNotEmpty()) {
                    snapshot.seismicEvents.take(2).forEach { event ->
                        SeismicItemCard(event = event, isDark = isDark, borderColor = borderColor, textColor = textColor, mutedColor = mutedColor)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertItemCard(
    alert: DisasterAlert,
    isDark: Boolean,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color
) {
    val (accentColor, bgTint) = when (alert.severity) {
        AlertSeverity.CRITICAL -> Color(0xFFDA3633) to (if (isDark) Color(0xFF3B1212) else Color(0xFFFFEBE9))
        AlertSeverity.WARNING -> Color(0xFFD29922) to (if (isDark) Color(0xFF3B2300) else Color(0xFFFFF8C5))
        AlertSeverity.WATCH -> Color(0xFF58A6FF) to (if (isDark) Color(0xFF0D2547) else Color(0xFFDDF4FF))
        AlertSeverity.INFO -> Color(0xFF8B949E) to (if (isDark) Color(0xFF161B22) else Color(0xFFF0F2F5))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgTint),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (alert.severity == AlertSeverity.CRITICAL) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = alert.title.uppercase(),
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = alert.severity.name,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = alert.description,
                color = textColor,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Source: ${alert.officialSource}",
                    color = mutedColor,
                    fontSize = 10.sp
                )
                Text(
                    text = "Event: ${alert.eventId}",
                    color = mutedColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun SeismicItemCard(
    event: SeismicEvent,
    isDark: Boolean,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF161B22) else Color(0xFFF6F8FA)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "M%.1f - %s".format(event.magnitude, event.place),
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%.0f km away".format(event.distanceKmFromCurrent),
                    color = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Depth: %.1f km • Official Confirmation: %s".format(event.depthKm, event.officialConfirmation),
                color = mutedColor,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun IntelligenceStat(label: String, value: String, color: Color, isDark: Boolean) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label.uppercase(),
            color = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
