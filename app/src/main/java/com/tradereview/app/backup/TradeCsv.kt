package com.tradereview.app.backup

import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import java.time.LocalDate

object TradeCsv {
    private val headers = listOf("交易编号", "稳定标识", "日期", "市场", "品种", "方向", "币种", "净盈亏", "仓位百分比", "策略", "情绪", "常见错误", "执行纪律", "已复盘")
    fun encode(trades: List<Trade>): String = buildString {
        append('\uFEFF') // UTF-8 BOM helps mobile/desktop spreadsheets recognize Chinese.
        append(row(headers))
        trades.forEach { t -> append(row(listOf(t.id.toString(), t.recordKey, LocalDate.ofEpochDay(t.date).toString(),
            Presets.label(Presets.markets, t.market), spreadsheetText(t.symbol), Presets.label(Presets.directions, t.direction),
            t.currency, t.netPnl, t.positionPercent.toString(), Presets.label(Presets.strategies, t.strategy),
            Presets.label(Presets.emotions, t.emotion), t.mistakes.joinToString("、") { Presets.label(Presets.mistakes, it) },
            t.discipline.joinToString("、") { Presets.label(Presets.discipline, it) }, if (t.reviewed) "是" else "否"))) }
    }
    internal fun row(cells: List<String>): String = cells.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\r\n"
    // Custom symbols can otherwise become spreadsheet formulas; JSON retains the original text.
    private fun spreadsheetText(value: String): String = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
}
