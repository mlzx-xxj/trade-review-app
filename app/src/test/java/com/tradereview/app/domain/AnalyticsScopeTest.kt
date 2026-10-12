package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnalyticsScopeTest {
    @Test fun switchingCurrencyOrFilterCannotRelabelOldFinancialResults(){
        val records=listOf(Trade(netPnl="100.00"),Trade(currency="USD",netPnl="999.99"));val f=TradeFilter()
        val cny=AdvancedAnalytics.calculate(records,emptyList(),"CNY",f)
        val scope=cny.source!!
        assertTrue(scope.matches(records,emptyList(),"CNY",f));assertFalse(scope.matches(records,emptyList(),"USD",f))
        assertFalse(scope.matches(records,emptyList(),"CNY",f.copy(directions=listOf("short"))))
        assertEquals("100.00",cny.home.netPnl.toPlainString());assertEquals("999.99",AdvancedAnalytics.calculate(records,emptyList(),"USD",f).home.netPnl.toPlainString())
    }
    @Test fun changedFactsAndDailyReviewCannotBeDisplayedWithStaleDerivedReport(){
        val records=listOf(Trade(netPnl="100.00"));val review=DailyReview(reviewSaved=true);val f=TradeFilter()
        val source=AdvancedAnalytics.calculate(records,listOf(review),"CNY",f).source!!
        assertFalse(source.matches(records.map{it.copy(netPnl="200.00")},listOf(review),"CNY",f))
        assertFalse(source.matches(records,listOf(review.copy(rating=1)),"CNY",f))
    }
    @Test fun sourceSnapshotDoesNotChangeWhenCallerReplacesListMembers(){
        val records=mutableListOf(Trade(netPnl="100.00"));val f=TradeFilter();val calculated=AdvancedAnalytics.calculate(records,emptyList(),"CNY",f,LocalDate.now())
        records[0]=records[0].copy(netPnl="200.00")
        assertFalse(calculated.source!!.matches(records,emptyList(),"CNY",f));assertEquals("100.00",calculated.home.netPnl.toPlainString())
    }
}
