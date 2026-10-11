package com.tradereview.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.math.RoundingMode

@Composable
fun CalendarPage(state: JournalState, calendar: Map<Long, List<DailyPnl>>, currency: String, onCurrency: (String) -> Unit,
    onDay: (Long) -> Unit, modifier: Modifier = Modifier) {
    var monthName by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var mode by rememberSaveable { mutableStateOf("calendar") }
    var range by rememberSaveable { mutableStateOf("month") }
    val month = YearMonth.parse(monthName)
    val months = TradingCalendar.months(month, if (range == "three") 3 else 1)
    val from = months.first().atDay(1).toEpochDay(); val to = month.atEndOfMonth().toEpochDay()
    val maximum = remember(calendar, currency, from, to) { calendar.filterKeys { it in from..to }.values.flatten().filter { it.currency == currency }.maxOfOrNull { it.netPnl.abs() } ?: BigDecimal.ZERO }
    val reviews = remember(state.reviews) { state.reviews.associateBy { it.date } }
    val today = LocalDate.now().toEpochDay()
    LazyColumn(modifier.fillMaxSize().testTag("calendar-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard("交易日历", "日期采用本地日历日期；每天按所选币种汇总已记录交易。") {
            SingleChoice(Presets.currencies, currency, onCurrency)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(enabled = month > YearMonth.of(1900, 1), onClick = { monthName = month.minusMonths(1).toString() }, modifier = Modifier.testTag("calendar-previous")) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月") }
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("calendar-month"))
                IconButton(enabled = month < YearMonth.of(2100, 12), onClick = { monthName = month.plusMonths(1).toString() }, modifier = Modifier.testTag("calendar-next")) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月") }
            }
            TextButton(onClick = { monthName = YearMonth.now().toString() }, modifier = Modifier.testTag("calendar-today")) { Text("返回今天") }
            SingleChoice(listOf(Option("calendar", "月历"), Option("heat", "盈亏热力图")), mode) { mode = it }
            SingleChoice(listOf(Option("month", "当前月"), Option("three", "近三个月")), range) { range = it }
            Text("图例：盈利 / 亏损颜色越深，净金额绝对值越大。持平显示“平”；无交易显示“—”。每个日期均可点击。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("盈利", color = ProfitColor); Text("亏损", color = LossColor); Text("持平 0"); Text("无交易 —") }
            Text("配色：${if (state.settings.pnlPalette == "red_profit") "红盈绿亏" else "绿盈红亏"} · 在设置中调整", style = MaterialTheme.typography.bodySmall)
            if (mode == "heat") Text("本范围颜色基准：最大每日绝对净金额 ${Money.format(maximum)} $currency；不同币种分别计算。", style = MaterialTheme.typography.bodySmall)
        } }
        items(months, key = { it.toString() }) { shown ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${shown.year}年${shown.monthValue}月 · $currency", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                    Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) } }
                    TradingCalendar.monthCells(shown).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            week.forEach { day ->
                                if (day == null) Spacer(Modifier.weight(1f).height(88.dp))
                                else {
                                    val value = calendar[day]?.firstOrNull { it.currency == currency }
                                    val journal = reviews[day]
                                    val tint = if (value == null || value.netPnl.signum() == 0) MaterialTheme.colorScheme.surfaceVariant else pnlColor(value.netPnl)
                                    val background = when { value == null -> MaterialTheme.colorScheme.surface; value.netPnl.signum() == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                        mode == "heat" -> tint.copy(alpha = 0.10f + 0.40f * TradingCalendar.heatIntensity(value.netPnl, maximum))
                                        else -> tint.copy(alpha = 0.10f) }
                                    val description = "${dayLabel(day)}，${value?.let { "${it.status} ${Money.format(it.netPnl, true)} $currency，${it.count}笔" } ?: "$currency 无交易"}${if (journal?.planSaved == true) "，有盘前计划" else ""}${if (journal?.reviewSaved == true) "，已盘后复盘" else ""}"
                                    Surface(onClick = { onDay(day) }, modifier = Modifier.weight(1f).heightIn(min = 88.dp).testTag("calendar-day-$day").semantics { contentDescription = description },
                                        shape = MaterialTheme.shapes.small, color = background, border = if (day == today) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                        Column(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("${LocalDate.ofEpochDay(day).dayOfMonth}", style = MaterialTheme.typography.labelLarge)
                                            Text(value?.let { when (it.netPnl.signum()) { 1 -> "盈"; -1 -> "亏"; else -> "平" } } ?: "—", style = MaterialTheme.typography.labelSmall)
                                            if (value != null) {
                                                Text(compactMoney(value.netPnl), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                                Text("${value.count}笔", style = MaterialTheme.typography.labelSmall)
                                            }
                                            if (journal?.planSaved == true || journal?.reviewSaved == true) Text(if (journal.reviewSaved) "复盘" else "计划", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text("格内大额以“万”简写；点击日期查看完整金额、所有币种和日志。", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun compactMoney(amount: BigDecimal): String = if (amount.abs() >= BigDecimal("10000")) amount.divide(BigDecimal("10000"), 1, RoundingMode.HALF_UP).toPlainString() + "万" else Money.format(amount)

@Composable
fun DailyDetailsPage(day: Long, state: JournalState, daily: List<DailyPnl>, onOpen: (Trade) -> Unit,
    onJournal: (String) -> Unit, modifier: Modifier = Modifier) {
    val trades = remember(state.trades, day) { state.trades.filter { it.date == day } }
    val journal = state.reviews.firstOrNull { it.date == day }
    val catalog = LocalCatalog.current
    fun labels(kind: String, ids: List<String>) = if (ids.isEmpty()) "暂无勾选" else ids.joinToString("、") { catalog.label(kind, it) }
    LazyColumn(modifier.fillMaxSize().testTag("day-details-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard("当日交易详情", dayLabel(day)) {
            Text("${trades.size} 笔交易", modifier = Modifier.testTag("day-trade-count"))
            Text("市场：${trades.map { Presets.label(Presets.markets, it.market) }.distinct().joinToString("、").ifEmpty { "暂无交易市场" }}")
            if (daily.isEmpty()) Text("当日没有交易；可查看或制定盘前计划。")
            daily.forEach { value -> Text("${value.currency}：${Money.format(value.netPnl, true)} · ${value.count} 笔", color = pnlColor(value.netPnl), modifier = Modifier.testTag("day-pnl-${value.currency}")) }
            Text("不同币种分别汇总；以下列出当天全部交易。", style = MaterialTheme.typography.bodySmall)
        } }
        items(trades, key = { it.id }) { TradeCard(it) { onOpen(it) } }
        item { SectionCard("盘前计划") {
            if (journal?.planSaved != true) Text("当天尚未保存盘前计划。") else {
                Text("判断：${Presets.label(Presets.outlooks, journal.outlook)} · 最大仓位 ${journal.maxPosition}%")
                Text("允许策略：${labels("strategy", journal.strategies)}")
                Text("检查事项：${labels("preparation", journal.preparations)}")
            }
            OutlinedButton(onClick = { onJournal("plan") }) { Text("编辑盘前计划") }
        } }
        item { SectionCard("盘后复盘") {
            if (journal?.reviewSaved != true) Text("当天尚未保存盘后复盘。") else {
                Text("纪律执行评分：${journal.rating} / 5", modifier = Modifier.testTag("day-rating"))
                Text("情绪：${catalog.label("emotion", journal.emotion)}")
                Text("执行纪律：${labels("discipline", journal.discipline)}")
                Text("常见错误：${labels("mistake", journal.mistakes)}")
                Text("改进事项：${labels("lesson", journal.lessons)}")
            }
            OutlinedButton(enabled = day <= LocalDate.now().toEpochDay(), onClick = { onJournal("review") }) { Text("编辑盘后复盘") }
        } }
    }
}
