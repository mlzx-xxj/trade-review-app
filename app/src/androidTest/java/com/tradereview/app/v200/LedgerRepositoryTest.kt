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
    @Test fun frozenLegacyEditKeepsItsConfirmedOrderWithoutAnotherTechnicalConfirmation()=runBlocking{
        save(buy())
        val second=repo.preview(buy("second"));assertTrue(second.orderWarning);repo.confirm(second,true)
        val old=repo.read().executions.first{it.id=="second"};assertTrue(old.orderConfirmed)
        val edit=repo.preview(old.copy(price="12"));assertFalse(edit.orderWarning)
        repo.confirm(edit)
        val saved=repo.read().executions.first{it.id==old.id}
        assertEquals(old.ordinal,saved.ordinal);assertEquals(old.minute,saved.minute);assertTrue(saved.orderConfirmed)
    }
    @Test fun deletingTheLastFactDoesNotReuseItsAuditedSaveOrder()=runBlocking{
        save(buy().copy(feeSource=AutomaticEntry.SOURCE));save(buy("second").copy(feeSource=AutomaticEntry.SOURCE))
        repo.confirm(repo.preview(null,"second"),true)
        save(buy("third").copy(feeSource=AutomaticEntry.SOURCE))
        assertEquals(3L,repo.read().executions.first{it.id=="third"}.ordinal)
    }
    @Test fun automaticOrderNeedsNoConfirmationAndAnEditCannotMoveTheOriginalFact()=runBlocking{
        save(buy().copy(feeSource=AutomaticEntry.SOURCE,ordinal=999))
        val second=repo.preview(buy("second").copy(feeSource=AutomaticEntry.SOURCE,ordinal=999))
        assertFalse(second.orderWarning);repo.confirm(second)
        val first=repo.read().executions.first{it.id=="buy"}
        assertEquals(1L,first.ordinal);assertEquals(2L,repo.read().executions.first{it.id=="second"}.ordinal)
        repo.confirm(repo.preview(first.copy(price="11",minute=1,date=day+1,tradingDay=day+1,ordinal=500)))
        val edited=repo.read().executions.first{it.id==first.id}
        assertEquals(first.ordinal,edited.ordinal);assertEquals(first.minute,edited.minute);assertEquals(first.date,edited.date)
        assertEquals(1,repo.read().audits.size)
    }
    @Test fun changedMatchingRepricesLaterAutoCloseOnlyFromItsSavedRuleAndAuditsIt()=runBlocking{
        val rule=LedgerContractRule("synthetic_auto","zz2701","合成测试","10","0.12","per_lot","1.735","2","3",effectiveDay=day,verified=true)
        repo.rule(rule)
        fun e(id:String,date:Long=day,side:String="buy",effect:String="open",qty:String="2")=LedgerExecution(id,"futures","zz2701","合成测试",date,600,date,0,side,effect,"3500",qty,"0",feeSource=AutomaticEntry.SOURCE)
        save(e("a"));save(e("b",day+1));save(e("c",day+1,"sell","close","3"))
        assertEquals("7.01",repo.read().executions.first{it.id=="c"}.fees)
        repo.rule(rule.copy(openFee="900",closeFee="900",todayFee="900"))
        val first=repo.read().executions.first{it.id=="a"};val change=repo.preview(first.copy(quantity="1"))
        assertEquals("7.01",repo.read().executions.first{it.id=="c"}.fees)
        assertEquals("8.01",change.after.executions.first{it.id=="c"}.fees)
        repo.confirm(change,true)
        assertEquals("1.75",repo.read().executions.first{it.id=="a"}.fees)
        assertEquals(setOf("a","c"),repo.read().audits.map{it.executionId}.toSet())
        LedgerValidation.snapshot(repo.read())
    }
    @Test fun newAutomaticStockUsesPersonalFixedRatesWhileHistoricalAccountAndFactsStayIntact()=runBlocking{
        repo.account(LedgerAccount("stock","stock","A股账户",buyRate="0.001",sellRate="0.002"))
        save(buy().copy(feeSource=AutomaticEntry.SOURCE,quantity="1000"))
        assertEquals("1.00",repo.read().executions.single().fees)
        assertEquals("0.001",repo.read().accounts.first{it.id=="stock"}.buyRate)
        val before=repo.read();val backup=BackupRepository(db);val data=backup.export()
        backup.restore(backup.preview(BackupCodec.decode(BackupCodec.encode(data))),RestoreMode.REPLACE)
        assertEquals(before,repo.read())
    }
    @Test fun saveThenPartialSaleAndPreviewedEditRebuildAuthoritativePnlAndKeepsOriginalAudit()=runBlocking{save(buy());save(buy("sell").copy(date=day+1,tradingDay=day+1,side="sell",effect="close",price="11",quantity="40"));val before=repo.read();assertEquals("39.696",before.matches.single().net);val closing=before.executions.first{it.id=="sell"};val preview=repo.preview(closing.copy(price="12"));assertEquals(before,repo.read());repo.confirm(preview);val after=repo.read();assertEquals("79.672",after.matches.single().net);assertEquals("11",LedgerJson.execution(after.audits.single().beforeJson).price);assertEquals("12",after.executions.first{it.id=="sell"}.price);assertEquals("buy",after.executions.first{it.side=="buy"}.id)}
    @Test fun stalePreviewAndOverSellingCannotLeaveHalfChangedFacts()=runBlocking{save(buy());val preview=repo.preview(buy("another"));repo.risk(MarketRisk("stock","1000"));val before=repo.read();try{repo.confirm(preview);fail()}catch(_:IllegalArgumentException){};assertEquals(before,repo.read());try{save(buy("sell").copy(date=day+1,tradingDay=day+1,side="sell",effect="close",quantity="101"));fail()}catch(_:IllegalArgumentException){};assertEquals(before,repo.read())}
    @Test fun oversizeRiskRequiresExplicitAcknowledgementAndRemainsStableAfterRulesChange()=runBlocking{repo.risk(MarketRisk("stock","10000","amount","5"));val preview=repo.preview(buy().copy(stopLoss="9"));assertTrue(preview.risk!!.exceeded);val before=repo.read();try{repo.confirm(preview);fail()}catch(_:IllegalArgumentException){};assertEquals(before,repo.read());repo.confirm(preview,true);val fact=repo.read().executions.single();assertTrue(fact.riskConfirmed);assertEquals("100",fact.riskAmount);repo.risk(MarketRisk("stock","100000","amount","500"));assertEquals(fact,repo.read().executions.single())}
    @Test fun strategyRenameDeactivateAndHistoricalPlanAssociationStayStable()=runBlocking{repo.strategy(LedgerStrategy("custom","原名"));repo.plan(LedgerPlan("plan","stock","600000","合成股票",day,"custom",note="计划价格不能当成交"));save(buy().copy(strategyId="custom",planId="plan"));repo.strategy(LedgerStrategy("custom","新名",active=false));assertEquals("custom",repo.read().executions.single().strategyId);assertEquals("plan",repo.read().executions.single().planId);assertEquals("新名",repo.read().strategies.first{it.id=="custom"}.name);try{save(buy("new").copy(strategyId="custom"));fail()}catch(_:IllegalArgumentException){};assertEquals(1,repo.read().executions.size)}
    @Test fun currentAndClosedCyclesBothAcceptMultipleDatedReviewsWithoutChangingExecutionFacts()=runBlocking{save(buy());val cycle=repo.read().cycles.single();repo.review(LedgerReview("first",cycle.id,day,note="持仓复盘"));save(buy("close").copy(date=day+1,tradingDay=day+1,side="sell",effect="close",price="11"));val facts=repo.read().executions;repo.review(LedgerReview("second",cycle.id,day+2,stopLoss="9",note="平仓后复盘"));assertEquals(facts,repo.read().executions);assertEquals(2,repo.read().reviews.size);assertNotNull(repo.read().cycles.single().closedDay)}
    @Test fun cashFlowsRemainSeparateFromRiskFundAndRealizedPnl()=runBlocking{repo.risk(MarketRisk("stock","50000"));repo.cash(LedgerCashFlow("cash","stock",day,"in","1000"));val s=repo.read();assertEquals("50000",s.risks.first{it.id=="stock"}.riskFund);assertTrue(s.matches.isEmpty());assertNull(LedgerStatistics.cash(s,s.accounts.first{it.id=="stock"}));repo.account(s.accounts.first{it.id=="stock"}.copy(initialCapital="100",historyComplete=true));assertEquals("1100",LedgerStatistics.cash(repo.read(),repo.read().accounts.first{it.id=="stock"}))}
    @Test fun restoreFailureRollsBackNewLedgerAndLegacyRecordsTogether()=runBlocking{save(buy());db.trades().insert(Trade(recordKey="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",date=day,netPnl="12.34"));val backup=BackupRepository(db);val before=backup.export();val incoming=before.copy(ledger=before.ledger!!.copy(cashFlows=listOf(LedgerCashFlow("bad_cash","stock",day,"in","500"))));val preview=backup.preview(incoming);db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_cash BEFORE INSERT ON ledger_cashflows BEGIN SELECT RAISE(ABORT,'synthetic storage failure'); END");try{backup.restore(preview,RestoreMode.REPLACE);fail()}catch(_:Exception){};val after=backup.export();assertEquals(before.copy(exportedAt=after.exportedAt),after)}
    @Test fun pendingFeesZipRestoreFailureRollsBackThenSuccessfulRestoreKeepsUnknownValues()=runBlocking{
        val today=AutomaticEntry.today().toEpochDay()
        fun pending(id:String,side:String="buy",effect:String="open",price:String="800",qty:String="2")=LedgerExecution(id,"futures","i2701","铁矿石",today,600,today,0,side,effect,price,qty,null,feeSource=AutomaticEntry.SOURCE)
        save(pending("i_open"));save(pending("i_close","sell","close","810","1"))
        val root=File(context.cacheDir,"pending-zip-${newKey()}");val store=AttachmentStore(root)
        try{
            val service=FullZipRepository(db,store);val output=ByteArrayOutputStream();service.export(output)
            val backup=BackupRepository(db,store);val original=backup.export(true)
            save(buy("extra"));val before=backup.export(true)
            val preview=service.read(output.toByteArray().inputStream())
            try{
                db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_pending BEFORE INSERT ON ledger_matches WHEN NEW.net IS NULL BEGIN SELECT RAISE(ABORT,'synthetic pending restore failure'); END")
                try{service.restore(preview,RestoreMode.REPLACE);fail("must fail atomically")}catch(_:Exception){}
                val after=backup.export(true);assertEquals(before.copy(exportedAt=after.exportedAt),after)
            }finally{db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_pending");preview.archive.close()}
            val fresh=service.read(output.toByteArray().inputStream())
            try{service.restore(fresh,RestoreMode.REPLACE)}finally{fresh.archive.close()}
            val restored=backup.export(true);assertEquals(original.copy(exportedAt=restored.exportedAt),restored)
            assertTrue(repo.read().executions.all{it.fees==null});assertNull(repo.read().matches.single().net);assertEquals("1",repo.read().cycles.single().remaining)
        }finally{root.deleteRecursively()}
    }
    @Test fun legacyJsonReplacementPreservesNewLedgerItDoesNotContain()=runBlocking{save(buy());val before=repo.read();val backup=BackupRepository(db);val old=BackupCodec.read(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("v02-backup.json"));backup.restore(backup.preview(old),RestoreMode.REPLACE);assertEquals(before,repo.read());assertEquals(old.trades.map{it.recordKey}.toSet(),db.trades().allTrades().map{it.recordKey}.toSet())}
    @Test fun completeZipRestoresNewLedgerAndOldPrivateScreenshotsTogether()=runBlocking{save(buy());val root=File(context.cacheDir,"v200-evidence-${newKey()}");val store=AttachmentStore(root);try{val trade=Trade(recordKey="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",date=day,netPnl="12.34");db.trades().insert(trade);AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri(),trade.recordKey,"before","合成旧截图");val service=FullZipRepository(db,store);val bytes=ByteArrayOutputStream();service.export(bytes);val before=BackupRepository(db,store).export(true);val preview=service.read(bytes.toByteArray().inputStream());service.restore(preview,RestoreMode.REPLACE);preview.archive.close();val after=BackupRepository(db,store).export(true);assertEquals(before.copy(exportedAt=after.exportedAt),after);store.verifyAll(db.trades().allAttachments());assertEquals("0.1",repo.read().executions.single().fees)}finally{root.deleteRecursively()}}
    @Test fun sameMinuteUnknownOrderRequiresConfirmationAndKeepsStableOrderInBackup()=runBlocking{save(buy());val preview=repo.preview(buy("second"));assertTrue(preview.orderWarning);val before=repo.read();try{repo.confirm(preview);fail("unknown order was silently accepted")}catch(_:IllegalArgumentException){};assertEquals(before,repo.read());repo.confirm(preview,true);val after=repo.read();assertTrue(after.executions.first{it.id=="second"}.orderConfirmed);assertEquals(listOf("buy","second"),MatchingEngine.ordered(after.executions).map{it.id});val data=BackupRepository(db).export();assertEquals(after,BackupCodec.read(BackupCodec.encode(data).byteInputStream()).ledger)}
    @Test fun failedZipReplacementRollsBackNewLedgerLegacyFactsAndBothExistingImageReferences()=runBlocking{
        save(buy());repo.cash(LedgerCashFlow("source_cash","stock",day,"in","500"))
        val root=File(context.cacheDir,"v200-zip-rollback-${newKey()}");val store=AttachmentStore(root)
        try{
            val key="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";db.trades().insert(Trade(recordKey=key,date=day,netPnl="12.34"))
            AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri(),key,"before","源合成截图")
            val service=FullZipRepository(db,store);val bytes=ByteArrayOutputStream();service.export(bytes)
            db.ledger().clearCashFlows();db.trades().save(db.trades().findTradeByKey(key)!!.copy(netPnl="25.00"))
            val extra="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";db.trades().insert(Trade(recordKey=extra,date=day,netPnl="-4.5"))
            AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri(),extra,"after","本机合成截图")
            val backup=BackupRepository(db,store);val before=backup.export(true);val pending=service.read(bytes.toByteArray().inputStream())
            try{db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_zip_cash BEFORE INSERT ON ledger_cashflows BEGIN SELECT RAISE(ABORT,'synthetic ZIP storage failure'); END");try{service.restore(pending,RestoreMode.REPLACE);fail("half-restored ZIP accepted")}catch(_:Exception){};val after=backup.export(true);assertEquals(before.copy(exportedAt=after.exportedAt),after);assertEquals(2,after.attachments.size);store.verifyAll(after.attachments)}finally{pending.archive.close()}
        }finally{root.deleteRecursively()}
    }
    @Test fun ambiguousSameDayContractRulesAreRejectedAndChangingRulesNeverRepricesHistoricalFees()=runBlocking{
        val r=LedgerContractRule("rule","rb2701","合成规则",multiplier="10",openFee="2",closeFee="3",todayFee="10",effectiveDay=day,verified=true);repo.rule(r)
        val e=LedgerExecution("future","futures","rb2701","合成期货",day,600,day,0,"buy","open","3500","2","0",feeSource="rule",second=1);save(e)
        val original=repo.read().executions.single();assertEquals("4",original.fees)
        try{repo.rule(r.copy(id="ambiguous",openFee="8"));fail("ambiguous rule silently selected")}catch(_:IllegalArgumentException){}
        assertEquals(listOf(r),repo.read().rules);repo.rule(r.copy(openFee="5"));assertEquals(original,repo.read().executions.single());assertEquals("2",LedgerJson.rule(original.ruleSnapshot!!).openFee)
        save(e.copy(id="future2",date=day+1,tradingDay=day+1));assertEquals("10",repo.read().executions.first{it.id=="future2"}.fees);LedgerValidation.snapshot(repo.read())
    }
    @Test fun confirmingOneExecutionMultiplierDoesNotVerifyAnUnverifiedFeeRule()=runBlocking{
        val r=LedgerContractRule("unverified","rb2701","合成未核实费率",multiplier="10",openFee="2",closeFee="3",todayFee="10",effectiveDay=day,verified=false);repo.rule(r)
        val e=LedgerExecution("future","futures","rb2701","合成期货",day,600,day,0,"buy","open","3500","2","0",feeSource="rule",multiplier="10",parametersConfirmed=true,second=1)
        val before=repo.read();try{repo.preview(e);fail("multiplier confirmation incorrectly promoted fee rule")}catch(_:IllegalArgumentException){};assertEquals(before,repo.read())
        repo.rule(r.copy(verified=true));try{repo.preview(e.copy(multiplier="20",parametersConfirmed=false));fail("unconfirmed parameter override accepted")}catch(_:IllegalArgumentException){};assertTrue(repo.read().executions.isEmpty())
        repo.rule(r);save(e.copy(feeSource="actual",fees="4"));assertEquals("4",repo.read().executions.single().fees);assertFalse(repo.read().rules.single().verified)
    }
}
