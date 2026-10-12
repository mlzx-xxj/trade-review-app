package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class SmartInsightsTest {
    private val today=LocalDate.of(2026,1,5).toEpochDay()
    private val settings=SmartSettings(minimumSamples=3,lookbackDays=7)
    private fun rows(day:Long,count:Int=3,pnl:String="-100.00",currency:String="CNY",mistakes:List<String> = emptyList(),reviewed:Boolean=false,plan:String="unknown")=
        (1..count).map { Trade(id=it.toLong(),date=day,netPnl=pnl,currency=currency,mistakes=mistakes,reviewed=reviewed,professional=ProfessionalTrade(riskMode="amount",manualRisk="100.00",planStatus=plan)) }
    private fun calculate(trades:List<Trade>,s:SmartSettings=settings,period:ReviewPeriod?=null,goals:List<ReviewGoal> = emptyList())=
        SmartInsights.calculate(trades,emptyList(),goals,s,CatalogSeeds.tags,today,period)
    @Test fun emptyLowSamplesAndDisabledCategoriesNeverInventInsights() {
        assertTrue(calculate(emptyList()).isEmpty());assertTrue(calculate(rows(today,2)).isEmpty());assertTrue(calculate(rows(today),settings.copy(enabledCategories=emptyList())).isEmpty())
    }
    @Test fun repeatedErrorsUseStableHiddenTagsAndTraceRealRows() {
        val now=rows(today,mistakes=listOf("chase"));val tags=CatalogSeeds.tags.map { if(it.kind=="mistake" && it.id=="chase")it.copy(name="自定义历史名字",hidden=true)else it }
        val result=SmartInsights.calculate(now,emptyList(),emptyList(),settings,tags,today)
        val r=result.first { it.id=="error:chase" };assertTrue(r.title.contains("自定义历史名字"));assertEquals(3,r.sample);assertEquals(now.map { it.recordKey }.toSet(),r.tradeKeys.toSet())
        assertEquals(result.size,result.map { it.id }.distinct().size)
    }
    @Test fun explicitPlanDenominatorExcludesUnknownAndDetectsIncrease() {
        val result=calculate(rows(today,plan="out")+rows(today-7,plan="in")+rows(today,plan="unknown"))
        val r=result.first { it.id=="plan-out" };assertEquals(3,r.sample);assertEquals(3,r.previousSample);assertTrue(r.evidence.contains("未知 3"))
        assertFalse(calculate(rows(today,plan="unknown")+rows(today-7,plan="in")).any { it.id=="plan-out" })
    }
    @Test fun reviewDeclineUsesAllTradesAndComparableWindowsAcrossYear() {
        val r=calculate(rows(today,reviewed=false)+rows(today-7,reviewed=true)).first { it.id=="review-rate" }
        assertEquals(today-6,r.start);assertEquals(today-13,r.previousStart);assertTrue(r.evidence.contains("100.00% → 0.00%"))
    }
    @Test fun strategiesSeparateCurrenciesAndOnlyEffectiveRiskSamplesCount() {
        val result=calculate(rows(today,pnl="200.00")+rows(today-7,pnl="-100.00")+rows(today,pnl="999.00",currency="USD"))
        val r=result.first { it.id=="strategy:trend:CNY" };assertTrue(r.evidence.contains("+2.000 R"));assertEquals(3,r.sample);assertFalse(result.any { it.id=="strategy:trend:USD" })
        val noRisk=rows(today,pnl="200.00").map { it.copy(professional=null) }+rows(today-7,pnl="100.00").map { it.copy(professional=null) }
        assertFalse(calculate(noRisk).any { it.id.startsWith("strategy:") })
    }
    @Test fun emotionsShowAssociationAndRiskUnknownNeverMeansCompliant() {
        val r=calculate(rows(today)).first { it.id=="emotion:calm:CNY" };assertTrue(r.formula.contains("不代表"));assertFalse(calculate(rows(today)).any { it.category=="risk" })
    }
    @Test fun riskChecksHavePriorityAndComparableCounts() {
        val rules=RiskRules(maxPositionPercent="5");val current=rows(today).map { t->t.copy(riskRecord=RiskRecord(listOf(RiskEngine.check(t,rules,emptyList())))) }
        val result=calculate(current);assertEquals("risk",result.first().id);assertTrue(result.first().evidence.contains("未检查 0"))
    }
    @Test fun streakFrequencyCountsCalendarDaysAndRespectsCurrency() {
        val r=calculate(rows(today-2)+rows(today-3,pnl="10.00")+rows(today,currency="USD",pnl="5.00")).first { it.id=="loss-frequency:CNY" }
        assertTrue(r.evidence.contains("连续亏损 3"));assertTrue(r.formula.contains("同日数据库 ID"));assertEquals(6,r.sample)
    }
    @Test fun weeklyMonthlyRangesCrossYearAndNeverMixMoney() {
        val week=ReviewPeriod.at("week",LocalDate.ofEpochDay(today));val w=calculate(rows(today)+rows(today-7),period=week)
        assertTrue(w.all { it.start==week.start });assertTrue(w.all { it.previousStart==week.shift(-1).start })
        val month=ReviewPeriod.at("month",LocalDate.ofEpochDay(today));val m=calculate(rows(today,mistakes=listOf("chase"))+rows(today-7,mistakes=listOf("chase")),period=month)
        assertTrue(m.all { it.previousStart==LocalDate.of(2025,12,1).toEpochDay() })
    }
    @Test fun errorImprovementRequiresBothCountsAndRatesToImprove() {
        val r=calculate(rows(today,3,mistakes=listOf("chase"))+rows(today-7,6,mistakes=listOf("chase")))
        assertFalse(r.any { it.id=="improved:chase" }) // equal 100% rate is not evidence of improvement
        val improved=calculate(rows(today,3)+rows(today-7,3,mistakes=listOf("chase")))
        assertTrue(improved.any { it.id=="improved:chase" })
    }
    @Test fun personalGoalStagnationHasEffectiveSamplesAndDoesNotMutateFacts() {
        val all=rows(today)+rows(today-7);val before=all.toList();val goal=ReviewGoal(title="补齐复盘",kind="review_ratio",period="week",startDate=ReviewPeriod.at("week",LocalDate.ofEpochDay(today-14)).start,threshold="80")
        assertTrue(calculate(all,goals=listOf(goal)).any { it.id=="goal:${goal.id}" });assertEquals(before,all)
        assertFalse(calculate(all,goals=listOf(goal.copy(kind="text",threshold=null))).any { it.id=="goal:${goal.id}" })
    }
}
