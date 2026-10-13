package com.tradereview.app.v200

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface LedgerDao {
    @Query("SELECT * FROM ledger_accounts ORDER BY id") suspend fun accounts():List<LedgerAccount>
    @Upsert suspend fun save(value:LedgerAccount)
    @Query("DELETE FROM ledger_accounts") suspend fun clearAccounts()
    @Query("SELECT * FROM ledger_strategies ORDER BY id") suspend fun strategies():List<LedgerStrategy>
    @Upsert suspend fun save(value:LedgerStrategy)
    @Query("DELETE FROM ledger_strategies") suspend fun clearStrategies()
    @Query("SELECT * FROM market_risk ORDER BY id") suspend fun risks():List<MarketRisk>
    @Upsert suspend fun save(value:MarketRisk)
    @Query("DELETE FROM market_risk") suspend fun clearRisks()
    @Query("SELECT * FROM ledger_contract_rules ORDER BY id") suspend fun rules():List<LedgerContractRule>
    @Upsert suspend fun save(value:LedgerContractRule)
    @Query("DELETE FROM ledger_contract_rules") suspend fun clearRules()
    @Query("SELECT * FROM ledger_plans ORDER BY id") suspend fun plans():List<LedgerPlan>
    @Upsert suspend fun save(value:LedgerPlan)
    @Query("DELETE FROM ledger_plans") suspend fun clearPlans()
    @Query("SELECT * FROM ledger_executions ORDER BY id") suspend fun executions():List<LedgerExecution>
    @Upsert suspend fun save(value:LedgerExecution)
    @Query("DELETE FROM ledger_executions") suspend fun clearExecutions()
    @Query("SELECT * FROM ledger_cycles ORDER BY id") suspend fun cycles():List<LedgerCycle>
    @Upsert suspend fun save(value:LedgerCycle)
    @Query("DELETE FROM ledger_cycles") suspend fun clearCycles()
    @Query("SELECT * FROM ledger_lots ORDER BY id") suspend fun lots():List<LedgerLot>
    @Upsert suspend fun save(value:LedgerLot)
    @Query("DELETE FROM ledger_lots") suspend fun clearLots()
    @Query("SELECT * FROM ledger_matches ORDER BY id") suspend fun matches():List<LedgerMatch>
    @Upsert suspend fun save(value:LedgerMatch)
    @Query("DELETE FROM ledger_matches") suspend fun clearMatches()
    @Query("SELECT * FROM ledger_reviews ORDER BY id") suspend fun reviews():List<LedgerReview>
    @Upsert suspend fun save(value:LedgerReview)
    @Query("DELETE FROM ledger_reviews") suspend fun clearReviews()
    @Query("SELECT * FROM ledger_cashflows ORDER BY id") suspend fun cashFlows():List<LedgerCashFlow>
    @Upsert suspend fun save(value:LedgerCashFlow)
    @Query("DELETE FROM ledger_cashflows") suspend fun clearCashFlows()
    @Query("SELECT id FROM ledger_accounts UNION ALL SELECT id FROM ledger_strategies UNION ALL SELECT id FROM market_risk UNION ALL SELECT id FROM ledger_contract_rules UNION ALL SELECT id FROM ledger_plans UNION ALL SELECT id FROM ledger_executions UNION ALL SELECT id FROM ledger_cycles UNION ALL SELECT id FROM ledger_lots UNION ALL SELECT id FROM ledger_matches UNION ALL SELECT id FROM ledger_reviews UNION ALL SELECT id FROM ledger_cashflows") fun observeChanges():Flow<List<String>>
    @Query("DELETE FROM ledger_executions WHERE id=:id") suspend fun removeExecution(id:String)
    @Query("DELETE FROM ledger_cycles WHERE id NOT IN (:ids)") suspend fun removeCyclesExcept(ids:List<String>)
}
