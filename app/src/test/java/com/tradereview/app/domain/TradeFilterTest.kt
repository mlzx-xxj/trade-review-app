package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TradeFilterTest {
    private val today = LocalDate.of(2024, 3, 1)
    private val a = Trade(id = 1, date = today.toEpochDay(), market = "futures", symbol = "螺纹钢", direction = "short", strategy = "custom_abc", netPnl = "1.00", reviewed = true, mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop", "size_control"))
    private val b = a.copy(id = 2, netPnl = "-2.00", mistakes = listOf("chase"), reviewed = false)
    private val c = a.copy(id = 3, netPnl = "0.00", market = "stock", symbol = "沪深300", strategy = "trend", direction = "long", date = today.minusDays(1).toEpochDay(), mistakes = emptyList())
    private fun apply(f: TradeFilter, values: List<Trade> = listOf(a, b, c)) = TradeFilters.apply(values, "CNY", f, today).map { it.id }

    @Test fun allDimensionsCombineWithAndAndAnyWithinMultiSelection() {
        val f = TradeFilter(period = "month", markets = listOf("futures"), symbols = listOf("螺纹钢"), directions = listOf("short"), outcomes = listOf("gain"),
            strategies = listOf("custom_abc"), mistakes = listOf("chase", "no_stop"), discipline = listOf("respect_stop"), review = "reviewed")
        assertEquals(listOf(1L), apply(f)); assertTrue(apply(f.copy(directions = listOf("long"))).isEmpty())
    }
    @Test fun anyAndAllModesHandleErrorsAndDisciplineIndependently() {
        val f = TradeFilter(mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop", "size_control"))
        assertEquals(listOf(2L, 1L), apply(f)); assertEquals(listOf(1L), apply(f.copy(tagMatch = "all")))
        assertTrue(apply(TradeFilter(strategies = listOf("trend", "custom_abc"), tagMatch = "all")).isEmpty())
    }
    @Test fun zeroAndEmptyFiltersAreExplicitAndCurrencyIsIsolated() {
        assertEquals(listOf(3L), apply(TradeFilter(outcomes = listOf("flat"))))
        assertEquals(3, apply(TradeFilter()).size); assertTrue(apply(TradeFilter(symbols = listOf("不存在"))).isEmpty())
        assertEquals(3, apply(TradeFilter(), listOf(a, b, c, a.copy(id = 4, currency = "USDT"))).size)
    }
    @Test fun weeklyMonthThirtyDayAndCustomBoundsAreInclusive() {
        assertEquals(LocalDate.of(2024, 2, 26).toEpochDay()..today.toEpochDay(), TradeFilter(period = "week").bounds(today))
        assertEquals(today.toEpochDay()..today.toEpochDay(), TradeFilter(period = "month").bounds(today))
        assertEquals(LocalDate.of(2024, 2, 1).toEpochDay()..today.toEpochDay(), TradeFilter(period = "30").bounds(today))
        assertEquals(listOf(2L, 1L), apply(TradeFilter(period = "custom", startDay = today.toEpochDay(), endDay = today.toEpochDay())))
    }
    @Test fun renamedHiddenCustomTagsStillMatchStableIds() {
        val tags = CatalogSeeds.tags + ManagedTag("strategy", "custom_abc", "已改名策略", hidden = true)
        val preset = SavedFilter(name = "隐藏策略", criteria = TradeFilter(strategies = listOf("custom_abc")))
        validateSavedFilter(preset, tags)
        assertEquals(listOf(2L, 1L), apply(preset.criteria))
        assertEquals("已改名策略", Catalog(tags).label("strategy", "custom_abc"))
    }
    @Test fun serializedPresetsKeepRelativeRangesAndAllConditions() {
        val f = TradeFilter(period = "30", symbols = listOf("中文,品种\n第二行"), strategies = listOf("custom_abc"), tagMatch = "all", errorPresence = "with", review = "pending")
        assertEquals(f, FilterCodec.decode(FilterCodec.encode(f)))
        assertNotEquals(f.bounds(today), f.bounds(today.plusDays(1)))
        assertFalse(TradeFilter().active); assertTrue(f.active)
    }
    @Test fun invalidDateOptionTypeUnknownFieldAndMissingTagAreRejected() {
        listOf(TradeFilter(period = "bad"), TradeFilter(period = "custom", startDay = 2, endDay = 1), TradeFilter(startDay = Long.MIN_VALUE),
            TradeFilter(markets = listOf("bad")), TradeFilter(mistakes = listOf("chase", "chase")), TradeFilter(tagMatch = "bad"))
            .forEach { assertThrows(IllegalArgumentException::class.java) { FilterCodec.validate(it) } }
        val json = FilterCodec.encode(TradeFilter())
        assertThrows(IllegalArgumentException::class.java) { FilterCodec.decode(json.replace("\"startDay\":0", "\"startDay\":\"0\"")) }
        assertThrows(IllegalArgumentException::class.java) { FilterCodec.decode(json.dropLast(1) + ",\"unknown\":true}") }
        assertThrows(IllegalArgumentException::class.java) { validateSavedFilter(SavedFilter(name = "坏标签", criteria = TradeFilter(strategies = listOf("missing"))), CatalogSeeds.tags) }
    }
    @Test fun drillDownFiltersIncludeEmotionsAndPresenceWithoutInventingNewTrades() {
        assertEquals(listOf(2L, 1L), apply(TradeFilter(errorPresence = "with", emotions = listOf("calm"))))
        assertEquals(listOf(3L), apply(TradeFilter(errorPresence = "without")))
    }
}
