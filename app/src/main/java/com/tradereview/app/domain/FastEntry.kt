package com.tradereview.app.domain

import com.tradereview.app.data.*
import java.time.LocalDate

object FastEntry {
    fun error(d: EntryDraft): String? {
        if (d.date !in LocalDate.of(1900, 1, 1).toEpochDay()..LocalDate.now().toEpochDay()) return "交易日期需在 1900 年至今天之间"
        if (d.market !in Presets.markets.map { it.id } || d.direction !in Presets.directions.map { it.id } || d.currency !in Presets.currencies.map { it.id }) return "市场、方向或币种无效"
        if (d.symbol.isBlank() || d.symbol.length > 30 || '\u0000' in d.symbol) return "请选择品种，名称最多 30 字"
        if (d.note.length > 300 || '\u0000' in d.note) return "备注最多 300 字"
        if (d.outcome !in listOf("gain", "loss", "flat")) return "请选择盈利、亏损或持平"
        if (d.outcome != "flat") {
            val amount = Money.parse(d.amount) ?: return "请输入有效金额，最多两位小数"
            if (amount.signum() <= 0) return "金额必须大于 0；持平请明确选择“持平”"
        } else if (d.amount.isNotBlank()) return "持平无需金额，请清空金额后保存"
        if (d.positionPercent !in 1..100) return "仓位应为 1–100%"
        return null
    }
    fun trade(d: EntryDraft): Trade {
        require(error(d) == null) { error(d).orEmpty() }
        val amount = if (d.outcome == "flat") "0.00" else requireNotNull(Money.parse(d.amount)).let { if (d.outcome == "loss") it.negate() else it }.toPlainString()
        return Trade(id = d.tradeId, recordKey = d.recordKey, date = d.date, market = d.market, symbol = d.symbol.trim(),
            direction = d.direction, currency = d.currency, netPnl = amount, positionPercent = d.positionPercent,
            strategy = d.strategy, emotion = d.emotion, mistakes = d.mistakes, discipline = d.discipline, reviewed = d.reviewed, note = d.note.trim())
    }
}
