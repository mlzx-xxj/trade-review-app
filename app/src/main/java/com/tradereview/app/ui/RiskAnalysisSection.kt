package com.tradereview.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import java.math.BigDecimal
import java.math.RoundingMode

private fun rText(v: BigDecimal?): String = ProfessionalMath.formatR(v)

fun LazyListScope.riskAnalyticsItems(report: RiskReport, currency: String, catalog: Catalog, filter: TradeFilter, onDrill: (TradeFilter) -> Unit) {
    item { SectionCard("风险与纪律分析", "与上方筛选一致；金额按币种核算，风险缺失不计为 0R。") {
        val p = report.r
        Text("有效 R 样本 ${p.valid} / ${p.total} 笔", modifier = Modifier.testTag("r-valid-samples"))
        Text("平均 R：${rText(p.average)}", modifier = Modifier.testTag("r-average")); Text("累计 R：${rText(p.cumulative)}")
        Text("盈利平均 R：${rText(p.winningAverage)} · ${p.winningSamples} 笔")
        Text("亏损平均 R：${rText(p.losingAverage)} · ${p.losingSamples} 笔")
        Text("交易期望值（历史有效样本）：${p.expectationNet?.let { Money.format(it, true) } ?: "—"} $currency / 笔 · ${p.valid} 笔")
        Text("R 期望 = 有效样本 R 算术平均；金额期望 = 同一有效样本净盈亏算术平均，不代表未来收益。", style = MaterialTheme.typography.bodySmall)
        Text("有效样本平均盈利：${p.money.averageWin?.let { Money.format(it) } ?: "—"} $currency · ${p.winningSamples} 笔")
        Text("有效样本平均亏损：${p.money.averageLoss?.let { Money.format(it) } ?: "—"} $currency · ${p.losingSamples} 笔")
        Text("有效样本实际盈亏比：${p.money.payoffRatio?.let { Money.format(it) } ?: "—"} · ${p.valid} 笔")
        if(p.valid in 1..19) Text("有效样本少于 20 笔，结论可能不稳定。", color = MaterialTheme.colorScheme.primary)
        Text("R = 实际已入账净盈亏 / 本笔有效初始计划风险。无仓位到风险的自动换算。金额/胜率/回撤继续沿用 V0.4 口径。", style = MaterialTheme.typography.bodySmall)
    } }
    item { SectionCard("风险纪律记录", "显示保存时曾触发的参考纪律；缺数据不能计作守规，改规则不删除旧检查。") {
        Text("曾触发 ${report.violations.size} 笔 / 有可执行检查 ${report.checked} 笔 · ${RiskAnalytics.percent(report.violations.size, report.checked)}", modifier = Modifier.testTag("risk-violation-summary"))
        Text("超出本笔计划仓位 ${report.exceededPositions.size} / 明确计划仓位 ${report.plannedPositions} 笔 · ${RiskAnalytics.percent(report.exceededPositions.size, report.plannedPositions)}")
        Text("连续亏损段（至少 2 笔）${report.lossRuns} 段 · 最大连亏 ${report.longestLoss} 笔 · 筛选样本 ${report.r.total} 笔")
        Text("不同提醒可关联同一笔交易，各项不能直接相加作为违规交易总数；只描述记录，不判断因果。", style = MaterialTheme.typography.bodySmall)
    } }
    item { DistributionCard("风险违规类型（按交易计数）", report.violationGroups, report.checked, currency) }
    item { SectionCard("计划内 / 计划外 / 未说明", "未知状态单独列出；净盈亏包含该组全部交易，R 仅含有效风险样本。") {
        if(report.plans.isEmpty()) Text("暂无匹配交易。")
        report.plans.forEach { g ->
            val title = when(g.key) { "in" -> "计划内"; "out" -> "计划外"; else -> "未说明" }
            Text("$title ${g.performance.total} 笔 · 净盈亏 ${Money.format(g.performance.allMoney.netPnl, true)} $currency · 平均 ${rText(g.performance.average)} · 有效 R ${g.performance.valid} 笔")
            if(g.performance.total in 1..19) Text("样本较少，比较可能不稳定。", style = MaterialTheme.typography.bodySmall)
        }
    } }
    item { SectionCard("各策略平均 R", "可点击查看相同筛选下的策略交易。") { if(report.strategies.isEmpty()) Text("暂无策略样本。") } }
    items(report.strategies, key = { "r-strategy-${it.key}" }) { g -> RGroupCard(g, catalog.label("strategy", g.key), currency) { onDrill(filter.copy(strategies = listOf(g.key))) } }
    item { SectionCard("各品种 R 表现") { if(report.instruments.isEmpty()) Text("暂无品种样本。") } }
    items(report.instruments, key = { "r-symbol-${it.market}-${it.key}" }) { g -> RGroupCard(g, "${g.key} · ${Presets.label(Presets.markets, g.market)}", currency) { onDrill(filter.copy(markets = listOf(g.market), symbols = listOf(g.key))) } }
    item { DistributionCard("R 倍数分布（有效风险样本）", report.rBuckets, report.r.valid, currency) }
    item { DistributionCard("净盈亏分布（$currency）", report.pnlBuckets, report.r.total, currency) }
}

@Composable
private fun RGroupCard(g: RGroup, title: String, currency: String, onClick: () -> Unit) {
    SectionCard(title) {
        Text("平均 ${rText(g.performance.average)} · 累计 ${rText(g.performance.cumulative)}", color = g.performance.average?.let { pnlColor(it) } ?: MaterialTheme.colorScheme.onSurface)
        Text("有效风险样本 ${g.performance.valid} / 全部 ${g.performance.total} 笔 · 净盈亏 ${Money.format(g.performance.allMoney.netPnl, true)} $currency")
        if(g.performance.valid in 1..19) Text("有效样本较少，结论可能不稳定。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onClick) { Text("查看交易明细") }
    }
}

@Composable
private fun DistributionCard(title: String, values: List<RiskBucket>, total: Int, currency: String) {
    var detail by remember { mutableStateOf<RiskBucket?>(null) }
    SectionCard(title, "样本 $total 笔；点击区间查看真实数值。") {
        if(total == 0) Text("暂无有效样本。") else values.forEach { b ->
            TextButton(onClick = { detail = b }, modifier = Modifier.fillMaxWidth()) { Text("${b.label}：${b.trades.size} 笔 · ${RiskAnalytics.percent(b.trades.size, total)}") }
            LinearProgressIndicator(progress = { b.trades.size.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        }
    }
    detail?.let { b -> AlertDialog(onDismissRequest = { detail = null }, title = { Text("${b.label} · ${b.trades.size} 笔") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(b.trades.isEmpty()) item { Text("该区间没有交易。") }
            items(b.trades, key = { it.recordKey }) { t -> Text("${dayLabel(t.date)} ${t.symbol}\n净盈亏 ${t.netPnl} $currency · ${rText(ProfessionalMath.r(t))}") }
        }
    }, confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } }) }
}
