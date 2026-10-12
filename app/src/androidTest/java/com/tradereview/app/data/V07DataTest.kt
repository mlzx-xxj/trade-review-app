package com.tradereview.app.data
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.backup.*
import com.tradereview.app.domain.*
import com.tradereview.app.evidence.*
import com.tradereview.app.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.time.LocalDate
@RunWith(AndroidJUnit4::class)
class V07DataTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase;private lateinit var store:AttachmentStore;private lateinit var root:File
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build();root=File(context.cacheDir,"v07-data-${newKey()}");store=AttachmentStore(root)}
    @After fun close(){db.close();root.deleteRecursively()}
    private suspend fun seed() {
        val t=Trade();db.trades().insert(t)
        AttachmentRepository(db,store).importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","合成备份证据")
        val today=LocalDate.now().toEpochDay();val item=PreflightItem(text="先检查风险",archived=true)
        db.trades().saveSmartSettings(SmartSettings(minimumSamples=20,automaticReminders=false));db.trades().savePreflightItem(item)
        db.trades().savePreflightCheck(PreflightCheck(today,"manual:${item.id}",item.text,true,"manual"))
        db.trades().saveOcrDraft(OcrDraft(fields=SmartJson.fields(mapOf("quantity" to "2.","symbol" to "螺纹钢")),blocked=true,closedEvidence=false,createdDay=today))
    }
    private suspend fun waitWrite(model:TradeViewModel){withTimeout(15000){while(model.busy.value)delay(25)}}
    @Test fun damagedOcrInputShowsErrorAndCannotChangeAnyHistoryOrEvidence()=runBlocking {
        seed();val repository=BackupRepository(db,store);val before=repository.export(full=true);val ocr=OcrViewModel(context)
        try {
            ocr.read(TestImageProvider.uri("bad.jpg"),EvidenceSettings())
            withTimeout(15000){while(ocr.state.value.busy)delay(25)}
            assertNotNull(ocr.state.value.error);assertTrue(ocr.state.value.candidates.isEmpty());assertEquals("",ocr.state.value.raw)
            val after=repository.export(full=true);assertEquals(before.copy(exportedAt=after.exportedAt),after);store.verifyAll(after.attachments)
        }finally {ocr.clear()}
    }
    @Test fun unverifiedOcrCannotSaveAndVerifiedNewTradePreservesExistingQuickDraftAndHistory()=runBlocking {
        val old=Trade(netPnl="100.00");db.trades().insert(old);val quick=EntryDraft(amount="12.");db.trades().saveDraft(quick)
        val c=OcrTradeParser.parse("Date: 2024-02-29\nMarket: futures\nSymbol: 螺纹钢\nDirection: short\nCurrency: CNY\nRealized P&L: -25.35").single()
        val model=TradeViewModel(db,SavedStateHandle(),store);val key=newKey()
        model.saveOcr(c,emptySet(),true,"trend",10,key,OcrSession(),false,emptySet(),null){};waitWrite(model);assertEquals(1,db.trades().allTrades().size)
        model.saveOcr(c,c.fields.keys,true,"trend",10,key,OcrSession(),false,emptySet(),null){};waitWrite(model)
        assertEquals("-25.35",db.trades().findTradeByKey(key)!!.netPnl);assertEquals(quick,db.trades().allDrafts().single());assertEquals(old,db.trades().findTradeByKey(old.recordKey)!!.copy(id=0))
        model.saveOcr(c,c.fields.keys,true,"trend",10,newKey(),OcrSession(),false,emptySet(),null){};waitWrite(model);assertEquals(2,db.trades().allTrades().size)
    }
    @Test fun fullZipAndJsonRoundTripsAllSixteenTablesAndPictures()=runBlocking {
        seed();val source=BackupRepository(db,store).export(full=true);val bytes=ByteArrayOutputStream();val zip=FullZipRepository(db,store);zip.export(bytes)
        db.trades().clearSmartSettings();db.trades().clearPreflightItems();db.trades().clearPreflightChecks();db.trades().clearOcrDrafts()
        val pending=zip.read(bytes.toByteArray().inputStream());try { zip.restore(pending,RestoreMode.REPLACE) }finally {pending.archive.close()}
        val after=BackupRepository(db,store).export(full=true);assertEquals(source.copy(exportedAt=after.exportedAt),after);store.verifyAll(after.attachments)
        val json=BackupCodec.decode(BackupCodec.encode(BackupRepository(db,store).export()));assertTrue(json.attachments.isEmpty());assertEquals(source.ocrDrafts,json.ocrDrafts);assertEquals(source.preflightChecks,json.preflightChecks)
    }
    @Test fun lateRestoreFailureRollsBackOldAndAllNewTablesAndPreservesFiles()=runBlocking {
        seed();val repository=BackupRepository(db,store);val before=repository.export(full=true)
        val incoming=before.copy(smartSettings=SmartSettings(minimumSamples=50),preflightChecks=before.preflightChecks.map { it.copy(checked=false) })
        val pending=repository.preview(incoming)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_smart_restore BEFORE INSERT ON smart_settings BEGIN SELECT RAISE(ABORT,'synthetic rollback'); END")
        try {repository.restore(pending,RestoreMode.REPLACE);fail()}catch(_:Exception){}
        val after=repository.export(full=true);assertEquals(before.copy(exportedAt=after.exportedAt),after);store.verifyAll(after.attachments)
    }
    @Test fun mergeKeepsLocalChecksAndRulesAndPreviewDetectsAnyNewStateChange()=runBlocking {
        seed();val repository=BackupRepository(db,store);val before=repository.export(full=true)
        val other=before.copy(smartSettings=SmartSettings(),preflightChecks=before.preflightChecks.map { it.copy(checked=false) })
        repository.restore(repository.preview(other),RestoreMode.MERGE);assertEquals(before.preflightChecks,db.trades().allPreflightChecks());assertEquals(before.smartSettings,db.trades().smartSettings())
        val p=repository.preview(other);db.trades().savePreflightItem(PreflightItem(text="新增检查"))
        try {repository.restore(p,RestoreMode.REPLACE);fail()}catch(_:BackupException){};assertEquals(2,db.trades().allPreflightItems().size)
    }
    @Test fun v06GoldenImportKeepsProfessionalFactsAndSuppliesNewDefaults()=runBlocking {
        val old=InstrumentationRegistry.getInstrumentation().context.assets.open("v06-backup.json").use(BackupCodec::read);assertEquals(5,old.sourceSchemaVersion)
        val repository=BackupRepository(db,store);repository.restore(repository.preview(old),RestoreMode.REPLACE)
        assertEquals(old.trades,db.trades().allTrades());assertEquals(SmartSettings(),db.trades().smartSettings());assertTrue(db.trades().allPreflightChecks().isEmpty())
    }
}
