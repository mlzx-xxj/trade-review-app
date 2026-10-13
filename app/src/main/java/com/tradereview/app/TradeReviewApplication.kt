package com.tradereview.app

import android.app.Application
import androidx.room.Room
import com.tradereview.app.data.TradeDatabase
import com.tradereview.app.data.MIGRATION_1_2
import com.tradereview.app.data.MIGRATION_2_3
import com.tradereview.app.data.MIGRATION_3_4
import com.tradereview.app.data.CatalogSeeds

class TradeReviewApplication : Application() {
    val database: TradeDatabase by lazy {
        Room.databaseBuilder(this, TradeDatabase::class.java, "trade-review.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, com.tradereview.app.data.MIGRATION_4_5, com.tradereview.app.data.MIGRATION_5_6, com.tradereview.app.data.MIGRATION_6_7, com.tradereview.app.v200.MIGRATION_7_8).addCallback(CatalogSeeds.callback).build()
    }
    private val evidence=lazy { com.tradereview.app.evidence.AttachmentStore(java.io.File(filesDir, "trade-evidence")) }
    val evidenceStore by evidence
    val deviceExperience by lazy { com.tradereview.app.device.DeviceExperienceStore(this) }
    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && evidence.isInitialized())evidence.value.clearImageCache()
    }
}
