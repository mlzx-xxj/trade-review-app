package com.tradereview.app.backup
import com.tradereview.app.data.*
import com.tradereview.app.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
class V07BackupTest {
    private val day=LocalDate.of(2024,2,29).toEpochDay()
    private fun data()=BackupData("2026-10-12T00:00:00Z",emptyList(),emptyList(),smartSettings=SmartSettings(minimumSamples=20,automaticReminders=false),
        preflightItems=listOf(PreflightItem(text="先核对风险",archived=true)),preflightChecks=listOf(PreflightCheck(day,"auto:review:${day-1}","补齐昨日复盘",true,"auto",listOf("review:${day-1}"))),
        ocrDrafts=listOf(OcrDraft(fields=SmartJson.fields(mapOf("symbol" to "螺纹钢","realizedPnl" to "-12.")),blocked=true,closedEvidence=false,createdDay=day)))
    @Test fun allNewSettingsChecksAndIncompleteOcrFieldsRoundTripExactly() {
        val d=data();val output=BackupCodec.encode(d);assertEquals(d,BackupCodec.decode(output));assertFalse(output.contains("rawText"));assertEquals(7,BackupCodec.SCHEMA_VERSION)
    }
    @Test fun everySupportedOldGoldenVersionIncludingV06RemainsCompatible() {
        for(name in listOf("v02-backup.json","v03-backup.json","v03-android-backup.json","v04-backup.json","v05-backup.json","v06-backup.json")) {
            val old=BackupCodec.read(javaClass.classLoader!!.getResourceAsStream(name)!!);val upgraded=BackupCodec.decode(BackupCodec.encode(old))
            assertEquals(old.copy(sourceSchemaVersion=BackupCodec.SCHEMA_VERSION),upgraded);assertEquals(SmartSettings(),old.smartSettings);assertTrue(old.preflightChecks.isEmpty());assertTrue(old.ocrDrafts.isEmpty())
        }
    }
    @Test fun illegalSmartRulesAndDuplicateChecksOrDraftFieldsAreRejected() {
        val d=data()
        val bad=listOf(d.copy(smartSettings=SmartSettings(minimumSamples=0)),d.copy(smartSettings=SmartSettings(enabledCategories=listOf("cloud"))),
            d.copy(preflightChecks=d.preflightChecks+d.preflightChecks),d.copy(ocrDrafts=d.ocrDrafts.map { it.copy(fields="{\"rawText\":\"unbounded\"}") }),
            d.copy(preflightItems=d.preflightItems.map { it.copy(id="bad") }))
        for(v in bad)try { BackupCodec.encode(v);fail("illegal backup accepted") }catch(_:IllegalArgumentException){}
    }
    @Test fun strictSmartTypesAndCanonicalChecksumPreventSilentCoercion() {
        val text=BackupCodec.encode(data())
        for(v in listOf(text.replace("\"minimumSamples\": 20","\"minimumSamples\": \"20\""),text.replace("\"checked\": true","\"checked\": 1"),text.replace("先核对风险","改写检查")))
            try { BackupCodec.decode(v);fail() }catch(_:IllegalArgumentException){}
    }
}
