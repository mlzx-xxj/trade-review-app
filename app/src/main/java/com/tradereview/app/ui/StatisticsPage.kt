package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import java.math.BigDecimal

@Composable
fun StatisticsPage(state: JournalState, analytics: AnalyticsState, currency: String, filter: TradeFilter,
    model: TradeViewModel, busy: Boolean, onDay: (Long) -> Unit, onDrill: (TradeFilter) -> Unit, modifier: Modifier = Modifier,onReports: (String)->Unit = {}) {
    val report = analytics.report; val performance = report.performance; val catalog = LocalCatalog.current
    var sort by rememberSaveable { mutableStateOf("pnl") }
    var dimension by rememberSaveable { mutableStateOf("symbol") }
    fun sorted(values: List<GroupResult>) = when (sort) {
        "count" -> values.sortedWith(compareByDescending<GroupResult> { it.performance.count }.thenBy { it.key })
        "win" -> values.sortedWith(compareByDescending<GroupResult> { it.performance.winRate ?: -1.0 }.thenByDescending { it.performance.count }.thenBy { it.key })
        else -> values.sortedWith(compareByDescending<GroupResult> { it.performance.netPnl }.thenBy { it.key })
    }
    val strategies = sorted(report.strategies)
    val groups = sorted(when (dimension) { "market" -> report.markets; "direction" -> report.directions; "emotion" -> report.emotions; "discipline" -> report.discipline; else -> report.instruments })
    val strategyMaximum = strategies.maxOfOrNull { it.performance.netPnl.abs() }?.max(BigDecimal.ONE) ?: BigDecimal.ONE
    val groupMaximum = groups.maxOfOrNull { it.performance.netPnl.abs() }?.max(BigDecimal.ONE) ?: BigDecimal.ONE
    val errorMaximum = (report.mistakes.maxOfOrNull { it.performance.count } ?: 1).toBigDecimal()
    fun groupLabel(group: GroupResult) = when (dimension) {
        "market" -> Presets.label(Presets.markets, group.key); "direction" -> Presets.label(Presets.directions, group.key)
        "emotion" -> catalog.label("emotion", group.key); "discipline" -> catalog.label("discipline", group.key)
        else -> "${group.key} · ${Presets.label(Presets.markets, group.market)}"
    }
    fun drill(group: GroupResult) = onDrill(when (dimension) {
        "market" -> filter.copy(markets = listOf(group.key)); "direction" -> filter.copy(directions = listOf(group.key))
        "emotion" -> filter.copy(emotions = listOf(group.key)); "discipline" -> filter.copy(discipline = listOf(group.key))
        else -> filter.copy(markets = listOf(group.market), symbols = listOf(group.key))
    })
    LazyColumn(modifier.fillMaxSize().testTag("statistics-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard("周期复盘与改善追踪") { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={onReports("week")},modifier=Modifier.testTag("open-week-report")){Text("每周复盘")}
            OutlinedButton(onClick={onReports("month")},modifier=Modifier.testTag("open-month-report")){Text("每月复盘")}
        };TextButton(onClick={onReports("goals")}){Text("纪律改善目标")} } }
        item { FilterCard("分析范围", state, currency, filter, performance.count, model::selectCurrency, model::updateFilter,
            model::saveFilter, model::applyFilter, model::deleteFilter, busy) }
        if (performance.count == 0) item { SectionCard("暂无符合条件的统计") {
            Text("当前没有匹配记录。可调整条件、清除筛选或录入交易；图表保持为空。")
        } }
        item { SectionCard("交易表现", "仅统计已录入的已平仓净收益；胜率包含持平交易。") {
            Text("总交易 ${performance.count} 笔", modifier = Modifier.testTag("statistics-trade-count"))
            Text("盈利 ${performance.wins} · 亏损 ${performance.losses} · 持平 ${performance.flats}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("胜率", performance.winRate?.let(::percent) ?: "—", Modifier.weight(1f))
                Metric("复盘完成率", performance.reviewRate?.let(::percent) ?: "—", Modifier.weight(1f))
            }
            Text("累计净盈亏 · $currency", style = MaterialTheme.typography.labelMedium)
            Text(if (performance.count == 0) "—" else Money.format(performance.netPnl, true), color = pnlColor(performance.netPnl), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("statistics-net-pnl"))
            Text("平均盈利：${performance.averageWin?.let { Money.format(it) } ?: "—"} $currency")
            Text("平均亏损：${performance.averageLoss?.let { Money.format(it) } ?: "—"} $currency")
            Text("盈亏比：${performance.payoffRatio?.let { Money.format(it) } ?: "—（需同时存在盈利与亏损交易）"}")
            Text("单笔平均净收益：${performance.averageNet?.let { Money.format(it, true) } ?: "—"} $currency")
            Text("最大连续盈利 ${performance.longestWins} 笔 · 最大连续亏损 ${performance.longestLosses} 笔")
            Text("最大回撤：${if (performance.count == 0) "—" else Money.format(performance.maxDrawdown)} $currency", modifier = Modifier.testTag("statistics-drawdown"))
            Text("回撤口径：筛选后的同币种已平仓交易按日期、数据库编号排序，从累计净盈亏 0 开始，取历史高点至后续低点的最大金额差；不含浮动盈亏、初始权益或回撤百分比。同日编号反映录入次序，未记录成交时间。持平中断连续盈亏。", style = MaterialTheme.typography.bodySmall)
            if (performance.smallSample) Text("样本量 ${performance.count} 笔，少于 20 笔，结论可能不稳定。", color = MaterialTheme.colorScheme.primary)
        } }
        item { SectionCard("每日净盈亏柱状图", "每日归集，点击柱形查看精确金额。") { AmountBarChart(report.daily, currency, onDay = onDay) } }
        item { SectionCard("累计净盈亏曲线", "由同一筛选结果逐日累计。") { PnlChart(report.curve, currency, onDay) } }
        item { SectionCard("每周盈亏汇总", "周一为每周首日；只汇总筛选范围内的交易。") { AmountBarChart(report.weekly, currency, weekly = true, onDay = onDay) } }
        item { SectionCard("策略表现横向对比", "条长表示净盈亏绝对值；点击策略查看对应交易。") {
            SingleChoice(listOf(Option("pnl", "按净盈亏"), Option("count", "按样本量"), Option("win", "按胜率")), sort) { sort = it }
            if (strategies.isEmpty()) Text("暂无策略样本。")
        } }
        items(strategies, key = { "strategy-${it.key}" }) { result -> GroupResultCard {
            GroupBar(result, catalog.label("strategy", result.key), currency, strategyMaximum) {
                onDrill(filter.copy(strategies = listOf(result.key)))
            }
        } }
        item { SectionCard("品种与行为比较", "每组均显示真实样本量与结果；点击可筛选明细。") {
            SingleChoice(listOf(Option("symbol", "品种"), Option("market", "市场"), Option("direction", "方向"), Option("emotion", "情绪"), Option("discipline", "执行纪律")), dimension) { dimension = it }
            SingleChoice(listOf(Option("pnl", "按净盈亏"), Option("count", "按样本量"), Option("win", "按胜率")), sort) { sort = it }
            if (groups.isEmpty()) Text("暂无匹配样本。")
        } }
        items(groups, key = { "dimension-$dimension-${it.market}-${it.key}" }) { result -> GroupResultCard {
            GroupBar(result, groupLabel(result), currency, groupMaximum) { drill(result) }
        } }
        item { SectionCard("错误标签频次与关联表现", "每个错误在同一笔交易中只计一次；一笔交易可关联多个标签，各行不可相加作为交易总数。") {
            Text("标签与盈亏展示统计相关性，不代表标签造成盈亏。")
            if (report.mistakes.isEmpty()) Text("暂无错误标签样本。")
        } }
        items(report.mistakes, key = { "mistake-${it.key}" }) { result -> GroupResultCard {
            GroupBar(result, catalog.label("mistake", result.key), currency, errorMaximum, frequency = true) {
                onDrill(filter.copy(mistakes = listOf(result.key)))
            }
        } }
        item { SectionCard("有错误与无错误对比", "按交易错误标签是否为空分组；需要足够样本才能作稳定比较。") {
            if (report.errorPresence.isEmpty()) Text("暂无匹配交易。")
            report.errorPresence.forEach { result -> GroupBar(result, if (result.key == "with") "有错误标签" else "无错误标签", currency,
                report.errorPresence.maxOf { it.performance.netPnl.abs() }.max(BigDecimal.ONE)) { onDrill(filter.copy(errorPresence = result.key)) } }
        } }
        item { SectionCard("每日执行评分趋势", "来自日期范围内已保存的盘后复盘；加入交易条件后，仅显示匹配交易日期。每日日志本身不区分币种。") {
            RatingTrend(report.ratings, onDay)
        } }
        riskAnalyticsItems(analytics.risk, currency, catalog, filter, onDrill)
    }
}

@Composable
private fun GroupResultCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}
