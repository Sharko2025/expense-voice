package com.sitpro.expensevoice.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A recording waiting to be sent. Deleted (with its file) once the server has answered for good. */
@Entity(tableName = "pending_uploads")
data class PendingUpload(
    @PrimaryKey val entryId: String,
    val filePath: String,
    val recordedAt: String, // ISO 8601 with offset, sent as-is
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val blocked: Boolean = false, // true after 401: wait for the user to fix the token
    val occurredOn: String? = null, // yyyy-MM-dd when recorded for an earlier day from the Today screen
)

/** Server transactions cached per Vietnam calendar day for the Today screen. Only non-deleted rows. */
@Entity(tableName = "cached_transactions")
data class CachedTransaction(
    @PrimaryKey val id: String,
    val voiceEntryId: String?,
    val occurredAtMillis: Long,
    val day: String, // yyyy-MM-dd in Asia/Ho_Chi_Minh
    val item: String,
    val amountVnd: Long,
    val categoryId: Int?,
    val category: String?,
    val method: String?,
    val merchant: String?,
)

@Entity(tableName = "cached_summaries")
data class CachedSummary(@PrimaryKey val month: String, val json: String, val fetchedAtMillis: Long)

@Entity(tableName = "cached_categories")
data class CachedCategory(@PrimaryKey val id: Int, val name: String, val sortOrder: Int?)

/** "Last updated at" per cached key, e.g. "day:2026-10-11". */
@Entity(tableName = "cache_meta")
data class CacheMeta(@PrimaryKey val key: String, val fetchedAtMillis: Long)

@Dao
interface PendingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: PendingUpload)

    @Query("SELECT * FROM pending_uploads WHERE entryId = :entryId")
    suspend fun get(entryId: String): PendingUpload?

    @Query("SELECT * FROM pending_uploads ORDER BY createdAtMillis")
    suspend fun all(): List<PendingUpload>

    @Query("SELECT * FROM pending_uploads ORDER BY createdAtMillis")
    fun observeAll(): Flow<List<PendingUpload>>

    @Query("SELECT COUNT(*) FROM pending_uploads")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM pending_uploads WHERE entryId = :entryId")
    suspend fun delete(entryId: String)

    @Query("UPDATE pending_uploads SET attempts = attempts + 1, lastError = :error WHERE entryId = :entryId")
    suspend fun recordFailure(entryId: String, error: String)

    @Query("UPDATE pending_uploads SET blocked = :blocked WHERE entryId = :entryId")
    suspend fun setBlocked(entryId: String, blocked: Boolean)

    @Query("UPDATE pending_uploads SET blocked = 0")
    suspend fun unblockAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM cached_transactions WHERE day = :day ORDER BY occurredAtMillis DESC, id")
    fun observeDay(day: String): Flow<List<CachedTransaction>>

    @Upsert
    suspend fun upsert(rows: List<CachedTransaction>)

    @Query("DELETE FROM cached_transactions WHERE day = :day")
    suspend fun clearDay(day: String)

    @Transaction
    suspend fun replaceDay(day: String, rows: List<CachedTransaction>) {
        clearDay(day)
        upsert(rows)
    }

    /** Days are yyyy-MM-dd, so string comparison is date order. */
    @Query("SELECT * FROM cached_transactions WHERE day BETWEEN :from AND :to ORDER BY occurredAtMillis DESC, id")
    fun observeRange(from: String, to: String): Flow<List<CachedTransaction>>

    @Query("DELETE FROM cached_transactions WHERE day BETWEEN :from AND :to")
    suspend fun clearRange(from: String, to: String)

    @Transaction
    suspend fun replaceRange(from: String, to: String, rows: List<CachedTransaction>) {
        clearRange(from, to)
        upsert(rows)
    }

    @Query("DELETE FROM cached_transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_transactions WHERE voiceEntryId = :entryId")
    suspend fun deleteByVoiceEntry(entryId: String)
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM cached_summaries WHERE month = :month")
    fun observe(month: String): Flow<CachedSummary?>

    @Upsert
    suspend fun upsert(row: CachedSummary)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM cached_categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CachedCategory>>

    @Query("DELETE FROM cached_categories")
    suspend fun clear()

    @Upsert
    suspend fun upsert(rows: List<CachedCategory>)

    @Transaction
    suspend fun replaceAll(rows: List<CachedCategory>) {
        clear()
        upsert(rows)
    }
}

@Dao
interface MetaDao {
    @Query("SELECT * FROM cache_meta WHERE `key` = :key")
    fun observe(key: String): Flow<CacheMeta?>

    @Upsert
    suspend fun put(row: CacheMeta)
}

@Database(
    entities = [PendingUpload::class, CachedTransaction::class, CachedSummary::class, CachedCategory::class, CacheMeta::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)], // adds pending_uploads.occurredOn, keeps the queue
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingDao(): PendingDao
    abstract fun transactionDao(): TransactionDao
    abstract fun summaryDao(): SummaryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun metaDao(): MetaDao
}
