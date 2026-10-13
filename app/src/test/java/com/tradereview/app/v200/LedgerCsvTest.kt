package com.tradereview.app.v200

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class LedgerCsvTest {
    private val day=LocalDate.of(2025,1,2).toEpochDay()
    private fun fact(id:String="open")=LedgerExecution(id,"stock","600000","中文,名称\n换行\"引号",day,600,day,1,"buy","open","10","100","0.1")
    private fun state(facts:List<LedgerExecution>):LedgerSnapshot {val r=MatchingEngine.replay(facts);return LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股账户")),executions=facts,lots=r.lots,cycles=r.cycles,matches=r.matches)}
    @Test fun chineseCommaNewlineAndQuotesRoundTripWithoutChangingAmounts(){val csv=LedgerCsv.encode(state(listOf(fact())));assertTrue(csv.startsWith("\uFEFF"));assertTrue(csv.contains("\"中文,名称\n换行\"\"引号\""));assertTrue(csv.contains("\"0.1\""));assertTrue(csv.contains("\"CNY\""))}
    @Test fun openingHasNoInventedRealizedPnlAndClosingFeeIsAllocatedOnce(){val facts=listOf(fact(),fact("close").copy(date=day+1,tradingDay=day+1,ordinal=2,side="sell",effect="close",price="11",quantity="40",fees="0.264"));val csv=LedgerCsv.encode(state(facts));assertTrue(csv.contains("\"40\",\"39.696\",\"CNY\""));val open=csv.substringBefore("\"close\"");assertTrue(open.contains("\"\",\"\",\"CNY\""))}
    @Test fun dangerousSpreadsheetTextIsEscapedAndStableStrategyIdRetained(){val s=state(listOf(fact().copy(name="=SUM(A1)",strategyId="stable"))).copy(strategies=listOf(LedgerStrategy("stable","+中文策略")));val csv=LedgerCsv.encode(s);assertTrue(csv.contains("'=SUM(A1)"));assertTrue(csv.contains("'+中文策略"));assertTrue(csv.contains("\"stable\""))}
}
