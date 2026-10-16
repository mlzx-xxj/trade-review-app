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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        primary = Color(0xFF304C75), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E9F7), onPrimaryContainer = Color(0xFF243E63),
        secondary = Color(0xFF526780), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4EAF2), onSecondaryContainer = Color(0xFF30465F),
        background = Color(0xFFF3F5F9), onBackground = Color(0xFF17243A),
        surface = Color.White, onSurface = Color(0xFF17243A),
        surfaceVariant = Color(0xFFE9EEF5), onSurfaceVariant = Color(0xFF53637B),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F8FC),
        surfaceContainer = Color(0xFFEEF2F8), surfaceContainerHigh = Color(0xFFE9EEF5),
        surfaceContainerHighest = Color(0xFFE3E9F2),
        outline = Color(0xFF7F8EA5), outlineVariant = Color(0xFFD7DFEA),
    )
    val dark = darkColorScheme(
        primary = Color(0xFFB4CBF7), onPrimary = Color(0xFF173158),
        primaryContainer = Color(0xFF263C5D), onPrimaryContainer = Color(0xFFD9E6FF),
        secondary = Color(0xFFB9C9DF), onSecondary = Color(0xFF223248),
        secondaryContainer = Color(0xFF304258), onSecondaryContainer = Color(0xFFDDE7F5),
        background = Color(0xFF0F1622), onBackground = Color(0xFFE5EBF5),
        surface = Color(0xFF182230), onSurface = Color(0xFFE5EBF5),
        surfaceVariant = Color(0xFF233147), onSurfaceVariant = Color(0xFFADBAD0),
        surfaceContainerLowest = Color(0xFF0D1420), surfaceContainerLow = Color(0xFF151F2D),
        surfaceContainer = Color(0xFF1D293A), surfaceContainerHigh = Color(0xFF253347),
        surfaceContainerHighest = Color(0xFF2E3C50),
        outline = Color(0xFF7F8EA5), outlineVariant = Color(0xFF35445B),
    )
    MaterialTheme(colorScheme = if (darkTheme) dark else light,
        typography=Typography(
            headlineSmall=TextStyle(fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold),
            titleLarge=TextStyle(fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
            titleMedium=TextStyle(fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
            titleSmall=TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium),
            bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontSize=14.sp,lineHeight=20.sp),
            bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
            labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
            labelMedium=TextStyle(fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium),
            labelSmall=TextStyle(fontSize=12.sp,lineHeight=16.sp)),
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(16.dp)),content = content)
}
