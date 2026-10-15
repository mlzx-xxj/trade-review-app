package com.tradereview.app.v200

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Only nullability changes. Every old value, stable ID, index and FK is retained.
 * Room runs the complete copy/swap transaction atomically. Never disable FKs.
 */
val MIGRATION_8_9=object:Migration(8,9){
    override fun migrate(db:SupportSQLiteDatabase){
        db.execSQL("CREATE TABLE IF NOT EXISTS `new_ledger_executions` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `date` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `tradingDay` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `side` TEXT NOT NULL, `effect` TEXT NOT NULL, `price` TEXT NOT NULL, `quantity` TEXT NOT NULL, `fees` TEXT, `feeSource` TEXT NOT NULL, `feeRateSnapshot` TEXT, `multiplier` TEXT, `marginRate` TEXT, `parametersConfirmed` INTEGER NOT NULL, `ruleSnapshot` TEXT, `strategyId` TEXT, `planId` TEXT, `takeProfit` TEXT, `stopLoss` TEXT, `riskAmount` TEXT, `riskFundSnapshot` TEXT, `riskLimitSnapshot` TEXT, `riskExceeded` INTEGER NOT NULL, `riskConfirmed` INTEGER NOT NULL, `riskWarningEnabled` INTEGER NOT NULL, `second` INTEGER, `orderConfirmed` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `ledger_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`strategyId`) REFERENCES `ledger_strategies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`planId`) REFERENCES `ledger_plans`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("INSERT INTO `new_ledger_executions` SELECT * FROM `ledger_executions`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `new_ledger_cycles` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `direction` TEXT NOT NULL, `firstDay` INTEGER NOT NULL, `closedDay` INTEGER, `remaining` TEXT NOT NULL, `averagePrice` TEXT NOT NULL, `openCost` TEXT, `fees` TEXT, `realized` TEXT, `takeProfit` TEXT, `stopLoss` TEXT, `referenceMargin` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `ledger_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("INSERT INTO `new_ledger_cycles` SELECT * FROM `ledger_cycles`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `new_ledger_lots` (`id` TEXT NOT NULL, `executionId` TEXT NOT NULL, `cycleId` TEXT NOT NULL, `originalQuantity` TEXT NOT NULL, `remaining` TEXT NOT NULL, `allocatedFees` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`executionId`) REFERENCES `new_ledger_executions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`cycleId`) REFERENCES `new_ledger_cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("INSERT INTO `new_ledger_lots` SELECT * FROM `ledger_lots`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `new_ledger_matches` (`id` TEXT NOT NULL, `openExecutionId` TEXT NOT NULL, `closeExecutionId` TEXT NOT NULL, `cycleId` TEXT NOT NULL, `quantity` TEXT NOT NULL, `gross` TEXT NOT NULL, `openFees` TEXT, `closeFees` TEXT, `net` TEXT, `day` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`openExecutionId`) REFERENCES `new_ledger_executions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`closeExecutionId`) REFERENCES `new_ledger_executions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`cycleId`) REFERENCES `new_ledger_cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("INSERT INTO `new_ledger_matches` SELECT * FROM `ledger_matches`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `new_ledger_reviews` (`id` TEXT NOT NULL, `cycleId` TEXT NOT NULL, `day` INTEGER NOT NULL, `takeProfit` TEXT, `stopLoss` TEXT, `levels` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`cycleId`) REFERENCES `new_ledger_cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("INSERT INTO `new_ledger_reviews` SELECT * FROM `ledger_reviews`")
        db.execSQL("DROP TABLE `ledger_reviews`")
        db.execSQL("DROP TABLE `ledger_matches`")
        db.execSQL("DROP TABLE `ledger_lots`")
        db.execSQL("DROP TABLE `ledger_cycles`")
        db.execSQL("DROP TABLE `ledger_executions`")
        db.execSQL("ALTER TABLE `new_ledger_executions` RENAME TO `ledger_executions`")
        db.execSQL("ALTER TABLE `new_ledger_cycles` RENAME TO `ledger_cycles`")
        db.execSQL("ALTER TABLE `new_ledger_lots` RENAME TO `ledger_lots`")
        db.execSQL("ALTER TABLE `new_ledger_matches` RENAME TO `ledger_matches`")
        db.execSQL("ALTER TABLE `new_ledger_reviews` RENAME TO `ledger_reviews`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_accountId` ON `ledger_executions` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_accountId_code_tradingDay` ON `ledger_executions` (`accountId`, `code`, `tradingDay`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_planId` ON `ledger_executions` (`planId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_strategyId` ON `ledger_executions` (`strategyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_cycles_accountId` ON `ledger_cycles` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_cycles_accountId_code_direction` ON `ledger_cycles` (`accountId`, `code`, `direction`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_lots_cycleId` ON `ledger_lots` (`cycleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_lots_executionId` ON `ledger_lots` (`executionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_closeExecutionId` ON `ledger_matches` (`closeExecutionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_cycleId` ON `ledger_matches` (`cycleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_day` ON `ledger_matches` (`day`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_openExecutionId` ON `ledger_matches` (`openExecutionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_reviews_cycleId` ON `ledger_reviews` (`cycleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_reviews_cycleId_day` ON `ledger_reviews` (`cycleId`, `day`)")
        db.query("PRAGMA foreign_key_check").use{check(it.count==0){"迁移外键校验失败，全部回滚"}}
    }
}
