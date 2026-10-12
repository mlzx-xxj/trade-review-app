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
        assertEquals(data.copy(sourceSchemaVersion = 4), BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun oldAndroidReflectionOrderIsAcceptedWithoutWeakeningIntegrity() {
        val data = BackupCodec.read(requireNotNull(javaClass.getResourceAsStream("/v03-android-backup.json")))
        val jvm = BackupCodec.read(requireNotNull(javaClass.getResourceAsStream("/v03-backup.json")))
        assertEquals(jvm, data)
        val json = requireNotNull(javaClass.getResourceAsStream("/v03-android-backup.json")).bufferedReader().use { it.readText() }
        assertThrows(BackupException::class.java) { BackupCodec.decode(json.replace("隐藏的旧策略", "修改的旧策略")) }
        assertEquals(data.copy(sourceSchemaVersion = 4), BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun v04RoundTripPreservesPaletteFiltersAllTagModesAndCurrency() {
        val criteria = TradeFilter(period = "custom", startDay = 19999, endDay = 20000, markets = listOf("stock"), symbols = listOf("沪深300"),
            mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop"), outcomes = listOf("loss"), review = "reviewed", tagMatch = "all")
        val data = BackupData("2026-10-11T07:00:00Z", listOf(Trade(id = 1, date = 20000)), emptyList(),
            settings = AppSettings(pnlPalette = "red_profit"), savedFilters = listOf(SavedFilter(name = "常用筛选", currency = "USDT", criteria = criteria)))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun canonicalV04GoldenChecksumMatchesAcrossRuntimeFieldOrders() {
        val json = requireNotNull(javaClass.getResourceAsStream("/v04-backup.json")).bufferedReader().use { it.readText() }
        val data = BackupCodec.decode(json)
        assertEquals("red_profit", data.settings.pnlPalette); assertEquals("12.", data.drafts.single().amount)
        assertEquals("custom_v03", data.savedFilters.single().criteria.strategies.single())
        val before = com.google.gson.JsonParser.parseString(json).asJsonObject["checksumSha256"].asString
        val after = com.google.gson.JsonParser.parseString(BackupCodec.encode(data)).asJsonObject["checksumSha256"].asString
        assertNotEquals(before, after) // Re-export intentionally upgrades to format 4.
        assertEquals(data.copy(sourceSchemaVersion = 4), BackupCodec.decode(BackupCodec.encode(data)))
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
