package com.tradereview.app.domain

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test

class FastEntryTest {
    @Test fun amountIsRequiredAndZeroOnlyWorksForExplicitFlat() {
        listOf("", "0", "-1", "1.001", "NaN", "1000000000000").forEach { assertNotNull(FastEntry.error(EntryDraft(amount = it))) }
        assertEquals("12.30", FastEntry.trade(EntryDraft(amount = "12.3")).netPnl)
        assertEquals("-12.30", FastEntry.trade(EntryDraft(amount = "12.3", outcome = "loss")).netPnl)
        assertEquals("0.00", FastEntry.trade(EntryDraft(outcome = "flat")).netPnl)
        assertNotNull(FastEntry.error(EntryDraft(outcome = "flat", amount = "5")))
    }
    @Test fun copyRetainsReusableFieldsButClearsOutcomeAndReview() {
        val t = Trade(id = 7, market = "futures", symbol = "黄金", direction = "short", currency = "USD", strategy = "range", netPnl = "12.00", reviewed = true, mistakes = listOf("chase"), note = "old")
        val d = EntryDraft.copyReusable(t)
        assertEquals(t.symbol, d.symbol); assertEquals(t.strategy, d.strategy); assertEquals(t.direction, d.direction)
        assertEquals("", d.amount); assertEquals("", d.outcome); assertFalse(d.reviewed); assertTrue(d.mistakes.isEmpty()); assertEquals("", d.note)
        assertEquals(0L, d.tradeId); assertNotEquals(t.recordKey, d.recordKey)
    }
    @Test fun hiddenAndRenamedTagStillResolvesHistoricalStableId() {
        val catalog = Catalog(CatalogSeeds.tags.map { if(it.kind == "strategy" && it.id == "trend") it.copy(name = "新名称", hidden = true) else it })
        assertEquals("新名称", catalog.label("strategy", "trend"))
        assertFalse(catalog.options("strategy").any { it.id == "trend" })
        assertTrue(catalog.options("strategy", listOf("trend")).any { it.id == "trend" })
    }
    @Test fun symbolsSearchByChineseOrCodeAndRecentThenFavoriteThenOrder() {
        val a = Instrument("a", "futures", "黄金", "AU", 0)
        val b = Instrument("b", "futures", "沪铜", "CU", 1, true)
        val c = Instrument("c", "futures", "铁矿石", "I", 2, false, 100)
        assertEquals(listOf(c,b,a), orderedInstruments(listOf(a,b,c), "futures"))
        assertEquals(listOf(a), orderedInstruments(listOf(a,b,c), "futures", "au"))
        assertEquals(listOf(c), orderedInstruments(listOf(a,b,c), "futures", "铁"))
        assertEquals(15, CatalogSeeds.instruments.count { it.market == "futures" })
    }
    @Test fun existingFlatTradeCanBeEditedAndSavedWithoutTypingZero() {
        val d = EntryDraft.from(Trade(id = 3, netPnl = "0.00"))
        assertEquals("", d.amount); assertNull(FastEntry.error(d)); assertEquals("0.00", FastEntry.trade(d).netPnl)
    }
}
