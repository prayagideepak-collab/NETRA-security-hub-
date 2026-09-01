package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorCategory

import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun SensorsScreen(
    capabilities: List<SensorCapabilityInfo>,
    liveReadings: Map<String, RawSensorReading>,
    watchdogStates: Map<String, com.example.data.engine.WatchdogModuleState> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<SensorCategory?>(null) }
    var showUnsupported by remember { mutableStateOf(false) }
    var expandedSensorId by remember { mutableStateOf<String?>(null) }

    val filteredCapabilities = capabilities.filter { cap ->
        (showUnsupported || cap.isSupported) &&
                (selectedCategory == null || cap.category == selectedCategory) &&
                (searchQuery.isBlank() || cap.name.contains(searchQuery, ignoreCase = true) || cap.vendor.contains(searchQuery, ignoreCase = true))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("sensors_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header Title Block
        item {
            Column {
                Text(
                    text = "UNIVERSAL HARDWARE EXPLORER",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Hardware Detection & Waveforms",
                    color = BentoTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search hardware by name or vendor...", color = BentoTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = BentoTextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BentoGreenPrimary,
                    unfocusedBorderColor = BentoBorder,
                    focusedContainerColor = BentoCardBg,
                    unfocusedContainerColor = BentoCardBg,
                    focusedTextColor = BentoTextPrimary,
                    unfocusedTextColor = BentoTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sensor_search_field")
            )
        }

        // Category Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All (${capabilities.count { showUnsupported || it.isSupported }})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BentoHeroCardBg,
                            selectedLabelColor = BentoGreenPrimary,
                            containerColor = BentoCardBg,
                            labelColor = BentoTextSecondary
                        ),
                        shape = CircleShape
                    )
                }
                items(SensorCategory.entries.toTypedArray()) { category ->
                    val count = capabilities.count { (showUnsupported || it.isSupported) && it.category == category }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text("${category.displayName} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BentoHeroCardBg,
                            selectedLabelColor = BentoGreenPrimary,
                            containerColor = BentoCardBg,
                            labelColor = BentoTextSecondary
                        ),
                        shape = CircleShape
                    )
                }
            }
        }

        // Show Unsupported Hardware Toggle
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Show Unsupported System Hardware Specs",
                    color = BentoTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showUnsupported,
                    onCheckedChange = { showUnsupported = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BentoGreenPrimary,
                        checkedTrackColor = BentoHeroCardBg
                    )
                )
            }
        }

        // Sensor List Cards
        items(filteredCapabilities, key = { it.id }) { cap ->
            val liveData = liveReadings[cap.id] ?: liveReadings["sensor_${cap.type}"]
            val isExpanded = expandedSensorId == cap.id

            SensorCapabilityCard(
                cap = cap,
                liveData = liveData,
                isExpanded = isExpanded,
                watchdogStates = watchdogStates,
                onToggleExpand = { expandedSensorId = if (isExpanded) null else cap.id }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SensorCapabilityCard(
    cap: SensorCapabilityInfo,
    liveData: RawSensorReading?,
    isExpanded: Boolean,
    watchdogStates: Map<String, com.example.data.engine.WatchdogModuleState>,
    onToggleExpand: () -> Unit
) {
    val statusColor = if (cap.isSupported) BentoGreenVibrant else BentoRed

    val mappedModules = when (cap.category) {
        SensorCategory.POWER -> listOf("Battery", "Charging")
        SensorCategory.THERMAL -> listOf("Temperature")
        SensorCategory.LOCATION -> listOf("Driving")
        SensorCategory.ENVIRONMENTAL -> {
            when {
                cap.id.contains("sensor_2") || cap.type == 2 -> listOf("Magnetic Field")
                cap.id.contains("sensor_13") || cap.type == 13 -> listOf("Temperature")
                else -> emptyList()
            }
        }
        else -> emptyList()
    }
    val isRefreshing = mappedModules.any { module ->
        watchdogStates[module]?.status == "Refreshing" || watchdogStates[module]?.isRefreshing == true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BentoCardBg)
            .border(1.dp, if (isExpanded) BentoGreenPrimary else BentoBorder, RoundedCornerShape(24.dp))
            .clickable { onToggleExpand() }
            .padding(16.dp)
            .testTag("sensor_card_${cap.id}")
    ) {
        Column(
            modifier = Modifier.graphicsLayer { alpha = if (isRefreshing) 0.5f else 1f }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoHeroCardBg)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = BentoGreenPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cap.name,
                        color = BentoTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${cap.vendor} • ${cap.category.displayName}",
                        color = BentoTextMuted,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoHeroCardBg)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (cap.isSupported) "DETECTED" else "UNSUPPORTED",
                        color = if (cap.isSupported) BentoGreenPrimary else BentoRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = "Expand",
                    tint = BentoTextMuted
                )
            }

            // Live Values Bar (if available and supported)
            if (cap.isSupported && liveData != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val valuesText = liveData.values.joinToString(" | ") { "%.2f".format(it) }
                    Text(
                        text = "Live Stream: $valuesText ${liveData.unit}",
                        color = BentoGreenPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expanded View with Specs & Real-Time Waveform Chart
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BentoBorder)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "HARDWARE SPECIFICATIONS",
                        color = BentoGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SpecRow("Description", cap.description)
                        SpecRow("Max Range", "${cap.maxRange}")
                        SpecRow("Resolution", "${cap.resolution}")
                        SpecRow("Power Drain", "${cap.powerMa} mA")
                        SpecRow("Min Delay", "${cap.minDelayUs} µs")
                    }


                }
            }
        }

        if (isRefreshing) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoHeroCardBg.copy(alpha = 0.95f))
                        .border(1.dp, BentoGreenPrimary, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = BentoGreenPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WATCHDOG RECOVERING...",
                        color = BentoGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row {
        Text(text = "$label: ", color = BentoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = BentoTextSecondary, fontSize = 11.sp)
    }
}
