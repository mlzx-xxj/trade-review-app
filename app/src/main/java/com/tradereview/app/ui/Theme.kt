package com.tradereview.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ProfitColor = Color(0xFF16846B)
val LossColor = Color(0xFFC34356)

@Composable
fun TradeReviewTheme(content: @Composable () -> Unit) {
    val light = lightColorScheme(
        primary = Color(0xFF155AAB), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEAFF), onPrimaryContainer = Color(0xFF123457),
        secondary = Color(0xFF16846B), background = Color(0xFFF5F7FB),
        surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDF1F7),
    )
    val dark = darkColorScheme(
        primary = Color(0xFFA8CCFF), primaryContainer = Color(0xFF193E69),
        secondary = Color(0xFF80E3BF), background = Color(0xFF111820),
        surface = Color(0xFF1B2530), surfaceVariant = Color(0xFF29333E),
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
}
