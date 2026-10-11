package com.tradereview.app.backup

import com.tradereview.app.data.*
import com.tradereview.app.domain.TradeFilter
import org.junit.Assert.*
import org.junit.Test

class V04BackupTest {
    @Test fun authenticV03FieldSetAndChecksumImportWithoutLosingHiddenHistory() {
        val data = BackupCodec.read(requireNotNull(javaClass.getResourceAsStream("/v03-backup.json")))
        assertEquals(2, data.sourceSchemaVersion); assertEquals("V0.3 原始备注", data.trades.single().note)
        assertEquals("custom_v03", data.trades.single().strategy); assertTrue(data.tags.first { it.id == "custom_v03" }.hidden)
        assertEquals("dark", data.settings.theme); assertEquals("green_profit", data.settings.pnlPalette); assertTrue(data.savedFilters.isEmpty())
        assertEquals(data.copy(sourceSchemaVersion = 3), BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun v04RoundTripPreservesPaletteFiltersAllTagModesAndCurrency() {
        val criteria = TradeFilter(period = "custom", startDay = 19999, endDay = 20000, markets = listOf("stock"), symbols = listOf("沪深300"),
            mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop"), outcomes = listOf("loss"), review = "reviewed", tagMatch = "all")
        val data = BackupData("2026-10-11T07:00:00Z", listOf(Trade(id = 1, date = 20000)), emptyList(),
            settings = AppSettings(pnlPalette = "red_profit"), savedFilters = listOf(SavedFilter(name = "常用筛选", currency = "USDT", criteria = criteria)))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun invalidFilterReferencesCountsSettingsAndTamperingAreRejected() {
        val data = BackupData("2026-10-11T07:00:00Z", emptyList(), emptyList(), savedFilters = listOf(SavedFilter(name = "常用")))
        listOf(data.copy(settings = AppSettings(pnlPalette = "bad")), data.copy(savedFilters = data.savedFilters + data.savedFilters),
            data.copy(savedFilters = listOf(data.savedFilters.single().copy(criteria = TradeFilter(strategies = listOf("missing"))))))
            .forEach { invalid -> assertThrows(BackupException::class.java) { BackupCodec.validate(invalid) } }
        val json = BackupCodec.encode(data)
        assertThrows(BackupException::class.java) { BackupCodec.decode(json.replace("\"savedFilters\": 1", "\"savedFilters\": 5")) }
        assertThrows(BackupException::class.java) { BackupCodec.decode(json.replace("green_profit", "red_profit")) }
        assertThrows(BackupException::class.java) { BackupCodec.decode(json.replace("\"tagMatch\": \"any\"", "\"tagMatch\": \"all\"")) }
    }
}
