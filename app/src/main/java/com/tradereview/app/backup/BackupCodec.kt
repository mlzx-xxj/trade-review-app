package com.tradereview.app.backup

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tradereview.app.data.*
import com.tradereview.app.domain.Money
import com.tradereview.app.domain.FilterCodec
import com.tradereview.app.domain.ProfessionalMath
import com.tradereview.app.domain.ProfessionalJson
import com.tradereview.app.domain.EvidenceJson
import com.tradereview.app.domain.ReviewGoals
import com.tradereview.app.domain.SmartJson
import com.tradereview.app.evidence.EvidenceLimits
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

class BackupException(message: String) : IllegalArgumentException(message)

data class BackupData(val exportedAt: String, val trades: List<Trade>, val dailyReviews: List<DailyReview>,
    val tags: List<ManagedTag> = CatalogSeeds.tags, val instruments: List<Instrument> = CatalogSeeds.instruments,
    val settings: AppSettings = AppSettings(), val drafts: List<EntryDraft> = emptyList(), val sourceSchemaVersion: Int = 7,
    val savedFilters: List<SavedFilter> = emptyList(), val riskRules: List<RiskRules> = emptyList(),
    val contractTemplates: List<ContractTemplate> = emptyList(), val attachments: List<Attachment> = emptyList(),
    val goals: List<ReviewGoal> = emptyList(), val evidenceSettings: EvidenceSettings = EvidenceSettings(),
    val backupScope: String = "structured-data-only", val smartSettings: SmartSettings = SmartSettings(),
    val preflightItems: List<PreflightItem> = emptyList(), val preflightChecks: List<PreflightCheck> = emptyList(), val ocrDrafts: List<OcrDraft> = emptyList(), val ledger:com.tradereview.app.v200.LedgerSnapshot?=null) {
    val planCount get() = dailyReviews.count { it.planSaved }
    val reviewCount get() = dailyReviews.count { it.reviewSaved }
}

/** Schema version is the interchange format version, independent of Room's version. */
object BackupCodec {
    const val SCHEMA_VERSION = 7
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_RECORDS = 10_000
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val pretty = GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create()
    private val firstDay = LocalDate.of(1900, 1, 1).toEpochDay()
    private val lastDay = LocalDate.of(2100, 12, 31).toEpochDay()

    fun strictObject(text: String): JsonObject = JsonReader(StringReader(text)).use { reader ->
        reader.strictness = Strictness.STRICT
        val value = readNode(reader, 0)
        requireBackup(reader.peek() == JsonToken.END_DOCUMENT, "JSON 后有多余内容")
        value.obj()
    }

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
            val rawVersion = root.integer("schemaVersion")
            requireBackup(rawVersion in 1..SCHEMA_VERSION.toLong(), "不支持此备份版本")
            val version = rawVersion.toInt()
            requireBackup(version in 1..SCHEMA_VERSION, "不支持此备份版本，请使用匹配版本的应用")
            if (version == 1) root.fields("format", "schemaVersion", "exportedAt", "counts", "trades", "dailyReviews", "checksumSha256")
            else root.fields(*(listOf("format", "schemaVersion", "exportedAt", "counts", "trades", "dailyReviews", "tags", "instruments", "settings", "drafts", "checksumSha256") + (if (version >= 3) listOf("savedFilters") else emptyList()) + (if (version >= 4) listOf("riskRules", "contractTemplates") else emptyList()) + (if(version>=5)listOf("backupScope","attachments","goals","evidenceSettings")else emptyList()) + (if(version>=6)listOf("smartSettings","preflightItems","preflightChecks","ocrDrafts")else emptyList()) + (if(version>=7)listOf("ledger")else emptyList())).toTypedArray())
            requireBackup(root.string("format") == "trade-review-backup", "不是交易复盘备份文件")
            val data = BackupData(root.string("exportedAt"), root.array("trades").map { parseTrade(it.obj(), version) },
                root.array("dailyReviews").map { parseReview(it.obj()) },
                tags = if (version == 1) CatalogSeeds.tags else root.array("tags").map { parseTag(it.obj()) },
                instruments = if (version == 1) CatalogSeeds.instruments else root.array("instruments").map { parseInstrument(it.obj()) },
                settings = if (version == 1) AppSettings() else parseSettings(root.get("settings").obj(), version),
                drafts = if (version == 1) emptyList() else root.array("drafts").map { parseDraft(it.obj(), version) }, sourceSchemaVersion = version,
                savedFilters = if (version < 3) emptyList() else root.array("savedFilters").map { parseFilter(it.obj()) },
                riskRules = if(version < 4) emptyList() else root.array("riskRules").map(ProfessionalJson::parseRule),
                contractTemplates = if(version < 4) emptyList() else root.array("contractTemplates").map(ProfessionalJson::parseTemplate),
                attachments=if(version<5)emptyList()else root.array("attachments").map(EvidenceJson::attachment),
                goals=if(version<5)emptyList()else root.array("goals").map(EvidenceJson::goal),
                evidenceSettings=if(version<5)EvidenceSettings()else EvidenceJson.settings(root["evidenceSettings"]),
                backupScope=if(version<5)"structured-data-only"else root.string("backupScope"),
                smartSettings=if(version<6)SmartSettings()else SmartJson.settings(root["smartSettings"]),
                preflightItems=if(version<6)emptyList()else root.array("preflightItems").map(SmartJson::item),
                preflightChecks=if(version<6)emptyList()else root.array("preflightChecks").map(SmartJson::check),
                ocrDrafts=if(version<6)emptyList()else root.array("ocrDrafts").map(SmartJson::draft), ledger=if(version<7 || root["ledger"].isJsonNull)null else com.tradereview.app.v200.LedgerJson.parse(root["ledger"]))
            validate(data)
            val counts = root.getAsJsonObject("counts") ?: throw BackupException("缺少数量校验")
            if (version == 1) counts.fields("trades", "dailyReviews", "plans", "reviews")
            else {
                counts.fields(*(listOf("trades", "dailyReviews", "plans", "reviews", "tags", "instruments", "drafts") + (if (version >= 3) listOf("savedFilters") else emptyList()) + (if (version >= 4) listOf("riskRules", "contractTemplates") else emptyList()) + (if(version>=5)listOf("attachments","goals")else emptyList()) + (if(version>=6)listOf("preflightItems","preflightChecks","ocrDrafts")else emptyList()) + (if(version>=7)listOf("ledgerRecords")else emptyList())).toTypedArray())
                requireBackup(counts.integer("tags") == data.tags.size.toLong() && counts.integer("instruments") == data.instruments.size.toLong() && counts.integer("drafts") == data.drafts.size.toLong(), "目录或草稿数量不一致")
                if (version >= 3) requireBackup(counts.integer("savedFilters") == data.savedFilters.size.toLong(), "筛选方案数量不一致")
                if(version >= 4) requireBackup(counts.integer("riskRules") == data.riskRules.size.toLong() && counts.integer("contractTemplates") == data.contractTemplates.size.toLong(), "风险规则或模板数量不一致")
                if(version>=6)requireBackup(counts.integer("preflightItems")==data.preflightItems.size.toLong() && counts.integer("preflightChecks")==data.preflightChecks.size.toLong() && counts.integer("ocrDrafts")==data.ocrDrafts.size.toLong(),"盘前清单或 OCR 草稿数量不一致")
                if(version>=5)requireBackup(counts.integer("attachments")==data.attachments.size.toLong() && counts.integer("goals")==data.goals.size.toLong(),"截图或目标数量不一致")
            }
            requireBackup(counts.integer("trades") == data.trades.size.toLong() &&
                counts.integer("dailyReviews") == data.dailyReviews.size.toLong() &&
                counts.integer("plans") == data.planCount.toLong() && counts.integer("reviews") == data.reviewCount.toLong(), "数量不一致，备份可能不完整")
            if(version>=7)requireBackup(counts.integer("ledgerRecords")==ledgerRecords(data).toLong(),"新账本数量校验不一致")
            val checksum = root.string("checksumSha256")
            val canonical = digest(gson.toJson(payload(data, version)))
            // V0.3 used reflection for catalog/draft objects: ART sorts declared fields,
            // whereas the JVM follows declaration order. Accept only those two legacy
            // layouts, after complete strict validation; schema 3 never uses reflection.
            val legacyAndroid = version == 2 && checksum == digest(gson.toJson(payload(data, version, androidLegacy = true)))
            requireBackup(checksum == canonical || legacyAndroid, "完整性校验失败，文件可能损坏或被修改")
            return data
        } catch (error: BackupException) { throw error }
        catch (_: Exception) { throw BackupException("JSON 格式或字段类型不正确，未恢复任何数据") }
    }

    fun validate(data: BackupData, today: Long = LocalDate.now().toEpochDay()) {
        try { data.ledger?.let(com.tradereview.app.v200.LedgerValidation::snapshot) } catch(e:Exception) { throw BackupException(e.message?:"成交账本无效") }
        requireBackup(data.exportedAt.length <= 40 && runCatching { Instant.parse(data.exportedAt) }.isSuccess, "导出时间无效")
        requireBackup(data.trades.size <= MAX_RECORDS && data.dailyReviews.size <= MAX_RECORDS, "记录数超过 10000 上限")
        requireBackup(data.trades.map { it.id }.toSet().size == data.trades.size, "交易编号重复")
        requireBackup(data.trades.map { it.recordKey }.toSet().size == data.trades.size, "交易稳定标识重复")
        requireBackup(data.dailyReviews.map { it.date }.toSet().size == data.dailyReviews.size, "每日日志日期重复")
        requireBackup(data.tags.size <= MAX_RECORDS && data.instruments.size <= MAX_RECORDS && data.drafts.size <= MAX_RECORDS, "目录或草稿超过上限")
        requireBackup(data.tags.map { it.kind to it.id }.distinct().size == data.tags.size, "标签 ID 重复")
        data.tags.forEach { t ->
            requireBackup(t.kind in TagKind.names && Regex("[A-Za-z0-9_]{1,64}").matches(t.id), "标签类型或 ID 无效")
            name(t.name); requireBackup(t.sortOrder in 0..100000 && t.lastUsed in 0..4133980799999L, "标签排序或最近时间无效")
        }
        CatalogSeeds.tags.forEach { seed -> requireBackup(data.tags.any { it.kind == seed.kind && it.id == seed.id }, "缺少原有稳定标签 ID") }
        requireBackup(data.instruments.map { it.id }.distinct().size == data.instruments.size, "品种 ID 重复")
        requireBackup(data.instruments.map { it.market to it.name }.distinct().size == data.instruments.size, "同一市场品种重名")
        data.instruments.forEach { i ->
            requireBackup(Regex("[A-Za-z0-9_]{1,64}").matches(i.id), "品种 ID 无效")
            choice(i.market, Presets.markets); name(i.name); name(i.code)
            requireBackup(i.sortOrder in 0..100000 && i.lastUsed in 0..4133980799999L, "品种排序或最近时间无效")
        }
        requireBackup(data.settings.id == 1 && data.settings.theme in listOf("system", "light", "dark") && data.settings.pnlPalette in listOf("green_profit", "red_profit"), "应用设置无效")
        choice(data.settings.lastMarket, Presets.markets)
        requireBackup(data.savedFilters.size <= 100 && data.savedFilters.map { it.id }.distinct().size == data.savedFilters.size, "筛选方案重复或超过 100 个上限")
        data.savedFilters.forEach { value ->
            try { validateSavedFilter(value, data.tags) }
            catch (e: IllegalArgumentException) { throw BackupException(e.message ?: "筛选方案无效") }
        }
        requireBackup(data.riskRules.size <= 3 && data.riskRules.map { it.currency }.distinct().size == data.riskRules.size, "风险规则币种重复或过多")
        data.riskRules.forEach(ProfessionalMath::validateRule)
        requireBackup(data.contractTemplates.size <= 500 && data.contractTemplates.map { it.id }.distinct().size == data.contractTemplates.size, "合约模板重复或过多")
        data.contractTemplates.forEach(ProfessionalMath::validateTemplate)
        requireBackup(data.backupScope in listOf("structured-data-only","with-attachment-files"),"备份范围无效")
        requireBackup(data.backupScope=="with-attachment-files" || data.attachments.isEmpty(),"仅数据 JSON 不能包含缺少文件的截图引用")
        EvidenceLimits.settings(data.evidenceSettings)
        requireBackup(data.attachments.size<=EvidenceLimits.MAX_IMAGES && data.attachments.map { it.id }.distinct().size==data.attachments.size,"附件重复或超过 10000 张上限")
        val tradeKeys=data.trades.map { it.recordKey }.toSet()
        data.attachments.forEach { a -> EvidenceLimits.validate(a);requireBackup(a.recordKey in tradeKeys,"截图没有关联交易") }
        requireBackup(data.attachments.groupBy { it.recordKey }.values.all { it.size<=10 },"每笔交易最多 10 张截图")
        requireBackup(data.goals.size<=500 && data.goals.map { it.id }.distinct().size==data.goals.size,"目标重复或超过 500 个上限")
        data.goals.forEach { ReviewGoals.validate(it,data.tags,today) }
        SmartJson.validate(data.smartSettings)
        requireBackup(data.preflightItems.size<=100 && data.preflightItems.map { it.id }.distinct().size==data.preflightItems.size,"盘前项目重复或超限")
        requireBackup(data.preflightChecks.size<=MAX_RECORDS && data.preflightChecks.map { it.date to it.itemKey }.distinct().size==data.preflightChecks.size,"盘前检查重复或超限")
        requireBackup(data.ocrDrafts.size<=100 && data.ocrDrafts.map { it.id }.distinct().size==data.ocrDrafts.size,"OCR 草稿重复或超限")
        data.preflightItems.forEach(SmartJson::validate);data.preflightChecks.forEach(SmartJson::validate);data.ocrDrafts.forEach(SmartJson::validate)
        fun options(kind: String) = data.tags.filter { it.kind == kind }.map { Option(it.id, it.name) }
        data.trades.forEach { trade ->
            requireBackup(trade.id in 1 until Long.MAX_VALUE, "交易编号无效")
            requireBackup(Regex("[0-9a-f]{32}").matches(trade.recordKey), "交易稳定标识无效")
            trade.professional?.let { p ->
                ProfessionalMath.validate(p, trade.market)
                if(p.pnlMode == "auto") requireBackup(ProfessionalMath.netForSave(p, trade.direction) == trade.netPnl, "自动净盈亏与专业数据不一致")
            }
            ProfessionalJson.validateRecord(trade.riskRecord, trade.recordKey)
            day(trade.date)
            requireBackup(trade.date <= today, "交易日期不能晚于今天")
            requireBackup(trade.symbol.isNotBlank() && trade.symbol.length <= 30 && '\u0000' !in trade.symbol, "品种名称无效")
            requireBackup(Money.parse(trade.netPnl)?.toPlainString() == trade.netPnl, "交易金额无效，需使用规范的两位小数文本")
            requireBackup(trade.positionPercent in 1..100, "交易仓位无效")
            requireBackup(trade.note.length <= 300 && '\u0000' !in trade.note, "备注无效")
            choice(trade.market, Presets.markets); choice(trade.direction, Presets.directions)
            choice(trade.currency, Presets.currencies); choice(trade.strategy, options("strategy"))
            choice(trade.emotion, options("emotion")); tags(trade.mistakes, options("mistake"))
            tags(trade.discipline, options("discipline"))
        }
        data.dailyReviews.forEach { review ->
            day(review.date)
            requireBackup(!review.reviewSaved || review.date <= today, "盘后复盘日期不能晚于今天")
            requireBackup(review.maxPosition in 1..100 && review.rating in 1..5, "每日日志仓位或评分无效")
            choice(review.outlook, Presets.outlooks); choice(review.emotion, options("emotion"))
            tags(review.strategies, options("strategy")); tags(review.preparations, options("preparation"))
            tags(review.discipline, options("discipline")); tags(review.mistakes, options("mistake")); tags(review.lessons, options("lesson"))
        }
        requireBackup(data.drafts.map { it.key }.distinct().size == data.drafts.size, "草稿键重复")
        data.drafts.forEach { d ->
            requireBackup(Regex("[0-9a-f]{32}").matches(d.recordKey), "草稿交易标识无效")
            requireBackup((d.key == "new" && d.tradeId == 0L) || (d.key == d.recordKey && data.trades.any { it.id == d.tradeId && it.recordKey == d.recordKey }), "草稿关联不完整")
            requireBackup(d.key != "new" || data.trades.none { it.recordKey == d.recordKey }, "新增草稿标识与已保存交易重复")
            d.professional?.let(ProfessionalJson::validateDraft)
            day(d.date); requireBackup(d.date <= today, "草稿日期不能晚于今天")
            name(d.symbol); requireBackup(d.note.length <= 300 && '\u0000' !in d.note && d.amount.length <= 32 && '\u0000' !in d.amount, "草稿文字无效")
            requireBackup(d.outcome in listOf("", "gain", "loss", "flat") && d.positionPercent in 1..100, "草稿选项无效")
            choice(d.market, Presets.markets); choice(d.direction, Presets.directions); choice(d.currency, Presets.currencies)
            choice(d.strategy, options("strategy")); choice(d.emotion, options("emotion"))
            tags(d.mistakes, options("mistake")); tags(d.discipline, options("discipline"))
        }
    }

    // Fixed field order makes the checksum independent of whitespace or incoming object key order.
    private fun payload(data: BackupData, version: Int = SCHEMA_VERSION, androidLegacy: Boolean = false) = JsonObject().apply {
        addProperty("format", "trade-review-backup"); addProperty("schemaVersion", version)
        addProperty("exportedAt", data.exportedAt)
        if(version>=5)addProperty("backupScope",data.backupScope)
        add("counts", JsonObject().apply {
            addProperty("trades", data.trades.size); addProperty("dailyReviews", data.dailyReviews.size)
            addProperty("plans", data.planCount); addProperty("reviews", data.reviewCount)
            if (version >= 2) { addProperty("tags", data.tags.size); addProperty("instruments", data.instruments.size); addProperty("drafts", data.drafts.size) }
            if (version >= 3) addProperty("savedFilters", data.savedFilters.size)
            if(version >= 4) { addProperty("riskRules", data.riskRules.size); addProperty("contractTemplates", data.contractTemplates.size) }
            if(version>=5) { addProperty("attachments",data.attachments.size);addProperty("goals",data.goals.size) }
            if(version>=6) { addProperty("preflightItems",data.preflightItems.size);addProperty("preflightChecks",data.preflightChecks.size);addProperty("ocrDrafts",data.ocrDrafts.size) }
        })
        if(version>=7){rootLedger(this,data);getAsJsonObject("counts").addProperty("ledgerRecords",ledgerRecords(data))}
        add("trades", JsonArray().apply { data.trades.forEach { add(tradeJson(it, version)) } })
        add("dailyReviews", JsonArray().apply { data.dailyReviews.forEach { add(reviewJson(it)) } })
        if (version >= 2) {
            fun legacy(o: JsonObject): JsonObject = if (version == 2 && androidLegacy) JsonObject().apply { o.keySet().sorted().forEach { add(it, o[it]) } } else o
            add("tags", JsonArray().apply { data.tags.forEach { add(legacy(tagObject(it))) } })
            add("instruments", JsonArray().apply { data.instruments.forEach { add(legacy(instrumentObject(it))) } })
            // Preserve the exact V0.3 canonical field set for old-format checksum verification.
            add("settings", JsonObject().apply {
                addProperty("id", data.settings.id); addProperty("lastMarket", data.settings.lastMarket); addProperty("theme", data.settings.theme)
                if (version >= 3) addProperty("pnlPalette", data.settings.pnlPalette)
            }); add("drafts", JsonArray().apply { data.drafts.forEach { add(legacy(draftObject(it, version))) } })
            if (version >= 3) add("savedFilters", JsonArray().apply { data.savedFilters.forEach { f -> add(JsonObject().apply {
                addProperty("id", f.id); addProperty("name", f.name); addProperty("currency", f.currency); add("criteria", FilterCodec.json(f.criteria))
            }) } })
            if(version >= 4) {
                add("riskRules", JsonArray().apply { data.riskRules.forEach { add(ProfessionalJson.rule(it)) } })
                add("contractTemplates", JsonArray().apply { data.contractTemplates.forEach { add(ProfessionalJson.template(it)) } })
            }
            if(version>=5) {
                add("attachments",JsonArray().apply { data.attachments.forEach { add(EvidenceJson.json(it)) } })
                add("goals",JsonArray().apply { data.goals.forEach { add(EvidenceJson.json(it)) } })
                add("evidenceSettings",EvidenceJson.json(data.evidenceSettings))
            }
            if(version>=6) {
                add("smartSettings",SmartJson.json(data.smartSettings))
                add("preflightItems",JsonArray().apply { data.preflightItems.forEach { add(SmartJson.json(it)) } })
                add("preflightChecks",JsonArray().apply { data.preflightChecks.forEach { add(SmartJson.json(it)) } })
                add("ocrDrafts",JsonArray().apply { data.ocrDrafts.forEach { add(SmartJson.json(it)) } })
            }
        }
    }
    private fun rootLedger(root:JsonObject,data:BackupData){root.add("ledger",data.ledger?.let(com.tradereview.app.v200.LedgerJson::element)?:JsonNull.INSTANCE)}
    private fun ledgerRecords(data:BackupData):Int=data.ledger?.let{it.accounts.size+it.strategies.size+it.risks.size+it.rules.size+it.plans.size+it.executions.size+it.cycles.size+it.lots.size+it.matches.size+it.reviews.size+it.cashFlows.size+it.audits.size}?:0
    private fun tagObject(t: ManagedTag) = JsonObject().apply {
        addProperty("kind", t.kind); addProperty("id", t.id); addProperty("name", t.name); addProperty("sortOrder", t.sortOrder)
        addProperty("favorite", t.favorite); addProperty("hidden", t.hidden); addProperty("lastUsed", t.lastUsed)
    }
    private fun instrumentObject(i: Instrument) = JsonObject().apply {
        addProperty("id", i.id); addProperty("market", i.market); addProperty("name", i.name); addProperty("code", i.code)
        addProperty("sortOrder", i.sortOrder); addProperty("favorite", i.favorite); addProperty("lastUsed", i.lastUsed)
    }
    private fun draftObject(d: EntryDraft, version: Int) = JsonObject().apply {
        addProperty("key", d.key); addProperty("recordKey", d.recordKey); addProperty("tradeId", d.tradeId); addProperty("date", d.date)
        addProperty("market", d.market); addProperty("symbol", d.symbol); addProperty("direction", d.direction); addProperty("currency", d.currency)
        addProperty("amount", d.amount); addProperty("outcome", d.outcome); addProperty("positionPercent", d.positionPercent)
        addProperty("strategy", d.strategy); addProperty("emotion", d.emotion); add("mistakes", tagJson(d.mistakes)); add("discipline", tagJson(d.discipline))
        addProperty("reviewed", d.reviewed); addProperty("note", d.note); addProperty("details", d.details)
        if(version >= 4) add("professional", ProfessionalJson.draft(d.professional))
    }
    private fun tradeJson(t: Trade, version: Int) = JsonObject().apply {
        addProperty("id", t.id); addProperty("recordKey", t.recordKey); addProperty("date", t.date)
        addProperty("market", t.market); addProperty("symbol", t.symbol); addProperty("direction", t.direction)
        addProperty("currency", t.currency); addProperty("netPnl", t.netPnl); addProperty("positionPercent", t.positionPercent)
        addProperty("strategy", t.strategy); addProperty("emotion", t.emotion)
        add("mistakes", tagJson(t.mistakes)); add("discipline", tagJson(t.discipline)); addProperty("reviewed", t.reviewed)
        if (version >= 2) addProperty("note", t.note)
        if(version >= 4) { add("professional", ProfessionalJson.professional(t.professional)); add("riskRecord", ProfessionalJson.record(t.riskRecord)) }
    }
    private fun reviewJson(r: DailyReview) = JsonObject().apply {
        addProperty("date", r.date); addProperty("outlook", r.outlook); add("strategies", tagJson(r.strategies))
        add("preparations", tagJson(r.preparations)); addProperty("maxPosition", r.maxPosition); addProperty("planSaved", r.planSaved)
        addProperty("emotion", r.emotion); add("discipline", tagJson(r.discipline)); add("mistakes", tagJson(r.mistakes))
        add("lessons", tagJson(r.lessons)); addProperty("rating", r.rating); addProperty("reviewSaved", r.reviewSaved)
    }
    private fun tagJson(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }
    private fun parseTrade(o: JsonObject, version: Int): Trade {
        val fields = listOf("id", "recordKey", "date", "market", "symbol", "direction", "currency", "netPnl", "positionPercent", "strategy", "emotion", "mistakes", "discipline", "reviewed")
        o.fields(*(fields + (if (version >= 2) listOf("note") else emptyList()) + (if(version >= 4) listOf("professional", "riskRecord") else emptyList())).toTypedArray())
        return Trade(id = o.integer("id"), recordKey = o.string("recordKey"), date = o.integer("date"), market = o.string("market"),
            symbol = o.string("symbol"), direction = o.string("direction"), currency = o.string("currency"), netPnl = o.string("netPnl"),
            positionPercent = o.smallInt("positionPercent"), strategy = o.string("strategy"), emotion = o.string("emotion"),
            mistakes = o.strings("mistakes"), discipline = o.strings("discipline"), reviewed = o.boolean("reviewed"), note = if (version >= 2) o.string("note") else "", professional = if(version >= 4) ProfessionalJson.parseProfessional(o["professional"]) else null,
            riskRecord = if(version >= 4) ProfessionalJson.parseRecord(o["riskRecord"]) else null)
    }
    private fun parseReview(o: JsonObject): DailyReview {
        o.fields("date", "outlook", "strategies", "preparations", "maxPosition", "planSaved", "emotion", "discipline", "mistakes", "lessons", "rating", "reviewSaved")
        return DailyReview(date = o.integer("date"), outlook = o.string("outlook"), strategies = o.strings("strategies"), preparations = o.strings("preparations"),
            maxPosition = o.smallInt("maxPosition"), planSaved = o.boolean("planSaved"), emotion = o.string("emotion"), discipline = o.strings("discipline"),
            mistakes = o.strings("mistakes"), lessons = o.strings("lessons"), rating = o.smallInt("rating"), reviewSaved = o.boolean("reviewSaved"))
    }
    private fun parseTag(o: JsonObject): ManagedTag {
        o.fields("kind", "id", "name", "sortOrder", "favorite", "hidden", "lastUsed")
        return ManagedTag(o.string("kind"), o.string("id"), o.string("name"), o.smallInt("sortOrder"), o.boolean("favorite"), o.boolean("hidden"), o.integer("lastUsed"))
    }
    private fun parseInstrument(o: JsonObject): Instrument {
        o.fields("id", "market", "name", "code", "sortOrder", "favorite", "lastUsed")
        return Instrument(o.string("id"), o.string("market"), o.string("name"), o.string("code"), o.smallInt("sortOrder"), o.boolean("favorite"), o.integer("lastUsed"))
    }
    private fun parseSettings(o: JsonObject, version: Int): AppSettings {
        o.fields(*(listOf("id", "lastMarket", "theme") + if (version >= 3) listOf("pnlPalette") else emptyList()).toTypedArray())
        return AppSettings(o.smallInt("id"), o.string("lastMarket"), o.string("theme"), if (version >= 3) o.string("pnlPalette") else "green_profit")
    }
    private fun parseFilter(o: JsonObject): SavedFilter {
        o.fields("id", "name", "currency", "criteria")
        return SavedFilter(o.string("id"), o.string("name"), o.string("currency"), FilterCodec.parse(o["criteria"].obj()))
    }
    private fun parseDraft(o: JsonObject, version: Int): EntryDraft {
        o.fields(*(listOf("key", "recordKey", "tradeId", "date", "market", "symbol", "direction", "currency", "amount", "outcome", "positionPercent", "strategy", "emotion", "mistakes", "discipline", "reviewed", "note", "details") + if(version >= 4) listOf("professional") else emptyList()).toTypedArray())
        return EntryDraft(key = o.string("key"), recordKey = o.string("recordKey"), tradeId = o.integer("tradeId"), date = o.integer("date"), market = o.string("market"), symbol = o.string("symbol"),
            direction = o.string("direction"), currency = o.string("currency"), amount = o.string("amount"), outcome = o.string("outcome"), positionPercent = o.smallInt("positionPercent"),
            strategy = o.string("strategy"), emotion = o.string("emotion"), mistakes = o.strings("mistakes"), discipline = o.strings("discipline"), reviewed = o.boolean("reviewed"), note = o.string("note"), details = o.boolean("details"), professional = if(version >= 4) ProfessionalJson.parseDraft(o["professional"]) else null)
    }
    private fun name(value: String) = requireBackup(value.isNotBlank() && value.length <= 30 && '\u0000' !in value, "名称或代码无效")
    private fun readNode(r: JsonReader, depth: Int): JsonElement {
        requireBackup(depth <= 12, "JSON 嵌套过深")
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
            JsonToken.NULL -> { r.nextNull(); JsonNull.INSTANCE }
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
