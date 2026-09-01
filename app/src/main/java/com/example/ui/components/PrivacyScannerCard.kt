package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PrivacyScannerState
import com.example.ui.theme.*

@Composable
fun PrivacyScannerCard(
    state: PrivacyScannerState,
    onToggle: (Boolean) -> Unit,
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("privacy_scanner_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = BorderStroke(1.dp, BentoBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header...
            // (Implementation would be moved from DashboardScreen)
        }
    }
}
