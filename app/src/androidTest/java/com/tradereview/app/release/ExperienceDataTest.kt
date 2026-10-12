package com.tradereview.app.release

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.tradereview.app.data.*
import com.tradereview.app.device.*
import com.tradereview.app.ui.BackupViewModel
import com.tradereview.app.evidence.AttachmentStore
import com.tradereview.app.backup.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExperienceDataTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:TradeDatabase;private lateinit var ops:DeviceExperienceStore;private lateinit var folder:File;private lateinit var model:BackupViewModel
    private val name="v100-device-${newKey()}"
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build();ops=DeviceExperienceStore(context,name);folder=File(context.cacheDir,"v100-${newKey()}");model=BackupViewModel(db,context.contentResolver,AttachmentStore(folder),ops)}
    @After fun close(){ops.close();context.deleteSharedPreferences(name);db.close();folder.deleteRecursively()}
    private suspend fun idle(){val deadline=System.currentTimeMillis()+30000;while(model.busy.value && System.currentTimeMillis()<deadline)delay(30);assertFalse("operation did not complete",model.busy.value)}
    @Test fun freshGuideSkipAndExistingUpgradePersistWithoutBusinessWrites()=runBlocking{
        ops.initialize(false,1000);assertFalse(ops.state.value.onboardingDone);ops.finishOnboarding();ops.close();ops=DeviceExperienceStore(context,name);ops.initialize(false,2000);assertTrue(ops.state.value.onboardingDone);assertEquals(1000L,ops.state.value.firstSeenMillis);assertTrue(db.trades().allTrades().isEmpty())
        val other=DeviceExperienceStore(context,name+"old");try{other.initialize(true,1000);assertTrue(other.state.value.onboardingDone)}finally{other.close();context.deleteSharedPreferences(name+"old")}
    }
    @Test fun verifiedJsonAndZipOnlyUpdateAfterRealProviderOutputAndRestoresDoNotImportDeviceHistory()=runBlocking{
        val id=db.trades().insert(Trade(netPnl="123.45"));ops.initialize(true)
        assertEquals(0L,ops.state.value.zipWrittenMillis)
        model.exportJson(BackupDocumentProvider.uri());idle();assertTrue(model.message.value!!,model.message.value!!.contains("校验"));assertTrue(ops.state.value.verifiedJsonMillis>0);assertEquals(0L,ops.state.value.zipWrittenMillis)
        val uri=BackupDocumentProvider.uri();model.exportZip(uri);idle();assertTrue(ops.state.value.verifiedZipMillis>0);assertEquals("sha256",ops.state.value.zipVerification)
        val originalKey=db.trades().allTrades().single().recordKey;val history=ops.state.value;model.readBackup(uri);idle();assertNotNull(model.preview.value);model.confirmRestore(RestoreMode.REPLACE);idle();assertEquals(history,ops.state.value);assertEquals("123.45",db.trades().allTrades().single().netPnl);assertNotNull(db.trades().findTradeByKey(originalKey));assertEquals(originalKey,db.trades().allTrades().single().recordKey)
        val encoded=BackupCodec.encode(BackupRepository(db).export());assertFalse(encoded.contains("zipWritten"));assertFalse(encoded.contains("onboardingDone"))
    }
    @Test fun outputFailureAndReadableCorruptionKeepPreviousSuccessfulTimesAndTransactions()=runBlocking{
        val trade=Trade(netPnl="-1.23");val id=db.trades().insert(trade);ops.initialize(true);ops.exported("zip",true,1000)
        for(mode in listOf("write-fail","corrupt")){model.exportZip(BackupDocumentProvider.uri(mode));idle();assertEquals(1000L,ops.state.value.zipWrittenMillis);assertEquals(1000L,ops.state.value.verifiedZipMillis);assertEquals(listOf(trade.copy(id=id)),db.trades().allTrades());assertNotNull(model.message.value)}
    }
    @Test fun writeOnlyProviderShowsUnverifiedAndCsvNeverUpdatesBackupHistory()=runBlocking{
        db.trades().insert(Trade(netPnl="1.00"));ops.initialize(true)
        model.exportZip(BackupDocumentProvider.uri("no-read"));idle();assertTrue(ops.state.value.zipWrittenMillis>0);assertEquals(0L,ops.state.value.verifiedZipMillis);assertEquals("unavailable",ops.state.value.zipVerification);assertTrue(model.message.value!!.contains("无法回读"))
        val before=ops.state.value;model.exportCsv(BackupDocumentProvider.uri());idle();assertEquals(before,ops.state.value)
    }
    @Test fun reminderDisableAndSnoozeSurviveReopen(){ops.initialize(true,1000);ops.reminders(false);ops.snooze(7,2000);ops.close();ops=DeviceExperienceStore(context,name);assertFalse(ops.state.value.remindersEnabled);assertEquals(2000+7L*24*60*60*1000,ops.state.value.snoozedUntilMillis)}
}
