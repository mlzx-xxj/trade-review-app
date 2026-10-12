package com.tradereview.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Null means unknown; prices and quantities use decimal text, never floating point. */
data class ProfessionalTrade(
    val pnlMode: String = "manual", val riskMode: String = "price", val quantityUnit: String = "unknown",
    val contractCode: String? = null, val entryPrice: String? = null, val exitPrice: String? = null,
    val quantity: String? = null, val multiplier: String? = null, val initialStop: String? = null,
    val takeProfit: String? = null, val minTick: String? = null, val fees: String? = null,
    val otherCosts: String? = null, val stopExecutionCost: String? = null, val manualRisk: String? = null,
    val plannedPositionPercent: String? = null, val planStatus: String = "unknown",
    val reason: String = "", val executionNote: String = "", val costsConfirmed: Boolean = false,
    val templateName: String? = null, val linearConfirmed: Boolean = false,
)

/** Raw, possibly incomplete input is a draft, not a financial result. */
data class ProfessionalDraft(
    val pnlMode: String = "manual", val riskMode: String = "price", val quantityUnit: String = "unknown",
    val contractCode: String = "", val entryPrice: String = "", val exitPrice: String = "",
    val quantity: String = "", val multiplier: String = "", val initialStop: String = "",
    val takeProfit: String = "", val minTick: String = "", val fees: String = "",
    val otherCosts: String = "", val stopExecutionCost: String = "", val manualRisk: String = "",
    val plannedPositionPercent: String = "", val planStatus: String = "unknown",
    val reason: String = "", val executionNote: String = "", val costsConfirmed: Boolean = false,
    val templateName: String? = null, val expanded: Boolean = false, val linearConfirmed: Boolean = false,
) {
    companion object {
        fun from(p: ProfessionalTrade) = ProfessionalDraft(p.pnlMode, p.riskMode, p.quantityUnit,
            p.contractCode.orEmpty(), p.entryPrice.orEmpty(), p.exitPrice.orEmpty(), p.quantity.orEmpty(),
            p.multiplier.orEmpty(), p.initialStop.orEmpty(), p.takeProfit.orEmpty(), p.minTick.orEmpty(),
            p.fees.orEmpty(), p.otherCosts.orEmpty(), p.stopExecutionCost.orEmpty(), p.manualRisk.orEmpty(),
            p.plannedPositionPercent.orEmpty(), p.planStatus, p.reason, p.executionNote, p.costsConfirmed, p.templateName, linearConfirmed = p.linearConfirmed)
    }
}

@Entity(tableName = "risk_rules")
data class RiskRules(@PrimaryKey val currency: String = "CNY", val accountEquity: String? = null,
    val maxRiskPercent: String? = null, val maxPositionPercent: String? = null,
    val lossStreakThreshold: Int? = null, val dailyLossLimit: String? = null) {
    val configured get() = listOf(maxRiskPercent, maxPositionPercent, lossStreakThreshold, dailyLossLimit).any { it != null }
}

@Entity(tableName = "contract_templates")
data class ContractTemplate(@PrimaryKey val id: String = newKey(), val name: String,
    val market: String, val symbol: String, val quantityUnit: String, val multiplier: String? = null,
    val minTick: String? = null, val note: String = "", val userVerified: Boolean = false)

/** Checks are append-only when financial facts change; later rule edits cannot erase history. */
data class RiskRecord(val events: List<RiskEvent> = emptyList()) {
    val violations get() = events.flatMap { it.violations }.distinct()
}
data class RiskEvent(val recordKey: String, val checkedAt: String, val date: Long, val currency: String,
    val direction: String, val netPnl: String, val positionPercent: Int, val initialRisk: String?,
    val plannedPositionPercent: String?, val rules: RiskRules?, val lossStreak: Int, val dailyNet: String,
    val violations: List<String>, val notChecked: List<String>)
