package com.tradereview.app.v200

import androidx.room.*
import com.tradereview.app.data.newKey

@Entity(tableName="ledger_accounts")
data class LedgerAccount(@PrimaryKey val id: String, val market: String, val name: String, val initialCapital: String? = null, val historyComplete: Boolean = false, val buyRate: String = AutomaticEntry.STOCK_BUY_RATE, val sellRate: String = AutomaticEntry.STOCK_SELL_RATE)

@Entity(tableName="ledger_strategies")
data class LedgerStrategy(@PrimaryKey val id: String, val name: String, val description: String = "", val sortOrder: Int = 0, val active: Boolean = true)

@Entity(tableName="market_risk", foreignKeys=[ForeignKey(entity=LedgerAccount::class, parentColumns=["id"], childColumns=["id"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["id"])])
data class MarketRisk(@PrimaryKey val id: String, val riskFund: String? = null, val limitMode: String = "amount", val limitValue: String? = null, val defaultLimit: String? = null, val warnEnabled: Boolean = true)

@Entity(tableName="ledger_contract_rules", indices=[Index(value=["code", "effectiveDay"])])
data class LedgerContractRule(@PrimaryKey val id: String, val code: String, val name: String, val multiplier: String? = null, val marginRate: String? = null, val feeMode: String = "per_lot", val openFee: String? = null, val closeFee: String? = null, val todayFee: String? = null, val addMode: String = "none", val addValue: String? = null, val effectiveDay: Long, val verified: Boolean = false)

@Entity(tableName="ledger_plans", foreignKeys=[ForeignKey(entity=LedgerAccount::class, parentColumns=["id"], childColumns=["accountId"], onDelete=ForeignKey.RESTRICT), ForeignKey(entity=LedgerStrategy::class, parentColumns=["id"], childColumns=["strategyId"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["accountId"]), Index(value=["accountId", "code", "day"]), Index(value=["strategyId"])])
data class LedgerPlan(@PrimaryKey val id: String, val accountId: String, val code: String, val name: String, val day: Long, val strategyId: String? = null, val entryPrice: String? = null, val takeProfit: String? = null, val stopLoss: String? = null, val levels: String = "[]", val note: String = "")

@Entity(tableName="ledger_executions", foreignKeys=[ForeignKey(entity=LedgerAccount::class, parentColumns=["id"], childColumns=["accountId"], onDelete=ForeignKey.RESTRICT), ForeignKey(entity=LedgerStrategy::class, parentColumns=["id"], childColumns=["strategyId"], onDelete=ForeignKey.RESTRICT), ForeignKey(entity=LedgerPlan::class, parentColumns=["id"], childColumns=["planId"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["accountId"]), Index(value=["accountId", "code", "tradingDay"]), Index(value=["planId"]), Index(value=["strategyId"])])
data class LedgerExecution(@PrimaryKey val id: String, val accountId: String, val code: String, val name: String, val date: Long, val minute: Int, val tradingDay: Long, val ordinal: Long, val side: String, val effect: String, val price: String, val quantity: String, val fees: String, val feeSource: String = "actual", val feeRateSnapshot: String? = null, val multiplier: String? = null, val marginRate: String? = null, val parametersConfirmed: Boolean = false, val ruleSnapshot: String? = null, val strategyId: String? = null, val planId: String? = null, val takeProfit: String? = null, val stopLoss: String? = null, val riskAmount: String? = null, val riskFundSnapshot: String? = null, val riskLimitSnapshot: String? = null, val riskExceeded: Boolean = false, val riskConfirmed: Boolean = false, val riskWarningEnabled: Boolean = true, val second: Int? = null, val orderConfirmed: Boolean = false)

@Entity(tableName="ledger_cycles", foreignKeys=[ForeignKey(entity=LedgerAccount::class, parentColumns=["id"], childColumns=["accountId"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["accountId"]), Index(value=["accountId", "code", "direction"])])
data class LedgerCycle(@PrimaryKey val id: String, val accountId: String, val code: String, val name: String, val direction: String, val firstDay: Long, val closedDay: Long? = null, val remaining: String, val averagePrice: String, val openCost: String, val fees: String, val realized: String, val takeProfit: String? = null, val stopLoss: String? = null, val referenceMargin: String? = null)

@Entity(tableName="ledger_lots", foreignKeys=[ForeignKey(entity=LedgerExecution::class, parentColumns=["id"], childColumns=["executionId"], onDelete=ForeignKey.RESTRICT), ForeignKey(entity=LedgerCycle::class, parentColumns=["id"], childColumns=["cycleId"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["cycleId"]), Index(value=["executionId"])])
data class LedgerLot(@PrimaryKey val id: String, val executionId: String, val cycleId: String, val originalQuantity: String, val remaining: String, val allocatedFees: String)

@Entity(tableName="ledger_matches", foreignKeys=[ForeignKey(entity=LedgerExecution::class, parentColumns=["id"], childColumns=["openExecutionId"], onDelete=ForeignKey.RESTRICT), ForeignKey(entity=LedgerExecution::class, parentColumns=["id"], childColumns=["closeExecutionId"], onDelete=ForeignKey.RESTRICT), ForeignKey(entity=LedgerCycle::class, parentColumns=["id"], childColumns=["cycleId"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["closeExecutionId"]), Index(value=["cycleId"]), Index(value=["day"]), Index(value=["openExecutionId"])])
data class LedgerMatch(@PrimaryKey val id: String, val openExecutionId: String, val closeExecutionId: String, val cycleId: String, val quantity: String, val gross: String, val openFees: String, val closeFees: String, val net: String, val day: Long)

@Entity(tableName="ledger_reviews", foreignKeys=[ForeignKey(entity=LedgerCycle::class, parentColumns=["id"], childColumns=["cycleId"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["cycleId"]), Index(value=["cycleId", "day"])])
data class LedgerReview(@PrimaryKey val id: String, val cycleId: String, val day: Long, val takeProfit: String? = null, val stopLoss: String? = null, val levels: String = "[]", val note: String = "")

@Entity(tableName="ledger_cashflows", foreignKeys=[ForeignKey(entity=LedgerAccount::class, parentColumns=["id"], childColumns=["accountId"], onDelete=ForeignKey.RESTRICT)], indices=[Index(value=["accountId"]), Index(value=["accountId", "day"])])
data class LedgerCashFlow(@PrimaryKey val id: String, val accountId: String, val day: Long, val kind: String, val amount: String, val note: String = "")

@Entity(tableName="ledger_execution_audits", indices=[Index(value=["executionId"])])
data class ExecutionAudit(@PrimaryKey val id: String, val executionId: String, val operation: String, val beforeJson: String, val afterJson: String? = null, val createdAt: Long)

data class LedgerSnapshot(val accounts:List<LedgerAccount> = emptyList(), val strategies:List<LedgerStrategy> = emptyList(), val risks:List<MarketRisk> = emptyList(), val rules:List<LedgerContractRule> = emptyList(), val plans:List<LedgerPlan> = emptyList(), val executions:List<LedgerExecution> = emptyList(), val cycles:List<LedgerCycle> = emptyList(), val lots:List<LedgerLot> = emptyList(), val matches:List<LedgerMatch> = emptyList(), val reviews:List<LedgerReview> = emptyList(), val cashFlows:List<LedgerCashFlow> = emptyList(), val audits:List<ExecutionAudit> = emptyList()) {
    val isEmpty get() = executions.isEmpty() && plans.isEmpty() && reviews.isEmpty() && cashFlows.isEmpty()
}
