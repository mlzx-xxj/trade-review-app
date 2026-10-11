package com.tradereview.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradereview.app.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Floating point is used only for pixel coordinates; labels and aggregates retain BigDecimal. */
private fun fraction(value: BigDecimal, span: BigDecimal): Float = if (span.signum() == 0) 0f else value.divide(span, 8, RoundingMode.HALF_UP).toFloat()
private data class CurveGeometry(val low: BigDecimal, val high: BigDecimal, val span: BigDecimal, val vertices: List<Pair<Int, Float>>)

@Composable
fun AmountBarChart(points: List<AmountPoint>, currency: String, weekly: Boolean = false, onDay: (Long) -> Unit) {
    if (points.isEmpty()) { Text("暂无匹配交易，记录交易后显示图表。"); return }
    var selected by remember(points) { mutableStateOf(points.last()) }
    val maximum = points.maxOf { it.amount.abs() }.max(BigDecimal.ONE)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Text("${dayLabel(selected.date)}${if (weekly) "起的一周" else ""}：${Money.format(selected.amount, true)} $currency · ${selected.count} 笔", modifier = Modifier.testTag(if (weekly) "weekly-chart-value" else "daily-chart-value"), color = pnlColor(selected.amount))
    LazyRow(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(points, key = { it.date }) { point ->
            val color = pnlColor(point.amount)
            Column(Modifier.width(52.dp).fillMaxHeight().clickable { selected = point }.testTag("${if (weekly) "weekly" else "daily"}-bar-${point.date}").semantics {
                contentDescription = "${dayLabel(point.date)}${if (weekly) "起的一周" else ""}，${Money.format(point.amount, true)} $currency，${point.count}笔，点击查看数值"
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val zero = size.height / 2
                    drawLine(baseline, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
                    val extent = fraction(point.amount.abs(), maximum) * (zero - 5.dp.toPx())
                    val left = size.width * .2f; val width = size.width * .6f
                    if (point.amount.signum() == 0) drawLine(color, Offset(left, zero), Offset(left + width, zero), 3.dp.toPx())
                    else drawRect(color.copy(alpha = if (point == selected) 1f else .6f), Offset(left, if (point.amount.signum() > 0) zero - extent else zero), androidx.compose.ui.geometry.Size(width, extent))
                }
                Text(LocalDate.ofEpochDay(point.date).let { "${it.monthValue}/${it.dayOfMonth}" }, style = MaterialTheme.typography.labelSmall)
                Text("${point.count}笔", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text("纵轴范围 ±${Money.format(maximum)} $currency · 点击柱形查看金额，左右滑动查看更多日期。", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { onDay(selected.date) }) { Text(if (weekly) "查看该周起始日" else "查看该日详情") }
}

@Composable
fun InteractivePnlChart(points: List<CurvePoint>, currency: String, onDay: (Long) -> Unit) {
    if (points.isEmpty()) { Text("暂无匹配交易，累计曲线为空。"); return }
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val geometry by produceState<CurveGeometry?>(null, points) {
        value = withContext(Dispatchers.Default) {
            val values = listOf(BigDecimal.ZERO) + points.map { it.cumulative }
            val low = values.minOrNull()!!.min(BigDecimal.ZERO); val high = values.maxOrNull()!!.max(BigDecimal.ZERO)
            val span = (high - low).takeIf { it.signum() > 0 } ?: BigDecimal.ONE
            // Retain each block's extrema while bounding draw commands on small screens.
            val block = maxOf(1, values.size / 300)
            val indices = sortedSetOf(0, values.lastIndex)
            values.indices.chunked(block).forEach { part -> indices += part.minBy { values[it] }; indices += part.maxBy { values[it] } }
            CurveGeometry(low, high, span, indices.map { it to fraction(high - values[it], span) })
        }
    }
    val prepared = geometry
    if (prepared == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    val low = prepared.low; val high = prepared.high; val span = prepared.span
    val profit = ProfitColor; val loss = LossColor; val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val current = points[selected]
    Text("${dayLabel(current.date)}：累计 ${Money.format(current.cumulative, true)} $currency", modifier = Modifier.testTag("curve-chart-value"), color = pnlColor(current.cumulative))
    Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("interactive-pnl-curve").semantics { contentDescription = "累计净盈亏曲线，${points.size}个交易日，点击曲线查看精确数值" }
        .pointerInput(points) { detectTapGestures { at ->
            val pad = 12.dp.toPx(); val width = (size.width.toFloat() - 2 * pad).coerceAtLeast(1f)
            selected = (((at.x - pad) / width * points.size).roundToInt() - 1).coerceIn(0, points.lastIndex)
        } }) {
        val pad = 12.dp.toPx(); val width = size.width - pad * 2; val height = size.height - pad * 2
        fun coordinate(index: Int, relative: Float) = Offset(pad + width * index / points.size, pad + height * relative)
        val zero = pad + height * fraction(high, span)
        drawLine(baseline, Offset(pad, zero), Offset(size.width - pad, zero), 1.dp.toPx())
        prepared.vertices.zipWithNext().forEach { (from, to) ->
            val amount = if (to.first == 0) BigDecimal.ZERO else points[to.first - 1].cumulative
            val color = when (amount.signum()) { 1 -> profit; -1 -> loss; else -> neutral }
            drawLine(color, coordinate(from.first, from.second), coordinate(to.first, to.second), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(neutral, 5.dp.toPx(), coordinate(selected + 1, fraction(high - current.cumulative, span)))
    }
    Text("纵轴 ${Money.format(low)} ～ ${Money.format(high)} $currency；交易日等距排列，起点 0。", style = MaterialTheme.typography.bodySmall)
    if (points.size > 600) Text("数据较多，曲线保留分段高低点；点击数值仍来自完整交易日数据。", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = selected > 0, onClick = { selected-- }) { Text("前一交易日") }
        TextButton(enabled = selected < points.lastIndex, onClick = { selected++ }) { Text("后一交易日") }
        TextButton(onClick = { onDay(current.date) }) { Text("当日详情") }
    }
}

@Composable
fun GroupBar(result: GroupResult, label: String, currency: String, maximum: BigDecimal, frequency: Boolean = false, onClick: () -> Unit) {
    val performance = result.performance
    val color = if (frequency) MaterialTheme.colorScheme.primary else pnlColor(performance.netPnl)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("group-${result.market}-${result.key}").padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text("${performance.count} 笔 · 盈 ${performance.wins} / 亏 ${performance.losses} / 平 ${performance.flats} · 胜率 ${performance.winRate?.let(::percent) ?: "—"}", style = MaterialTheme.typography.bodySmall)
        Text("净盈亏 ${Money.format(performance.netPnl, true)} $currency${if (performance.smallSample) " · 样本不足 20 笔" else ""}", style = MaterialTheme.typography.bodySmall, color = pnlColor(performance.netPnl))
        LinearProgressIndicator(progress = { if (frequency) fraction(performance.count.toBigDecimal(), maximum).coerceIn(0f, 1f) else fraction(performance.netPnl.abs(), maximum).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = color)
        Text("点击查看关联交易", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun RatingTrend(points: List<RatingPoint>, onDay: (Long) -> Unit) {
    if (points.isEmpty()) { Text("当前范围没有已保存的盘后复盘评分。"); return }
    var selected by remember(points) { mutableStateOf(points.last()) }
    val color = MaterialTheme.colorScheme.primary
    Text("${dayLabel(selected.date)}：执行评分 ${selected.rating} / 5", modifier = Modifier.testTag("rating-chart-value"))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(points, key = { it.date }) { point ->
            Column(Modifier.width(52.dp).height(130.dp).clickable { selected = point }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Canvas(Modifier.fillMaxWidth().height((point.rating * 16).dp)) { drawRect(color.copy(alpha = if (point == selected) 1f else .5f)) }
                Text("${point.rating}/5", style = MaterialTheme.typography.labelSmall)
                Text(LocalDate.ofEpochDay(point.date).let { "${it.monthValue}/${it.dayOfMonth}" }, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    TextButton(onClick = { onDay(selected.date) }) { Text("查看该日复盘") }
}
