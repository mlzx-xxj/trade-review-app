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
    @Query("SELECT * FROM trades ORDER BY date DESC, id DESC")
    fun observeTrades(): Flow<List<Trade>>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun findTrade(id: Long): Trade?

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

@Database(entities = [Trade::class, DailyReview::class, ManagedTag::class, Instrument::class, AppSettings::class, EntryDraft::class], version = 3, exportSchema = true)
@TypeConverters(TagConverters::class)
abstract class TradeDatabase : RoomDatabase() {
    abstract fun trades(): TradeDao
}
