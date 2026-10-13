package com.tradereview.app.v200

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

object LedgerSchema {
    fun create(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_accounts` (`id` TEXT NOT NULL PRIMARY KEY, `market` TEXT NOT NULL, `name` TEXT NOT NULL, `initialCapital` TEXT, `historyComplete` INTEGER NOT NULL, `buyRate` TEXT NOT NULL, `sellRate` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_strategies` (`id` TEXT NOT NULL PRIMARY KEY, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `active` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `market_risk` (`id` TEXT NOT NULL PRIMARY KEY, `riskFund` TEXT, `limitMode` TEXT NOT NULL, `limitValue` TEXT, `defaultLimit` TEXT, `warnEnabled` INTEGER NOT NULL, FOREIGN KEY(`id`) REFERENCES `ledger_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_risk_id` ON `market_risk` (`id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_contract_rules` (`id` TEXT NOT NULL PRIMARY KEY, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `multiplier` TEXT, `marginRate` TEXT, `feeMode` TEXT NOT NULL, `openFee` TEXT, `closeFee` TEXT, `todayFee` TEXT, `addMode` TEXT NOT NULL, `addValue` TEXT, `effectiveDay` INTEGER NOT NULL, `verified` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_contract_rules_code_effectiveDay` ON `ledger_contract_rules` (`code`, `effectiveDay`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_plans` (`id` TEXT NOT NULL PRIMARY KEY, `accountId` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `day` INTEGER NOT NULL, `strategyId` TEXT, `entryPrice` TEXT, `takeProfit` TEXT, `stopLoss` TEXT, `levels` TEXT NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `ledger_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`strategyId`) REFERENCES `ledger_strategies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_plans_accountId` ON `ledger_plans` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_plans_accountId_code_day` ON `ledger_plans` (`accountId`, `code`, `day`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_plans_strategyId` ON `ledger_plans` (`strategyId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_executions` (`id` TEXT NOT NULL PRIMARY KEY, `accountId` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `date` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `tradingDay` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `side` TEXT NOT NULL, `effect` TEXT NOT NULL, `price` TEXT NOT NULL, `quantity` TEXT NOT NULL, `fees` TEXT NOT NULL, `feeSource` TEXT NOT NULL, `feeRateSnapshot` TEXT, `multiplier` TEXT, `marginRate` TEXT, `parametersConfirmed` INTEGER NOT NULL, `ruleSnapshot` TEXT, `strategyId` TEXT, `planId` TEXT, `takeProfit` TEXT, `stopLoss` TEXT, `riskAmount` TEXT, `riskFundSnapshot` TEXT, `riskLimitSnapshot` TEXT, `riskExceeded` INTEGER NOT NULL, `riskConfirmed` INTEGER NOT NULL, `riskWarningEnabled` INTEGER NOT NULL, `second` INTEGER, `orderConfirmed` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `ledger_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`strategyId`) REFERENCES `ledger_strategies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`planId`) REFERENCES `ledger_plans`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_accountId` ON `ledger_executions` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_accountId_code_tradingDay` ON `ledger_executions` (`accountId`, `code`, `tradingDay`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_planId` ON `ledger_executions` (`planId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_executions_strategyId` ON `ledger_executions` (`strategyId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_cycles` (`id` TEXT NOT NULL PRIMARY KEY, `accountId` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `direction` TEXT NOT NULL, `firstDay` INTEGER NOT NULL, `closedDay` INTEGER, `remaining` TEXT NOT NULL, `averagePrice` TEXT NOT NULL, `openCost` TEXT NOT NULL, `fees` TEXT NOT NULL, `realized` TEXT NOT NULL, `takeProfit` TEXT, `stopLoss` TEXT, `referenceMargin` TEXT, FOREIGN KEY(`accountId`) REFERENCES `ledger_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_cycles_accountId` ON `ledger_cycles` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_cycles_accountId_code_direction` ON `ledger_cycles` (`accountId`, `code`, `direction`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_lots` (`id` TEXT NOT NULL PRIMARY KEY, `executionId` TEXT NOT NULL, `cycleId` TEXT NOT NULL, `originalQuantity` TEXT NOT NULL, `remaining` TEXT NOT NULL, `allocatedFees` TEXT NOT NULL, FOREIGN KEY(`executionId`) REFERENCES `ledger_executions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`cycleId`) REFERENCES `ledger_cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_lots_cycleId` ON `ledger_lots` (`cycleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_lots_executionId` ON `ledger_lots` (`executionId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_matches` (`id` TEXT NOT NULL PRIMARY KEY, `openExecutionId` TEXT NOT NULL, `closeExecutionId` TEXT NOT NULL, `cycleId` TEXT NOT NULL, `quantity` TEXT NOT NULL, `gross` TEXT NOT NULL, `openFees` TEXT NOT NULL, `closeFees` TEXT NOT NULL, `net` TEXT NOT NULL, `day` INTEGER NOT NULL, FOREIGN KEY(`openExecutionId`) REFERENCES `ledger_executions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`closeExecutionId`) REFERENCES `ledger_executions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`cycleId`) REFERENCES `ledger_cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_closeExecutionId` ON `ledger_matches` (`closeExecutionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_cycleId` ON `ledger_matches` (`cycleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_day` ON `ledger_matches` (`day`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_matches_openExecutionId` ON `ledger_matches` (`openExecutionId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_reviews` (`id` TEXT NOT NULL PRIMARY KEY, `cycleId` TEXT NOT NULL, `day` INTEGER NOT NULL, `takeProfit` TEXT, `stopLoss` TEXT, `levels` TEXT NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`cycleId`) REFERENCES `ledger_cycles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_reviews_cycleId` ON `ledger_reviews` (`cycleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_reviews_cycleId_day` ON `ledger_reviews` (`cycleId`, `day`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_cashflows` (`id` TEXT NOT NULL PRIMARY KEY, `accountId` TEXT NOT NULL, `day` INTEGER NOT NULL, `kind` TEXT NOT NULL, `amount` TEXT NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `ledger_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_cashflows_accountId` ON `ledger_cashflows` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_cashflows_accountId_day` ON `ledger_cashflows` (`accountId`, `day`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_execution_audits` (`id` TEXT NOT NULL PRIMARY KEY, `executionId` TEXT NOT NULL, `operation` TEXT NOT NULL, `beforeJson` TEXT NOT NULL, `afterJson` TEXT, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_execution_audits_executionId` ON `ledger_execution_audits` (`executionId`)")
    }
    fun seed(db:SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO ledger_accounts(id,market,name,initialCapital,historyComplete,buyRate,sellRate) VALUES('stock','stock','A股账户',NULL,0,'0.0001','0.0006'),('futures','futures','国内期货账户',NULL,0,'0.0001','0.0006')")
        db.execSQL("INSERT OR IGNORE INTO market_risk(id,riskFund,limitMode,limitValue,defaultLimit,warnEnabled) VALUES('stock',NULL,'amount',NULL,NULL,1),('futures',NULL,'amount',NULL,NULL,1)")
        db.execSQL("INSERT OR IGNORE INTO ledger_strategies(id,name,description,sortOrder,active) SELECT id,name,'旧版策略，稳定身份保留',sortOrder,CASE WHEN hidden=1 THEN 0 ELSE 1 END FROM tags WHERE kind='strategy'")
    }
}
val MIGRATION_7_8 = object:Migration(7,8) {
    override fun migrate(db:SupportSQLiteDatabase) {LedgerSchema.create(db);LedgerSchema.seed(db)}
}
