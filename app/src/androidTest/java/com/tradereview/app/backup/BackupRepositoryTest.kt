package com.tradereview.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tradereview.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var db: TradeDatabase
    private lateinit var repository: BackupRepository
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TradeDatabase::class.java).build()
        repository = BackupRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun seed(): Trade {
        val trade = Trade(date = 20000, netPnl = "12.34", mistakes = listOf("chase"), discipline = listOf("respect_stop"), reviewed = true)
        val id = db.trades().insert(trade)
        db.trades().saveReview(DailyReview(date = 20000, planSaved = true, reviewSaved = true, strategies = listOf("trend"), lessons = listOf("stop")))
        return trade.copy(id = id)
    }
    private suspend fun state() = DatabaseSnapshot(db.trades().allTrades(), db.trades().allReviews())

    @Test fun exportDecodeReplaceRoundTripPreservesAllData() = runBlocking {
        seed(); val before = state()
        val data = BackupCodec.decode(BackupCodec.encode(repository.export()))
        db.trades().clearTrades(); db.trades().clearReviews()
        repository.restore(repository.preview(data), RestoreMode.REPLACE)
        assertEquals(before, state())
    }
    @Test fun previewDoesNotWriteAndChangedDatabaseRejectsStaleConfirmation() = runBlocking {
        val original = seed(); val before = state(); val preview = repository.preview(repository.export())
        assertEquals(before, state())
        db.trades().save(original.copy(netPnl = "99.99")); val changed = state()
        try { repository.restore(preview, RestoreMode.REPLACE); fail("Should reject stale preview") }
        catch (_: BackupException) { assertEquals(changed, state()) }
    }
    @Test fun mergeIsIdempotentUsesStableKeysAndHandlesCollidingRowIds() = runBlocking {
        val original = seed()
        val incoming = Trade(id = original.id, date = 20000, symbol = "合并新记录", netPnl = "5.00")
        val data = BackupData("2026-10-11T07:00:00Z", listOf(original.copy(id = 777), incoming), emptyList())
        val preview = repository.preview(data)
        assertEquals(1, preview.summary.newTrades); assertEquals(1, preview.summary.repeatedTrades)
        repository.restore(preview, RestoreMode.MERGE)
        assertEquals(2, db.trades().allTrades().size)
        assertEquals(original, db.trades().findTrade(original.id))
        val result = state()
        repository.restore(repository.preview(data), RestoreMode.MERGE)
        assertEquals(result, state())
    }
    @Test fun conflictsKeepLocalValuesAndMissingPlanOrReviewIsFilled() = runBlocking {
        val original = seed()
        val localDay = DailyReview(date = 20000, outlook = "bearish", planSaved = true)
        db.trades().saveReview(localDay)
        db.trades().saveReview(DailyReview(date = 19999, reviewSaved = true, rating = 5))
        val incomingDay = DailyReview(date = 20000, outlook = "bullish", planSaved = true, reviewSaved = true, rating = 4, lessons = listOf("stop"))
        val data = BackupData("2026-10-11T07:00:00Z", listOf(original.copy(netPnl = "777.00")), listOf(incomingDay,
            DailyReview(date = 19999, planSaved = true, preparations = listOf("risk"), reviewSaved = true, rating = 2)))
        val preview = repository.preview(data)
        assertEquals(1, preview.summary.conflictingTrades); assertEquals(1, preview.summary.conflictingPlans)
        assertEquals(1, preview.summary.conflictingReviews); assertEquals(1, preview.summary.addedPlans); assertEquals(1, preview.summary.addedReviews)
        repository.restore(preview, RestoreMode.MERGE)
        assertEquals(original, db.trades().findTrade(original.id))
        assertEquals("bearish", db.trades().findReview(20000)!!.outlook)
        assertEquals(4, db.trades().findReview(20000)!!.rating)
        assertEquals(listOf("stop"), db.trades().findReview(20000)!!.lessons)
        assertEquals(listOf("risk"), db.trades().findReview(19999)!!.preparations)
        assertEquals(5, db.trades().findReview(19999)!!.rating)
    }
    @Test fun overwriteFailureAfterDeleteAndFirstInsertRollsBackBothTables() = runBlocking {
        seed(); val before = state()
        val data = BackupData("2026-10-11T07:00:00Z", listOf(Trade(id = 20, date = 20000, symbol = "先插入"), Trade(id = 21, date = 20000, symbol = "故障记录")),
            listOf(DailyReview(date = 19998, planSaved = true)))
        forceInsertFailure()
        try { repository.restore(repository.preview(data), RestoreMode.REPLACE); fail("Expected actual SQLite failure") }
        catch (_: android.database.sqlite.SQLiteException) { assertEquals(before, state()) }
    }
    @Test fun mergeFailureRollsBackPartialInsert() = runBlocking {
        seed(); val before = state()
        val data = BackupData("2026-10-11T07:00:00Z", listOf(Trade(id = 20, date = 20000, symbol = "先插入"), Trade(id = 21, date = 20000, symbol = "故障记录")), emptyList())
        forceInsertFailure()
        try { repository.restore(repository.preview(data), RestoreMode.MERGE); fail("Expected actual SQLite failure") }
        catch (_: android.database.sqlite.SQLiteException) { assertEquals(before, state()) }
    }
    @Test fun invalidBackupCannotReachDatabaseWrites() = runBlocking {
        seed(); val before = state()
        val invalid = BackupData("2026-10-11T07:00:00Z", listOf(Trade(id = 3, positionPercent = 999)), emptyList())
        try { repository.preview(invalid); fail("Should reject invalid backup") }
        catch (_: BackupException) { assertEquals(before, state()) }
    }
    @Test fun reviewInsertFailureRollsBackTradeAndReviewWritesInBothModes() = runBlocking {
        seed(); val before = state()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_review BEFORE INSERT ON daily_reviews WHEN NEW.date = 19998 BEGIN SELECT RAISE(ABORT, 'injected review failure'); END")
        val data = BackupData("2026-10-11T07:00:00Z", listOf(Trade(id = 20, date = 20000, symbol = "新交易")),
            listOf(DailyReview(date = 19999, planSaved = true), DailyReview(date = 19998, reviewSaved = true)))
        RestoreMode.entries.forEach { mode ->
            try { repository.restore(repository.preview(data), mode); fail("Expected review insert failure") }
            catch (_: android.database.sqlite.SQLiteException) { assertEquals(before, state()) }
        }
    }
    private fun forceInsertFailure() {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON trades WHEN NEW.symbol = '故障记录' BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END")
    }
}
