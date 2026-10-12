package com.tradereview.app.domain

import com.tradereview.app.data.*
import java.math.BigDecimal
import java.time.Instant
import java.security.MessageDigest

object RiskEngine {
    val labels = linkedMapOf("single_risk" to "计划风险超过权益比例", "position_limit" to "仓位超过参考上限",
        "plan_position" to "仓位超过本笔计划", "loss_streak" to "达到连续亏损提醒", "daily_loss" to "达到当日净亏损提醒")
    fun check(t: Trade, rule: RiskRules?, trades: List<Trade>, at: String = Instant.now().toString()): RiskEvent {
        rule?.let { ProfessionalMath.validateRule(it); require(it.currency == t.currency) }
        val rules = rule?.takeIf { it.configured }
        val risk = ProfessionalMath.risk(t.professional, t.direction)
        val previous = trades.filter { it.recordKey != t.recordKey && it.currency == t.currency &&
            (it.date < t.date || it.date == t.date && (t.id == 0L || it.id < t.id)) }.sortedWith(compareBy<Trade> { it.date }.thenBy { it.id })
        val streak = if (Money.value(t).signum() >= 0) 0 else 1 + previous.asReversed().takeWhile { Money.value(it).signum() < 0 }.size
        val daily = trades.filter { it.recordKey != t.recordKey && it.currency == t.currency && it.date == t.date }
            .fold(Money.value(t)) { sum, v -> sum + Money.value(v) }
        val notChecked = mutableListOf<String>()
        if(rules?.maxRiskPercent != null && (risk == null || rules.accountEquity == null)) notChecked += "单笔风险比例：缺少有效风险或账户权益"
        if(risk == null) notChecked += "初始计划风险未提供或无效，R 未提供"
        val event = RiskEvent(t.recordKey, at, t.date, t.currency, t.direction, t.netPnl, t.positionPercent,
            risk?.let(ProfessionalMath::plain), t.professional?.plannedPositionPercent, rules, streak, daily.setScale(2).toPlainString(), emptyList(), notChecked)
        return event.copy(violations = expectedViolations(event))
    }
    fun evaluated(e: RiskEvent): Boolean = e.plannedPositionPercent != null || e.rules?.let { r ->
        r.maxPositionPercent != null || r.lossStreakThreshold != null || r.dailyLossLimit != null ||
            r.maxRiskPercent != null && r.accountEquity != null && e.initialRisk != null
    } == true
    fun expectedViolations(e: RiskEvent): List<String> {
        val r = e.rules; val codes = mutableListOf<String>()
        if(r?.maxRiskPercent != null && r.accountEquity != null && e.initialRisk != null &&
            e.initialRisk.toBigDecimal() * BigDecimal("100") > r.accountEquity.toBigDecimal() * r.maxRiskPercent.toBigDecimal()) codes += "single_risk"
        if(r?.maxPositionPercent != null && e.positionPercent.toBigDecimal() > r.maxPositionPercent.toBigDecimal()) codes += "position_limit"
        if(e.plannedPositionPercent != null && e.positionPercent.toBigDecimal() > e.plannedPositionPercent.toBigDecimal()) codes += "plan_position"
        if(r?.lossStreakThreshold != null && e.lossStreak >= r.lossStreakThreshold) codes += "loss_streak"
        if(r?.dailyLossLimit != null && e.dailyNet.toBigDecimal() <= r.dailyLossLimit.toBigDecimal().negate()) codes += "daily_loss"
        return codes
    }

    fun fingerprint(event: RiskEvent): String = MessageDigest.getInstance("SHA-256")
        .digest(ProfessionalJson.event(event.copy(checkedAt = "")).toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun financialFactsChanged(old: Trade?, t: Trade) = old == null ||
        listOf(old.date, old.currency, old.direction, old.netPnl, old.positionPercent, old.professional?.copy(reason = "", executionNote = "", templateName = null)) !=
        listOf(t.date, t.currency, t.direction, t.netPnl, t.positionPercent, t.professional?.copy(reason = "", executionNote = "", templateName = null))
}
