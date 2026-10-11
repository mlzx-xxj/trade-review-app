package com.tradereview.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

object TagKind {
    val names = linkedMapOf("strategy" to "交易策略", "emotion" to "情绪状态", "mistake" to "常见错误", "discipline" to "执行纪律", "preparation" to "盘前检查事项", "lesson" to "盘后改进事项")
    val presets = linkedMapOf("strategy" to Presets.strategies, "emotion" to Presets.emotions, "mistake" to Presets.mistakes, "discipline" to Presets.discipline, "preparation" to Presets.preparations, "lesson" to Presets.lessons)
}

@Entity(tableName = "tags", primaryKeys = ["kind", "id"])
data class ManagedTag(val kind: String, val id: String, val name: String, val sortOrder: Int = 0,
    val favorite: Boolean = false, val hidden: Boolean = false, val lastUsed: Long = 0)

@Entity(tableName = "instruments")
data class Instrument(@PrimaryKey val id: String, val market: String, val name: String, val code: String,
    val sortOrder: Int = 0, val favorite: Boolean = false, val lastUsed: Long = 0)

@Entity(tableName = "app_settings")
data class AppSettings(@PrimaryKey val id: Int = 1, val lastMarket: String = "stock", val theme: String = "system")

/** A partial form is stored separately: an empty amount never becomes a completed trade. */
@Entity(tableName = "entry_drafts")
data class EntryDraft(@PrimaryKey val key: String = "new", val recordKey: String = newKey(), val tradeId: Long = 0,
    val date: Long = LocalDate.now().toEpochDay(), val market: String = "stock", val symbol: String = "沪深300",
    val direction: String = "long", val currency: String = "CNY", val amount: String = "", val outcome: String = "gain",
    val positionPercent: Int = 10, val strategy: String = "trend", val emotion: String = "calm",
    val mistakes: List<String> = emptyList(), val discipline: List<String> = emptyList(),
    val reviewed: Boolean = false, val note: String = "", val details: Boolean = false) {
    companion object {
        fun from(trade: Trade) = EntryDraft(key = trade.recordKey, recordKey = trade.recordKey, tradeId = trade.id,
            date = trade.date, market = trade.market, symbol = trade.symbol, direction = trade.direction, currency = trade.currency,
            amount = if (java.math.BigDecimal(trade.netPnl).signum() == 0) "" else java.math.BigDecimal(trade.netPnl).abs().toPlainString(),
            outcome = when (java.math.BigDecimal(trade.netPnl).signum()) { -1 -> "loss"; 0 -> "flat"; else -> "gain" },
            positionPercent = trade.positionPercent, strategy = trade.strategy, emotion = trade.emotion,
            mistakes = trade.mistakes, discipline = trade.discipline, reviewed = trade.reviewed, note = trade.note)
        fun copyReusable(trade: Trade) = EntryDraft(market = trade.market, symbol = trade.symbol, direction = trade.direction,
            currency = trade.currency, positionPercent = trade.positionPercent, strategy = trade.strategy, outcome = "")
    }
}
fun newKey(): String = java.util.UUID.randomUUID().toString().replace("-", "")

object CatalogSeeds {
    val tags: List<ManagedTag> get() = TagKind.presets.flatMap { (kind, values) -> values.mapIndexed { i, o -> ManagedTag(kind, o.id, o.label, i) } }
    val instruments: List<Instrument> get() {
        val futures = listOf("螺纹钢" to "RB", "铁矿石" to "I", "热卷" to "HC", "沪铜" to "CU", "沪铝" to "AL", "黄金" to "AU", "白银" to "AG", "原油" to "SC", "豆粕" to "M", "豆油" to "Y", "棕榈油" to "P", "纯碱" to "SA", "玻璃" to "FG", "焦煤" to "JM", "焦炭" to "J")
        return Presets.markets.flatMap { market ->
            val values = if (market.id == "futures") futures else Presets.symbols(market.id).map { it to it }
            values.mapIndexed { i, (name, code) -> Instrument("preset_${market.id}_$i", market.id, name, code, i, favorite = i < 3) }
        }
    }
    fun insert(db: SupportSQLiteDatabase) {
        tags.forEach { db.execSQL("INSERT OR IGNORE INTO tags(kind,id,name,sortOrder,favorite,hidden,lastUsed) VALUES(?,?,?,?,?,?,?)", arrayOf(it.kind, it.id, it.name, it.sortOrder, 0, 0, 0)) }
        instruments.forEach { db.execSQL("INSERT OR IGNORE INTO instruments(id,market,name,code,sortOrder,favorite,lastUsed) VALUES(?,?,?,?,?,?,?)", arrayOf(it.id, it.market, it.name, it.code, it.sortOrder, if(it.favorite) 1 else 0, 0)) }
        db.execSQL("INSERT OR IGNORE INTO app_settings(id,lastMarket,theme) VALUES(1,'stock','system')")
    }
    val callback = object : RoomDatabase.Callback() { override fun onCreate(db: SupportSQLiteDatabase) { insert(db) } }
}

class Catalog(val tags: List<ManagedTag> = CatalogSeeds.tags) {
    fun label(kind: String, id: String) = tags.firstOrNull { it.kind == kind && it.id == id }?.name ?: id
    fun options(kind: String, selected: List<String> = emptyList()) = tags.filter { it.kind == kind && (!it.hidden || it.id in selected) }
        .sortedWith(compareByDescending<ManagedTag> { it.lastUsed }.thenByDescending { it.favorite }.thenBy { it.sortOrder }.thenBy { it.id })
        .map { Option(it.id, it.name + if (it.hidden) "（已隐藏）" else "") }
}
fun orderedInstruments(values: List<Instrument>, market: String, query: String = "") = values.filter {
    it.market == market && (query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true))
}.sortedWith(compareByDescending<Instrument> { it.lastUsed }.thenByDescending { it.favorite }.thenBy { it.sortOrder }.thenBy { it.id })
