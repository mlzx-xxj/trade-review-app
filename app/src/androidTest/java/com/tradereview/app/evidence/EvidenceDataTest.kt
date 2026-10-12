package com.tradereview.app.evidence

import android.content.Context
import android.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradereview.app.backup.*
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EvidenceDataTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase;private lateinit var root:File;private lateinit var store:AttachmentStore;private lateinit var images:AttachmentRepository
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build();root=File(context.cacheDir,"evidence-test-${newKey()}");store=AttachmentStore(root);images=AttachmentRepository(db,store)}
    @After fun close(){db.close();root.deleteRecursively()}
    private suspend fun trade():Trade { val t=Trade(netPnl="12.34");return t.copy(id=db.trades().insert(t)) }
    @Test fun pickerImportCopiesPrivateImageNormalizesOrientationAndRemovesGps()=runBlocking {
        val t=trade();val a=images.importImage(context.contentResolver,TestImageProvider.uri("gps.jpg"),t.recordKey,"before","测试入场前")
        assertEquals(400,a.width);assertEquals(640,a.height);assertNull(ExifInterface(store.file(a).path).getAttribute(ExifInterface.TAG_GPS_LATITUDE));store.verifyAll(db.trades().allAttachments())
        context.contentResolver.call(TestImageProvider.uri(),"erase","gps.jpg",null)
        store.bitmap(a,256).recycle();assertEquals(t,db.trades().allTrades().single());assertEquals("测试入场前",a.caption)
    }
    @Test fun invalidSourceMissingPermissionsOversizeAndLowSpaceNeverCreateRows()=runBlocking {
        val t=trade();db.trades().saveEvidenceSettings(EvidenceSettings(maxInputMiB=5))
        listOf("bad.jpg","missing.png","denied.png","huge.png").forEach { name->try { images.importImage(context.contentResolver,TestImageProvider.uri(name),t.recordKey,"analysis","");fail(name) } catch(_:IllegalArgumentException){} }
        val noSpace=AttachmentRepository(db,AttachmentStore(root){0L})
        try{noSpace.importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","");fail()}catch(_:IllegalArgumentException){}
        assertTrue(db.trades().allAttachments().isEmpty());assertTrue(root.listFiles()!!.none { it.isFile });assertEquals(t,db.trades().allTrades().single())
    }
    @Test fun tenImageLimitCaptionDeleteAndCascadeTradeCleanupAreReal()=runBlocking {
        val t=trade();repeat(10){images.importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","$it")}
        try{images.importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","");fail()}catch(_:IllegalArgumentException){}
        val first=db.trades().allAttachments().first();images.caption(first,"更新说明");assertEquals("更新说明",db.trades().allAttachments().first().caption)
        images.delete(db.trades().allAttachments().first());assertFalse(store.file(first).exists());assertEquals(9,db.trades().allAttachments().size)
        images.deleteTrade(t);assertTrue(db.trades().allAttachments().isEmpty());assertTrue(root.listFiles()!!.none { it.isFile });assertTrue(db.trades().allTrades().isEmpty())
    }
    @Test fun missingOrCorruptedPrivateFilesRefuseFullExportAndRead()=runBlocking {
        val t=trade();val a=images.importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","");store.file(a).writeBytes(byteArrayOf(1,2))
        try{FullZipRepository(db,store).export(ByteArrayOutputStream());fail()}catch(_:IllegalArgumentException){}
        store.file(a).delete();try{store.bitmap(a,256);fail()}catch(_:IllegalArgumentException){}
        assertEquals(listOf(a),db.trades().allAttachments());assertEquals(t,db.trades().allTrades().single())
    }
    @Test fun completeZipRestoresEveryNewTableAndMergeRetainsStableReferences()=runBlocking {
        val t=trade();val a=images.importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"after","完整证据")
        val goal=ReviewGoal(title="减少追价",kind="mistake_count",threshold="1",mistakeId="chase",startDate=ReviewPeriod.at("week",LocalDate.now()).start)
        db.trades().saveGoal(goal);db.trades().saveEvidenceSettings(EvidenceSettings(maxInputMiB=5))
        val zip=FullZipRepository(db,store);val bytes=ByteArrayOutputStream().also { zip.export(it) }.toByteArray();images.deleteTrade(t)
        val preview=zip.read(ByteArrayInputStream(bytes));zip.restore(preview,RestoreMode.REPLACE);preview.archive.close()
        assertEquals(t,db.trades().allTrades().single());assertEquals(a,db.trades().allAttachments().single());assertEquals(goal,db.trades().allGoals().single());assertEquals(5,db.trades().evidenceSettings()!!.maxInputMiB);store.verifyAll(listOf(a))
        val same=zip.read(ByteArrayInputStream(bytes));zip.restore(same,RestoreMode.MERGE);same.archive.close();assertEquals(1,db.trades().allAttachments().size)
    }
    @Test fun failureAfterFilePublicationRollsBackDatabaseAndCleansOnlyNewOrphans()=runBlocking {
        val t=trade();images.importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","")
        val zip=FullZipRepository(db,store);val bytes=ByteArrayOutputStream().also { zip.export(it) }.toByteArray();images.deleteTrade(t)
        val local=trade();val old=images.importImage(context.contentResolver,TestImageProvider.uri("second.png"),local.recordKey,"holding","本地保留")
        val before=BackupRepository(db,store).export(full=true);val preview=zip.read(ByteArrayInputStream(bytes))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON attachments BEGIN SELECT RAISE(ABORT,'synthetic restore failure'); END")
        try{zip.restore(preview,RestoreMode.REPLACE);fail()}catch(_:Exception){}finally{db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_restore");preview.archive.close()}
        val after=BackupRepository(db,store).export(full=true);assertEquals(before.copy(exportedAt=after.exportedAt),after)
        store.verify(old.let(store::file),old);assertEquals(setOf(attachmentFileName(old)),root.listFiles()!!.filter { it.isFile }.map { it.name }.toSet())
    }
    @Test fun jsonScopeIsHonestOldFormatsImportAndPreviewIncludesGoalsAndImages()=runBlocking {
        val t=trade();images.importImage(context.contentResolver,TestImageProvider.uri(),t.recordKey,"analysis","")
        val repo=BackupRepository(db,store);val json=repo.export();assertEquals("structured-data-only",json.backupScope);assertTrue(json.attachments.isEmpty())
        val oldAssets=InstrumentationRegistry.getInstrumentation().context.assets
        listOf("v02-backup.json","v03-backup.json","v03-android-backup.json","v04-backup.json","v05-backup.json").forEach { name->
            val old=oldAssets.open(name).use(BackupCodec::read);assertEquals("structured-data-only",old.backupScope);assertTrue(old.goals.isEmpty());assertTrue(old.attachments.isEmpty())
            val p=repo.preview(old);repo.restore(p,RestoreMode.MERGE);assertTrue(store.file(db.trades().allAttachments().single()).exists())
        }
        val full=repo.preview(repo.export(full=true));assertEquals(1,full.before.attachments.size);assertEquals(1,full.backup.attachments.size)
        val changed=repo.preview(repo.export());db.trades().saveEvidenceSettings(EvidenceSettings(maxInputMiB=5))
        try{repo.restore(changed,RestoreMode.REPLACE);fail()}catch(_:BackupException){};assertEquals(1,db.trades().allAttachments().size)
    }
}
