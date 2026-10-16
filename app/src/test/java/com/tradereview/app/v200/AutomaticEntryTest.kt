package com.tradereview.app.v200

import com.tradereview.app.backup.BackupCodec
import com.tradereview.app.backup.BackupData
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AutomaticEntryTest {
    private val day=LocalDate.of(2026,10,14).toEpochDay()
    private fun fact(id:String,order:Long=1,side:String="buy",effect:String="open",price:String="3500",qty:String="2",date:Long=day)=LedgerExecution(id,"futures","zz2701","仅合成测试",date,600,date,order,side,effect,price,qty,"0",feeSource=AutomaticEntry.SOURCE)
    private fun rule(mode:String="per_lot",open:String="1.735",close:String="2",today:String="3")=LedgerContractRule("synthetic","zz2701","仅合成参数，非交易所标准","10","0.12",mode,open,close,today,effectiveDay=day,verified=true)
    private fun prepared(e:LedgerExecution= fact("a"),prior:List<LedgerExecution> = emptyList(),r:LedgerContractRule=rule())=AutomaticEntry.prepare(e,null,prior,listOf(r))
    private fun state(facts:List<LedgerExecution>,rules:List<LedgerContractRule> = emptyList()):LedgerSnapshot {val result=MatchingEngine.replay(facts);return LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股"),LedgerAccount("futures","futures","期货")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")),rules=rules,executions=facts,lots=result.lots,matches=result.matches,cycles=result.cycles)}
    @Test fun stockFixedComprehensiveFeesHaveNoExtraPennyOrMinimum(){val e=fact("a").copy(accountId="stock",code="600000");assertEquals("1.00",prepared(e.copy(price="10",quantity="1000")).fees);assertEquals("6.00",prepared(e.copy(price="10",quantity="1000",side="sell",effect="close")).fees);assertEquals("0.00",prepared(e.copy(price="0.01",quantity="1")).fees)}
    @Test fun stockFeesRoundHalfUpOnlyAtTheCentBoundary(){assertEquals("0.01",prepared(fact("a").copy(accountId="stock",code="600000",price="50",quantity="1")).fees)}
    @Test fun syntheticFixedPerLotFeeAddsOnePennyPerWholeExecution(){assertEquals("3.48",prepared().fees);assertEquals("5.22",prepared(fact("a",qty="3")).fees)}
    @Test fun syntheticTurnoverFeeUsesExactDecimalAndOneFinalRounding(){val e=prepared(fact("a",price="3500.123456",qty="2"),r=rule("turnover_rate","0.00005","0.00005","0.00005"));assertEquals("3.51",e.fees);assertEquals("3.49",prepared(r=rule(open="1.7375")).fees)}
    @Test fun syntheticMargin8400IsNotAnOfficialReference(){val s=state(emptyList(),listOf(rule()));val quote=AutomaticEntry.estimate(fact("a"),s);assertEquals("8400.00",quote.margin);assertEquals("3.48",quote.fees)}
    @Test fun mixedNaturalDatesSplitCloseFeesButAddOnlyOnePenny(){val a=prepared(fact("a"));val b=prepared(fact("b",2,date=day+1),listOf(a));val c=prepared(fact("c",3,"sell","close",qty="3",date=day+1),listOf(a,b));assertEquals("7.01",c.fees);val result=MatchingEngine.replay(listOf(a,b,c));assertEquals(0,BigDecimal("7.01").compareTo(sum(result.matches.map{decimal(it.closeFees)})))}
    @Test fun automaticFifoUsesSaveOrderEvenWhenClockMinuteMovesBack(){val a=prepared(fact("a").copy(minute=700));val b=prepared(fact("b",2,price="3510",qty="3").copy(minute=500),listOf(a));val c=prepared(fact("c",3,"sell","close","3530","4").copy(minute=400),listOf(a,b));val result=MatchingEngine.replay(listOf(c,b,a));assertEquals(listOf("a","b","c"),MatchingEngine.ordered(listOf(c,b,a)).map{it.id});assertEquals(setOf("a","b"),result.matches.map{it.openExecutionId}.toSet());assertEquals("1000",exact(sum(result.matches.map{decimal(it.gross)})));assertEquals("1",result.lots.first{it.executionId=="b"}.remaining);assertTrue(MatchingEngine.uncertainTimes(listOf(a,b,c)).isEmpty())}
    @Test fun autoClosingQuoteShowsRemainingMarginRatherThanNewOccupation(){val a=prepared(fact("a"));val quote=AutomaticEntry.estimate(fact("b",2,"sell","close",qty="1"),state(listOf(a),listOf(rule())));assertEquals("4200.00",quote.margin);assertEquals("平仓后预计剩余占用",quote.marginLabel)}
    @Test fun longAndShortLotsRemainIndependent(){val a=prepared(fact("a"));val b=prepared(fact("b",2,"sell","open"),listOf(a));val c=prepared(fact("c",3,"buy","close","3490","1"),listOf(a,b));val result=MatchingEngine.replay(listOf(a,b,c));assertEquals("b",result.matches.single().openExecutionId);assertEquals("100",result.matches.single().gross);assertEquals("2",result.cycles.first{it.direction=="long"}.remaining);assertEquals("1",result.cycles.first{it.direction=="short"}.remaining)}
    @Test fun unknownOrUnverifiedReferenceNeverInventsZeroFeeOrMargin(){val q=AutomaticEntry.estimate(fact("a"),state(emptyList()));assertNull(q.fees);assertNull(q.margin);assertThrows(LedgerException::class.java){prepared(r=rule().copy(verified=false))};assertThrows(LedgerException::class.java){AutomaticEntry.prepare(fact("a"),null,emptyList(),emptyList())}}
    @Test fun savedRuleSnapshotWinsOverFutureCatalogUpdates(){val e=prepared();val changed=AutomaticEntry.prepare(e,e,emptyList(),listOf(rule(open="900")));assertEquals(e.fees,changed.fees);assertEquals(e.ruleSnapshot,changed.ruleSnapshot)}
    @Test fun duplicateOrUnknownAutoOrderIsRejectedRatherThanGuessed(){val a=prepared();assertThrows(LedgerException::class.java){MatchingEngine.replay(listOf(a,a.copy(id="b")))};assertThrows(LedgerException::class.java){MatchingEngine.replay(listOf(a.copy(ordinal=0)))}}
    @Test fun legacyOrderAndUnroundedHistoricalFeesRemainUntouched(){val a=fact("legacy",price="10",qty="100").copy(accountId="stock",code="600000",feeSource="rule",fees="0.1",feeRateSnapshot="0.0001",ordinal=0);val b=prepared(fact("new",2).copy(accountId="stock",code="000001",price="10",quantity="1",minute=1));val result=state(listOf(b,a));assertEquals(listOf("legacy","new"),MatchingEngine.ordered(result.executions).map{it.id});LedgerValidation.snapshot(result);assertEquals("0.1",result.executions.first{it.id=="legacy"}.fees)}
    @Test fun stockTPlusOneStillRejectsSameNaturalDate(){val a=prepared(fact("a").copy(accountId="stock",code="600000",price="10",quantity="1000"));val b=prepared(a.copy(id="b",ordinal=2,side="sell",effect="close"));assertThrows(LedgerException::class.java){MatchingEngine.replay(listOf(a,b))};assertEquals("-7",MatchingEngine.replay(listOf(a,b.copy(date=day+1,tradingDay=day+1))).matches.single().net)}
    @Test fun deviceTimeIsConvertedToBeijingWithoutRelyingOnSystemZone(){val c=Clock.fixed(Instant.parse("2026-10-14T16:00:00Z"),ZoneOffset.ofHours(-7));assertEquals(LocalDate.of(2026,10,15),AutomaticEntry.now(c).toLocalDate());assertEquals(0,AutomaticEntry.now(c).hour)}
    @Test fun allV2FactsAllowBeijingTodayAndStillRejectTomorrow(){
        val today=AutomaticEntry.today().toEpochDay();val e=prepared(fact("a",date=today))
        for(source in listOf(AutomaticEntry.SOURCE,"actual","rule")){
            LedgerValidation.execution(e.copy(feeSource=source))
            assertThrows(LedgerException::class.java){LedgerValidation.execution(e.copy(feeSource=source,date=today+1,tradingDay=today+1))}
        }
        assertThrows(LedgerException::class.java){LedgerValidation.execution(e.copy(tradingDay=today+1))}
        LedgerValidation.execution(e.copy(feeSource="actual",tradingDay=today+1)) // historical night facts remain compatible
        LedgerValidation.cash(LedgerCashFlow("today","stock",today,"in","1"))
        assertThrows(LedgerException::class.java){LedgerValidation.cash(LedgerCashFlow("future","stock",today+1,"in","1"))}
    }
    @Test fun officialCatalogRecognizesPrefixAndKeepsUnavailableDataUnknown(){assertEquals("10",FuturesReferenceCatalog.rule("rb2701",day)!!.multiplier);assertEquals("5",FuturesReferenceCatalog.rule("AL2701".lowercase(),day)!!.multiplier);assertEquals("100",FuturesReferenceCatalog.rule("i2701",day)!!.multiplier);assertNull(FuturesReferenceCatalog.rule("i2701",day)!!.openFee);assertNull(FuturesReferenceCatalog.rule("zz2701",day));assertNull(FuturesReferenceCatalog.rule("rb2701",day-1));assertEquals(44,FuturesReferenceCatalog.products.count{it.multiplier!=null})}
    @Test fun autoLedgerJsonRoundTripIncludesFeesSnapshotsAndSaveOrder(){val a=prepared();val b=prepared(fact("b",2,"sell","close",qty="1"),listOf(a));val s=state(listOf(a,b),listOf(rule()));val d=BackupData("2026-10-15T00:00:00Z",emptyList(),emptyList(),ledger=s);assertEquals(s,BackupCodec.decode(BackupCodec.encode(d)).ledger);assertEquals(s,LedgerRepository.merge(s,s))}
    @Test fun conflictingIndependentSaveOrdersAreRejectedByMerge(){val a=state(listOf(prepared()));val b=state(listOf(prepared(fact("b"))));assertThrows(LedgerException::class.java){LedgerRepository.merge(a,b)}}
}
