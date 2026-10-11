package com.tradereview.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.tradereview.app.domain.FilterCodec
import com.tradereview.app.domain.TradeFilter

@Entity(tableName = "saved_filters")
data class SavedFilter(@PrimaryKey val id: String = newKey(), val name: String, val currency: String = "CNY", val criteria: TradeFilter = TradeFilter())

class FilterConverters {
    @TypeConverter fun encode(value: TradeFilter): String = FilterCodec.encode(value)
    @TypeConverter fun decode(value: String): TradeFilter = FilterCodec.decode(value)
}

fun validateSavedFilter(value: SavedFilter, tags: List<ManagedTag>) {
    require(Regex("[0-9a-f]{32}").matches(value.id) && value.name.isNotBlank() && value.name.length <= 30 && '\u0000' !in value.name) { "筛选方案名称或标识无效" }
    require(value.currency in Presets.currencies.map { it.id }) { "筛选方案币种无效" }
    FilterCodec.validate(value.criteria)
    listOf("strategy" to value.criteria.strategies, "mistake" to value.criteria.mistakes,
        "discipline" to value.criteria.discipline, "emotion" to value.criteria.emotions).forEach { (kind, ids) ->
        require(ids.all { id -> tags.any { it.kind == kind && it.id == id } }) { "筛选方案引用的标签不存在" }
    }
}
