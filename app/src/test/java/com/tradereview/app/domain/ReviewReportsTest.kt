package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewReportsTest {
    private fun day(s: String)=LocalDate.parse(s).toEpochDay()
    private val p=ReviewPeriod.at("week",LocalDate.parse("2025-01-01"))
    @Test fun mondayWeekCrossesYearAndSundayBelongsToOldWeek() {
        assertEquals(day("2024-12-30"),p.start);assertEquals(day("2025-01-05"),p.end)
        assertEquals(p,ReviewPeriod.at("week",LocalDate.parse("2025-01-05")))
        assertEquals(day("2025-01-06"),p.shift(1).start);assertEquals(day("2024-12-23"),p.shift(-1).start)
    }
    @Test fun leapFebruaryMonthEndAndDecemberShiftUseEpochDays() {
        val leap=ReviewPeriod.at("month",LocalDate.parse("2024-02-29"));assertEquals(day("2024-02-01"),leap.start);assertEquals(day("2024-02-29"),leap.end)
        val next=ReviewPeriod.at("month",LocalDate.parse("2024-12-31")).shift(1);assertEquals(day("2025-01-01"),next.start);assertEquals(day("2025-01-31"),next.end)
    }
    @Test fun currenciesNeverMixAndDecimalDailyAmountsRemainExact() {
        val rows=listOf(Trade(date=p.start,netPnl="0.10"),Trade(date=p.start,netPnl="0.20"),Trade(date=p.start,currency="USD",netPnl="99.00"),Trade(date=p.start,currency="USDT",netPnl="-5.00"))
        val r=ReviewReports.calculate(rows,emptyList(),p,day("2025-01-05"))
        assertEquals(4,r.trades.size);assertEquals("0.30",r.currencies[0].analysis.performance.netPnl.toPlainString());assertEquals("99.00",r.currencies[1].analysis.performance.netPnl.toPlainString());assertEquals("-5.00",r.currencies[2].analysis.performance.netPnl.toPlainString())
        assertEquals("0.30",r.currencies[0].analysis.daily.single().amount.toPlainString())
    }
    @Test fun validRiskOnlyContributesToMeanAndCumulativeR() {
        val rows=listOf(Trade(date=p.start,netPnl="400.00",professional=ProfessionalTrade(riskMode="amount",manualRisk="200.00")),Trade(date=p.start,netPnl="-100.00",professional=ProfessionalTrade(riskMode="amount",manualRisk="100.00")),Trade(date=p.start,netPnl="999.00"))
        val r=ReviewReports.calculate(rows,emptyList(),p,day("2025-01-05")).currencies[0].risk.r
        assertEquals(2,r.valid);assertEquals(3,r.total);assertEquals("+0.500 R",ProfessionalMath.formatR(r.average));assertEquals("+1.000 R",ProfessionalMath.formatR(r.cumulative))
    }
    @Test fun unknownPlanAndDisciplineDoNotBecomeCompliance() {
        val a=Trade(date=p.start,professional=ProfessionalTrade(planStatus="in"),discipline=listOf("follow_plan"));val b=Trade(date=p.start,reviewed=true)
        val r=ReviewReports.calculate(listOf(a,b),emptyList(),p,day("2025-01-05"));assertEquals(1,r.plansIn);assertEquals(1,r.plansUnknown);assertEquals(0,r.riskChecked);assertEquals(1,r.disciplineProvided)
    }
    @Test fun countsOutcomesAndZeroAndEmptySamplesHonestly() {
        val empty=ReviewReports.calculate(emptyList(),emptyList(),p,day("2025-01-05"));assertNull(empty.currencies[0].risk.r.average);assertNull(empty.currencies[0].analysis.performance.winRate)
        val flat=ReviewReports.calculate(listOf(Trade(date=p.start,netPnl="0.00")),emptyList(),p,day("2025-01-05"));assertEquals(1,flat.flats);assertEquals(0,flat.wins);assertEquals(0,flat.losses)
    }
    @Test fun hiddenRenamedStableErrorSummariesHaveExplicitPriorEvidence() {
        val tags=CatalogSeeds.tags.map { if(it.kind=="mistake" && it.id=="chase")it.copy(name="冲动追价",hidden=true)else it }
        val rows=listOf(Trade(date=p.start,mistakes=listOf("chase","chase")),Trade(date=p.start-1,mistakes=listOf("chase")),Trade(date=p.start-2,mistakes=listOf("chase")))
        val r=ReviewReports.calculate(rows,emptyList(),p,day("2025-01-05"));assertEquals(listOf("chase" to 1),r.errors)
        val summary=ReviewReports.summary(r,Catalog(tags));assertTrue(summary.contains("冲动追价"));assertTrue(summary.contains("减少 1 笔"));assertTrue(summary.contains("上期 2 笔"))
    }
    @Test fun activePeriodExcludesFutureFactsWhilePriorComparisonUsesItsOwnBounds() {
        val rows=listOf(Trade(date=p.start),Trade(date=day("2025-01-01")),Trade(date=day("2025-01-02")),Trade(date=p.start-1),Trade(date=p.start-8))
        val r=ReviewReports.calculate(rows,listOf(DailyReview(date=p.start,reviewSaved=true),DailyReview(date=p.end,reviewSaved=true)),p,day("2025-01-01"))
        assertEquals(2,r.trades.size);assertEquals(1,r.previousCount);assertEquals(1,r.reviews.size);assertEquals(day("2025-01-01"),r.through)
    }
}
