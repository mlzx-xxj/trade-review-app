package com.tradereview.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.math.BigDecimal

data class PnlColors(val profit: Color, val loss: Color)
val LocalPnlColors = staticCompositionLocalOf { PnlColors(Color(0xFF086B54), Color(0xFFA82D43)) }
val ProfitColor: Color @Composable get() = LocalPnlColors.current.profit
val LossColor: Color @Composable get() = LocalPnlColors.current.loss

@Composable
fun paletteColors(palette: String): PnlColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val green = if (dark) Color(0xFF80E3BF) else Color(0xFF086B54)
    val red = if (dark) Color(0xFFFFA6B2) else Color(0xFFA82D43)
    return if (palette == "red_profit") PnlColors(red, green) else PnlColors(green, red)
}

@Composable
fun pnlColor(amount: BigDecimal): Color = when (amount.signum()) { 1 -> ProfitColor; -1 -> LossColor; else -> MaterialTheme.colorScheme.onSurfaceVariant }

@Composable
fun TradeReviewTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
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
    MaterialTheme(colorScheme = if (darkTheme) dark else light, content = content)
}
