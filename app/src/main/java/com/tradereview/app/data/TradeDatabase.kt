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
import kotlinx.coroutines.flow.Flow

class TagConverters {
    // Only fixed preset IDs are persisted. A delimiter cannot occur in an ID.
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
}

@Database(entities = [Trade::class, DailyReview::class], version = 1, exportSchema = true)
@TypeConverters(TagConverters::class)
abstract class TradeDatabase : RoomDatabase() {
    abstract fun trades(): TradeDao
}
