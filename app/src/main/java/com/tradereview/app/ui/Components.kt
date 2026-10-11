package com.tradereview.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.tradereview.app.data.Catalog
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.Option
import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import com.tradereview.app.domain.CurvePoint
import com.tradereview.app.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

val LocalChoicesEnabled = staticCompositionLocalOf { true }
val LocalCatalog = staticCompositionLocalOf { Catalog() }

fun dayLabel(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
fun percent(value: Double) = String.format(java.util.Locale.CHINA, "%.1f%%", value)

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<Option>, selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(enabled = LocalChoicesEnabled.current, selected = option.id in selected, onClick = { onToggle(option.id) }, label = { Text(option.label) }) }
    }
}

@Composable
fun SingleChoice(options: List<Option>, selected: String, onSelect: (String) -> Unit) =
    ChoiceChips(options, listOf(selected), onSelect)

fun toggleTag(tags: List<String>, value: String): List<String> = if (value in tags) tags - value else tags + value

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun TradeCard(trade: Trade, onClick: () -> Unit) {
    val catalog = LocalCatalog.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trade.symbol, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Money.format(Money.value(trade), signed = true), color = pnlColor(Money.value(trade)),
                    style = MaterialTheme.typography.titleMedium)
            }
            Text("${dayLabel(trade.date)} · ${trade.currency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Presets.label(Presets.markets, trade.market)} · ${Presets.label(Presets.directions, trade.direction)} · 仓位 ${trade.positionPercent}% · ${catalog.label("strategy", trade.strategy)}", style = MaterialTheme.typography.bodySmall)
            Text(if (trade.reviewed) "已复盘 · ${catalog.label("emotion", trade.emotion)}" else "待复盘 · 点击补充错误与纪律", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (trade.mistakes.isNotEmpty()) Text(trade.mistakes.joinToString(" · ") { catalog.label("mistake", it) }, style = MaterialTheme.typography.bodySmall, color = LossColor)
        }
    }
}

@Composable
fun PnlChart(points: List<CurvePoint>, currency: String) {
    if (points.isEmpty()) {
        Text("记录交易后，这里会显示累计净盈亏曲线。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val values = listOf(0.0) + points.map { it.cumulative.toDouble() }
    val low = min(0.0, values.min())
    val high = max(0.0, values.max())
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = "累计净盈亏曲线，${dayLabel(points.first().date)}至${dayLabel(points.last().date)}，末值${Money.format(points.last().cumulative)} $currency"
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
        val padding = 12.dp.toPx()
        val width = size.width - padding * 2
        val height = size.height - padding * 2
        fun y(value: Double) = (padding + height * (high - value) / span).toFloat()
        drawLine(gridColor, Offset(padding, y(0.0)), Offset(size.width - padding, y(0.0)), strokeWidth = 1.dp.toPx())
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = padding + width * index / (values.size - 1)
            if (index == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(size.width - padding, y(values.last())))
    }
    Text("范围 ${Money.format(low.toBigDecimal())} ～ ${Money.format(high.toBigDecimal())} $currency", style = MaterialTheme.typography.bodySmall)
    Text("起点 0 · ${dayLabel(points.first().date)} → ${dayLabel(points.last().date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("按交易日汇总累计金额，横轴按交易日等距排列。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
