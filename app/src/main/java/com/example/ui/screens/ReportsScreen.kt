package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import java.io.File
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.SafetyEventEntity
import com.example.data.model.SafetyRiskLevel
import com.example.ui.components.SafetyReportDialog
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    eventLogs: List<SafetyEventEntity>,
    onClearLogs: () -> Unit,
    onExportTxt: ((Long, (Boolean, String) -> Unit) -> Unit),
    onExportCsv: ((Long, (Boolean, String) -> Unit) -> Unit),
    onExportJson: ((Long, (Boolean, String) -> Unit) -> Unit),
    onSync: (Boolean) -> Unit,
    onShareFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var exportFeedback by remember { mutableStateOf<String?>(null) }
    var selectedEventForDialog by remember { mutableStateOf<SafetyEventEntity?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var visibleLogsCount by remember { mutableStateOf(10) }
    var pendingExportAction by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                    pendingExportAction?.invoke(selectedDate ?: System.currentTimeMillis())
                    visibleLogsCount = 10 // Reset pagination
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val filterOptions = listOf("All", "Emergency", "Warning", "Sensor", "AI", "Driving", "Safety", "Background", "Charging", "Bluetooth", "Notification", "Announcement")

    val filteredLogsByDate = eventLogs.filter { log ->
        selectedDate?.let { sdf.format(Date(it)) == sdf.format(Date(log.timestamp)) } ?: true
    }

    val filteredLogs = filteredLogsByDate.filter { log ->
        when (selectedFilter) {
            "All" -> true
            "Emergency" -> log.riskLevel.equals("EMERGENCY", ignoreCase = true) || log.severity.equals("CRITICAL", ignoreCase = true)
            "Warning" -> log.riskLevel.equals("WARNING", ignoreCase = true) || log.severity.equals("WARNING", ignoreCase = true)
            else -> log.moduleName.contains(selectedFilter, ignoreCase = true) || log.eventType.contains(selectedFilter, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "UNIVERSAL EVENT LOG DATABASE",
                        color = BentoGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "SELECTED DATE: ${selectedDate?.let { sdf.format(Date(it)) } ?: "ALL"}",
                        color = BentoGreenPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 4.dp).clickable { showDatePicker = true }
                    )
                    Text(
                        text = "Activity Timeline & Reports",
                        color = BentoTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                if (eventLogs.isNotEmpty()) {
                    IconButton(onClick = onClearLogs, modifier = Modifier.testTag("clear_logs_button")) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Logs", tint = BentoRed)
                    }
                }
            }
        }

        // Export Actions Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            pendingExportAction = { date -> onExportTxt(date) { success, msg -> exportFeedback = msg } }
                            showDatePicker = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoHeroCardBg, contentColor = BentoGreenPrimary),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).testTag("export_txt_button")
                    ) {
                        Text("Export TXT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            pendingExportAction = { date -> onExportCsv(date) { success, msg -> exportFeedback = msg } }
                            showDatePicker = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoHeroCardBg, contentColor = BentoGreenPrimary),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).testTag("export_csv_button")
                    ) {
                        Text("Export CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            pendingExportAction = { date -> onExportJson(date) { success, msg -> exportFeedback = msg } }
                            showDatePicker = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoHeroCardBg, contentColor = BentoGreenPrimary),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).testTag("export_json_button")
                    ) {
                        Text("Export JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Sync Engine Action
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(BentoHeroCardBg)
                        .combinedClickable(
                            onClick = { onSync(false) },
                            onLongClick = { onSync(true) }
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = BentoGreenPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manual Data Sync (Long press for extended)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BentoGreenPrimary)
                    }
                }

                exportFeedback?.let { feedback ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoCardBg)
                            .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(text = feedback, color = BentoGreenPrimary, fontSize = 12.sp)
                    }
                }
            }
        }


        // Event List Cards
        items(filteredLogs.take(visibleLogsCount), key = { it.id }) { event ->
            EventLogCard(
                event = event,
                onClick = { selectedEventForDialog = event }
            )
        }

        // "See All" Progressive Loading
        if (visibleLogsCount < filteredLogs.size) {
            item {
                Button(
                    onClick = { visibleLogsCount += 10 },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoCardBg, contentColor = BentoGreenPrimary)
                ) {
                    Text("See All")
                }
            }
        } else if (filteredLogs.isNotEmpty()) {
            item {
                Text(
                    text = "✔ All records loaded",
                    color = BentoTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Report Dialog
    selectedEventForDialog?.let { event ->
        SafetyReportDialog(
            event = event,
            onDismiss = { selectedEventForDialog = null }
        )
    }
}

@Composable
fun EventLogCard(
    event: SafetyEventEntity,
    onClick: () -> Unit
) {
    val levelColor = when (event.riskLevel.uppercase()) {
        "EMERGENCY" -> BentoRed
        "WARNING" -> Color(0xFFFF9100)
        "ATTENTION" -> BentoAmber
        else -> BentoGreenVibrant
    }

    val formattedTime = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(16.dp)
            .testTag("event_log_card_${event.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BentoHeroCardBg)
                    .padding(10.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = BentoGreenPrimary)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.riskLevel,
                        color = levelColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        color = BentoTextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = event.title,
                    color = BentoTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = event.description,
                    color = BentoTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = BentoTextMuted)
        }
    }
}
