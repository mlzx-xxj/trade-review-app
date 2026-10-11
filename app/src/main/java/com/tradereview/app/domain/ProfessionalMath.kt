package com.tradereview.app.domain

import com.tradereview.app.data.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.math.MathContext

object ProfessionalMath {
    val units = listOf(Option("stock_share", "股"), Option("futures_lot", "手"),
        Option("forex_unit", "基础货币单位"), Option("forex_lot", "外汇手"), Option("crypto_unit", "币 / 张（按乘数）"))
    fun units(market: String) = units.filter { when (market) {
        "stock" -> it.id == "stock_share"; "futures" -> it.id == "futures_lot"
        "forex" -> it.id.startsWith("forex_"); else -> it.id == "crypto_unit"
    } }
    fun plain(v: BigDecimal): String = if (v.signum() == 0) "0" else v.stripTrailingZeros().toPlainString()
    fun formatR(v: BigDecimal?): String = if(v == null) "未提供" else
        (if(v.signum() > 0) "+" else "") +
            (if(v.signum() != 0 && v.abs() < BigDecimal("0.0005")) plain(v) else v.setScale(3, RoundingMode.HALF_UP).toPlainString()) + " R"
    fun decimal(value: String): BigDecimal? = value.takeIf { Regex("[+-]?[0-9]{1,13}(\\.[0-9]{1,8})?").matches(it) }
        ?.toBigDecimalOrNull()?.takeIf { it.abs() <= BigDecimal("1000000000000") }
    private fun number(s: String, label: String, positive: Boolean = false): String? {
        if (s.isBlank()) return null
        val value = decimal(s.trim()) ?: throw IllegalArgumentException("$label 需为有效数字，最多 8 位小数")
        require(!positive || value.signum() > 0) { "$label 必须大于 0" }
        return plain(value)
    }
    private fun money(s: String, label: String, positive: Boolean = false): String? {
        if (s.isBlank()) return null
        val value = Money.parse(s.trim()) ?: throw IllegalArgumentException("$label 需为有效金额，最多两位小数")
        require(if (positive) value.signum() > 0 else value.signum() >= 0) { "$label ${if(positive) "必须大于 0" else "不能为负数"}" }
        return value.toPlainString()
    }
    fun professional(d: ProfessionalDraft): ProfessionalTrade = ProfessionalTrade(
        d.pnlMode, d.riskMode, d.quantityUnit, d.contractCode.trim().ifBlank { null },
        number(d.entryPrice, "入场价"), number(d.exitPrice, "出场价"), number(d.quantity, "数量", true),
        number(d.multiplier, "合约乘数", true), number(d.initialStop, "初始止损"), number(d.takeProfit, "计划止盈"),
        number(d.minTick, "最小变动单位", true), money(d.fees, "手续费"), money(d.otherCosts, "其他成本"),
        money(d.stopExecutionCost, "计划止损执行成本"), money(d.manualRisk, "计划最大亏损", true),
        number(d.plannedPositionPercent, "计划仓位", true), d.planStatus, d.reason.trim(), d.executionNote.trim(),
        d.costsConfirmed, d.templateName, d.linearConfirmed)
    fun validate(p: ProfessionalTrade, market: String) {
        require(p.pnlMode in listOf("manual", "auto") && p.riskMode in listOf("price", "amount", "none")) { "计算模式无效" }
        require(p.quantityUnit == "unknown" || p.quantityUnit in units(market).map { it.id }) { "数量单位与市场不符，请核对参数" }
        require(p.planStatus in listOf("unknown", "in", "out")) { "计划状态无效" }
        require(p.contractCode == null || p.contractCode.isNotBlank() && p.contractCode.length <= 30 && '\u0000' !in p.contractCode) { "合约代码最多 30 字" }
        require(p.reason.length <= 300 && p.executionNote.length <= 300 && '\u0000' !in p.reason && '\u0000' !in p.executionNote) { "理由与执行备注最多 300 字" }
        require(p.templateName == null || p.templateName.isNotBlank() && p.templateName.length <= 30 && '\u0000' !in p.templateName)
        listOf(p.entryPrice, p.exitPrice, p.quantity, p.multiplier, p.initialStop, p.takeProfit, p.minTick, p.plannedPositionPercent).filterNotNull().forEach {
            require(decimal(it)?.let(::plain) == it) { "专业数字必须规范且在范围内" }
        }
        listOf(p.quantity, p.multiplier, p.minTick).filterNotNull().forEach { require(it.toBigDecimal().signum() > 0) { "数量、乘数和最小变动单位必须大于 0" } }
        listOf(p.fees, p.otherCosts, p.stopExecutionCost, p.manualRisk).filterNotNull().forEach {
            require(Money.parse(it)?.toPlainString() == it && it.toBigDecimal().signum() >= 0) { "成本或风险金额无效" }
        }
        require(p.manualRisk == null || p.manualRisk.toBigDecimal().signum() > 0) { "计划最大亏损必须大于 0" }
        require(p.plannedPositionPercent == null || p.plannedPositionPercent.toBigDecimal() > BigDecimal.ZERO && p.plannedPositionPercent.toBigDecimal() <= BigDecimal("100")) { "计划仓位应大于 0 且不超过 100%" }
    }
    fun gross(p: ProfessionalTrade?, direction: String): BigDecimal? {
        if (p == null || !p.linearConfirmed || p.quantityUnit == "unknown") return null
        val entry = p.entryPrice?.toBigDecimalOrNull() ?: return null
        val exit = p.exitPrice?.toBigDecimalOrNull() ?: return null
        val q = p.quantity?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        val multiplier = p.multiplier?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        return (if (direction == "long") exit - entry else entry - exit) * q * multiplier
    }
    fun net(p: ProfessionalTrade?, direction: String): BigDecimal? {
        if (p == null || !p.costsConfirmed || p.fees == null) return null
        return gross(p, direction)?.let { it - p.fees.toBigDecimal() - (p.otherCosts?.toBigDecimal() ?: BigDecimal.ZERO) }
    }
    fun netForSave(p: ProfessionalTrade, direction: String): String {
        val value = net(p, direction) ?: throw IllegalArgumentException("自动模式需要完整参数、线性结算确认、手续费及成本确认；无费用请明确填 0")
        val rounded = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
        require(Money.parse(rounded)?.toPlainString() == rounded) { "计算的净盈亏超出可入账金额范围" }
        return rounded
    }
    fun risk(p: ProfessionalTrade?, direction: String): BigDecimal? {
        if (p == null) return null
        if (p.riskMode == "amount") return p.manualRisk?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        if (p.riskMode != "price" || !p.linearConfirmed || p.quantityUnit == "unknown") return null
        val entry = p.entryPrice?.toBigDecimalOrNull() ?: return null
        val stop = p.initialStop?.toBigDecimalOrNull() ?: return null
        if (if(direction == "long") stop >= entry else stop <= entry) return null
        val q = p.quantity?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        val m = p.multiplier?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        return ((entry - stop).abs() * q * m + (p.stopExecutionCost?.toBigDecimal() ?: BigDecimal.ZERO)).takeIf { it.signum() > 0 }
    }
    fun riskExplanation(p: ProfessionalTrade?, direction: String): String = when {
        p == null || p.riskMode == "none" -> "未提供初始计划风险，不计入 R 样本"
        p.riskMode == "price" && !p.linearConfirmed -> "未核实线性计价与结算币种，不计算价格风险；需换汇或非线性交易请手填计划最大亏损"
        p.riskMode == "amount" -> "口径：用户明确填写的计划最大亏损；不再重复加执行成本"
        p.entryPrice != null && p.initialStop != null && risk(p, direction) == null &&
            (if(direction == "long") p.initialStop.toBigDecimal() >= p.entryPrice.toBigDecimal() else p.initialStop.toBigDecimal() <= p.entryPrice.toBigDecimal()) -> "初始止损方向无效或距离为零：做多止损应低于入场，做空应高于入场，不计算 R"
        else -> "口径：初始止损价格距离 × 数量 × 乘数，加明确填写的计划止损执行成本；未填完整则不计算 R"
    }
    fun r(trade: Trade): BigDecimal? = risk(trade.professional, trade.direction)?.let {
        Money.value(trade).divide(it, MathContext(24, RoundingMode.HALF_UP))
    }
    fun validateRule(r: RiskRules) {
        require(r.currency in Presets.currencies.map { it.id })
        listOf(r.accountEquity, r.dailyLossLimit).filterNotNull().forEach { require(Money.parse(it)?.toPlainString() == it && it.toBigDecimal().signum() > 0) { "权益和每日亏损阈值必须是正金额" } }
        listOf(r.maxRiskPercent, r.maxPositionPercent).filterNotNull().forEach { require(decimal(it)?.let(::plain) == it && it.toBigDecimal() > BigDecimal.ZERO && it.toBigDecimal() <= BigDecimal("100")) { "规则比例应大于 0 且不超过 100%" } }
        require(r.lossStreakThreshold == null || r.lossStreakThreshold in 1..1000) { "连续亏损提醒阈值为 1–1000 笔" }
    }
    fun validateTemplate(t: ContractTemplate) {
        require(Regex("[0-9a-f]{32}").matches(t.id))
        require(t.name.isNotBlank() && t.name.length <= 30 && '\u0000' !in t.name && t.symbol.isNotBlank() && t.symbol.length <= 30 && '\u0000' !in t.symbol)
        require(t.quantityUnit in units(t.market).map { it.id } && t.market in Presets.markets.map { it.id }) { "模板市场或数量单位无效" }
        listOf(t.multiplier, t.minTick).filterNotNull().forEach { require(decimal(it)?.let(::plain) == it && it.toBigDecimal().signum() > 0) { "模板参数需为已填写的正数" } }
        require(t.note.length <= 300 && '\u0000' !in t.note)
    }
    fun applyTemplate(d: ProfessionalDraft, t: ContractTemplate, market: String, symbol: String, confirmed: Boolean): ProfessionalDraft {
        validateTemplate(t)
        require(confirmed && t.market == market && t.symbol == symbol) { "请核实模板适用的品种与本次合约" }
        return d.copy(quantityUnit = t.quantityUnit, multiplier = t.multiplier.orEmpty(), minTick = t.minTick.orEmpty(), templateName = t.name)
    }
}
