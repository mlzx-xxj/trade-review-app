package com.tradereview.app.backup

import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test

class V03BackupTest {
    @Test fun v02GoldenBackupImportsWithOriginalChecksumAndStableIds() {
        val data = BackupCodec.read(requireNotNull(javaClass.getResourceAsStream("/v02-backup.json")))
        assertEquals(1, data.sourceSchemaVersion); assertEquals("-123.45", data.trades.single().netPnl)
        assertEquals(listOf("chase", "oversize"), data.trades.single().mistakes)
        assertEquals("", data.trades.single().note); assertTrue(data.dailyReviews.single().reviewSaved)
        assertEquals(CatalogSeeds.tags, data.tags)
        assertEquals(data.copy(sourceSchemaVersion = 4), BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun newBackupRoundTripCoversCatalogsOrderingSettingsAndPartialDrafts() {
        val tag = ManagedTag("strategy", "custom_abc", "自定义,策略\n第二行", 12, true, true, 500)
        val draft = EntryDraft(amount = "12.", strategy = tag.id, note = "草稿")
        val trade = Trade(id = 7, date = 20000, strategy = tag.id, note = "备注,\"中文\"\n换行")
        val data = BackupData("2026-10-11T07:00:00Z", listOf(trade), emptyList(), CatalogSeeds.tags + tag,
            CatalogSeeds.instruments + Instrument("custom_xyz", "stock", "自定义品种", "ZZ", 55, true, 77), AppSettings(theme = "dark"), listOf(draft))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
        assertTrue(TradeCsv.encode(data.trades, Catalog(data.tags)).contains("自定义,策略\n第二行"))
    }
    @Test fun rejectsMissingReferencesDuplicateIdsBadSettingsAndDamagedNewMetadata() {
        val data = BackupData("2026-10-11T07:00:00Z", emptyList(), emptyList())
        listOf(data.copy(tags = emptyList()), data.copy(tags = data.tags + data.tags.first()), data.copy(settings = AppSettings(theme = "bad")),
            data.copy(drafts = listOf(EntryDraft(strategy = "missing"))), data.copy(instruments = data.instruments + data.instruments.first()),
            data.copy(drafts = listOf(EntryDraft(key = "missing", tradeId = 7))))
            .forEach { assertThrows(BackupException::class.java) { BackupCodec.validate(it) } }
        val json = BackupCodec.encode(data)
        assertThrows(BackupException::class.java) { BackupCodec.decode(json.replace("趋势跟随", "修改名称")) }
    }
}
