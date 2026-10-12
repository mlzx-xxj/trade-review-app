package com.tradereview.app.backup

import com.google.gson.JsonParser
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class V05BackupTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use { it.readText() }
    @Test fun independentV05GoldenMatchesCanonicalNullsAndAllNewObjects() {
        val json = fixture("v05-backup.json"); val data = BackupCodec.decode(json)
        assertEquals(4,data.sourceSchemaVersion); assertEquals("395.00",data.trades.single().netPnl)
        assertEquals("rb2701",data.trades.single().professional!!.contractCode); assertNull(data.trades.single().professional!!.manualRisk)
        assertEquals(3,data.trades.single().riskRecord!!.violations.size); assertEquals(1,data.riskRules.size); assertEquals(1,data.contractTemplates.size)
        val output = BackupCodec.encode(data)
        assertEquals(JsonParser.parseString(json).asJsonObject["checksumSha256"],JsonParser.parseString(output).asJsonObject["checksumSha256"])
        assertEquals(data,BackupCodec.decode(output))
    }
    @Test fun allLegacyFormatsUpgradeWithUnknownProfessionalFieldsAndUnchangedAmountsAndKeys() {
        listOf("v02-backup.json","v03-backup.json","v03-android-backup.json","v04-backup.json").forEach { name ->
            val old = BackupCodec.decode(fixture(name)); assertTrue(old.trades.all { it.professional == null && it.riskRecord == null })
            assertTrue(old.riskRules.isEmpty()); assertTrue(old.contractTemplates.isEmpty())
            val newer = BackupCodec.decode(BackupCodec.encode(old));assertEquals(old.copy(sourceSchemaVersion=4),newer)
            assertEquals(old.trades.map { it.netPnl to it.recordKey },newer.trades.map { it.netPnl to it.recordKey })
        }
    }
    @Test fun partialProfessionalDraftRoundTripsWithoutTurningUnknownIntoZero() {
        val data=BackupData("2026-10-11T22:00:00Z",emptyList(),emptyList(),drafts=listOf(EntryDraft(amount="12.",professional=ProfessionalDraft(entryPrice="3500.",multiplier="10",expanded=true))))
        val copy=BackupCodec.decode(BackupCodec.encode(data));assertEquals(data,copy)
        assertEquals("",copy.drafts.single().professional!!.fees);assertEquals("3500.",copy.drafts.single().professional!!.entryPrice)
    }
    @Test fun invalidAutoNetReferencesPrecisionUnknownFieldsAndTamperingAreRejected() {
        val json=fixture("v05-backup.json")
        listOf(json.replace("395.00","396.00"),json.replace("\"linearConfirmed\": true","\"linearConfirmed\": false"),
            json.replace("\"fees\": \"3.00\"","\"fees\": 3"),json.replace("\"multiplier\": \"10\"","\"multiplier\": \"0\""),
            json.replace("\"planStatus\": \"out\"","\"planStatus\": \"guessed\""), json.replace("\"initialRisk\": \"200\"","\"initialRisk\": \"0\""))
            .forEach { assertThrows(BackupException::class.java) { BackupCodec.decode(it) } }
        val data=BackupCodec.decode(json); assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(data.copy(trades=listOf(data.trades.single().copy(netPnl="0.00")))) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(data.copy(riskRules=data.riskRules+data.riskRules)) }
    }
    @Test fun professionalCsvKeepsExactFieldsEmptyUnknownAndEscapesUserNotes() {
        val data=BackupCodec.decode(fixture("v05-backup.json")); val trade=data.trades.single().copy(professional=data.trades.single().professional!!.copy(reason="=SUM(1,2)\n检查"))
        val csv=TradeCsv.encode(listOf(trade,Trade(id=2,netPnl="0.00")))
        assertTrue(csv.contains("\"有效初始风险\""));assertTrue(csv.contains("\"rb2701\""));assertTrue(csv.contains("\"200\""));assertTrue(csv.contains("\"1.975\""))
        assertTrue(csv.contains("\"'=SUM(1,2)\n检查\""));assertTrue(csv.contains("\"费用逐项确认\n避免重复滑点\"")); assertTrue(csv.contains("\"未说明\""))
    }
}
