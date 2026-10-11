package com.tradereview.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TradeDatabaseTest {
    @Test fun tradesAndDailyReviewPersistAcrossReopenAndSupportEditDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, TradeDatabase::class.java, name).build()
        var db = open()
        try {
            val original = Trade(netPnl = "123.45", mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop"))
            val id = db.trades().insert(original)
            db.trades().saveReview(DailyReview(date = 20_000, planSaved = true, strategies = listOf("trend")))
            db.close()
            db = open()
            assertEquals(original.copy(id = id), db.trades().findTrade(id))
            val edited = original.copy(id = id, netPnl = "-20.00", reviewed = true)
            db.trades().save(edited)
            assertEquals(edited, db.trades().findTrade(id))
            val plan = requireNotNull(db.trades().findReview(20_000))
            db.trades().saveReview(plan.copy(reviewSaved = true, rating = 5, lessons = listOf("stop")))
            val review = requireNotNull(db.trades().findReview(20_000))
            assertTrue(review.planSaved)
            assertTrue(review.reviewSaved)
            assertEquals(listOf("trend"), review.strategies)
            assertEquals(5, review.rating)
            db.trades().delete(edited)
            assertNull(db.trades().findTrade(id))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
