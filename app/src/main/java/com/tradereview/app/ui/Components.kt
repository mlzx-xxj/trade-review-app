package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.tradereview.app.data.Catalog
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.Option
import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import com.tradereview.app.domain.CurvePoint
import com.tradereview.app.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
            val r = com.tradereview.app.domain.ProfessionalMath.r(trade)
            Text("R：${com.tradereview.app.domain.ProfessionalMath.formatR(r)}", color = r?.let(::pnlColor) ?: MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            trade.professional?.contractCode?.let { Text("合约：$it", style = MaterialTheme.typography.bodySmall) }
            trade.riskRecord?.violations?.takeIf { it.isNotEmpty() }?.let { Text("曾触发风险纪律：${it.joinToString("、") { id -> com.tradereview.app.domain.RiskEngine.labels.getValue(id) }}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (trade.mistakes.isNotEmpty()) Text(trade.mistakes.joinToString(" · ") { catalog.label("mistake", it) }, style = MaterialTheme.typography.bodySmall, color = LossColor)
        }
    }
}

@Composable
fun PnlChart(points: List<CurvePoint>, currency: String, onDay: (Long) -> Unit = {}) {
    InteractivePnlChart(points, currency, onDay)
}
