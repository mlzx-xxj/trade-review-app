package com.tradereview.app.data

import androidx.room.withTransaction
import com.tradereview.app.domain.FastEntry

/** Saving a completed trade, recency and clearing its draft is one transaction. */
class EntryRepository(private val database: TradeDatabase) {
    private val dao get() = database.trades()
    suspend fun save(draft: EntryDraft) = database.withTransaction {
        val trade = FastEntry.trade(draft)
        val tags = dao.allTags()
        fun contains(kind: String, id: String) = tags.any { it.kind == kind && it.id == id }
        require(contains("strategy", trade.strategy) && contains("emotion", trade.emotion) &&
            trade.mistakes.all { contains("mistake", it) } && trade.discipline.all { contains("discipline", it) }) { "标签已变化，请重新选择" }
        if (trade.id == 0L) dao.insert(trade) else {
            require(dao.findTrade(trade.id)?.recordKey == trade.recordKey) { "交易不存在，请返回列表刷新" }
            dao.save(trade)
        }
        val time = System.currentTimeMillis()
        val instrument = dao.allInstruments().firstOrNull { it.market == trade.market && it.name == trade.symbol }
            ?: Instrument("custom_${newKey()}", trade.market, trade.symbol, trade.symbol, 1000)
        dao.saveInstrument(instrument.copy(lastUsed = time))
        tags.filter { it.kind == "strategy" && it.id == trade.strategy }.forEach { dao.saveTag(it.copy(lastUsed = time)) }
        dao.saveSettings((dao.settings() ?: AppSettings()).copy(lastMarket = trade.market))
        dao.deleteDraft(draft.key)
    }
    suspend fun delete(trade: Trade) = database.withTransaction { dao.delete(trade); dao.deleteDraft(trade.recordKey) }
    suspend fun saveDraft(draft: EntryDraft) { dao.saveDraft(draft) }
    suspend fun saveTag(tag: ManagedTag) {
        require(tag.kind in TagKind.names && Regex("[A-Za-z0-9_]{1,64}").matches(tag.id))
        require(tag.name.isNotBlank() && tag.name.length <= 30 && '\u0000' !in tag.name)
        dao.saveTag(tag.copy(name = tag.name.trim()))
    }
    suspend fun saveInstrument(value: Instrument) = database.withTransaction {
        require(value.market in Presets.markets.map { it.id } && value.name.isNotBlank() && value.name.length <= 30)
        require(value.code.isNotBlank() && value.code.length <= 30 && '\u0000' !in value.name && '\u0000' !in value.code)
        require(dao.allInstruments().none { it.id != value.id && it.market == value.market && it.name == value.name.trim() }) { "此市场已有同名品种" }
        dao.saveInstrument(value.copy(name = value.name.trim(), code = value.code.trim()))
    }
    suspend fun moveTag(tag: ManagedTag, delta: Int) = database.withTransaction {
        val values = dao.allTags().filter { it.kind == tag.kind }.sortedWith(compareBy<ManagedTag> { it.sortOrder }.thenBy { it.id })
        val index = values.indexOfFirst { it.id == tag.id }; val target = index + delta
        if (index >= 0 && target in values.indices) {
            val reordered = values.toMutableList().apply { val moved = removeAt(index); add(target, moved) }
            reordered.forEachIndexed { i, value -> dao.saveTag(value.copy(sortOrder = i)) }
        }
    }
    suspend fun moveInstrument(value: Instrument, delta: Int) = database.withTransaction {
        val values = dao.allInstruments().filter { it.market == value.market }.sortedWith(compareBy<Instrument> { it.sortOrder }.thenBy { it.id })
        val index = values.indexOfFirst { it.id == value.id }; val target = index + delta
        if (index >= 0 && target in values.indices) {
            val reordered = values.toMutableList().apply { val moved = removeAt(index); add(target, moved) }
            reordered.forEachIndexed { i, item -> dao.saveInstrument(item.copy(sortOrder = i)) }
        }
    }
}
