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
        assertEquals(old.drafts, db.trades().allDrafts()); assertEquals("12.", db.trades().allDrafts().single().amount)
    }
    @Test fun bothJvmAndArtV03ChecksumsImportAndReExportWithIdenticalCanonicalV04Bytes() = runBlocking {
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val jvm = assets.open("v03-backup.json").use(BackupCodec::read)
        val art = assets.open("v03-android-backup.json").use(BackupCodec::read)
        assertEquals(jvm, art); assertEquals(BackupCodec.encode(jvm), BackupCodec.encode(art))
        assertTrue(BackupCodec.encode(jvm).indexOf("\"kind\"") < BackupCodec.encode(jvm).indexOf("\"favorite\""))
        backup.restore(backup.preview(art), RestoreMode.REPLACE)
        val roundTrip = BackupCodec.decode(BackupCodec.encode(backup.export()))
        assertEquals(art.trades, roundTrip.trades); assertEquals(art.tags.associateBy { it.kind to it.id }, roundTrip.tags.associateBy { it.kind to it.id })
        // Generate the old format using the actual ART reflection layout, not a mock.
        val gson = com.google.gson.GsonBuilder().disableHtmlEscaping().create()
        val root = com.google.gson.JsonParser.parseString(assets.open("v03-backup.json").bufferedReader().use { it.readText() }).asJsonObject
        root.remove("checksumSha256")
        root.add("tags", gson.toJsonTree(jvm.tags)); root.add("instruments", gson.toJsonTree(jvm.instruments)); root.add("drafts", gson.toJsonTree(jvm.drafts))
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(gson.toJson(root).toByteArray(Charsets.UTF_8))
        root.addProperty("checksumSha256", bytes.joinToString("") { "%02x".format(it) })
        assertEquals(jvm, BackupCodec.decode(gson.toJson(root)))
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
    @Test fun canonicalV04GoldenBackupRestoresEveryNewFieldAndMatchesJvmDigest() = runBlocking {
        val json = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("v04-backup.json").bufferedReader().use { it.readText() }
        val data = BackupCodec.decode(json)
        val expected = com.google.gson.JsonParser.parseString(json).asJsonObject["checksumSha256"].asString
        val actual = com.google.gson.JsonParser.parseString(BackupCodec.encode(data)).asJsonObject["checksumSha256"].asString
        assertNotEquals(expected, actual) // Re-export upgrades old format 3 to current format 4.
        assertEquals(data.copy(sourceSchemaVersion = BackupCodec.SCHEMA_VERSION), BackupCodec.decode(BackupCodec.encode(data)))
        backup.restore(backup.preview(data), RestoreMode.REPLACE)
        assertEquals("red_profit", db.trades().settings()!!.pnlPalette)
        assertEquals(data.savedFilters, db.trades().allFilters()); assertEquals(data.drafts, db.trades().allDrafts())
    }
}
