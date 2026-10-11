package com.tradereview.app.domain

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.tradereview.app.data.Presets
import com.tradereview.app.data.Trade
import java.io.StringReader
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Relative ranges are resolved when used, rather than frozen when a preset is saved. */
data class TradeFilter(
    val period: String = "all", val startDay: Long = 0, val endDay: Long = 0,
    val markets: List<String> = emptyList(), val symbols: List<String> = emptyList(),
    val directions: List<String> = emptyList(), val outcomes: List<String> = emptyList(),
    val strategies: List<String> = emptyList(), val mistakes: List<String> = emptyList(),
    val discipline: List<String> = emptyList(), val emotions: List<String> = emptyList(),
    val tagMatch: String = "any", val review: String = "all", val errorPresence: String = "all",
) {
    fun bounds(today: LocalDate): LongRange? = when (period) {
        "week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()..today.toEpochDay()
        "month" -> today.withDayOfMonth(1).toEpochDay()..today.toEpochDay()
        "30" -> today.minusDays(29).toEpochDay()..today.toEpochDay()
        "custom" -> startDay..endDay
        else -> null
    }
    val hasTradeConditions: Boolean get() = listOf(markets, symbols, directions, outcomes, strategies, mistakes, discipline, emotions).any { it.isNotEmpty() } || review != "all" || errorPresence != "all"
    val active: Boolean get() = period != "all" || hasTradeConditions
}

object TradeFilters {
    fun apply(trades: List<Trade>, currency: String, filter: TradeFilter, today: LocalDate = LocalDate.now()): List<Trade> {
        val range = filter.bounds(today)
        fun selected(value: String, choices: List<String>) = choices.isEmpty() || value in choices
        fun tags(values: List<String>, choices: List<String>) = choices.isEmpty() ||
            if (filter.tagMatch == "all") choices.all { it in values } else choices.any { it in values }
        return trades.filter { t ->
            t.currency == currency && (range == null || t.date in range) &&
                selected(t.market, filter.markets) && selected(t.symbol, filter.symbols) && selected(t.direction, filter.directions) &&
                selected(when (Money.value(t).signum()) { 1 -> "gain"; -1 -> "loss"; else -> "flat" }, filter.outcomes) &&
                tags(listOf(t.strategy), filter.strategies) && tags(t.mistakes, filter.mistakes) && tags(t.discipline, filter.discipline) &&
                tags(listOf(t.emotion), filter.emotions) &&
                (filter.review == "all" || t.reviewed == (filter.review == "reviewed")) &&
                (filter.errorPresence == "all" || t.mistakes.isNotEmpty() == (filter.errorPresence == "with"))
        }.sortedWith(compareByDescending<Trade> { it.date }.thenByDescending { it.id })
    }
}

/** Strict validation is shared by saved presets and complete JSON backups. */
object FilterCodec {
    private val gson = Gson().newBuilder().disableHtmlEscaping().create()
    private val fields = setOf("period", "startDay", "endDay", "markets", "symbols", "directions", "outcomes", "strategies", "mistakes", "discipline", "emotions", "tagMatch", "review", "errorPresence")
    fun encode(value: TradeFilter): String { validate(value); return gson.toJson(value) }
    fun decode(value: String): TradeFilter {
        require(value.length <= 32_768) { "筛选方案过长" }
        val parsed = JsonReader(StringReader(value)).use { reader ->
            reader.strictness = Strictness.STRICT
            JsonParser.parseReader(reader).also { require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT) }
        }
        require(parsed.isJsonObject) { "筛选方案应为对象" }
        return parse(parsed.asJsonObject)
    }
    fun parse(o: JsonObject): TradeFilter {
        require(o.keySet() == fields) { "筛选方案字段缺失或未知" }
        fun text(key: String): String { val v = o[key]; require(v.isJsonPrimitive && v.asJsonPrimitive.isString); return v.asString }
        fun number(key: String): Long { val v = o[key]; require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber && Regex("-?(0|[1-9][0-9]*)").matches(v.asString)); return v.asString.toLong() }
        fun items(key: String): List<String> { val v = o[key]; require(v.isJsonArray); return v.asJsonArray.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString } }
        return TradeFilter(text("period"), number("startDay"), number("endDay"), items("markets"), items("symbols"), items("directions"), items("outcomes"),
            items("strategies"), items("mistakes"), items("discipline"), items("emotions"), text("tagMatch"), text("review"), text("errorPresence")).also(::validate)
    }
    fun validate(f: TradeFilter) {
        require(f.period in listOf("all", "week", "month", "30", "custom")) { "日期筛选无效" }
        val limits = LocalDate.of(1900, 1, 1).toEpochDay()..LocalDate.of(2100, 12, 31).toEpochDay()
        require(f.startDay in limits && f.endDay in limits && f.startDay <= f.endDay) { "日期范围无效，开始日期不能晚于结束日期" }
        require(f.tagMatch in listOf("any", "all") && f.review in listOf("all", "reviewed", "pending") && f.errorPresence in listOf("all", "with", "without")) { "筛选选项无效" }
        fun choices(values: List<String>, allowed: List<String>) { require(values.distinct().size == values.size && values.all { it in allowed }) { "筛选包含未知选项" } }
        choices(f.markets, Presets.markets.map { it.id }); choices(f.directions, Presets.directions.map { it.id }); choices(f.outcomes, listOf("gain", "loss", "flat"))
        require(f.symbols.size <= 100 && f.symbols.distinct().size == f.symbols.size && f.symbols.all { it.isNotBlank() && it.length <= 30 && '\u0000' !in it }) { "筛选品种无效" }
        listOf(f.strategies, f.mistakes, f.discipline, f.emotions).forEach { ids -> require(ids.size <= 100 && ids.distinct().size == ids.size && ids.all { Regex("[A-Za-z0-9_]{1,64}").matches(it) }) { "筛选标签无效" } }
    }
}
