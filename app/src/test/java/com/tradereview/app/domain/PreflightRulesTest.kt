package com.tradereview.app.domain
import com.tradereview.app.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
class PreflightRulesTest {
    private val today=LocalDate.of(2026,1,5).toEpochDay()
    @Test fun remindersUsePastFactsGoalsAndRulesAndNeverFutureTrades() {
        val past=Trade(date=today-1,mistakes=listOf("chase"));val future=Trade(date=today+1,mistakes=listOf("oversize"))
        val goal=ReviewGoal(title="人工核对",kind="text",startDate=ReviewPeriod.at("week",LocalDate.ofEpochDay(today)).start)
        val list=PreflightRules.reminders(today,listOf(past,future),emptyList(),listOf(goal),listOf(RiskRules(maxPositionPercent="20")),SmartSettings(),CatalogSeeds.tags)
        assertTrue(list.any { it.key=="auto:mistake:chase" && "trade:${past.recordKey}" in it.refs });assertFalse(list.any { it.key=="auto:mistake:oversize" })
        assertTrue(list.any { it.key=="auto:goal:${goal.id}" });assertTrue(list.any { it.key=="auto:rule:CNY" });assertTrue(list.any { it.key=="auto:review:${today-1}" })
    }
    @Test fun disabledAutomaticRemindersReturnNoGeneratedItems() { assertTrue(PreflightRules.reminders(today,listOf(Trade(date=today-1,mistakes=listOf("chase"))),emptyList(),emptyList(),emptyList(),SmartSettings(automaticReminders=false),CatalogSeeds.tags).isEmpty()) }
    @Test fun yesterdayWithoutActivityDoesNotInventIncompleteReview() {
        assertTrue(PreflightRules.reminders(today,emptyList(),emptyList(),emptyList(),emptyList(),SmartSettings(),CatalogSeeds.tags).isEmpty())
        assertTrue(PreflightRules.reminders(today,listOf(Trade(date=today-1)),listOf(DailyReview(date=today-1,reviewSaved=true)),emptyList(),emptyList(),SmartSettings(),CatalogSeeds.tags).isEmpty())
    }
    @Test fun hiddenRenamedTagsKeepReferencesAndGoalArchiveDoesNotMutateHistory() {
        val t=Trade(date=today-1,mistakes=listOf("chase"));val tags=CatalogSeeds.tags.map { if(it.id=="chase")it.copy(name="更名错误",hidden=true)else it }
        val a=PreflightRules.reminders(today,listOf(t),emptyList(),emptyList(),emptyList(),SmartSettings(),tags)
        assertTrue(a.first().text.contains("更名错误"));assertEquals("auto:mistake:chase",a.first().key);assertEquals(listOf("chase"),t.mistakes)
    }
}
