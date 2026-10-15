package com.tradereview.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class TagConverters {
    // Stable preset/custom IDs are validated to exclude the delimiter.
    @TypeConverter fun encode(tags: List<String>): String = tags.distinct().joinToString("|")
    @TypeConverter fun decode(value: String): List<String> = value.split('|').filter { it.isNotEmpty() }
}

@Dao
interface TradeDao {
    @Query("SELECT EXISTS(SELECT 1 FROM trades LIMIT 1) OR EXISTS(SELECT 1 FROM daily_reviews LIMIT 1) OR EXISTS(SELECT 1 FROM review_goals LIMIT 1) OR EXISTS(SELECT 1 FROM ocr_drafts LIMIT 1) OR EXISTS(SELECT 1 FROM entry_drafts LIMIT 1) OR EXISTS(SELECT 1 FROM preflight_checks LIMIT 1)")
    suspend fun hasUserRecords():Boolean
    @Query("SELECT * FROM trades ORDER BY date DESC, id DESC")
    fun observeTrades(): Flow<List<Trade>>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun findTrade(id: Long): Trade?

    @Query("SELECT * FROM trades WHERE recordKey = :key") suspend fun findTradeByKey(key: String): Trade?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trade: Trade): Long

    @Upsert suspend fun save(trade: Trade)
    @Delete suspend fun delete(trade: Trade)

    @Query("SELECT * FROM daily_reviews ORDER BY date DESC")
    fun observeReviews(): Flow<List<DailyReview>>

    @Query("SELECT * FROM daily_reviews WHERE date = :date")
    suspend fun findReview(date: Long): DailyReview?

    @Upsert suspend fun saveReview(review: DailyReview)

    @Query("SELECT * FROM trades ORDER BY date DESC, id DESC")
    suspend fun allTrades(): List<Trade>
    @Query("SELECT * FROM daily_reviews ORDER BY date DESC")
    suspend fun allReviews(): List<DailyReview>
    @Query("DELETE FROM trades") suspend fun clearTrades()
    @Query("DELETE FROM daily_reviews") suspend fun clearReviews()
    @Query("SELECT * FROM tags ORDER BY kind, sortOrder, id") fun observeTags(): Flow<List<ManagedTag>>
    @Query("SELECT * FROM tags ORDER BY kind, sortOrder, id") suspend fun allTags(): List<ManagedTag>
    @Upsert suspend fun saveTag(tag: ManagedTag)
    @Query("SELECT * FROM instruments ORDER BY market, sortOrder, id") fun observeInstruments(): Flow<List<Instrument>>
    @Query("SELECT * FROM instruments ORDER BY market, sortOrder, id") suspend fun allInstruments(): List<Instrument>
    @Upsert suspend fun saveInstrument(instrument: Instrument)
    @Query("SELECT * FROM app_settings WHERE id = 1") fun observeSettings(): Flow<AppSettings?>
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun settings(): AppSettings?
    @Upsert suspend fun saveSettings(settings: AppSettings)
    @Query("SELECT * FROM entry_drafts ORDER BY `key`") fun observeDrafts(): Flow<List<EntryDraft>>
    @Query("SELECT * FROM entry_drafts ORDER BY `key`") suspend fun allDrafts(): List<EntryDraft>
    @Upsert suspend fun saveDraft(draft: EntryDraft)
    @Query("DELETE FROM entry_drafts WHERE `key` = :key") suspend fun deleteDraft(key: String)
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM instruments") suspend fun clearInstruments()
    @Query("DELETE FROM app_settings") suspend fun clearSettings()
    @Query("DELETE FROM entry_drafts") suspend fun clearDrafts()
    @Query("SELECT * FROM saved_filters ORDER BY name, id") fun observeFilters(): Flow<List<SavedFilter>>
    @Query("SELECT * FROM saved_filters ORDER BY name, id") suspend fun allFilters(): List<SavedFilter>
    @Upsert suspend fun saveFilter(value: SavedFilter)
    @Delete suspend fun deleteFilter(value: SavedFilter)
    @Query("DELETE FROM saved_filters") suspend fun clearFilters()
    @Query("SELECT * FROM risk_rules ORDER BY currency") fun observeRiskRules(): Flow<List<RiskRules>>
    @Query("SELECT * FROM risk_rules ORDER BY currency") suspend fun allRiskRules(): List<RiskRules>
    @Upsert suspend fun saveRiskRules(value: RiskRules)
    @Query("DELETE FROM risk_rules") suspend fun clearRiskRules()
    @Query("SELECT * FROM contract_templates ORDER BY market, name, id") fun observeTemplates(): Flow<List<ContractTemplate>>
    @Query("SELECT * FROM contract_templates ORDER BY market, name, id") suspend fun allTemplates(): List<ContractTemplate>
    @Upsert suspend fun saveTemplate(value: ContractTemplate)
    @Delete suspend fun deleteTemplate(value: ContractTemplate)
    @Query("DELETE FROM contract_templates") suspend fun clearTemplates()
    @Query("SELECT * FROM attachments ORDER BY recordKey, sortOrder, createdAt, id") fun observeAttachments(): Flow<List<Attachment>>
    @Query("SELECT * FROM attachments ORDER BY recordKey, sortOrder, createdAt, id") suspend fun allAttachments(): List<Attachment>
    @Query("SELECT * FROM attachments WHERE recordKey=:key ORDER BY sortOrder,createdAt,id") suspend fun attachmentsForTrade(key:String):List<Attachment>
    @Query("SELECT count(*) FROM attachments") suspend fun attachmentCount():Int
    @Query("SELECT count(*) FROM attachments WHERE recordKey=:key") suspend fun attachmentCountForTrade(key:String):Int
    @Query("SELECT * FROM attachments WHERE id=:id") suspend fun findAttachment(id:String):Attachment?
    @Upsert suspend fun saveAttachment(value: Attachment)
    @Delete suspend fun deleteAttachment(value: Attachment)
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
    @Query("SELECT * FROM review_goals ORDER BY startDate DESC, id") fun observeGoals(): Flow<List<ReviewGoal>>
    @Query("SELECT * FROM review_goals ORDER BY startDate DESC, id") suspend fun allGoals(): List<ReviewGoal>
    @Upsert suspend fun saveGoal(value: ReviewGoal)
    @Query("DELETE FROM review_goals") suspend fun clearGoals()
    @Query("SELECT * FROM evidence_settings WHERE id=1") fun observeEvidenceSettings(): Flow<EvidenceSettings?>
    @Query("SELECT * FROM evidence_settings WHERE id=1") suspend fun evidenceSettings(): EvidenceSettings?
    @Upsert suspend fun saveEvidenceSettings(value: EvidenceSettings)
    @Query("DELETE FROM evidence_settings") suspend fun clearEvidenceSettings()
    @Query("SELECT * FROM smart_settings WHERE id=1") fun observeSmartSettings(): Flow<SmartSettings?>
    @Query("SELECT * FROM smart_settings WHERE id=1") suspend fun smartSettings(): SmartSettings?
    @Upsert suspend fun saveSmartSettings(value: SmartSettings)
    @Query("DELETE FROM smart_settings") suspend fun clearSmartSettings()
    @Query("SELECT * FROM preflight_items ORDER BY id") fun observePreflightItems(): Flow<List<PreflightItem>>
    @Query("SELECT * FROM preflight_items ORDER BY id") suspend fun allPreflightItems(): List<PreflightItem>
    @Upsert suspend fun savePreflightItem(value: PreflightItem)
    @Query("DELETE FROM preflight_items") suspend fun clearPreflightItems()
    @Query("SELECT * FROM preflight_checks ORDER BY date, itemKey") fun observePreflightChecks(): Flow<List<PreflightCheck>>
    @Query("SELECT * FROM preflight_checks ORDER BY date, itemKey") suspend fun allPreflightChecks(): List<PreflightCheck>
    @Upsert suspend fun savePreflightCheck(value: PreflightCheck)
    @Query("DELETE FROM preflight_checks") suspend fun clearPreflightChecks()
    @Query("SELECT * FROM ocr_drafts ORDER BY createdDay DESC, id") fun observeOcrDrafts(): Flow<List<OcrDraft>>
    @Query("SELECT * FROM ocr_drafts ORDER BY createdDay DESC, id") suspend fun allOcrDrafts(): List<OcrDraft>
    @Upsert suspend fun saveOcrDraft(value: OcrDraft)
    @Delete suspend fun deleteOcrDraft(value: OcrDraft)
    @Query("DELETE FROM ocr_drafts") suspend fun clearOcrDrafts()
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReview(review: DailyReview)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trades ADD COLUMN recordKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE trades SET recordKey = lower(hex(randomblob(16)))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_trades_recordKey ON trades(recordKey)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trades ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS tags (kind TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, favorite INTEGER NOT NULL, hidden INTEGER NOT NULL, lastUsed INTEGER NOT NULL, PRIMARY KEY(kind,id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS instruments (id TEXT NOT NULL PRIMARY KEY, market TEXT NOT NULL, name TEXT NOT NULL, code TEXT NOT NULL, sortOrder INTEGER NOT NULL, favorite INTEGER NOT NULL, lastUsed INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (id INTEGER NOT NULL PRIMARY KEY, lastMarket TEXT NOT NULL, theme TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS entry_drafts (`key` TEXT NOT NULL PRIMARY KEY, recordKey TEXT NOT NULL, tradeId INTEGER NOT NULL, date INTEGER NOT NULL, market TEXT NOT NULL, symbol TEXT NOT NULL, direction TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, outcome TEXT NOT NULL, positionPercent INTEGER NOT NULL, strategy TEXT NOT NULL, emotion TEXT NOT NULL, mistakes TEXT NOT NULL, discipline TEXT NOT NULL, reviewed INTEGER NOT NULL, note TEXT NOT NULL, details INTEGER NOT NULL)")
        CatalogSeeds.insert(db)
        // Existing symbols stay verbatim. Add missing custom symbols to the picker without rewriting trades.
        db.execSQL("INSERT OR IGNORE INTO instruments(id,market,name,code,sortOrder,favorite,lastUsed) SELECT 'legacy_' || lower(hex(randomblob(16))), market, symbol, symbol, 1000, 0, 0 FROM trades t WHERE NOT EXISTS(SELECT 1 FROM instruments i WHERE i.market=t.market AND i.name=t.symbol) GROUP BY market,symbol")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN pnlPalette TEXT NOT NULL DEFAULT 'green_profit'")
        db.execSQL("CREATE TABLE IF NOT EXISTS saved_filters (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, currency TEXT NOT NULL, criteria TEXT NOT NULL)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trades ADD COLUMN professional TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE trades ADD COLUMN riskRecord TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE entry_drafts ADD COLUMN professional TEXT DEFAULT NULL")
        db.execSQL("CREATE TABLE IF NOT EXISTS risk_rules (currency TEXT NOT NULL PRIMARY KEY, accountEquity TEXT, maxRiskPercent TEXT, maxPositionPercent TEXT, lossStreakThreshold INTEGER, dailyLossLimit TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS contract_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, market TEXT NOT NULL, symbol TEXT NOT NULL, quantityUnit TEXT NOT NULL, multiplier TEXT, minTick TEXT, note TEXT NOT NULL, userVerified INTEGER NOT NULL)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id TEXT NOT NULL PRIMARY KEY, recordKey TEXT NOT NULL, kind TEXT NOT NULL, caption TEXT NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, createdAt INTEGER NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY(recordKey) REFERENCES trades(recordKey) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_recordKey ON attachments(recordKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS review_goals (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, kind TEXT NOT NULL, period TEXT NOT NULL, startDate INTEGER NOT NULL, threshold TEXT, mistakeId TEXT, archivedDay INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS evidence_settings (id INTEGER NOT NULL PRIMARY KEY, maxInputMiB INTEGER NOT NULL)")
    }
}

val MIGRATION_6_7 = object : Migration(6,7) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS smart_settings (id INTEGER NOT NULL PRIMARY KEY, enabledCategories TEXT NOT NULL, minimumSamples INTEGER NOT NULL, lookbackDays INTEGER NOT NULL, automaticReminders INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS preflight_items (id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, archived INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS preflight_checks (date INTEGER NOT NULL, itemKey TEXT NOT NULL, label TEXT NOT NULL, checked INTEGER NOT NULL, sourceType TEXT NOT NULL, sourceRefs TEXT NOT NULL, PRIMARY KEY(date,itemKey))")
        db.execSQL("CREATE TABLE IF NOT EXISTS ocr_drafts (id TEXT NOT NULL PRIMARY KEY, fields TEXT NOT NULL, blocked INTEGER NOT NULL, closedEvidence INTEGER NOT NULL, createdDay INTEGER NOT NULL)")
    }
}
@Database(entities = [Trade::class, DailyReview::class, ManagedTag::class, Instrument::class, AppSettings::class, EntryDraft::class, SavedFilter::class, RiskRules::class, ContractTemplate::class, Attachment::class, ReviewGoal::class, EvidenceSettings::class, SmartSettings::class, PreflightItem::class, PreflightCheck::class, OcrDraft::class, com.tradereview.app.v200.LedgerAccount::class, com.tradereview.app.v200.LedgerStrategy::class, com.tradereview.app.v200.MarketRisk::class, com.tradereview.app.v200.LedgerContractRule::class, com.tradereview.app.v200.LedgerPlan::class, com.tradereview.app.v200.LedgerExecution::class, com.tradereview.app.v200.LedgerCycle::class, com.tradereview.app.v200.LedgerLot::class, com.tradereview.app.v200.LedgerMatch::class, com.tradereview.app.v200.LedgerReview::class, com.tradereview.app.v200.LedgerCashFlow::class, com.tradereview.app.v200.ExecutionAudit::class], version = 9, exportSchema = true)
@TypeConverters(TagConverters::class, FilterConverters::class, ProfessionalConverters::class)
abstract class TradeDatabase : RoomDatabase() {
    abstract fun trades(): TradeDao
    abstract fun ledger(): com.tradereview.app.v200.LedgerDao
}
