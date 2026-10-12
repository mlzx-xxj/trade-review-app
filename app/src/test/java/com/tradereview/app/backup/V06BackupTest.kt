package com.tradereview.app.backup

import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class V06BackupTest {
    @Test fun structuredJsonIncludesGoalHistoryAndImagePolicyWithExplicitScope() {
        val first=ReviewPeriod.at("month",LocalDate.parse("2025-01-01")).start
        val goal=ReviewGoal(title="复盘比例",kind="review_ratio",period="month",startDate=first,threshold="80",archivedDay=first+10)
        val data=BackupData("2025-01-12T00:00:00Z",listOf(Trade(id=1,date=first)),emptyList(),goals=listOf(goal),evidenceSettings=EvidenceSettings(maxInputMiB=5))
        val json=BackupCodec.encode(data);assertTrue(json.contains("structured-data-only"));assertEquals(data,BackupCodec.decode(json));assertEquals(BackupCodec.SCHEMA_VERSION,BackupCodec.decode(json).sourceSchemaVersion)
    }
    @Test fun allFourOldVersionsKeepFinancialFactsAndHaveNoImaginaryAttachments() {
        listOf("v02-backup.json","v03-backup.json","v03-android-backup.json","v04-backup.json","v05-backup.json").forEach { name ->
            val old=javaClass.getResourceAsStream("/$name")!!.use(BackupCodec::read);val upgraded=BackupCodec.decode(BackupCodec.encode(old))
            assertEquals(old.copy(sourceSchemaVersion=BackupCodec.SCHEMA_VERSION),upgraded);assertTrue(upgraded.attachments.isEmpty());assertTrue(upgraded.goals.isEmpty());assertEquals("structured-data-only",upgraded.backupScope)
            assertEquals(old.trades.map { it.netPnl to it.recordKey },upgraded.trades.map { it.netPnl to it.recordKey })
        }
    }
    @Test fun invalidGoalMetadataAndImagePolicyAreStrictlyRejected() {
        val date=ReviewPeriod.at("week",LocalDate.parse("2025-01-01")).start
        val g=ReviewGoal(title="目标",kind="mistake_count",startDate=date,threshold="1",mistakeId="chase")
        val base=BackupData("2025-01-01T00:00:00Z",emptyList(),emptyList(),goals=listOf(g))
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(base.copy(goals=listOf(g.copy(mistakeId="missing"))))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(base.copy(goals=listOf(g,g)))}
        assertThrows(IllegalArgumentException::class.java){BackupCodec.encode(base.copy(evidenceSettings=EvidenceSettings(maxInputMiB=100)))}
        val json=BackupCodec.encode(base);assertThrows(BackupException::class.java){BackupCodec.decode(json.replace("\"maxInputMiB\": 10","\"maxInputMiB\": \"10\""))}
    }
}
