// Test-only frozen V0.7 financial calculations; used for exact output parity.
package com.tradereview.app.stability.baseline
import com.tradereview.app.domain.*

import com.tradereview.app.data.DailyReview
import com.tradereview.app.data.Trade
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

object LegacyAdvancedAnalytics {
    fun performance(trades: List<Trade>): Performance {
        require(trades.map { it.currency }.distinct().size <= 1) { "不同币种不能混合统计" }
        val ordered = trades.sortedWith(compareBy<Trade> { it.date }.thenBy { it.id }.thenBy { it.recordKey })
        var wins = 0; var losses = 0; var reviewed = 0
        var winsTotal = BigDecimal.ZERO; var lossesTotal = BigDecimal.ZERO
        var winning = 0; var losing = 0; var maxWinning = 0; var maxLosing = 0
        var running = BigDecimal.ZERO; var peak = BigDecimal.ZERO; var drawdown = BigDecimal.ZERO
        ordered.forEach { t ->
            val amount = Money.value(t)
            when (amount.signum()) {
                1 -> { wins++; winsTotal += amount; winning++; losing = 0 }
                -1 -> { losses++; lossesTotal += amount; losing++; winning = 0 }
                else -> { winning = 0; losing = 0 }
            }
            maxWinning = maxOf(maxWinning, winning); maxLosing = maxOf(maxLosing, losing)
            if (t.reviewed) reviewed++
            running += amount; peak = peak.max(running); drawdown = drawdown.max(peak - running)
        }
        fun average(total: BigDecimal, count: Int): BigDecimal? = if (count == 0) null else total.divide(count.toBigDecimal(), 8, RoundingMode.HALF_UP)
        val averageWin = average(winsTotal, wins); val averageLoss = average(lossesTotal, losses)
        return Performance(trades.size, wins, losses, trades.size - wins - losses, running, averageWin, averageLoss, average(running, trades.size),
            if (averageWin == null || averageLoss == null) null else (winsTotal * losses.toBigDecimal()).divide(lossesTotal.abs() * wins.toBigDecimal(), 8, RoundingMode.HALF_UP),
            reviewed, maxWinning, maxLosing, drawdown)
    }
    fun report(trades: List<Trade>, reviews: List<DailyReview>, filter: TradeFilter, today: LocalDate): AnalysisReport {
        // This entry point takes ONE already-filtered currency. Mixing currencies is rejected.
        require(trades.map { it.currency }.distinct().size <= 1) { "不同币种不能混合统计" }
        fun amount(group: List<Trade>) = group.fold(BigDecimal.ZERO) { sum, t -> sum + Money.value(t) }
        val daily = trades.groupBy { it.date }.toSortedMap().map { (date, group) -> AmountPoint(date, amount(group), group.size) }
        var cumulative = BigDecimal.ZERO
        val curve = daily.map { cumulative += it.amount; CurvePoint(it.date, cumulative) }
        val weekly = trades.groupBy { LocalDate.ofEpochDay(it.date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay() }
            .toSortedMap().map { (date, group) -> AmountPoint(date, amount(group), group.size) }
        fun groups(key: (Trade) -> String) = trades.groupBy(key).map { (id, group) -> GroupResult(id, performance(group)) }
            .sortedWith(compareByDescending<GroupResult> { it.performance.netPnl }.thenBy { it.key })
        fun tags(key: (Trade) -> List<String>): List<GroupResult> {
            val grouped = linkedMapOf<String, MutableList<Trade>>()
            trades.forEach { t -> key(t).distinct().forEach { id -> grouped.getOrPut(id) { mutableListOf() }.add(t) } }
            return grouped.map { (id, group) -> GroupResult(id, performance(group)) }
                .sortedWith(compareByDescending<GroupResult> { it.performance.count }.thenBy { it.key })
        }
        val dates = trades.map { it.date }.toSet(); val range = filter.bounds(today)
        val ratings = reviews.filter { it.reviewSaved && (range == null || it.date in range) && (!filter.hasTradeConditions || it.date in dates) }
            .sortedBy { it.date }.map { RatingPoint(it.date, it.rating) }
        return AnalysisReport(performance(trades), daily, weekly, curve, groups { it.strategy },
            trades.groupBy { it.market to it.symbol }.map { (key, group) -> GroupResult(key.second, performance(group), key.first) },
            groups { it.market }, groups { it.direction }, tags { it.mistakes }, tags { it.discipline }, groups { it.emotion },
            groups { if (it.mistakes.isEmpty()) "without" else "with" }, ratings)
    }
    fun calculate(trades: List<Trade>, reviews: List<DailyReview>, currency: String, filter: TradeFilter, today: LocalDate = LocalDate.now()): AnalyticsState {
        val filtered = TradeFilters.apply(trades, currency, filter, today)
        val home = trades.filter { it.currency == currency }
        return AnalyticsState(false, filtered, report(filtered, reviews, filter, today), TradingCalendar.daily(trades), performance(home), performance(home.filter { it.date == today.toEpochDay() }), RiskAnalytics.report(filtered))
    }
}
