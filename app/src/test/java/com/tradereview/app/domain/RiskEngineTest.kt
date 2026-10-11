package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test

class RiskEngineTest {
    private fun trade() = Trade(id = 3, date = 20000, netPnl = "-100.00", positionPercent = 25,
        professional = ProfessionalTrade(riskMode = "amount", manualRisk = "200.00", plannedPositionPercent = "20"))
    private val rule = RiskRules(accountEquity = "10000.00", maxRiskPercent = "1", maxPositionPercent = "20", lossStreakThreshold = 3, dailyLossLimit = "250.00")
    @Test fun allRulesUseSeparateRiskAndPositionDefinitionsAndIncludeThisTransaction() {
        val history = listOf(Trade(id = 1, date = 19999, netPnl = "-5.00"), Trade(id = 2, date = 20000, netPnl = "-160.00"), Trade(id = 4, date = 20000, currency = "USD", netPnl = "-999.00"))
        val e = RiskEngine.check(trade(), rule, history)
        assertEquals(RiskEngine.labels.keys.toList(), e.violations); assertEquals(3, e.lossStreak); assertEquals("-260.00", e.dailyNet)
        assertEquals("200", e.initialRisk)
    }
    @Test fun exactRiskLimitDoesNotTriggerAndMissingEquityCannotBeUsedAsZero() {
        val t = trade().copy(positionPercent = 10, professional = trade().professional!!.copy(manualRisk = "100.00", plannedPositionPercent = null), netPnl = "0.00")
        assertTrue(RiskEngine.check(t, rule, emptyList()).violations.isEmpty())
        val incomplete = RiskEngine.check(t.copy(professional = null), rule.copy(accountEquity = null), emptyList())
        assertTrue(incomplete.violations.isEmpty()); assertTrue(incomplete.notChecked.any { "缺少" in it })
    }
    @Test fun chronologicalBackfillAndFlatTradesBreakLossStreakWithoutCrossCurrencyMixing() {
        val list = listOf(Trade(id = 1, date = 19998, netPnl = "-1.00"), Trade(id = 2, date = 19999, netPnl = "0.00"), Trade(id = 7, date = 20001, netPnl = "-1000.00"))
        assertEquals(1, RiskEngine.check(trade(), rule, list).lossStreak)
        assertEquals("-100.00", RiskEngine.check(trade(), rule, list).dailyNet)
        assertThrows(IllegalArgumentException::class.java) { RiskEngine.check(trade(), rule.copy(currency = "USD"), list) }
    }
    @Test fun consentFingerprintIgnoresClockButChangesWhenRulesAmountsOrHistoryChange() {
        val first = RiskEngine.check(trade(), rule, emptyList(), "2026-10-11T00:00:00Z")
        assertEquals(RiskEngine.fingerprint(first), RiskEngine.fingerprint(first.copy(checkedAt = "2026-10-11T01:00:00Z")))
        assertNotEquals(RiskEngine.fingerprint(first), RiskEngine.fingerprint(first.copy(rules = rule.copy(maxRiskPercent = "3"))))
        assertNotEquals(RiskEngine.fingerprint(first), RiskEngine.fingerprint(first.copy(netPnl = "-101.00")))
        assertNotEquals(RiskEngine.fingerprint(first), RiskEngine.fingerprint(first.copy(dailyNet = "-200.00")))
    }
    @Test fun ruleFieldsAreStrictAndReviewOrNotesDoNotRecheckFinancialFacts() {
        assertFalse(RiskEngine.financialFactsChanged(trade(), trade().copy(reviewed = true, note = "备注")))
        assertFalse(RiskEngine.financialFactsChanged(trade(), trade().copy(professional = trade().professional!!.copy(reason = "补充理由"))))
        assertTrue(RiskEngine.financialFactsChanged(trade(), trade().copy(netPnl = "1.00")))
        listOf(rule.copy(maxRiskPercent = "0"), rule.copy(accountEquity = "-1.00"), rule.copy(lossStreakThreshold = 0), rule.copy(currency = "JPY"))
            .forEach { assertThrows(IllegalArgumentException::class.java) { ProfessionalMath.validateRule(it) } }
    }
}
