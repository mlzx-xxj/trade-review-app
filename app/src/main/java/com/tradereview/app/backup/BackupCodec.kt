package com.tradereview.app.backup

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tradereview.app.data.*
import com.tradereview.app.domain.Money
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

class BackupException(message: String) : IllegalArgumentException(message)

data class BackupData(val exportedAt: String, val trades: List<Trade>, val dailyReviews: List<DailyReview>) {
    val planCount get() = dailyReviews.count { it.planSaved }
    val reviewCount get() = dailyReviews.count { it.reviewSaved }
}

/** Schema version is the interchange format version, independent of Room's version. */
object BackupCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_RECORDS = 10_000
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val pretty = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
    private val firstDay = LocalDate.of(1900, 1, 1).toEpochDay()
    private val lastDay = LocalDate.of(2100, 12, 31).toEpochDay()

    fun read(input: InputStream): BackupData {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (output.size() <= MAX_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        requireBackup(bytes.size <= MAX_BYTES, "备份超过 16 MiB 上限")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { throw BackupException("文件不是有效的 UTF-8 JSON") }
        return decode(text.removePrefix("\uFEFF"))
    }

    fun encode(data: BackupData): String {
        validate(data)
        val root = payload(data)
        root.addProperty("checksumSha256", digest(gson.toJson(root)))
        val result = pretty.toJson(root)
        requireBackup(result.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, "备份超过 16 MiB 上限")
        return result
    }

    fun decode(text: String): BackupData {
        requireBackup(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, "备份超过 16 MiB 上限")
        try {
            val root = JsonReader(StringReader(text)).use { reader ->
                reader.strictness = Strictness.STRICT
                val value = readNode(reader, 0)
                requireBackup(reader.peek() == JsonToken.END_DOCUMENT, "JSON 后有多余内容")
                value.obj()
            }
            root.fields("format", "schemaVersion", "exportedAt", "counts", "trades", "dailyReviews", "checksumSha256")
            requireBackup(root.string("format") == "trade-review-backup", "不是交易复盘备份文件")
            requireBackup(root.integer("schemaVersion") == SCHEMA_VERSION.toLong(), "不支持此备份版本，请使用匹配版本的应用")
            val data = BackupData(root.string("exportedAt"), root.array("trades").map { parseTrade(it.obj()) },
                root.array("dailyReviews").map { parseReview(it.obj()) })
            validate(data)
            val counts = root.getAsJsonObject("counts") ?: throw BackupException("缺少数量校验")
            counts.fields("trades", "dailyReviews", "plans", "reviews")
            requireBackup(counts.integer("trades") == data.trades.size.toLong() &&
                counts.integer("dailyReviews") == data.dailyReviews.size.toLong() &&
                counts.integer("plans") == data.planCount.toLong() && counts.integer("reviews") == data.reviewCount.toLong(), "数量不一致，备份可能不完整")
            requireBackup(root.string("checksumSha256") == digest(gson.toJson(payload(data))), "完整性校验失败，文件可能损坏或被修改")
            return data
        } catch (error: BackupException) { throw error }
        catch (_: Exception) { throw BackupException("JSON 格式或字段类型不正确，未恢复任何数据") }
    }

    fun validate(data: BackupData, today: Long = LocalDate.now().toEpochDay()) {
        requireBackup(data.exportedAt.length <= 40 && runCatching { Instant.parse(data.exportedAt) }.isSuccess, "导出时间无效")
        requireBackup(data.trades.size <= MAX_RECORDS && data.dailyReviews.size <= MAX_RECORDS, "记录数超过 10000 上限")
        requireBackup(data.trades.map { it.id }.toSet().size == data.trades.size, "交易编号重复")
        requireBackup(data.trades.map { it.recordKey }.toSet().size == data.trades.size, "交易稳定标识重复")
        requireBackup(data.dailyReviews.map { it.date }.toSet().size == data.dailyReviews.size, "每日日志日期重复")
        data.trades.forEach { trade ->
            requireBackup(trade.id in 1 until Long.MAX_VALUE, "交易编号无效")
            requireBackup(Regex("[0-9a-f]{32}").matches(trade.recordKey), "交易稳定标识无效")
            day(trade.date)
            requireBackup(trade.date <= today, "交易日期不能晚于今天")
            requireBackup(trade.symbol.isNotBlank() && trade.symbol.length <= 30 && '\u0000' !in trade.symbol, "品种名称无效")
            requireBackup(Money.parse(trade.netPnl) != null, "交易金额无效")
            requireBackup(trade.positionPercent in 1..100, "交易仓位无效")
            choice(trade.market, Presets.markets); choice(trade.direction, Presets.directions)
            choice(trade.currency, Presets.currencies); choice(trade.strategy, Presets.strategies)
            choice(trade.emotion, Presets.emotions); tags(trade.mistakes, Presets.mistakes)
            tags(trade.discipline, Presets.discipline)
        }
        data.dailyReviews.forEach { review ->
            day(review.date)
            requireBackup(!review.reviewSaved || review.date <= today, "盘后复盘日期不能晚于今天")
            requireBackup(review.maxPosition in 1..100 && review.rating in 1..5, "每日日志仓位或评分无效")
            choice(review.outlook, Presets.outlooks); choice(review.emotion, Presets.emotions)
            tags(review.strategies, Presets.strategies); tags(review.preparations, Presets.preparations)
            tags(review.discipline, Presets.discipline); tags(review.mistakes, Presets.mistakes); tags(review.lessons, Presets.lessons)
        }
    }

    // Fixed field order makes the checksum independent of whitespace or incoming object key order.
    private fun payload(data: BackupData) = JsonObject().apply {
        addProperty("format", "trade-review-backup"); addProperty("schemaVersion", SCHEMA_VERSION)
        addProperty("exportedAt", data.exportedAt)
        add("counts", JsonObject().apply {
            addProperty("trades", data.trades.size); addProperty("dailyReviews", data.dailyReviews.size)
            addProperty("plans", data.planCount); addProperty("reviews", data.reviewCount)
        })
        add("trades", JsonArray().apply { data.trades.forEach { add(tradeJson(it)) } })
        add("dailyReviews", JsonArray().apply { data.dailyReviews.forEach { add(reviewJson(it)) } })
    }
    private fun tradeJson(t: Trade) = JsonObject().apply {
        addProperty("id", t.id); addProperty("recordKey", t.recordKey); addProperty("date", t.date)
        addProperty("market", t.market); addProperty("symbol", t.symbol); addProperty("direction", t.direction)
        addProperty("currency", t.currency); addProperty("netPnl", t.netPnl); addProperty("positionPercent", t.positionPercent)
        addProperty("strategy", t.strategy); addProperty("emotion", t.emotion)
        add("mistakes", tagJson(t.mistakes)); add("discipline", tagJson(t.discipline)); addProperty("reviewed", t.reviewed)
    }
    private fun reviewJson(r: DailyReview) = JsonObject().apply {
        addProperty("date", r.date); addProperty("outlook", r.outlook); add("strategies", tagJson(r.strategies))
        add("preparations", tagJson(r.preparations)); addProperty("maxPosition", r.maxPosition); addProperty("planSaved", r.planSaved)
        addProperty("emotion", r.emotion); add("discipline", tagJson(r.discipline)); add("mistakes", tagJson(r.mistakes))
        add("lessons", tagJson(r.lessons)); addProperty("rating", r.rating); addProperty("reviewSaved", r.reviewSaved)
    }
    private fun tagJson(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }
    private fun parseTrade(o: JsonObject): Trade {
        o.fields("id", "recordKey", "date", "market", "symbol", "direction", "currency", "netPnl", "positionPercent", "strategy", "emotion", "mistakes", "discipline", "reviewed")
        return Trade(id = o.integer("id"), recordKey = o.string("recordKey"), date = o.integer("date"), market = o.string("market"),
            symbol = o.string("symbol"), direction = o.string("direction"), currency = o.string("currency"), netPnl = o.string("netPnl"),
            positionPercent = o.smallInt("positionPercent"), strategy = o.string("strategy"), emotion = o.string("emotion"),
            mistakes = o.strings("mistakes"), discipline = o.strings("discipline"), reviewed = o.boolean("reviewed"))
    }
    private fun parseReview(o: JsonObject): DailyReview {
        o.fields("date", "outlook", "strategies", "preparations", "maxPosition", "planSaved", "emotion", "discipline", "mistakes", "lessons", "rating", "reviewSaved")
        return DailyReview(date = o.integer("date"), outlook = o.string("outlook"), strategies = o.strings("strategies"), preparations = o.strings("preparations"),
            maxPosition = o.smallInt("maxPosition"), planSaved = o.boolean("planSaved"), emotion = o.string("emotion"), discipline = o.strings("discipline"),
            mistakes = o.strings("mistakes"), lessons = o.strings("lessons"), rating = o.smallInt("rating"), reviewSaved = o.boolean("reviewSaved"))
    }
    private fun readNode(r: JsonReader, depth: Int): JsonElement {
        requireBackup(depth <= 8, "JSON 嵌套过深")
        return when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                r.beginObject()
                while (r.hasNext()) {
                    val key = r.nextName()
                    requireBackup(key.length <= 64 && !has(key) && size() < 32, "JSON 字段重复或过多")
                    add(key, readNode(r, depth + 1))
                }
                r.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                r.beginArray()
                while (r.hasNext()) { requireBackup(size() < MAX_RECORDS, "数组超过上限"); add(readNode(r, depth + 1)) }
                r.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(r.nextString().also { requireBackup(it.length <= 4096, "JSON 字符串过长") })
            JsonToken.NUMBER -> {
                val value = r.nextString()
                requireBackup(value.length <= 20 && Regex("-?(0|[1-9][0-9]*)").matches(value), "数字应为范围内的整数；金额应为文本")
                JsonPrimitive(value.toBigDecimal())
            }
            JsonToken.BOOLEAN -> JsonPrimitive(r.nextBoolean())
            else -> throw BackupException("JSON 包含无效字段或空值")
        }
    }
    private fun JsonElement.obj(): JsonObject { requireBackup(isJsonObject, "字段应为对象"); return asJsonObject }
    private fun JsonObject.fields(vararg names: String) = requireBackup(keySet() == names.toSet(), "备份字段缺失或含未知字段")
    private fun JsonObject.string(name: String): String {
        val v = get(name); requireBackup(v is JsonPrimitive && v.isString, "$name 应为文本"); return v.asString
    }
    private fun JsonObject.integer(name: String): Long {
        val v = get(name); requireBackup(v is JsonPrimitive && v.isNumber && Regex("-?(0|[1-9][0-9]*)").matches(v.asString), "$name 应为整数")
        return v.asString.toLongOrNull() ?: throw BackupException("$name 超出整数范围")
    }
    private fun JsonObject.smallInt(name: String): Int {
        val v = integer(name); requireBackup(v in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong(), "$name 超出范围"); return v.toInt()
    }
    private fun JsonObject.boolean(name: String): Boolean {
        val v = get(name); requireBackup(v is JsonPrimitive && v.isBoolean, "$name 应为布尔值"); return v.asBoolean
    }
    private fun JsonObject.array(name: String): JsonArray {
        val v = get(name); requireBackup(v is JsonArray, "$name 应为数组"); return v.asJsonArray
    }
    private fun JsonObject.strings(name: String) = array(name).map {
        requireBackup(it is JsonPrimitive && it.isString, "标签应为文本"); it.asString
    }
    private fun choice(value: String, choices: List<Option>) = requireBackup(choices.any { it.id == value }, "含未知的预设选项：$value")
    private fun tags(values: List<String>, choices: List<Option>) {
        requireBackup(values.size == values.distinct().size && values.size <= choices.size, "标签重复或过多")
        values.forEach { choice(it, choices) }
    }
    private fun day(value: Long) = requireBackup(value in firstDay..lastDay, "日期需在 1900–2100 年之间")
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun requireBackup(condition: Boolean, message: String) { if (!condition) throw BackupException(message) }
}
