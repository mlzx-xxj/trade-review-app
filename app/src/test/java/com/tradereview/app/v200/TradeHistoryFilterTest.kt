package com.tradereview.app.v200

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TradeHistoryFilterTest {
    private val day=LocalDate.of(2026,10,14).toEpochDay()
    private fun fixture():LedgerSnapshot {
        fun stock(id:String,code:String,name:String,date:Long,ordinal:Long,price:String,quantity:String,fees:String,side:String="buy")=
            LedgerExecution(id,"stock",code,name,date,600,date,ordinal,side,if(side=="buy")"open"else"close",price,quantity,fees,strategyId="trend")
        val ironOpen=AutomaticEntry.prepare(LedgerExecution("iron_open","futures","i2701","铁矿石",day+1,600,day+1,5,"buy","open","800","2","0",feeSource=AutomaticEntry.SOURCE),null,emptyList(),emptyList())
        val ironClose=AutomaticEntry.prepare(LedgerExecution("iron_close","futures","i2701","铁矿石",day+2,600,day+2,8,"sell","close","810","1","0",feeSource=AutomaticEntry.SOURCE),null,listOf(ironOpen),emptyList())
        val facts=listOf(
            stock("pufa_buy","600000","浦发银行",day,1,"10","100","0.10"),
            LedgerExecution("rb_open","futures","rb2701","螺纹钢",day,610,day,2,"buy","open","3500","2","4.01",multiplier="10",marginRate="0.1",parametersConfirmed=true),
            stock("pingan_buy","000001","平安银行",day+1,3,"12","100","0.12"),
            stock("pufa_sell","600000","浦发银行",day+1,4,"11","40","0.264","sell"),
            ironOpen,
            stock("pingan_sell","000001","平安银行",day+2,6,"11","100","0.66","sell"),
            LedgerExecution("rb_close","futures","rb2701","螺纹钢",day+2,610,day+2,7,"sell","close","3510","1","3.01",multiplier="10",marginRate="0.1",parametersConfirmed=true),
            ironClose
        )
        val result=MatchingEngine.replay(facts)
        return LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股"),LedgerAccount("futures","futures","国内期货")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")),strategies=listOf(LedgerStrategy("trend","趋势")),executions=facts,lots=result.lots,matches=result.matches,cycles=result.cycles)
            .also{LedgerValidation.snapshot(it)}
    }
    private fun ordered(state:LedgerSnapshot)=MatchingEngine.ordered(state.executions).reversed()

    @Test fun searchesStockFuturesAndChineseNamesIgnoringCaseAndOuterWhitespace() {
        val history=ordered(fixture())
        for(query in listOf("600000"," 浦发 "))assertEquals(listOf("pufa_sell","pufa_buy"),filterTradeHistory(history,query).map{it.id})
        for(query in listOf("rb"," RB2701 ","螺纹钢"))assertEquals(listOf("rb_close","rb_open"),filterTradeHistory(history,query).map{it.id})
        for(query in listOf(" I2701 ","铁矿"))assertEquals(listOf("iron_close","iron_open"),filterTradeHistory(history,query).map{it.id})
        assertTrue(filterTradeHistory(history,"不存在").isEmpty())
    }

    @Test fun marketAndTextCombineAndClearingBothRestoresEveryRecord() {
        val history=ordered(fixture())
        assertEquals(8,filterTradeHistory(history,"").size)
        assertEquals(4,filterTradeHistory(history,"","stock").size)
        assertEquals(4,filterTradeHistory(history,"","futures").size)
        assertEquals(2,filterTradeHistory(history,"600000","stock").size)
        assertEquals(2,filterTradeHistory(history,"rb2701","futures").size)
        assertTrue(filterTradeHistory(history,"rb2701","stock").isEmpty())
        assertTrue(filterTradeHistory(history,"银行","futures").isEmpty())
        assertEquals(history,filterTradeHistory(history,"  ","all"))
    }

    @Test fun filteringKeepsExistingMixedLegacyAndAutomaticOrderAndOriginalDetailIds() {
        val state=fixture();val history=ordered(state)
        assertEquals(listOf("iron_close","iron_open","rb_close","pingan_sell","pufa_sell","pingan_buy","rb_open","pufa_buy"),history.map{it.id})
        val selected=filterTradeHistory(history,"2701","futures")
        assertEquals(listOf("iron_close","iron_open","rb_close","rb_open"),selected.map{it.id})
        for(result in selected)assertSame(result,state.executions.single{it.id==result.id})
    }

    @Test fun searchesCannotChangeCountsAmountsSnapshotsMatchesOrCurrentPositions() {
        val state=fixture();val before=LedgerJson.text(state)
        val history=ordered(state)
        val amountBefore=sum(state.executions.map{decimal(it.price)*decimal(it.quantity)})
        for(market in listOf("all","stock","futures"))for(query in listOf("","600000","rb2701","铁矿","不存在"))filterTradeHistory(history,query,market)
        assertEquals(before,LedgerJson.text(state))
        assertEquals(8,state.executions.size)
        assertEquals(amountBefore,sum(state.executions.map{decimal(it.price)*decimal(it.quantity)}))
        assertEquals(4,state.matches.size)
        assertEquals(3,state.cycles.count{decimal(it.remaining).signum()>0})
        LedgerValidation.snapshot(state)
    }

    @Test fun unknownFeesAndNetRemainPendingAfterFiltering() {
        val state=fixture();val iron=filterTradeHistory(ordered(state),"I2701","futures")
        assertEquals(2,iron.size);assertTrue(iron.all{it.fees==null})
        val close=iron.single{it.effect=="close"}
        val net=completeTotal(state.matches.filter{it.closeExecutionId==close.id}.map{it.net})
        assertNull(net)
        assertEquals("待估算",amount(close.fees));assertEquals("待估算",amount(exactOrNull(net)))
        assertEquals("1000",state.matches.single{it.closeExecutionId==close.id}.gross)
    }
}
