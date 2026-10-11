package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone

class CalendarAnalyticsTest {
    private fun day(value: String) = LocalDate.parse(value).toEpochDay()
    private fun trade(id: Long, amount: String, date: String = "2024-02-29", currency: String = "CNY") = Trade(id = id, date = day(date), netPnl = amount, currency = currency)
    private fun decimal(expected: String, actual: BigDecimal?) { assertNotNull(actual); assertEquals(0, BigDecimal(expected).compareTo(actual)) }

    @Test fun mondayFirstCalendarHandlesMonthEdgesNewYearAndBothLeapYearRules() {
        listOf("2024-01", "2024-02", "2024-12", "2025-01", "2025-02", "2000-02", "2100-02").forEach { value ->
            val month = YearMonth.parse(value); val cells = TradingCalendar.monthCells(month)
            assertEquals(0, cells.size % 7)
            assertEquals(month.atDay(1).dayOfWeek.value - 1, cells.indexOfFirst { it != null })
            assertEquals((1..month.lengthOfMonth()).map { month.atDay(it).toEpochDay() }, cells.filterNotNull())
        }
        assertEquals(29, TradingCalendar.monthCells(YearMonth.of(2000, 2)).filterNotNull().size)
        assertEquals(28, TradingCalendar.monthCells(YearMonth.of(2100, 2)).filterNotNull().size)
        assertEquals(listOf(YearMonth.of(2023, 11), YearMonth.of(2023, 12), YearMonth.of(2024, 1)), TradingCalendar.months(YearMonth.of(2024, 1), 3))
    }
    @Test fun epochDatesDoNotChangeAcrossTimeZones() {
        val original = TimeZone.getDefault()
        try {
            val reference = TradingCalendar.monthCells(YearMonth.of(2024, 2))
            listOf("Pacific/Kiritimati", "America/Los_Angeles", "Asia/Shanghai").forEach {
                TimeZone.setDefault(TimeZone.getTimeZone(it)); assertEquals(reference, TradingCalendar.monthCells(YearMonth.of(2024, 2)))
                assertEquals("2024-02-29", LocalDate.ofEpochDay(day("2024-02-29")).toString())
            }
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun dailyTotalsAreExactAndNeverMixCnyUsdOrUsdt() {
        val values = listOf(trade(1, "0.10"), trade(2, "0.20"), trade(3, "-0.30"), trade(4, "999999999999.99", currency = "USD"), trade(5, "-0.01", currency = "USDT"))
        val daily = TradingCalendar.daily(values)[day("2024-02-29")]!!.associateBy { it.currency }
        decimal("0.00", daily["CNY"]!!.netPnl); assertEquals(3, daily["CNY"]!!.count); assertEquals("持平", daily["CNY"]!!.status)
        decimal("999999999999.99", daily["USD"]!!.netPnl); decimal("-0.01", daily["USDT"]!!.netPnl)
        assertNull(TradingCalendar.daily(values)[day("2024-03-01")])
        assertThrows(IllegalArgumentException::class.java) { AdvancedAnalytics.report(values, emptyList(), TradeFilter(), LocalDate.of(2024, 3, 1)) }
    }
    @Test fun emptyAndFlatSamplesDoNotInventRatesOrChartPoints() {
        val empty = AdvancedAnalytics.report(emptyList(), emptyList(), TradeFilter(), LocalDate.of(2024, 3, 1))
        assertNull(empty.performance.winRate); assertNull(empty.performance.reviewRate); assertNull(empty.performance.averageNet); assertNull(empty.performance.payoffRatio)
        assertTrue(empty.daily.isEmpty()); assertTrue(empty.curve.isEmpty()); assertTrue(empty.strategies.isEmpty())
        val flat = AdvancedAnalytics.performance(listOf(trade(1, "0.00")))
        assertEquals(1, flat.flats); assertEquals(0.0, flat.winRate!!, 0.0); decimal("0", flat.averageNet); assertNull(flat.averageWin); assertNull(flat.averageLoss)
    }
    @Test fun streaksAreChronologicalAndFlatsBreakBothStreaks() {
        val values = listOf("10.00", "20.00", "0.00", "5.00", "-8.00", "-12.00", "-9.00", "4.00").mapIndexed { i, pnl -> trade(i + 1L, pnl) }
        val result = AdvancedAnalytics.performance(values.reversed())
        assertEquals(2, result.longestWins); assertEquals(3, result.longestLosses); assertEquals(4, result.wins); assertEquals(3, result.losses); assertEquals(1, result.flats)
        decimal("10", result.netPnl); decimal("29", result.maxDrawdown)
    }
    @Test fun drawdownIncludesInitialZeroPeakAndIntradayLosses() {
        val result = AdvancedAnalytics.performance(listOf(trade(1, "-10.00"), trade(2, "30.00"), trade(3, "-25.00"), trade(4, "1.00")))
        decimal("25", result.maxDrawdown); decimal("-4", result.netPnl)
        decimal("10", AdvancedAnalytics.performance(listOf(trade(1, "-10.00"))).maxDrawdown)
        decimal("0", AdvancedAnalytics.performance(listOf(trade(1, "5.00"))).maxDrawdown)
    }
    @Test fun averagesRatiosAndWeeklyCurveUseOneCurrencyAndOneFilteredSample() {
        val values = listOf(trade(1, "0.10", "2023-12-31"), trade(2, "0.20", "2024-01-01"), trade(3, "-0.05", "2024-01-01"), trade(4, "500.00", "2024-01-01", "USD"))
        val state = AdvancedAnalytics.calculate(values, emptyList(), "CNY", TradeFilter(), LocalDate.of(2024, 1, 1))
        decimal("0.25", state.report.performance.netPnl); decimal("0.15", state.report.performance.averageWin); decimal("-0.05", state.report.performance.averageLoss); decimal("3", state.report.performance.payoffRatio)
        assertEquals(listOf(day("2023-12-25"), day("2024-01-01")), state.report.weekly.map { it.date })
        decimal("0.15", state.report.weekly.last().amount); decimal("0.25", state.report.curve.last().cumulative)
        assertEquals(state.filtered.size, state.report.performance.count)
    }
    @Test fun mistakesCountOncePerTradeAndGroupsHaveRealSampleSizes() {
        val values = listOf(trade(1, "10.00").copy(mistakes = listOf("chase", "chase"), emotion = "anxious"), trade(2, "-2.00").copy(mistakes = listOf("chase", "no_stop")), trade(3, "1.00"))
        val report = AdvancedAnalytics.report(values, emptyList(), TradeFilter(), LocalDate.of(2024, 3, 1))
        assertEquals(2, report.mistakes.first { it.key == "chase" }.performance.count)
        decimal("8", report.errorPresence.first { it.key == "with" }.performance.netPnl)
        assertEquals(1, report.emotions.first { it.key == "anxious" }.performance.count)
        assertTrue(report.performance.smallSample)
    }
    @Test fun executionRatingTrendRespectsDatesAndMatchedTradeDays() {
        val reviews = listOf(DailyReview(date = day("2024-02-28"), reviewSaved = true, rating = 2), DailyReview(date = day("2024-02-29"), reviewSaved = true, rating = 5), DailyReview(date = day("2024-03-01"), planSaved = true))
        val f = TradeFilter(mistakes = listOf("chase"))
        val report = AdvancedAnalytics.report(listOf(trade(1, "1.00").copy(mistakes = listOf("chase"))), reviews, f, LocalDate.of(2024, 3, 1))
        assertEquals(listOf(RatingPoint(day("2024-02-29"), 5)), report.ratings)
    }
    @Test fun heatmapIntensityUsesMagnitudeAndBounds() {
        assertEquals(0f, TradingCalendar.heatIntensity(BigDecimal.ZERO, BigDecimal.TEN))
        assertEquals(0.5f, TradingCalendar.heatIntensity(BigDecimal("-5"), BigDecimal.TEN))
        assertEquals(1f, TradingCalendar.heatIntensity(BigDecimal("100"), BigDecimal.TEN))
    }
}
