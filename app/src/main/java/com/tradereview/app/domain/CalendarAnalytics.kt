package com.tradereview.app.domain

import com.tradereview.app.data.DailyReview
import com.tradereview.app.data.Trade
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class DailyPnl(val date: Long, val currency: String, val count: Int, val netPnl: BigDecimal) {
    val status: String get() = when (netPnl.signum()) { 1 -> "盈利"; -1 -> "亏损"; else -> "持平" }
}
data class AmountPoint(val date: Long, val amount: BigDecimal, val count: Int)
data class RatingPoint(val date: Long, val rating: Int)
data class Performance(val count: Int = 0, val wins: Int = 0, val losses: Int = 0, val flats: Int = 0,
    val netPnl: BigDecimal = BigDecimal.ZERO, val averageWin: BigDecimal? = null, val averageLoss: BigDecimal? = null,
    val averageNet: BigDecimal? = null, val payoffRatio: BigDecimal? = null, val reviewed: Int = 0,
    val longestWins: Int = 0, val longestLosses: Int = 0, val maxDrawdown: BigDecimal = BigDecimal.ZERO) {
    val winRate: Double? get() = if (count == 0) null else wins * 100.0 / count
    val reviewRate: Double? get() = if (count == 0) null else reviewed * 100.0 / count
    val smallSample: Boolean get() = count in 1..19
}
data class GroupResult(val key: String, val performance: Performance, val market: String = "")
data class AnalysisReport(val performance: Performance = Performance(), val daily: List<AmountPoint> = emptyList(),
    val weekly: List<AmountPoint> = emptyList(), val curve: List<CurvePoint> = emptyList(),
    val strategies: List<GroupResult> = emptyList(), val instruments: List<GroupResult> = emptyList(),
    val markets: List<GroupResult> = emptyList(), val directions: List<GroupResult> = emptyList(),
    val mistakes: List<GroupResult> = emptyList(), val discipline: List<GroupResult> = emptyList(),
    val emotions: List<GroupResult> = emptyList(), val errorPresence: List<GroupResult> = emptyList(),
    val ratings: List<RatingPoint> = emptyList())
data class AnalyticsState(val loading: Boolean = true, val filtered: List<Trade> = emptyList(), val report: AnalysisReport = AnalysisReport(),
    val calendar: Map<Long, List<DailyPnl>> = emptyMap(), val home: Performance = Performance(), val today: Performance = Performance())

object TradingCalendar {
    /** Null cells are padding; all real dates are LocalDate epoch days, never UTC millis. */
    fun monthCells(month: YearMonth): List<Long?> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
        return List(cellCount) { index -> (index - leading + 1).takeIf { it in 1..month.lengthOfMonth() }?.let { month.atDay(it).toEpochDay() } }
    }
    fun months(ending: YearMonth, count: Int) = (count - 1 downTo 0).map { ending.minusMonths(it.toLong()) }
    fun daily(trades: List<Trade>): Map<Long, List<DailyPnl>> = trades.groupBy { it.date }.mapValues { (day, values) ->
        values.groupBy { it.currency }.map { (currency, group) -> DailyPnl(day, currency, group.size, group.fold(BigDecimal.ZERO) { sum, t -> sum + Money.value(t) }) }.sortedBy { it.currency }
    }
    fun heatIntensity(value: BigDecimal, maximumAbsolute: BigDecimal): Float = if (value.signum() == 0 || maximumAbsolute.signum() == 0) 0f
        else value.abs().divide(maximumAbsolute.abs(), 6, RoundingMode.HALF_UP).coerceIn(BigDecimal.ZERO, BigDecimal.ONE).toFloat()
}

object AdvancedAnalytics {
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
        return AnalyticsState(false, filtered, report(filtered, reviews, filter, today), TradingCalendar.daily(trades), performance(home), performance(home.filter { it.date == today.toEpochDay() }))
    }
}
