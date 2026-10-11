package com.tradereview.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V03DataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "v03-data-test.db"
    private lateinit var db: TradeDatabase
    @Before fun open() { context.deleteDatabase(name); db = build() }
    private fun build() = Room.databaseBuilder(context, TradeDatabase::class.java, name).addCallback(CatalogSeeds.callback).build()
    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun fastCrudCopyAndDraftRestorePersistAcrossReopen() = runBlocking {
        val repo = EntryRepository(db)
        val draft = EntryDraft(market = "futures", symbol = "黄金", amount = "123.45", note = "备注", mistakes = listOf("chase"), discipline = listOf("respect_stop"), reviewed = true)
        repo.saveDraft(draft); db.close(); db = build()
        assertEquals(draft, db.trades().allDrafts().single())
        EntryRepository(db).save(draft)
        assertTrue(db.trades().allDrafts().isEmpty())
        val trade = db.trades().allTrades().single()
        assertEquals("123.45", trade.netPnl); assertEquals("备注", trade.note)
        assertEquals("futures", db.trades().settings()!!.lastMarket)
        assertTrue(db.trades().allInstruments().first { it.name == "黄金" }.lastUsed > 0)
        assertTrue(db.trades().allTags().first { it.kind == "strategy" && it.id == "trend" }.lastUsed > 0)
        val copied = EntryDraft.copyReusable(trade)
        assertEquals("", copied.amount); assertFalse(copied.reviewed); assertEquals("", copied.outcome)
        EntryRepository(db).save(copied.copy(amount = "5", outcome = "loss"))
        EntryRepository(db).save(EntryDraft.from(trade).copy(amount = "20", outcome = "loss"))
        assertEquals("-20.00", db.trades().findTrade(trade.id)!!.netPnl)
        EntryRepository(db).delete(db.trades().findTrade(trade.id)!!)
        assertEquals(1, db.trades().allTrades().size)
    }
    @Test fun customTagRenameHideOrderAndFavoriteKeepHistoricalReferences() = runBlocking {
        val repo = EntryRepository(db)
        val custom = ManagedTag("strategy", "custom_test", "自定义", 0)
        repo.saveTag(custom)
        repo.save(EntryDraft(strategy = custom.id, amount = "10"))
        repo.saveTag(custom.copy(name = "重命名", hidden = true, favorite = true))
        repo.moveTag(custom, 1)
        db.close(); db = build()
        val tags = db.trades().allTags(); val tag = tags.first { it.id == custom.id }
        assertEquals("重命名", tag.name); assertTrue(tag.hidden); assertTrue(tag.favorite)
        assertEquals(custom.id, db.trades().allTrades().single().strategy)
        assertEquals("重命名", Catalog(tags).label("strategy", custom.id))
        assertFalse(Catalog(tags).options("strategy").any { it.id == custom.id })
        assertTrue(Catalog(tags).options("strategy", listOf(custom.id)).any { it.id == custom.id })
        assertTrue(TradeCsv.encode(db.trades().allTrades(), Catalog(tags)).contains("重命名"))
    }
    @Test fun favoritesRecentSearchAndUserOrderingPersist() = runBlocking {
        val repo = EntryRepository(db)
        val a = Instrument("custom_a", "futures", "测试甲", "TA", 50)
        val b = Instrument("custom_b", "futures", "测试乙", "TB", 51)
        repo.saveInstrument(a); repo.saveInstrument(b.copy(favorite = true))
        repo.moveInstrument(b, -1)
        repo.save(EntryDraft(market = "futures", symbol = a.name, amount = "1"))
        db.close(); db = build()
        val values = db.trades().allInstruments()
        assertEquals(a.id, orderedInstruments(values, "futures").first().id)
        assertEquals(a.id, orderedInstruments(values, "futures", "ta").single().id)
        assertTrue(values.first { it.id == b.id }.favorite)
        assertTrue(values.first { it.id == b.id }.sortOrder < values.first { it.id == a.id }.sortOrder)
        EntryRepository(db).saveInstrument(values.first { it.id == b.id }.copy(favorite = false))
        assertFalse(db.trades().allInstruments().first { it.id == b.id }.favorite)
    }
    @Test fun v03FullBackupRoundTripAndActualLateCatalogFailureRollBackEveryTable() = runBlocking {
        val dao = db.trades(); val tag = ManagedTag("strategy", "custom_backup", "策略备份", 99, true, true)
        EntryRepository(db).saveTag(tag)
        dao.saveInstrument(Instrument("custom_backup_symbol", "stock", "新品种", "NP", 9, true, 22))
        dao.saveSettings(AppSettings(theme = "dark")); dao.saveDraft(EntryDraft(amount = "9.", strategy = tag.id))
        val repo = BackupRepository(db)
        dao.insert(Trade(date = 20000, symbol = "原有记录", strategy = tag.id, netPnl = "8.00"))
        dao.saveReview(DailyReview(date = 20000, planSaved = true, reviewSaved = true))
        val original = repo.export()
        val roundTrip = BackupCodec.decode(BackupCodec.encode(original))
        repo.restore(repo.preview(roundTrip), RestoreMode.REPLACE)
        assertEquals(original.copy(exportedAt = "x"), repo.export().copy(exportedAt = "x"))
        val changed = roundTrip.copy(tags = roundTrip.tags.map { if(it.id == tag.id) it.copy(name = "变更名称") else it }, settings = AppSettings(theme = "light"), drafts = roundTrip.drafts.map { it.copy(note = "故障草稿") })
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_draft BEFORE INSERT ON entry_drafts WHEN NEW.note='故障草稿' BEGIN SELECT RAISE(ABORT,'injected late table failure'); END")
        try { repo.restore(repo.preview(changed), RestoreMode.REPLACE); fail("Expected SQL failure") }
        catch (_: android.database.sqlite.SQLiteException) { assertEquals(original.copy(exportedAt = "x"), repo.export().copy(exportedAt = "x")) }
    }
    @Test fun legacyV02BackupCanReplaceAndThenExportAsNewFormat() = runBlocking {
        val data = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("v02-backup.json").use(BackupCodec::read)
        val repo = BackupRepository(db)
        repo.restore(repo.preview(data), RestoreMode.REPLACE)
        assertEquals("旧备份黄金", db.trades().allTrades().single().symbol)
        assertEquals("0123456789abcdef0123456789abcdef", db.trades().allTrades().single().recordKey)
        assertTrue(db.trades().allReviews().single().reviewSaved)
        assertEquals(2, BackupCodec.decode(BackupCodec.encode(repo.export())).sourceSchemaVersion)
    }
    @Test fun entryFailureKeepsDraftAndDoesNotUpdateTradeOrRecents() = runBlocking {
        val d = EntryDraft(amount = "5")
        val repo = EntryRepository(db); repo.saveDraft(d)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_recency BEFORE UPDATE ON instruments BEGIN SELECT RAISE(ABORT,'injected recency failure'); END")
        try { repo.save(d); fail("Expected SQL failure") }
        catch (_: android.database.sqlite.SQLiteException) {
            assertTrue(db.trades().allTrades().isEmpty()); assertEquals(d, db.trades().allDrafts().single())
            assertTrue(db.trades().allInstruments().all { it.lastUsed == 0L })
        }
    }
    @Test fun delayedDraftWriteCannotResurrectSavedOrDeletedEntry() = runBlocking {
        val repo = EntryRepository(db); val d = EntryDraft(amount = "1")
        repo.saveDraft(d); repo.save(d); repo.saveDraft(d)
        assertTrue(db.trades().allDrafts().isEmpty())
        val trade = db.trades().allTrades().single(); val edit = EntryDraft.from(trade)
        repo.saveDraft(edit); repo.delete(trade); repo.saveDraft(edit)
        assertTrue(db.trades().allDrafts().isEmpty())
    }

}
