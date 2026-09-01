package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NetraBentoColorScheme = lightColorScheme(
    primary = BentoGreenPrimary,
    onPrimary = Color.White,
    secondary = BentoGreenVibrant,
    onSecondary = BentoTextPrimary,
    tertiary = BentoAmber,
    error = BentoRed,
    background = BentoBackground,
    onBackground = BentoTextPrimary,
    surface = BentoCardBg,
    onSurface = BentoTextPrimary,
    surfaceVariant = BentoHeroCardBg,
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoBorder
)

@Composable
fun NetraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NetraBentoColorScheme,
        typography = Typography,
        content = content
    )
}
