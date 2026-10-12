package com.tradereview.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.backup.*
import com.tradereview.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V05DataTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val assets=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
    private lateinit var db: TradeDatabase
    @Before fun open() { db=Room.inMemoryDatabaseBuilder(context,TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build() }
    @After fun close() { db.close() }
    private fun amount(amount: String="400")=EntryDraft(amount=amount,professional=ProfessionalDraft(riskMode="amount",manualRisk="200"))
    @Test fun nativeProfessionalRoundTripEditingAndDeletionKeepOneAuthorityAndStableIdentity()=runBlocking {
        val raw=ProfessionalDraft(pnlMode="auto",quantityUnit="futures_lot",entryPrice="3500.1",exitPrice="3520.35",quantity="2",multiplier="10",initialStop="3490.1",fees="3",otherCosts="2",costsConfirmed=true,linearConfirmed=true)
        val repo=EntryRepository(db);repo.save(EntryDraft(market="futures",symbol="螺纹钢",professional=raw))
        val saved=db.trades().allTrades().single();assertEquals("400.00",saved.netPnl);assertEquals("200",saved.riskRecord!!.events.single().initialRisk)
        val edit=EntryDraft.from(saved).copy(note="补录备注");repo.save(edit)
        val updated=db.trades().allTrades().single();assertEquals(saved.recordKey,updated.recordKey);assertEquals(saved.professional,updated.professional);assertEquals(saved.riskRecord,updated.riskRecord)
        repo.saveDraft(edit);repo.delete(updated);assertTrue(db.trades().allTrades().isEmpty());assertTrue(db.trades().allDrafts().isEmpty())
    }
    @Test fun riskConsentIsRequiredAndStaleRuleConfirmationDoesNotWriteAnything()=runBlocking {
        val repo=EntryRepository(db);val raw=amount();val rule=RiskRules(accountEquity="10000.00",maxRiskPercent="1")
        db.trades().saveRiskRules(rule);repo.saveDraft(raw)
        try { repo.save(raw);fail("Consent required") } catch(_:IllegalArgumentException) { }
        assertTrue(db.trades().allTrades().isEmpty());assertEquals(raw,db.trades().allDrafts().single())
        val token=RiskEngine.fingerprint(RiskEngine.check(FastEntry.trade(raw),rule,emptyList()))
        db.trades().saveRiskRules(rule.copy(maxRiskPercent="0.5"))
        try { repo.save(raw,token);fail("Stale consent") } catch(_:IllegalArgumentException) { }
        assertTrue(db.trades().allTrades().isEmpty())
        val current=db.trades().allRiskRules().single();repo.save(raw,RiskEngine.fingerprint(RiskEngine.check(FastEntry.trade(raw),current,emptyList())))
        val saved=db.trades().allTrades().single();assertEquals("400.00",saved.netPnl);assertEquals(listOf("single_risk"),saved.riskRecord!!.violations)
        db.trades().saveRiskRules(current.copy(maxRiskPercent="100"));repo.save(EntryDraft.from(saved).copy(note="复盘"))
        assertEquals(saved.riskRecord,db.trades().allTrades().single().riskRecord)
    }
    @Test fun backupRestoresAllNewDataAndMergeKeepsLocalConflictsAsWholeTrades()=runBlocking {
        val incoming=BackupCodec.read(assets.open("v05-backup.json"));val repository=BackupRepository(db)
        repository.restore(repository.preview(incoming),RestoreMode.REPLACE)
        val first=repository.export();assertEquals(incoming.trades,first.trades);assertEquals(incoming.riskRules,first.riskRules);assertEquals(incoming.contractTemplates,first.contractTemplates)
        assertEquals(first,BackupCodec.decode(BackupCodec.encode(first)))
        val local=first.trades.single().copy(netPnl="12.34",professional=first.trades.single().professional!!.copy(pnlMode="manual"))
        db.trades().save(local);db.trades().saveRiskRules(first.riskRules.single().copy(maxRiskPercent="3"))
        val preview=repository.preview(incoming);assertEquals(1,preview.summary.conflictingTrades)
        repository.restore(preview,RestoreMode.MERGE);assertEquals(local,db.trades().allTrades().single());assertEquals("3",db.trades().allRiskRules().single().maxRiskPercent)
        assertEquals(1,db.trades().allTemplates().size)
        val template=db.trades().allTemplates().single();db.trades().deleteTemplate(template);assertEquals(local.professional,db.trades().allTrades().single().professional)
    }
    @Test fun lastTableFailureRollsBackAllNineTablesForBothRestoreModes()=runBlocking {
        val repository=BackupRepository(db)
        db.trades().insert(Trade(netPnl="1.23"));db.trades().saveRiskRules(RiskRules(currency="USD",maxPositionPercent="40"))
        db.trades().saveDraft(EntryDraft(amount="12.",professional=ProfessionalDraft(entryPrice="3500.")))
        val incoming=BackupCodec.read(assets.open("v05-backup.json"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_v05_template BEFORE INSERT ON contract_templates BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
        for(mode in RestoreMode.values()) {
            val before=repository.export();val preview=repository.preview(incoming)
            try { repository.restore(preview,mode);fail("Injected failure must abort") } catch(_:Exception) { }
            assertEquals(before,repository.export().copy(exportedAt=before.exportedAt))
        }
    }
    @Test fun riskOrTemplateChangesInvalidateRestorePreviewAndOldFormatsPreserveAmounts()=runBlocking {
        val repo=BackupRepository(db)
        for(name in listOf("v02-backup.json","v03-backup.json","v03-android-backup.json","v04-backup.json")) {
            val old=BackupCodec.read(assets.open(name));repo.restore(repo.preview(old),RestoreMode.REPLACE)
            assertEquals(old.trades,db.trades().allTrades());assertTrue(db.trades().allTrades().all { it.professional==null && it.riskRecord==null })
            assertTrue(db.trades().allRiskRules().isEmpty());assertTrue(db.trades().allTemplates().isEmpty())
        }
        val data=BackupCodec.read(assets.open("v05-backup.json"));val preview=repo.preview(data)
        db.trades().saveRiskRules(RiskRules(maxPositionPercent="20"))
        try { repo.restore(preview,RestoreMode.REPLACE);fail("Stale rule snapshot") } catch(_:BackupException) { }
        val second=repo.preview(data);db.trades().saveTemplate(ContractTemplate(name="用户模板",market="futures",symbol="螺纹钢",quantityUnit="futures_lot",multiplier="10"))
        try { repo.restore(second,RestoreMode.REPLACE);fail("Stale template snapshot") } catch(_:BackupException) { }
    }
    @Test fun partialProfessionalDraftSurvivesNativeRoomAndJsonWithoutInventingFees()=runBlocking {
        val raw=EntryDraft(amount="12.",professional=ProfessionalDraft(entryPrice="3500.",expanded=true))
        EntryRepository(db).saveDraft(raw);assertEquals(raw,db.trades().allDrafts().single())
        val backup=BackupCodec.decode(BackupCodec.encode(BackupRepository(db).export()));assertEquals(raw,backup.drafts.single());assertEquals("",backup.drafts.single().professional!!.fees)
        val source=assets.open("v05-backup.json").bufferedReader().use { it.readText() }
        assertEquals("935070b335dad76a94d1d3bcd8615268260bb46adffd97fdebc1f1b622cea78a",com.google.gson.JsonParser.parseString(source).asJsonObject["checksumSha256"].asString)
        val old=BackupCodec.decode(source);val upgraded=BackupCodec.encode(old)
        assertNotEquals(com.google.gson.JsonParser.parseString(source).asJsonObject["checksumSha256"],com.google.gson.JsonParser.parseString(upgraded).asJsonObject["checksumSha256"])
        assertEquals(old.copy(sourceSchemaVersion=BackupCodec.SCHEMA_VERSION),BackupCodec.decode(upgraded))
    }
}
