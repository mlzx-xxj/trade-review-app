package com.tradereview.app.stability

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.backup.*
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import com.tradereview.app.ocr.OfflineRecognizer
import com.tradereview.app.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class ReliabilityTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private suspend fun seed(db:TradeDatabase,store:AttachmentStore):BackupData {
        val entry=EntryDraft(amount="123.45",professional=ProfessionalDraft(riskMode="amount",manualRisk="200",planStatus="in"))
        EntryRepository(db).save(entry)
        val t=db.trades().allTrades().single();AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"before","合成历史证据")
        val today=LocalDate.now().toEpochDay();val item=PreflightItem(text="先检查风险")
        db.trades().saveReview(DailyReview(date=today,planSaved=true,reviewSaved=true));db.trades().saveGoal(ReviewGoal(title="本周核对风险",kind="text",startDate=ReviewPeriod.at("week",LocalDate.now()).start))
        db.trades().saveRiskRules(RiskRules("CNY",maxPositionPercent="20"));db.trades().saveSmartSettings(SmartSettings(minimumSamples=3));db.trades().savePreflightItem(item)
        db.trades().savePreflightCheck(PreflightCheck(today,"manual:${item.id}",item.text,true,"manual",listOf("trade:${t.recordKey}")))
        db.trades().saveOcrDraft(OcrDraft(fields=SmartJson.fields(mapOf("quantity" to "2.")),blocked=true,closedEvidence=false,createdDay=today))
        return BackupRepository(db,store).export(full=true)
    }
    private fun same(before:BackupData,after:BackupData){assertEquals(before.copy(exportedAt=after.exportedAt),after)}
    @Test fun cancellationMidwayThroughReplaceRollsBackEveryTableAndPreservesImages()=runBlocking {
        val armed=AtomicBoolean(false);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback)
            .setQueryCallback({sql,_->if(armed.get() && sql.uppercase().contains("DELETE FROM TRADES")){entered.countDown();check(release.await(20,TimeUnit.SECONDS))}},Executor{it.run()}).build()
        val root=File(context.cacheDir,"cancel-restore-${newKey()}");val store=AttachmentStore(root)
        try {
            val before=seed(db,store);val repo=BackupRepository(db,store)
            val preview=repo.preview(before.copy(smartSettings=SmartSettings(minimumSamples=50)))
            armed.set(true)
            val operation=launch(Dispatchers.IO){repo.restore(preview,RestoreMode.REPLACE)}
            assertTrue("Restore never reached cancellation point",entered.await(15,TimeUnit.SECONDS))
            operation.cancel();armed.set(false);release.countDown();withTimeout(15000){operation.join()}
            assertTrue(operation.isCancelled);same(before,repo.export(full=true));store.verifyAll(db.trades().allAttachments())
        }finally{armed.set(false);release.countDown();db.close();root.deleteRecursively()}
    }
    @Test fun lowSpaceAfterZipPreviewPreservesOriginalAndCanRetryWithoutDuplicates()=runBlocking {
        val space=AtomicLong(Long.MAX_VALUE/4);val root=File(context.cacheDir,"zip-space-${newKey()}");val store=AttachmentStore(root){space.get()}
        val db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        try {
            val before=seed(db,store);val service=FullZipRepository(db,store);val bytes=ByteArrayOutputStream();service.export(bytes)
            val p=service.read(bytes.toByteArray().inputStream())
            try {
                space.set(0)
                try{service.restore(p,RestoreMode.REPLACE);fail("Should reject insufficient storage")}catch(_:IllegalArgumentException){}
                same(before,BackupRepository(db,store).export(full=true));store.verifyAll(before.attachments)
                space.set(Long.MAX_VALUE/4);service.restore(p,RestoreMode.MERGE)
                same(before,BackupRepository(db,store).export(full=true));assertEquals(1,db.trades().allAttachments().size)
            }finally{p.archive.close()}
        }finally{db.close();root.deleteRecursively()}
    }
    @Test fun staleDeleteAfterRestoreCannotDeleteReusedRowOrItsEvidence()=runBlocking {
        val root=File(context.cacheDir,"stale-delete-${newKey()}");val store=AttachmentStore(root)
        val db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build()
        try {
            seed(db,store);val old=db.trades().allTrades().single();AttachmentRepository(db,store).deleteTrade(old)
            val replacement=old.copy(recordKey=newKey(),netPnl="999.99",riskRecord=null,professional=null);db.trades().insert(replacement)
            AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri("second.png"),replacement.recordKey,"after","不能被旧删除操作清除")
            val before=BackupRepository(db,store).export(full=true)
            try{AttachmentRepository(db,store).deleteTrade(old);fail("Stale deletion must require a fresh confirmation")}catch(_:IllegalArgumentException){}
            same(before,BackupRepository(db,store).export(full=true));store.verifyAll(before.attachments)
        }finally{db.close();root.deleteRecursively()}
    }
    @Test fun cancelledAndFailingOcrCannotPublishLateResultOrChangeAnyHistory()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build();val root=File(context.cacheDir,"ocr-fault-${newKey()}");val store=AttachmentStore(root)
        try {
            val before=seed(db,store);val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
            val slow=object:OfflineRecognizer{override suspend fun recognize(bitmap:android.graphics.Bitmap):String {entered.complete(Unit);withContext(NonCancellable){finish.await()};return "Date: 2024-02-29\nRealized P&L: 100.00"}}
            val model=OcrViewModel(context,slow);model.read(TestImageProvider.uri("ocr.png"),EvidenceSettings())
            withTimeout(15000){entered.await()};model.clear();finish.complete(Unit);delay(300)
            assertFalse(model.state.value.busy);assertTrue(model.state.value.candidates.isEmpty());assertEquals("",model.state.value.raw)
            val broken=OcrViewModel(context,object:OfflineRecognizer{override suspend fun recognize(bitmap:android.graphics.Bitmap):String=throw IllegalStateException("Synthetic engine failure")})
            broken.read(TestImageProvider.uri("ocr.png"),EvidenceSettings());withTimeout(15000){while(broken.state.value.busy)delay(20)}
            assertNotNull(broken.state.value.error);assertTrue(broken.state.value.candidates.isEmpty());broken.clear()
            same(before,BackupRepository(db,store).export(full=true));store.verifyAll(before.attachments)
        }finally{db.close();root.deleteRecursively()}
    }
}
