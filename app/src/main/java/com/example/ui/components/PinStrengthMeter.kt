package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.util.PinStrength
import com.example.util.PinStrengthAnalyzer

@Composable
fun PinStrengthMeter(
    pin: String,
    modifier: Modifier = Modifier
) {
    val strength = PinStrengthAnalyzer.analyze(pin)
    val animatedColor by animateColorAsState(targetValue = strength.color, label = "color")

    // Define rules feedback in real-time
    val isLengthValid = pin.length in 4..6
    val isNotAllSame = pin.isNotEmpty() && !pin.all { it == pin[0] }
    
    var isNotSequential = true
    if (pin.length >= 4) {
        var isAscending = true
        var isDescending = true
        for (i in 0 until pin.length - 1) {
            val diff = pin[i + 1] - pin[i]
            if (diff != 1) isAscending = false
            if (diff != -1) isDescending = false
        }
        if (isAscending || isDescending) {
            isNotSequential = false
        }
    }

    var isNotRepeated = true
    if (pin.length == 4) {
        if (pin.substring(0, 2) == pin.substring(2, 4)) {
            isNotRepeated = false
        }
    } else if (pin.length == 6) {
        if (pin.substring(0, 3) == pin.substring(3, 6)) {
            isNotRepeated = false
        }
        if (pin.substring(0, 2) == pin.substring(2, 4) && pin.substring(2, 4) == pin.substring(4, 6)) {
            isNotRepeated = false
        }
    }

    val uniqueDigitsCount = pin.toSet().size
    val hasGoodDiversity = uniqueDigitsCount >= 3

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("pin_strength_meter"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Label & Status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PIN STRENGTH",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(animatedColor.copy(alpha = 0.15f))
                    .border(1.dp, animatedColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (pin.isEmpty()) "Empty" else strength.displayName.uppercase(),
                    color = animatedColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("strength_badge_text")
                )
            }
        }

        // Progress bar visual indicators (3-segments)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val fillCount = when {
                pin.isEmpty() -> 0
                strength == PinStrength.WEAK -> 1
                strength == PinStrength.MEDIUM -> 2
                else -> 3
            }

            for (i in 1..3) {
                val isActive = i <= fillCount
                val segmentColor = if (isActive) animatedColor else BentoBorder.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segmentColor)
                )
            }
        }

        // Real-time rules list
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            RuleRow(label = "4 to 6 numeric digits", isValid = isLengthValid, hasContent = pin.isNotEmpty())
            RuleRow(label = "Avoid sequential patterns (e.g. 1234)", isValid = isNotSequential, hasContent = pin.isNotEmpty())
            RuleRow(label = "Avoid repetitive sequences (e.g. 1111)", isValid = isNotAllSame && isNotRepeated, hasContent = pin.isNotEmpty())
            RuleRow(label = "Diverse digits (unique numbers)", isValid = hasGoodDiversity, hasContent = pin.isNotEmpty())
        }
    }
}

@Composable
private fun RuleRow(
    label: String,
    isValid: Boolean,
    hasContent: Boolean
) {
    val icon = if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning
    val iconColor = when {
        !hasContent -> BentoTextSecondary.copy(alpha = 0.4f)
        isValid -> BentoGreenPrimary
        else -> BentoRed
    }
    val textColor = when {
        !hasContent -> BentoTextSecondary.copy(alpha = 0.6f)
        isValid -> BentoTextPrimary
        else -> BentoRed.copy(alpha = 0.9f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (hasContent && !isValid) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
