package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Universal Scrollable Interface Standard (USIS) v1.0 Container
 * Ensures a single vertical scroll behavior across all dashboard screens with standardized padding.
 */
@Composable
fun NetraScrollContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = {
            content()
            Spacer(modifier = Modifier.height(32.dp))
        }
    )
}

/**
 * Centralized EmptyStateProvider that replaces blank card areas with compact,
 * context-aware information messages when a section has no data.
 */
@Composable
fun EmptyStateProvider(
    message: String = "No activity or data recorded yet.",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = BorderStroke(1.dp, BentoBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = BentoTextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = BentoTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * DynamicCardManager enforces the 'Wrap Content' height rule across all UI cards,
 * ensuring they auto-expand or collapse based on real-time data input without fixed height constraints.
 */
@Composable
fun DynamicCardManager(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = BorderStroke(1.dp, BentoBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * VisibilityEngine utility that uses conditional rendering to ensure that widgets
 * or cards with null or empty data are completely removed from the composition tree,
 * preventing any reserved blank space.
 */
@Composable
fun <T> VisibilityEngine(
    data: T?,
    isEmpty: Boolean = false,
    emptyMessage: String? = null,
    content: @Composable (T) -> Unit
) {
    if (data != null && !isEmpty) {
        if (data is Collection<*> && data.isEmpty()) {
            if (emptyMessage != null) {
                EmptyStateProvider(message = emptyMessage)
            }
        } else {
            content(data)
        }
    } else {
        if (emptyMessage != null) {
            EmptyStateProvider(message = emptyMessage)
        }
    }
}

@Composable
fun VisibilityEngine(
    visible: Boolean,
    emptyMessage: String? = null,
    content: @Composable () -> Unit
) {
    if (visible) {
        content()
    } else if (emptyMessage != null) {
        EmptyStateProvider(message = emptyMessage)
    }
}
