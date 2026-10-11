package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RiskAnalyticsTest {
    private fun t(id: Long, pnl: String, risk: String?, plan: String = "unknown", strategy: String = "trend") = Trade(id = id, date = 20000,
        market = "futures", symbol = "螺纹钢", netPnl = pnl, strategy = strategy,
        professional = risk?.let { ProfessionalTrade(riskMode = "amount", manualRisk = it, planStatus = plan) })
    private fun eq(s: String, actual: BigDecimal?) = assertEquals(0, BigDecimal(s).compareTo(requireNotNull(actual)))
    @Test fun onlyValidRiskSamplesEnterMeanCumulativeAndExpectation() {
        val list = listOf(t(1,"400.00","200.00","in"), t(2,"-100.00","100.00","out"), t(3,"0.00","50.00"), t(4,"999.00",null))
        val p = RiskAnalytics.report(list)
        assertEquals(3, p.r.valid); assertEquals(4,p.r.total); eq("1",p.r.cumulative); eq("0.333333333333333333333333",p.r.average)
        eq("2",p.r.winningAverage); eq("-1",p.r.losingAverage); eq("100",p.r.expectationNet)
        assertEquals(3,p.rBuckets.sumOf { it.trades.size }); assertEquals(4,p.pnlBuckets.sumOf { it.trades.size })
        val unknown = p.plans.single { it.key == "unknown" }; assertEquals(2,unknown.performance.total); eq("999",unknown.performance.allMoney.netPnl)
    }
    @Test fun allMissingRiskAndNoTradesDoNotProduceZeroR() {
        val p = RiskAnalytics.report(listOf(t(1,"-1.00",null)))
        assertEquals(0,p.r.valid); assertNull(p.r.average); assertNull(p.r.cumulative); assertNull(p.r.expectationNet)
        val empty = RiskAnalytics.report(emptyList()); assertEquals(0,empty.r.total); assertTrue(empty.strategies.isEmpty()); assertEquals("—",RiskAnalytics.percent(0,0))
    }
    @Test fun originalFiltersDriveRByMarketStrategySymbolAndCurrencyWithoutChangingV04Metrics() {
        val list = listOf(t(1,"400.00","200.00"),t(2,"-100.00","100.00",strategy = "breakout"),t(3,"1.00","1.00").copy(symbol="铁矿石"),t(4,"999.00","1.00").copy(currency="USD"))
        val f = TradeFilter(markets = listOf("futures"), symbols = listOf("螺纹钢"), strategies = listOf("trend"))
        val a = AdvancedAnalytics.calculate(list,emptyList(),"CNY",f,LocalDate.ofEpochDay(20001))
        assertEquals(1,a.filtered.size); eq("2",a.risk.r.average); eq("400",a.report.performance.netPnl)
        assertEquals(AdvancedAnalytics.performance(a.filtered),a.report.performance)
        assertThrows(IllegalArgumentException::class.java) { RiskAnalytics.report(list) }
    }
    @Test fun savedRuleChangesCannotEraseViolationsAndCurrentPlannedPositionHasItsOwnDenominator() {
        val trade = t(1,"400.00","200.00").copy(positionPercent=30,professional=t(1,"400.00","200.00").professional!!.copy(plannedPositionPercent="20"))
        val first = RiskEngine.check(trade,RiskRules(maxPositionPercent="20"),emptyList())
        val second = RiskEngine.check(trade,RiskRules(maxPositionPercent="100"),emptyList())
        val p = RiskAnalytics.report(listOf(trade.copy(riskRecord=RiskRecord(listOf(first,second))),t(2,"-1.00",null)))
        assertEquals(1,p.violations.size); assertEquals(1,p.checked); assertEquals(1,p.exceededPositions.size); assertEquals(1,p.plannedPositions)
        assertEquals(1,p.violationGroups.single { it.label==RiskEngine.labels["position_limit"] }.trades.size)
    }
    @Test fun consecutiveLossSegmentsAreRealFilteredChronologicalSequences() {
        val list = listOf(t(1,"-1.00",null),t(2,"-1.00",null),t(3,"-1.00",null),t(4,"0.00",null),t(5,"-1.00",null),t(6,"-1.00",null))
        val p=RiskAnalytics.report(list.reversed()); assertEquals(2,p.lossRuns); assertEquals(3,p.longestLoss)
        assertEquals(AdvancedAnalytics.performance(list).longestLosses,p.longestLoss)
    }
    @Test fun missingRiskOrEquityDoesNotBecomeAnEvaluatedCompliantTrade() {
        val trade=t(1,"-1.00",null)
        val e=RiskEngine.check(trade,RiskRules(maxRiskPercent="1"),emptyList())
        val report=RiskAnalytics.report(listOf(trade.copy(riskRecord=RiskRecord(listOf(e)))))
        assertEquals(0,report.checked);assertTrue(report.violations.isEmpty());assertEquals("—",RiskAnalytics.percent(0,report.checked))
    }
}
