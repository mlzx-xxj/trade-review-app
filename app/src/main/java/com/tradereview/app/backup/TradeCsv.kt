package com.tradereview.app.backup

import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import com.tradereview.app.data.Catalog
import java.time.LocalDate
import com.tradereview.app.domain.ProfessionalMath
import com.tradereview.app.domain.RiskEngine

object TradeCsv {
    private val headers = listOf("交易编号", "稳定标识", "日期", "市场", "品种", "方向", "币种", "净盈亏", "仓位百分比", "策略", "情绪", "常见错误", "执行纪律", "已复盘", "备注", "盈亏模式", "合约代码", "入场价", "出场价", "数量", "数量单位", "合约乘数", "价格数量推导毛盈亏", "最小价格变动", "初始止损", "计划止盈", "手续费", "其他实际成本", "计划止损执行成本", "风险口径", "计划最大亏损", "有效初始风险", "R倍数", "计划仓位百分比", "计划状态", "交易理由", "执行备注", "曾触发风险纪律")
    fun encode(trades: List<Trade>, catalog: Catalog = Catalog()): String = buildString {
        append('\uFEFF') // UTF-8 BOM helps mobile/desktop spreadsheets recognize Chinese.
        append(row(headers))
        trades.forEach { t -> append(row(listOf(t.id.toString(), t.recordKey, LocalDate.ofEpochDay(t.date).toString(),
            Presets.label(Presets.markets, t.market), spreadsheetText(t.symbol), Presets.label(Presets.directions, t.direction),
            t.currency, t.netPnl, t.positionPercent.toString(), spreadsheetText(catalog.label("strategy", t.strategy)),
            spreadsheetText(catalog.label("emotion", t.emotion)), spreadsheetText(t.mistakes.joinToString("、") { catalog.label("mistake", it) }),
            spreadsheetText(t.discipline.joinToString("、") { catalog.label("discipline", it) }), if (t.reviewed) "是" else "否", spreadsheetText(t.note)) + professionalCells(t))) }
    }
    private fun professionalCells(t: Trade): List<String> {
        val p = t.professional
        return listOf(if(p?.pnlMode == "auto") "自动" else "手动", spreadsheetText(p?.contractCode.orEmpty()), p?.entryPrice.orEmpty(), p?.exitPrice.orEmpty(),
            p?.quantity.orEmpty(), Presets.label(ProfessionalMath.units, p?.quantityUnit ?: "unknown").let { if(it == "unknown") "未提供" else it }, p?.multiplier.orEmpty(), ProfessionalMath.gross(p, t.direction)?.let(ProfessionalMath::plain).orEmpty(), p?.minTick.orEmpty(),
            p?.initialStop.orEmpty(), p?.takeProfit.orEmpty(), p?.fees.orEmpty(), p?.otherCosts.orEmpty(), p?.stopExecutionCost.orEmpty(),
            when(p?.riskMode) { "price" -> "初始止损价格风险加明确计划执行成本"; "amount" -> "手填计划最大亏损"; else -> "未提供" }, p?.manualRisk.orEmpty(),
            ProfessionalMath.risk(p, t.direction)?.let(ProfessionalMath::plain).orEmpty(), ProfessionalMath.r(t)?.let(ProfessionalMath::plain).orEmpty(), p?.plannedPositionPercent.orEmpty(),
            when(p?.planStatus) { "in" -> "计划内"; "out" -> "计划外"; else -> "未说明" }, spreadsheetText(p?.reason.orEmpty()), spreadsheetText(p?.executionNote.orEmpty()),
            t.riskRecord?.violations.orEmpty().joinToString("、") { RiskEngine.labels.getValue(it) })
    }
    internal fun row(cells: List<String>): String = cells.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\r\n"
    // Custom symbols can otherwise become spreadsheet formulas; JSON retains the original text.
    private fun spreadsheetText(value: String): String = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
}
