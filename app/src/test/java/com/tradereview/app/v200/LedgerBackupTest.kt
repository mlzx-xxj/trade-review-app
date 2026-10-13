package com.tradereview.app.v200

import com.tradereview.app.backup.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class LedgerBackupTest {
    private val day=LocalDate.of(2025,1,2).toEpochDay()
    private fun fixture():LedgerSnapshot {
        val facts=listOf(LedgerExecution("buy","stock","600000","合成股票",day,600,day,1,"buy","open","10","100","0.1",strategyId="custom"),LedgerExecution("sell","stock","600000","合成股票",day+1,600,day+1,2,"sell","close","11","40","0.264",strategyId="custom"))
        val result=MatchingEngine.replay(facts)
        return LedgerSnapshot(accounts=listOf(LedgerAccount("futures","futures","国内期货账户"),LedgerAccount("stock","stock","A股账户","10000",true)),strategies=listOf(LedgerStrategy("custom","更名策略","说明,\n下一行",active=false)),risks=listOf(MarketRisk("futures","5000"),MarketRisk("stock","10000")),plans=listOf(LedgerPlan("plan","stock","600000","合成股票",day,strategyId="custom",entryPrice="9",levels=LedgerJson.text(listOf(KeyPrice("支撑","8"),KeyPrice("压力","12"))),note="计划不是成交")),executions=facts,lots=result.lots,matches=result.matches,cycles=result.cycles,reviews=listOf(LedgerReview("review",result.cycles.single().id,day+1,note="仍持有，也可以复盘")),cashFlows=listOf(LedgerCashFlow("cash","stock",day,"in","1000",note="自己的入金")))
    }
    @Test fun newFullStructuredLedgerRoundTripPreservesAllStableReferencesAndNumbers(){val s=fixture();val d=BackupData("2026-10-13T00:00:00Z",emptyList(),emptyList(),ledger=s);val result=BackupCodec.decode(BackupCodec.encode(d));assertEquals(d,result);assertEquals("39.696",result.ledger!!.matches.single().net);assertFalse(result.ledger.strategies.single().active);assertEquals("说明,\n下一行",result.ledger.strategies.single().description)}
    @Test fun missingForeignReferencesAndFabricatedMatchingResultsAreRejected(){val s=fixture();for(bad in listOf(s.copy(strategies=emptyList()),s.copy(reviews=s.reviews.map{it.copy(cycleId="not_present")}),s.copy(matches=s.matches.map{it.copy(net="100000")}),s.copy(lots=s.lots.map{it.copy(remaining="99")}))){assertThrows(IllegalArgumentException::class.java){LedgerValidation.snapshot(bad)}}}
    @Test fun newLedgerStrictTypesUnknownFieldsAndCountsCannotBeCoerced(){val d=BackupData("2026-10-13T00:00:00Z",emptyList(),emptyList(),ledger=fixture());val text=BackupCodec.encode(d);for(bad in listOf(text.replace("\"historyComplete\": true","\"historyComplete\": \"true\""),text.replace("\"ledgerRecords\":", "\"unknownLedgerCount\":"),text.replace("\"quantity\": \"100\"","\"quantity\": 100"))){assertNotEquals(text,bad);assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(bad)}}}
    @Test fun everySupportedOldGoldenBackupRemainsArchiveOnlyWithoutInventedPositions(){for(name in listOf("v02-backup.json","v03-backup.json","v03-android-backup.json","v04-backup.json","v05-backup.json","v06-backup.json")){val old=BackupCodec.read(javaClass.classLoader!!.getResourceAsStream(name)!!);assertNull(old.ledger);val current=BackupCodec.decode(BackupCodec.encode(old));assertNull(current.ledger);assertEquals(old.trades,current.trades);assertEquals(old.attachments,current.attachments);assertEquals(7,current.sourceSchemaVersion)}}
    @Test fun repeatedMergePreservesLocalNamesAndDoesNotDuplicateFactsOrFees(){val s=fixture();val merged=LedgerRepository.merge(s,s.copy(strategies=s.strategies.map{it.copy(name="另一个设备的名字")}));assertEquals(s,merged);assertEquals(s.executions.size,LedgerRepository.merge(merged,s).executions.size);assertEquals(s.matches,merged.matches)}
    @Test fun duplicateIdsAndUnknownAccountCannotEnterNewLedger(){val s=fixture();assertThrows(IllegalArgumentException::class.java){LedgerValidation.snapshot(s.copy(executions=s.executions+s.executions))};assertThrows(IllegalArgumentException::class.java){LedgerValidation.snapshot(s.copy(accounts=s.accounts.map{it.copy(id="forex")}))}}
    @Test fun percentRiskLimitKeepsSubCentPrecision(){val e=fixture().executions.first().copy(stopLoss="9");val risk=LedgerRisk.evaluate(e,MarketRisk("stock","15.01","percent","1"));assertEquals("0.1501",risk.limit);assertTrue(risk.exceeded)}
}
