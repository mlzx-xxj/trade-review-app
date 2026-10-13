package com.tradereview.app.backup

import com.google.gson.JsonParser
import com.tradereview.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate

class BackupCodecTest {
    private val day = LocalDate.of(2024, 10, 4).toEpochDay()
    private fun fixture() = BackupData("2026-10-11T07:00:00Z", listOf(
        Trade(id = 7, date = day, symbol = "中文,\"品种\"\n第二行", netPnl = "-123.45", reviewed = true,
            recordKey = "0123456789abcdef0123456789abcdef", mistakes = listOf("chase", "oversize"), discipline = listOf("respect_stop")),
        Trade(id = 8, date = day, currency = "USD", netPnl = "999999999999.99", recordKey = "1123456789abcdef0123456789abcdef")),
        listOf(DailyReview(date = day, outlook = "bullish", strategies = listOf("trend", "breakout"), preparations = listOf("risk"),
            maxPosition = 25, planSaved = true, emotion = "anxious", discipline = listOf("respect_stop"), mistakes = listOf("chase"),
            lessons = listOf("stop", "rest"), rating = 4, reviewSaved = true)))

    @Test fun completeBackupRoundTripPreservesEveryFieldAndTag() {
        val original = fixture()
        val json = BackupCodec.encode(original)
        assertEquals(original, BackupCodec.decode(json))
        assertEquals(original, BackupCodec.read(ByteArrayInputStream(("\uFEFF" + json).toByteArray())))
        assertEquals(1, original.planCount); assertEquals(1, original.reviewCount)
    }
    @Test fun emptyBackupRoundTripsAndCanIntentionallyReplaceWithEmptyData() {
        val data = BackupData("2026-10-11T07:00:00Z", emptyList(), emptyList())
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun rejectsUnsupportedVersionMissingUnknownAndIncorrectlyTypedFields() {
        val json = BackupCodec.encode(fixture())
        reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}", "\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION + 1}"))
        reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}", "\"schemaVersion\": \"1\""))
        reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION},", ""))
        reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}", "\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}, \"unknown\": 5"))
        reject(json.replace("\"reviewed\": true", "\"reviewed\": \"true\""))
        reject(json.replace("\"positionPercent\": 10", "\"positionPercent\": 4294967306"))
        reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}", "\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}e0"))
    }
    @Test fun rejectsTruncatedWrongChecksumOrCountAndChangedPayload() {
        val json = BackupCodec.encode(fixture())
        reject(json.dropLast(1)); reject(json + " true")
        reject(json.replace("-123.45", "-123.46"))
        val objectValue = JsonParser.parseString(json).asJsonObject
        objectValue.getAsJsonObject("counts").addProperty("trades", 99)
        reject(objectValue.toString())
        objectValue.addProperty("checksumSha256", "0".repeat(64))
        reject(objectValue.toString())
    }
    @Test fun rejectsDuplicateJsonKeysLenientSyntaxNullAndDeepNesting() {
        val json = BackupCodec.encode(fixture())
        reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}", "\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}, \"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}"))
        reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}", "'schemaVersion': 1"))
        reject("/* comment */" + json); reject(json.replace("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION}", "\"schemaVersion\": null"))
        reject("[".repeat(20) + "0" + "]".repeat(20))
    }
    @Test fun rejectsInvalidOptionsDatesAmountsPositionsRatingsAndTags() {
        val data = fixture(); val t = data.trades.first(); val r = data.dailyReviews.first()
        listOf(t.copy(market = "unknown"), t.copy(currency = "EUR"), t.copy(direction = "bad"), t.copy(strategy = "bad"),
            t.copy(emotion = "bad"), t.copy(symbol = ""), t.copy(symbol = "a".repeat(31)), t.copy(positionPercent = 0),
            t.copy(positionPercent = 101), t.copy(netPnl = "1.001"), t.copy(netPnl = "NaN"), t.copy(netPnl = "1e2"), t.copy(netPnl = " 1.00 "),
            t.copy(date = Long.MAX_VALUE), t.copy(date = LocalDate.now().plusDays(1).toEpochDay()), t.copy(id = 0),
            t.copy(recordKey = "bad"), t.copy(mistakes = listOf("chase", "chase")), t.copy(discipline = listOf("bad")))
            .forEach { invalid -> rejectsData(data.copy(trades = listOf(invalid))) }
        listOf(r.copy(rating = 0), r.copy(rating = 6), r.copy(maxPosition = 101), r.copy(outlook = "bad"),
            r.copy(emotion = "bad"), r.copy(strategies = listOf("bad")), r.copy(preparations = listOf("bad")),
            r.copy(lessons = listOf("bad")), r.copy(date = LocalDate.now().plusDays(1).toEpochDay()))
            .forEach { invalid -> rejectsData(data.copy(dailyReviews = listOf(invalid))) }
    }
    @Test fun rejectsDuplicateRecordIdentityRowIdsAndReviewDates() {
        val data = fixture(); val t = data.trades.first()
        rejectsData(data.copy(trades = listOf(t, t.copy(id = 9))))
        rejectsData(data.copy(trades = listOf(t, t.copy(recordKey = "2".repeat(32)))))
        rejectsData(data.copy(dailyReviews = data.dailyReviews + data.dailyReviews))
    }
    @Test fun futurePrePlanIsSupportedAndInvalidUtf8IsRejected() {
        val data = fixture().copy(dailyReviews = listOf(DailyReview(date = LocalDate.now().plusDays(1).toEpochDay(), planSaved = true)))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
        assertThrows(BackupException::class.java) { BackupCodec.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
    }
    @Test fun inputSizeAndRecordLimitsAreEnforcedBeforeRestoring() {
        val tooLarge = object : InputStream() {
            var left = BackupCodec.MAX_BYTES + 1
            override fun read(): Int = if (left-- > 0) 32 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (left == 0) return -1
                val count = minOf(length, left); bytes.fill(32, offset, offset + count); left -= count; return count
            }
        }
        assertThrows(BackupException::class.java) { BackupCodec.read(tooLarge) }
        rejectsData(fixture().copy(trades = List(BackupCodec.MAX_RECORDS + 1) { fixture().trades.first() }))
    }
    private fun reject(json: String) { assertThrows(BackupException::class.java) { BackupCodec.decode(json) } }
    private fun rejectsData(data: BackupData) { assertThrows(BackupException::class.java) { BackupCodec.validate(data) } }
}
