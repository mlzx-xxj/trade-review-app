package com.tradereview.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TradeDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-v1-v2.db"
    @After fun clean() { context.deleteDatabase(name) }

    @Test fun officialV1MigrationPreservesEveryFieldAndStableKeysAcrossReopen() = runBlocking {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO trades VALUES(7,20000,'stock','沪深300','short','CNY','-123.45',25,'trend','anxious','chase|oversize','respect_stop',1)")
            execSQL("INSERT INTO trades VALUES(8,20000,'stock','AAPL','long','USD','1.00',10,'range','calm','','',0)")
            execSQL("INSERT INTO daily_reviews VALUES(20000,'bullish','trend|breakout','risk',25,1,'anxious','respect_stop','chase','stop|rest',4,1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()
        fun open() = Room.databaseBuilder(context, TradeDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        var db = open()
        try {
            val trade = requireNotNull(db.trades().findTrade(7))
            assertEquals(Trade(id = 7, date = 20000, direction = "short", netPnl = "-123.45", positionPercent = 25, emotion = "anxious",
                mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop"), reviewed = true, recordKey = trade.recordKey), trade)
            assertTrue(Regex("[0-9a-f]{32}").matches(trade.recordKey))
            assertNotEquals(trade.recordKey, db.trades().findTrade(8)!!.recordKey)
            assertEquals(DailyReview(date = 20000, outlook = "bullish", strategies = listOf("trend", "breakout"), preparations = listOf("risk"),
                maxPosition = 25, planSaved = true, emotion = "anxious", discipline = listOf("respect_stop"), mistakes = listOf("chase"),
                lessons = listOf("stop", "rest"), rating = 4, reviewSaved = true), db.trades().findReview(20000))
            db.trades().save(trade.copy(netPnl = "1.23"))
            db.close(); db = open()
            assertEquals(trade.recordKey, db.trades().findTrade(7)!!.recordKey)
            assertEquals("1.23", db.trades().findTrade(7)!!.netPnl)
            assertEquals(2, db.trades().allTrades().size)
        } finally { db.close() }
    }
    @Test fun emptyV1DatabaseMigratesAndAcceptsNewRecords() = runBlocking {
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()
        val db = Room.databaseBuilder(context, TradeDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val record = Trade()
            val id = db.trades().insert(record)
            assertEquals(record.copy(id = id), db.trades().findTrade(id))
        } finally { db.close() }
    }
}
