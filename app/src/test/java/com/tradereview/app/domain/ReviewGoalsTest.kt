package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewGoalsTest {
    private val p=ReviewPeriod.at("week",LocalDate.parse("2025-01-01"));private val today=p.end
    private fun goal(kind: String,threshold: String?="1")=ReviewGoal(title="改善",kind=kind,startDate=p.start,threshold=threshold,mistakeId=if(kind=="mistake_count")"chase"else null)
    @Test fun errorGoalCountsTradesOnceAndEmptySamplesAreUncomputable() {
        val g=goal("mistake_count");val rows=listOf(Trade(date=p.start,mistakes=listOf("chase")),Trade(date=p.start+1))
        val r=ReviewGoals.progress(g,rows,emptyList(),p,today);assertEquals("1",r.value!!.toPlainString());assertEquals(true,r.reached);assertEquals(2,r.denominator)
        assertNull(ReviewGoals.progress(g,emptyList(),emptyList(),p,today).reached)
    }
    @Test fun planRatioExcludesUnknownAndOnlyExplicitPlansCount() {
        val g=goal("plan_ratio","80");val rows=listOf(Trade(date=p.start,professional=ProfessionalTrade(planStatus="in")),Trade(date=p.start))
        val r=ReviewGoals.progress(g,rows,emptyList(),p,today);assertEquals(1,r.denominator);assertEquals(1,r.missing);assertEquals(true,r.reached)
        assertNull(ReviewGoals.progress(g,listOf(rows.last()),emptyList(),p,today).value)
    }
    @Test fun ratioDoesNotReachARoundedUpThreshold() {
        val rows=listOf(Trade(date=p.start,reviewed=true),Trade(date=p.start,reviewed=true),Trade(date=p.start))
        val r=ReviewGoals.progress(goal("review_ratio","66.67"),rows,emptyList(),p,today);assertEquals(false,r.reached);assertEquals(3,r.denominator)
    }
    @Test fun missingRiskCheckIsNotZeroViolationCompliance() {
        val unknown=Trade(date=p.start);val g=goal("risk_count","0")
        val r=ReviewGoals.progress(g,listOf(unknown),emptyList(),p,today);assertNull(r.value);assertEquals(1,r.missing);assertEquals(0,r.denominator)
        val t=Trade(date=p.start,netPnl="-2.00");val event=RiskEngine.check(t,RiskRules(dailyLossLimit="1.00"),emptyList())
        val known=t.copy(riskRecord=RiskRecord(listOf(event)));val checked=ReviewGoals.progress(g,listOf(known,unknown),emptyList(),p,today);assertEquals("1",checked.value!!.toPlainString());assertEquals(false,checked.reached)
    }
    @Test fun savedReviewDaysAndArchiveCutoffDoNotInventMissingScores() {
        val g=goal("review_days","2").copy(archivedDay=p.start+2)
        val logs=listOf(DailyReview(date=p.start,reviewSaved=true),DailyReview(date=p.start+1,reviewSaved=false),DailyReview(date=p.start+2,reviewSaved=true),DailyReview(date=p.end,reviewSaved=true))
        val r=ReviewGoals.progress(g,emptyList(),logs,p,today);assertEquals("2",r.value!!.toPlainString());assertEquals(3,r.denominator);assertEquals(1,r.missing)
        assertNull(ReviewGoals.progress(g,emptyList(),logs,p.shift(1),p.shift(1).end).value)
        assertEquals(4,ReviewGoals.history(g,emptyList(),logs,p,today).size)
    }
    @Test fun textGoalIsManualAndValidationRetainsHiddenStableIds() {
        val text=goal("text",null);ReviewGoals.validate(text,CatalogSeeds.tags);assertNull(ReviewGoals.progress(text,emptyList(),emptyList(),p,today).value)
        ReviewGoals.validate(goal("mistake_count"),CatalogSeeds.tags.map { it.copy(hidden=true) })
        assertThrows(IllegalArgumentException::class.java){ReviewGoals.validate(goal("mistake_count").copy(mistakeId="missing"),CatalogSeeds.tags)}
        assertThrows(IllegalArgumentException::class.java){ReviewGoals.validate(text.copy(threshold="0"),CatalogSeeds.tags)}
        assertThrows(IllegalArgumentException::class.java){ReviewGoals.validate(goal("review_ratio","101"),CatalogSeeds.tags)}
    }
}
