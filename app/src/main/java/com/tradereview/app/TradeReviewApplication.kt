package com.tradereview.app

import android.app.Application
import androidx.room.Room
import com.tradereview.app.data.TradeDatabase
import com.tradereview.app.data.MIGRATION_1_2
import com.tradereview.app.data.MIGRATION_2_3
import com.tradereview.app.data.CatalogSeeds

class TradeReviewApplication : Application() {
    val database: TradeDatabase by lazy {
        Room.databaseBuilder(this, TradeDatabase::class.java, "trade-review.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(CatalogSeeds.callback).build()
    }
}
