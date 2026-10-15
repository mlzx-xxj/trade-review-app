package com.tradereview.app.v200

import com.tradereview.app.backup.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class PendingAmountsTest {
    private val day=LocalDate.of(2026,10,14).toEpochDay()
    private fun raw(id:String,ordinal:Long,side:String="buy",effect:String="open",price:String="800",qty:String="2")=
        LedgerExecution(id,"futures","i2701","铁矿石",day,600,day,ordinal,side,effect,price,qty,"0",feeSource=AutomaticEntry.SOURCE)
    private fun prepare(e:LedgerExecution,prior:List<LedgerExecution> = emptyList())=AutomaticEntry.prepare(e,null,prior,emptyList())
    private fun snapshot(facts:List<LedgerExecution>):LedgerSnapshot {
        val r=MatchingEngine.replay(facts)
        return LedgerSnapshot(accounts=listOf(LedgerAccount("stock","stock","A股"),LedgerAccount("futures","futures","期货")),risks=listOf(MarketRisk("stock"),MarketRisk("futures")),executions=facts,lots=r.lots,matches=r.matches,cycles=r.cycles)
    }
    @Test fun allNineteenPreviouslyBlockedProductsCanEnterTheLedgerWithoutGuessedAmounts(){
        val units=mapOf("i" to "100","jm" to "60","j" to "100","eg" to "10","l" to "5","pp" to "5","v" to "5","m" to "10","y" to "10","p" to "10","c" to "10","fg" to "20","sa" to "20","ma" to "10","ta" to "5","if" to "300","ih" to "300","ic" to "200","im" to "200")
        val facts=units.entries.mapIndexed { index,(code,multiplier) ->
            val e=prepare(raw("p$index",index.toLong()+1).copy(code=code+"2701"))
            assertEquals(code,multiplier,e.multiplier);assertNull(e.fees);assertNull(e.marginRate);e
        }
        val s=snapshot(facts);assertEquals(19,s.cycles.size);assertTrue(s.cycles.all{it.remaining=="2" && it.fees==null && it.referenceMargin==null});LedgerValidation.snapshot(s)
    }
    @Test fun confirmedMultiplierAllowsOpeningWithoutInventingFeeMarginOrCost(){
        val e=prepare(raw("a",1));val s=snapshot(listOf(e))
        assertEquals("100",e.multiplier);assertNull(e.fees);assertNull(e.marginRate)
        assertEquals("2",s.cycles.single().remaining);assertNull(s.cycles.single().fees);assertNull(s.cycles.single().openCost);assertNull(s.cycles.single().referenceMargin)
        assertNull(s.lots.single().allocatedFees);LedgerValidation.snapshot(s)
        val quote=AutomaticEntry.estimate(raw("b",2),s)
        assertNull(quote.fees);assertNull(quote.margin);assertNull(quote.problem);assertTrue(quote.ready)
    }
    @Test fun partialClosePreservesGrossAndQuantityButUnknownNetNeverBecomesAWin(){
        val a=prepare(raw("a",1));val b=prepare(raw("b",2,"sell","close","810","1"),listOf(a));val s=snapshot(listOf(a,b));val m=s.matches.single()
        assertEquals("1000",m.gross);assertNull(m.openFees);assertNull(m.closeFees);assertNull(m.net)
        assertEquals("1",s.cycles.single().remaining);assertNull(s.cycles.single().realized);LedgerValidation.snapshot(s)
        val n=periodNumbers(LedgerStatistics.closed(s),LedgerPeriod.month(LocalDate.ofEpochDay(day)))
        assertEquals(1,n.records.size);assertEquals(1,n.pending);assertNull(n.net);assertNull(n.average);assertNull(n.winRate)
        assertEquals(0,n.wins+n.losses+n.flats);assertTrue(n.daily.isEmpty());assertTrue(n.distribution.isEmpty())
    }
    @Test fun unknownOnePartMakesTheWholeCloseUnknownIncludingKnownOtherLots(){
        val unknown=prepare(raw("a",1,qty="1"));val r=LedgerJson.rule(unknown.ruleSnapshot!!).copy(openFee="2",todayFee="3")
        val known=AutomaticEntry.prepare(raw("b",2,qty="1").copy(ruleSnapshot=LedgerJson.text(r)),null,listOf(unknown),emptyList())
        val close=AutomaticEntry.prepare(raw("c",3,"sell","close","810","2").copy(ruleSnapshot=LedgerJson.text(r)),null,listOf(unknown,known),emptyList())
        val s=snapshot(listOf(unknown,known,close));assertEquals(2,s.matches.size)
        assertEquals(1,s.matches.count{it.net!=null});assertNull(LedgerStatistics.closed(s).single().net)
        assertNull(s.cycles.single().realized);LedgerValidation.snapshot(s)
    }
    @Test fun zeroOfficialBaseFeeIsKnownAndReceivesExactlyOnePenny(){
        val r=LedgerContractRule("synthetic","i2701","仅合成","100",null,"per_lot","0","0","0",effectiveDay=day,verified=true)
        val e=AutomaticEntry.prepare(raw("a",1),null,emptyList(),listOf(r));assertEquals("0.01",e.fees)
        val quote=AutomaticEntry.estimate(e,snapshot(listOf(e)));assertEquals("0.01",quote.fees);assertNull(quote.margin)
    }
    @Test fun missingOpeningFeeDoesNotHideKnownClosingFeeButNetStaysUnknown(){
        val a=prepare(raw("a",1));val r=LedgerJson.rule(a.ruleSnapshot!!).copy(todayFee="2")
        val b=AutomaticEntry.prepare(raw("b",2,"sell","close","810","1").copy(ruleSnapshot=LedgerJson.text(r)),null,listOf(a),emptyList())
        assertEquals("2.01",b.fees);val m=snapshot(listOf(a,b)).matches.single();assertEquals("2.01",m.closeFees);assertNull(m.net)
    }
    @Test fun futureCatalogUpdateNeverBackfillsUnknownHistoricalFeesSilently(){
        val e=prepare(raw("a",1));val future=LedgerJson.rule(e.ruleSnapshot!!).copy(openFee="50",marginRate="0.15")
        val unchanged=AutomaticEntry.prepare(e,e,emptyList(),listOf(future));assertEquals(e,unchanged)
    }
    @Test fun strictValidationRejectsZeroForgeryAndMissingActualFees(){
        val e=prepare(raw("a",1));assertThrows(LedgerException::class.java){LedgerValidation.snapshot(snapshot(listOf(e.copy(fees="0"))))}
        assertThrows(LedgerException::class.java){LedgerValidation.execution(e.copy(feeSource="actual"))}
        val s=snapshot(listOf(e,prepare(raw("b",2,"sell","close","810","1"),listOf(e))))
        assertThrows(LedgerException::class.java){LedgerValidation.snapshot(s.copy(matches=s.matches.map{it.copy(net="1000")}))}
        assertThrows(IllegalArgumentException::class.java){decimal(null)}
    }
    @Test fun knownAndUnknownEventsHaveSeparateEffectiveStatisticsDenominators(){
        val a=prepare(raw("a",1));val b=prepare(raw("b",2,"sell","close","810","1"),listOf(a))
        val unknown=LedgerStatistics.closed(snapshot(listOf(a,b))).single()
        val n=periodNumbers(listOf(unknown,ClosedEvent(b.copy(id="known"),BigDecimal("91.99"))),LedgerPeriod.month(LocalDate.ofEpochDay(day)))
        assertEquals(BigDecimal("91.99"),n.net);assertEquals(BigDecimal("91.990000000000"),n.average);assertEquals(1,n.wins);assertEquals(1,n.pending)
        assertEquals(BigDecimal("100.000000000000"),n.winRate)
    }
    @Test fun pendingAmountsSurviveJsonZipMergeAndCsvWithoutTurningIntoZero(){
        val a=prepare(raw("a",1));val b=prepare(raw("b",2,"sell","close","810","1"),listOf(a));val s=snapshot(listOf(a,b))
        val data=BackupData("2026-10-15T00:00:00Z",emptyList(),emptyList(),ledger=s)
        val encoded=BackupCodec.encode(data);assertTrue(encoded.contains("\"schemaVersion\":8"));assertEquals(s,BackupCodec.decode(encoded).ledger)
        assertEquals(s,LedgerRepository.merge(s,s));assertTrue(LedgerCsv.encode(s).contains("待估算"));assertTrue(LedgerCsv.encode(s).contains("待计算"))
        val root=kotlin.io.path.createTempDirectory("pending-zip").toFile()
        try{val target=File(root,"full.zip");FullZipArchive.write(data.copy(backupScope="with-attachment-files"),{error("no images")},target)
            FullZipArchive.read(target.inputStream(),root).use{assertEquals(s,it.data.ledger)}}finally{root.deleteRecursively()}
    }
    @Test fun cnyDisplayAlwaysRoundsHalfUpAtTwoPlacesWithoutChangingRawPrecision(){
        assertEquals("￥971.83",amount("971.833333333333"));assertEquals("￥1078.85",amount("1078.846666666667"))
        assertEquals("￥1.01",amount("1.005"));assertEquals("￥-1.01",amount("-1.005"));assertEquals("￥0.00",amount("0"))
        assertEquals("待估算",amount(null));assertEquals("￥3.00",moneyText(BigDecimal("3")))
        assertEquals("971.833333333333",exact(decimal("971.833333333333")))
    }
}
