package com.tradereview.app

import android.app.Application
import androidx.room.Room
import com.tradereview.app.data.TradeDatabase
import com.tradereview.app.data.MIGRATION_1_2

class TradeReviewApplication : Application() {
    val database: TradeDatabase by lazy {
        Room.databaseBuilder(this, TradeDatabase::class.java, "trade-review.db")
            .addMigrations(MIGRATION_1_2).build()
    }
}
