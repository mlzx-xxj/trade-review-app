package com.tradereview.app.domain

import com.tradereview.app.data.Trade
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object Money {
    fun parse(value: String): BigDecimal? {
        val input = value.trim()
        if (!Regex("-?\\d{1,12}(\\.\\d{1,2})?").matches(input)) return null
        return input.toBigDecimalOrNull()?.setScale(2, RoundingMode.UNNECESSARY)
    }
    fun value(trade: Trade): BigDecimal = BigDecimal(trade.netPnl)
    fun format(value: BigDecimal, signed: Boolean = false): String {
        val text = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
        return if (signed && value.signum() > 0) "+$text" else text
    }
}

data class CurvePoint(val date: Long, val cumulative: BigDecimal)
data class StrategyResult(val strategy: String, val count: Int, val wins: Int, val netPnl: BigDecimal)
data class Statistics(
    val count: Int,
    val wins: Int,
    val losses: Int,
    val netPnl: BigDecimal,
    val reviewed: Int,
    val curve: List<CurvePoint>,
    val mistakes: List<Pair<String, Int>>,
    val strategies: List<StrategyResult>,
) {
    // Break-even trades count in the denominator, but are neither wins nor losses.
    val winRate: Double get() = if (count == 0) 0.0 else wins * 100.0 / count
    val reviewRate: Double get() = if (count == 0) 0.0 else reviewed * 100.0 / count
}

object Analytics {
    fun calculate(trades: List<Trade>, currency: String, since: Long? = null): Statistics {
        val filtered = trades.filter { it.currency == currency && (since == null || it.date >= since) }
        val total = filtered.fold(BigDecimal.ZERO) { sum, trade -> sum + Money.value(trade) }
        var cumulative = BigDecimal.ZERO
        val curve = filtered.groupBy { it.date }.toSortedMap().map { (date, dailyTrades) ->
            cumulative += dailyTrades.fold(BigDecimal.ZERO) { sum, trade -> sum + Money.value(trade) }
            CurvePoint(date, cumulative)
        }
        val mistakes = filtered.flatMap { it.mistakes.distinct() }.groupingBy { it }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        val strategies = filtered.groupBy { it.strategy }.map { (strategy, group) ->
            StrategyResult(strategy, group.size, group.count { Money.value(it).signum() > 0 },
                group.fold(BigDecimal.ZERO) { sum, trade -> sum + Money.value(trade) })
        }.sortedByDescending { it.netPnl }
        return Statistics(filtered.size, filtered.count { Money.value(it).signum() > 0 },
            filtered.count { Money.value(it).signum() < 0 }, total, filtered.count { it.reviewed },
            curve, mistakes, strategies)
    }

    fun validate(trade: Trade, today: Long = LocalDate.now().toEpochDay()): String? = when {
        trade.date > today -> "交易日期不能晚于今天"
        trade.symbol.isBlank() || trade.symbol.length > 30 -> "品种名称需为 1–30 个字符"
        Money.parse(trade.netPnl) == null -> "金额最多 12 位整数、2 位小数"
        trade.positionPercent !in 1..100 -> "仓位需在 1%–100% 之间"
        else -> null
    }
}
