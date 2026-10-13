package com.tradereview.app.v200

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.data.*
import com.tradereview.app.backup.*
import com.tradereview.app.evidence.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.io.File
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase;private lateinit var repo:LedgerRepository
    private val day=LocalDate.of(2025,1,2).toEpochDay()
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build();repo=LedgerRepository(db)}
    @After fun close(){db.close()}
    private fun buy(id:String="buy")=LedgerExecution(id,"stock","600000","合成股票",day,600,day,0,"buy","open","10","100","0",feeSource="rule")
    private suspend fun save(e:LedgerExecution)=repo.confirm(repo.preview(e))
    @Test fun saveThenPartialSaleAndPreviewedEditRebuildAuthoritativePnlAndKeepsOriginalAudit()=runBlocking{save(buy());save(buy("sell").copy(date=day+1,tradingDay=day+1,side="sell",effect="close",price="11",quantity="40"));val before=repo.read();assertEquals("39.696",before.matches.single().net);val closing=before.executions.first{it.id=="sell"};val preview=repo.preview(closing.copy(price="12"));assertEquals(before,repo.read());repo.confirm(preview);val after=repo.read();assertEquals("79.672",after.matches.single().net);assertEquals("11",LedgerJson.execution(after.audits.single().beforeJson).price);assertEquals("12",after.executions.first{it.id=="sell"}.price);assertEquals("buy",after.executions.first{it.side=="buy"}.id)}
    @Test fun stalePreviewAndOverSellingCannotLeaveHalfChangedFacts()=runBlocking{save(buy());val preview=repo.preview(buy("another"));repo.risk(MarketRisk("stock","1000"));val before=repo.read();try{repo.confirm(preview);fail()}catch(_:IllegalArgumentException){};assertEquals(before,repo.read());try{save(buy("sell").copy(date=day+1,tradingDay=day+1,side="sell",effect="close",quantity="101"));fail()}catch(_:IllegalArgumentException){};assertEquals(before,repo.read())}
    @Test fun oversizeRiskRequiresExplicitAcknowledgementAndRemainsStableAfterRulesChange()=runBlocking{repo.risk(MarketRisk("stock","10000","amount","5"));val preview=repo.preview(buy().copy(stopLoss="9"));assertTrue(preview.risk!!.exceeded);val before=repo.read();try{repo.confirm(preview);fail()}catch(_:IllegalArgumentException){};assertEquals(before,repo.read());repo.confirm(preview,true);val fact=repo.read().executions.single();assertTrue(fact.riskConfirmed);assertEquals("100",fact.riskAmount);repo.risk(MarketRisk("stock","100000","amount","500"));assertEquals(fact,repo.read().executions.single())}
    @Test fun strategyRenameDeactivateAndHistoricalPlanAssociationStayStable()=runBlocking{repo.strategy(LedgerStrategy("custom","原名"));repo.plan(LedgerPlan("plan","stock","600000","合成股票",day,"custom",note="计划价格不能当成交"));save(buy().copy(strategyId="custom",planId="plan"));repo.strategy(LedgerStrategy("custom","新名",active=false));assertEquals("custom",repo.read().executions.single().strategyId);assertEquals("plan",repo.read().executions.single().planId);assertEquals("新名",repo.read().strategies.first{it.id=="custom"}.name);try{save(buy("new").copy(strategyId="custom"));fail()}catch(_:IllegalArgumentException){};assertEquals(1,repo.read().executions.size)}
    @Test fun currentAndClosedCyclesBothAcceptMultipleDatedReviewsWithoutChangingExecutionFacts()=runBlocking{save(buy());val cycle=repo.read().cycles.single();repo.review(LedgerReview("first",cycle.id,day,note="持仓复盘"));save(buy("close").copy(date=day+1,tradingDay=day+1,side="sell",effect="close",price="11"));val facts=repo.read().executions;repo.review(LedgerReview("second",cycle.id,day+2,stopLoss="9",note="平仓后复盘"));assertEquals(facts,repo.read().executions);assertEquals(2,repo.read().reviews.size);assertNotNull(repo.read().cycles.single().closedDay)}
    @Test fun cashFlowsRemainSeparateFromRiskFundAndRealizedPnl()=runBlocking{repo.risk(MarketRisk("stock","50000"));repo.cash(LedgerCashFlow("cash","stock",day,"in","1000"));val s=repo.read();assertEquals("50000",s.risks.first{it.id=="stock"}.riskFund);assertTrue(s.matches.isEmpty());assertNull(LedgerStatistics.cash(s,s.accounts.first{it.id=="stock"}));repo.account(s.accounts.first{it.id=="stock"}.copy(initialCapital="100",historyComplete=true));assertEquals("1100",LedgerStatistics.cash(repo.read(),repo.read().accounts.first{it.id=="stock"}))}
    @Test fun restoreFailureRollsBackNewLedgerAndLegacyRecordsTogether()=runBlocking{save(buy());db.trades().insert(Trade(recordKey="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",date=day,netPnl="12.34"));val backup=BackupRepository(db);val before=backup.export();val incoming=before.copy(ledger=before.ledger!!.copy(cashFlows=listOf(LedgerCashFlow("bad_cash","stock",day,"in","500"))));val preview=backup.preview(incoming);db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_cash BEFORE INSERT ON ledger_cashflows BEGIN SELECT RAISE(ABORT,'synthetic storage failure'); END");try{backup.restore(preview,RestoreMode.REPLACE);fail()}catch(_:Exception){};val after=backup.export();assertEquals(before.copy(exportedAt=after.exportedAt),after)}
    @Test fun legacyJsonReplacementPreservesNewLedgerItDoesNotContain()=runBlocking{save(buy());val before=repo.read();val backup=BackupRepository(db);val old=BackupCodec.read(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("v02-backup.json"));backup.restore(backup.preview(old),RestoreMode.REPLACE);assertEquals(before,repo.read());assertEquals(old.trades.map{it.recordKey}.toSet(),db.trades().allTrades().map{it.recordKey}.toSet())}
    @Test fun completeZipRestoresNewLedgerAndOldPrivateScreenshotsTogether()=runBlocking{save(buy());val root=File(context.cacheDir,"v200-evidence-${newKey()}");val store=AttachmentStore(root);try{val trade=Trade(recordKey="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",date=day,netPnl="12.34");db.trades().insert(trade);AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri(),trade.recordKey,"before","合成旧截图");val service=FullZipRepository(db,store);val bytes=ByteArrayOutputStream();service.export(bytes);val before=BackupRepository(db,store).export(true);val preview=service.read(bytes.toByteArray().inputStream());service.restore(preview,RestoreMode.REPLACE);preview.archive.close();val after=BackupRepository(db,store).export(true);assertEquals(before.copy(exportedAt=after.exportedAt),after);store.verifyAll(db.trades().allAttachments());assertEquals("0.1",repo.read().executions.single().fees)}finally{root.deleteRecursively()}}
}
