package com.tradereview.app.backup

import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import com.tradereview.app.data.Catalog
import java.time.LocalDate

object TradeCsv {
    private val headers = listOf("交易编号", "稳定标识", "日期", "市场", "品种", "方向", "币种", "净盈亏", "仓位百分比", "策略", "情绪", "常见错误", "执行纪律", "已复盘", "备注")
    fun encode(trades: List<Trade>, catalog: Catalog = Catalog()): String = buildString {
        append('\uFEFF') // UTF-8 BOM helps mobile/desktop spreadsheets recognize Chinese.
        append(row(headers))
        trades.forEach { t -> append(row(listOf(t.id.toString(), t.recordKey, LocalDate.ofEpochDay(t.date).toString(),
            Presets.label(Presets.markets, t.market), spreadsheetText(t.symbol), Presets.label(Presets.directions, t.direction),
            t.currency, t.netPnl, t.positionPercent.toString(), spreadsheetText(catalog.label("strategy", t.strategy)),
            spreadsheetText(catalog.label("emotion", t.emotion)), spreadsheetText(t.mistakes.joinToString("、") { catalog.label("mistake", it) }),
            spreadsheetText(t.discipline.joinToString("、") { catalog.label("discipline", it) }), if (t.reviewed) "是" else "否", spreadsheetText(t.note)))) }
    }
    internal fun row(cells: List<String>): String = cells.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\r\n"
    // Custom symbols can otherwise become spreadsheet formulas; JSON retains the original text.
    private fun spreadsheetText(value: String): String = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
}
