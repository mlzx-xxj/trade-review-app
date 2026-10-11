package com.tradereview.app.domain

import com.tradereview.app.data.Trade
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AnalyticsTest {
    private fun trade(pnl: String, date: Long = 20_000, currency: String = "CNY", strategy: String = "trend", mistakes: List<String> = emptyList()) =
        Trade(netPnl = pnl, date = date, currency = currency, strategy = strategy, mistakes = mistakes)

    @Test fun decimalArithmeticAndCurrencyIsolation() {
        val result = Analytics.calculate(listOf(trade("0.10"), trade("0.20"), trade("-0.05"), trade("999.00", currency = "USD")), "CNY")
        assertEquals(3, result.count)
        assertEquals(BigDecimal("0.25"), result.netPnl)
        assertEquals(66.6667, result.winRate, 0.001)
    }

    @Test fun breakEvenAndEmptyDataHaveDefinedRates() {
        val result = Analytics.calculate(listOf(trade("10.00"), trade("0.00"), trade("-10.00")), "CNY")
        assertEquals(1, result.wins)
        assertEquals(1, result.losses)
        assertEquals(33.3333, result.winRate, 0.001)
        val empty = Analytics.calculate(emptyList(), "CNY")
        assertEquals(0.0, empty.winRate, 0.0)
        assertEquals(0.0, empty.reviewRate, 0.0)
        assertTrue(empty.curve.isEmpty())
    }

    @Test fun curveGroupsDaysAndSortsChronologically() {
        val result = Analytics.calculate(listOf(trade("3.00", 3), trade("2.00", 2), trade("-1.00", 2), trade("9.00", 1)), "CNY", since = 2)
        assertEquals(listOf(2L, 3L), result.curve.map { it.date })
        assertEquals(listOf(BigDecimal("1.00"), BigDecimal("4.00")), result.curve.map { it.cumulative })
    }

    @Test fun mistakesCountOncePerTradeAndStrategyIsIndependent() {
        val result = Analytics.calculate(listOf(trade("30.00", mistakes = listOf("chase", "chase")),
            trade("-10.00", mistakes = listOf("chase", "no_stop")), trade("5.00", strategy = "range")), "CNY")
        assertEquals(listOf("chase" to 2, "no_stop" to 1), result.mistakes)
        assertEquals("trend", result.strategies.first().strategy)
        assertEquals(2, result.strategies.first().count)
        assertEquals(1, result.strategies.first().wins)
        assertEquals(BigDecimal("20.00"), result.strategies.first().netPnl)
    }

    @Test fun amountValidationRejectsMalformedOversizedOrOverPreciseInput() {
        listOf("NaN", "Infinity", "1e3", "1.234", "--1", "1234567890123", "", "1,000").forEach { assertNull(Money.parse(it)) }
        assertEquals(BigDecimal("-12.30"), Money.parse(" -12.3 "))
        assertEquals("+0.30", Money.format(BigDecimal("0.30"), signed = true))
    }

    @Test fun validationRejectsInvalidPositionSymbolAndFutureDates() {
        assertNotNull(Analytics.validate(trade("1.00", 4), today = 3))
        assertNotNull(Analytics.validate(trade("1.00").copy(positionPercent = 0), today = 30_000))
        assertNotNull(Analytics.validate(trade("1.00").copy(symbol = " "), today = 30_000))
        assertNull(Analytics.validate(trade("1.00"), today = 30_000))
    }
}
