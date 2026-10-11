package com.tradereview.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.backup.*
import com.tradereview.app.domain.TradeFilter
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V04DataTest {
    private lateinit var db: TradeDatabase
    private lateinit var backup: BackupRepository
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TradeDatabase::class.java).addCallback(CatalogSeeds.callback).build(); backup = BackupRepository(db) }
    @After fun close() { db.close() }
    @Test fun newPreferencesAndSavedFiltersSurviveCompleteRestoreAndIdempotentMerge() = runBlocking {
        val custom = ManagedTag("strategy", "custom_v04", "隐藏历史策略", hidden = true)
        db.trades().saveTag(custom)
        db.trades().saveSettings(AppSettings(theme = "dark", pnlPalette = "red_profit"))
        val filter = SavedFilter(name = "历史策略筛选", currency = "USD", criteria = TradeFilter(strategies = listOf(custom.id), tagMatch = "all"))
        db.trades().saveFilter(filter)
        val original = BackupCodec.decode(BackupCodec.encode(backup.export()))
        db.trades().clearFilters(); db.trades().saveSettings(AppSettings())
        backup.restore(backup.preview(original), RestoreMode.REPLACE)
        assertEquals(filter, db.trades().allFilters().single()); assertEquals("red_profit", db.trades().settings()!!.pnlPalette)
        backup.restore(backup.preview(original), RestoreMode.MERGE); assertEquals(1, db.trades().allFilters().size)
    }
    @Test fun v03GoldenBackupRestoresHiddenCustomLabelsNotesReviewsAndDefaults() = runBlocking {
        val old = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("v03-backup.json").use(BackupCodec::read)
        backup.restore(backup.preview(old), RestoreMode.REPLACE)
        assertEquals(old.trades, db.trades().allTrades()); assertEquals(old.dailyReviews, db.trades().allReviews())
        assertTrue(db.trades().allTags().first { it.id == "custom_v03" }.hidden)
        assertEquals("green_profit", db.trades().settings()!!.pnlPalette); assertTrue(db.trades().allFilters().isEmpty())
    }
    @Test fun failureInNewFilterTableRollsBackAllTablesAndPaletteInBothModes() = runBlocking {
        db.trades().insert(Trade(date = 20000, netPnl = "7.00"))
        db.trades().saveFilter(SavedFilter(name = "原筛选"))
        db.trades().saveSettings(AppSettings(pnlPalette = "red_profit"))
        val original = backup.export()
        val incoming = original.copy(trades = listOf(Trade(id = 99, date = 20000, netPnl = "99.00")), settings = AppSettings(),
            savedFilters = listOf(SavedFilter(name = "先写入"), SavedFilter(name = "故障筛选")))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_filter BEFORE INSERT ON saved_filters WHEN NEW.name = '故障筛选' BEGIN SELECT RAISE(ABORT, 'injected filter failure'); END")
        RestoreMode.entries.forEach { mode ->
            try { backup.restore(backup.preview(incoming), mode); fail("Expected SQLite rollback") }
            catch (_: android.database.sqlite.SQLiteException) { val after = backup.export(); assertEquals(original.copy(exportedAt = after.exportedAt), after) }
        }
    }
    @Test fun changingPresetAfterPreviewRejectsStaleRestoreAndMergeKeepsLocalConflicts() = runBlocking {
        val value = SavedFilter(name = "原方案")
        db.trades().saveFilter(value)
        val data = backup.export(); val preview = backup.preview(data)
        db.trades().saveFilter(value.copy(name = "本地改名"))
        assertThrows(BackupException::class.java) { runBlocking { backup.restore(preview, RestoreMode.REPLACE) } }
        backup.restore(backup.preview(data), RestoreMode.MERGE)
        assertEquals("本地改名", db.trades().allFilters().single().name)
    }
}
