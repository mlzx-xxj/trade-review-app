package com.tradereview.app

import android.app.Application
import androidx.room.Room
import com.tradereview.app.data.TradeDatabase

class TradeReviewApplication : Application() {
    val database: TradeDatabase by lazy {
        Room.databaseBuilder(this, TradeDatabase::class.java, "trade-review.db").build()
    }
}
