package com.tradereview.app.domain

import com.google.gson.*
import com.tradereview.app.data.*
import java.time.Instant
import java.time.LocalDate

/** Recursive key sorting makes new objects identical on ART and the JVM. */
object ProfessionalJson {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val professionalFields = "pnlMode riskMode quantityUnit contractCode entryPrice exitPrice quantity multiplier initialStop takeProfit minTick fees otherCosts stopExecutionCost manualRisk plannedPositionPercent planStatus reason executionNote costsConfirmed templateName linearConfirmed".split(' ')
    private val numericDraft = "entryPrice exitPrice quantity multiplier initialStop takeProfit minTick fees otherCosts stopExecutionCost manualRisk plannedPositionPercent".split(' ')
    fun sorted(e: JsonElement): JsonElement = when {
        e.isJsonObject -> JsonObject().apply { e.asJsonObject.keySet().sorted().forEach { add(it, sorted(e.asJsonObject[it])) } }
        e.isJsonArray -> JsonArray().apply { e.asJsonArray.forEach { add(sorted(it)) } }
        else -> e
    }
    private fun json(value: Any?): JsonElement = sorted(gson.toJsonTree(value))
    fun professional(value: ProfessionalTrade?): JsonElement = json(value)
    fun draft(value: ProfessionalDraft?): JsonElement = json(value)
    fun rule(value: RiskRules): JsonObject = json(value).asJsonObject
    fun template(value: ContractTemplate): JsonObject = json(value).asJsonObject
    fun record(value: RiskRecord?): JsonElement = json(value)
    fun event(value: RiskEvent): JsonObject = json(value).asJsonObject
    fun encode(value: JsonElement): String = gson.toJson(value)
    fun decode(text: String): JsonElement = JsonParser.parseString(text)
    private fun obj(e: JsonElement, fields: List<String>): JsonObject {
        require(e.isJsonObject) { "专业字段应为对象" }
        return e.asJsonObject.also { require(it.keySet() == fields.toSet()) { "专业字段缺失或含未知字段" } }
    }
    private fun JsonObject.text(k: String): String { val e = get(k); require(e is JsonPrimitive && e.isString); return e.asString }
    private fun JsonObject.optional(k: String): String? = if (get(k).isJsonNull) null else text(k)
    private fun JsonObject.flag(k: String): Boolean { val e = get(k); require(e is JsonPrimitive && e.isBoolean); return e.asBoolean }
    private fun JsonObject.integer(k: String): Long {
        val e = get(k); require(e is JsonPrimitive && e.isNumber && Regex("-?(0|[1-9][0-9]*)").matches(e.asString)); return e.asString.toLong()
    }
    private fun JsonObject.list(k: String): List<String> { val e = get(k); require(e.isJsonArray); return e.asJsonArray.map { require(it is JsonPrimitive && it.isString); it.asString } }
    fun parseProfessional(e: JsonElement): ProfessionalTrade? {
        if (e.isJsonNull) return null
        val o = obj(e, professionalFields)
        return ProfessionalTrade(o.text("pnlMode"), o.text("riskMode"), o.text("quantityUnit"), o.optional("contractCode"),
            o.optional("entryPrice"), o.optional("exitPrice"), o.optional("quantity"), o.optional("multiplier"), o.optional("initialStop"),
            o.optional("takeProfit"), o.optional("minTick"), o.optional("fees"), o.optional("otherCosts"), o.optional("stopExecutionCost"),
            o.optional("manualRisk"), o.optional("plannedPositionPercent"), o.text("planStatus"), o.text("reason"), o.text("executionNote"),
            o.flag("costsConfirmed"), o.optional("templateName"), o.flag("linearConfirmed"))
    }
    fun parseDraft(e: JsonElement): ProfessionalDraft? {
        if (e.isJsonNull) return null
        val o = obj(e, professionalFields + "expanded")
        return ProfessionalDraft(o.text("pnlMode"), o.text("riskMode"), o.text("quantityUnit"), o.text("contractCode"),
            o.text("entryPrice"), o.text("exitPrice"), o.text("quantity"), o.text("multiplier"), o.text("initialStop"),
            o.text("takeProfit"), o.text("minTick"), o.text("fees"), o.text("otherCosts"), o.text("stopExecutionCost"),
            o.text("manualRisk"), o.text("plannedPositionPercent"), o.text("planStatus"), o.text("reason"), o.text("executionNote"),
            o.flag("costsConfirmed"), o.optional("templateName"), o.flag("expanded"), o.flag("linearConfirmed")).also(::validateDraft)
    }
    fun validateDraft(d: ProfessionalDraft) {
        val o = draft(d).asJsonObject
        require(d.pnlMode in listOf("manual", "auto") && d.riskMode in listOf("price", "amount", "none"))
        require(d.planStatus in listOf("unknown", "in", "out") && d.quantityUnit in ProfessionalMath.units.map { it.id } + "unknown")
        numericDraft.forEach { val v = o.text(it); require(v.length <= 40 && '\u0000' !in v) }
        require(d.contractCode.length <= 30 && '\u0000' !in d.contractCode && d.reason.length <= 300 && d.executionNote.length <= 300 && '\u0000' !in d.reason && '\u0000' !in d.executionNote)
        require(d.templateName == null || d.templateName.length <= 30 && '\u0000' !in d.templateName)
    }
    fun parseRule(e: JsonElement): RiskRules {
        val o = obj(e, "currency accountEquity maxRiskPercent maxPositionPercent lossStreakThreshold dailyLossLimit".split(' '))
        val threshold = if(o["lossStreakThreshold"].isJsonNull) null else o.integer("lossStreakThreshold").also { require(it in 1..1000) }.toInt()
        return RiskRules(o.text("currency"), o.optional("accountEquity"), o.optional("maxRiskPercent"), o.optional("maxPositionPercent"), threshold, o.optional("dailyLossLimit")).also(ProfessionalMath::validateRule)
    }
    fun parseTemplate(e: JsonElement): ContractTemplate {
        val o = obj(e, "id name market symbol quantityUnit multiplier minTick note userVerified".split(' '))
        return ContractTemplate(o.text("id"), o.text("name"), o.text("market"), o.text("symbol"), o.text("quantityUnit"),
            o.optional("multiplier"), o.optional("minTick"), o.text("note"), o.flag("userVerified")).also(ProfessionalMath::validateTemplate)
    }
    fun parseRecord(e: JsonElement): RiskRecord? {
        if (e.isJsonNull) return null
        val root = obj(e, listOf("events")); require(root["events"].isJsonArray)
        val events = root["events"].asJsonArray.map { value ->
            val o = obj(value, "recordKey checkedAt date currency direction netPnl positionPercent initialRisk plannedPositionPercent rules lossStreak dailyNet violations notChecked".split(' '))
            RiskEvent(o.text("recordKey"), o.text("checkedAt"), o.integer("date"), o.text("currency"), o.text("direction"), o.text("netPnl"),
                o.integer("positionPercent").also { require(it in 1..100) }.toInt(), o.optional("initialRisk"), o.optional("plannedPositionPercent"),
                if(o["rules"].isJsonNull) null else parseRule(o["rules"]), o.integer("lossStreak").also { require(it in 0..10001) }.toInt(),
                o.text("dailyNet"), o.list("violations"), o.list("notChecked"))
        }
        return RiskRecord(events)
    }
    fun validateRecord(record: RiskRecord?, key: String) {
        if (record == null) return
        require(record.events.size in 1..1000) { "纪律检查历史数量无效" }
        record.events.forEach { e ->
            require(e.recordKey == key && e.checkedAt.length <= 40 && runCatching { Instant.parse(e.checkedAt) }.isSuccess)
            require(e.date in LocalDate.of(1900,1,1).toEpochDay()..LocalDate.now().toEpochDay())
            require(e.currency in Presets.currencies.map { it.id } && e.direction in Presets.directions.map { it.id } && e.positionPercent in 1..100)
            require(Money.parse(e.netPnl)?.toPlainString() == e.netPnl)
            require(Regex("-?[0-9]{1,17}\\.[0-9]{2}").matches(e.dailyNet)) { "当日金额快照无效" }
            require(e.lossStreak in 0..10001)
            require(e.initialRisk == null || e.initialRisk.length <= 80 && e.initialRisk.toBigDecimalOrNull()?.let { it.signum() > 0 && it.scale() <= 24 && ProfessionalMath.plain(it) == e.initialRisk } == true)
            require(e.plannedPositionPercent == null || ProfessionalMath.decimal(e.plannedPositionPercent)?.let { ProfessionalMath.plain(it) == e.plannedPositionPercent && it > java.math.BigDecimal.ZERO && it <= java.math.BigDecimal("100") } == true)
            e.rules?.let { ProfessionalMath.validateRule(it); require(it.currency == e.currency) }
            require(e.violations.distinct() == e.violations && e.violations.all { it in RiskEngine.labels })
            require(e.violations == RiskEngine.expectedViolations(e)) { "违规标记与保存时的规则及金额快照不一致" }
            require(e.notChecked.size <= 10 && e.notChecked.all { it.length <= 100 && '\u0000' !in it })
        }
    }
}
