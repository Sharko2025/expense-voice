package com.sitpro.expensevoice.data

import com.sitpro.expensevoice.api.ApiJson
import com.sitpro.expensevoice.api.ApiService
import com.sitpro.expensevoice.api.CategoryDto
import com.sitpro.expensevoice.api.SummaryDto
import com.sitpro.expensevoice.api.TransactionDto
import com.sitpro.expensevoice.ui.VN_ZONE
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/** Room is what the screens show; the server is the source of truth that refreshes it. */
class ExpenseRepository(private val api: () -> ApiService, private val db: AppDatabase) {
    private val transactions = db.transactionDao()

    // ---- Today ----

    fun observeDay(day: LocalDate): Flow<List<CachedTransaction>> = transactions.observeDay(day.toString())

    fun observeDayUpdatedAt(day: LocalDate): Flow<Long?> = db.metaDao().observe(dayKey(day)).map { it?.fetchedAtMillis }

    suspend fun refreshDay(day: LocalDate) {
        val rows = api().transactions(day.toString(), day.toString())
        transactions.replaceDay(day.toString(), rows.map { it.toCached() })
        db.metaDao().put(CacheMeta(dayKey(day), System.currentTimeMillis()))
    }

    suspend fun cacheTransactions(rows: List<TransactionDto>) = transactions.upsert(rows.map { it.toCached() })

    suspend fun update(id: String, changes: JsonObject): CachedTransaction {
        val updated = api().patchTransaction(id, changes).toCached()
        transactions.delete(id) // occurred_at may have moved it to another day
        transactions.upsert(listOf(updated))
        return updated
    }

    suspend fun delete(id: String) {
        val resp = api().deleteTransaction(id)
        if (!resp.isSuccessful && resp.code() != 404) throw retrofit2.HttpException(resp)
        transactions.delete(id)
    }

    suspend fun restore(id: String) {
        transactions.upsert(listOf(api().restoreTransaction(id).toCached()))
    }

    suspend fun removeVoiceEntry(entryId: String) = transactions.deleteByVoiceEntry(entryId)

    // ---- Month ----

    fun observeSummary(month: YearMonth): Flow<Pair<SummaryDto, Long>?> =
        db.summaryDao().observe(month.toString()).map { row ->
            row?.let { ApiJson.decodeFromString(SummaryDto.serializer(), it.json) to it.fetchedAtMillis }
        }

    suspend fun refreshSummary(month: YearMonth) {
        val summary = api().summary(month.toString())
        db.summaryDao().upsert(
            CachedSummary(month.toString(), ApiJson.encodeToString(SummaryDto.serializer(), summary), System.currentTimeMillis())
        )
    }

    // ---- Categories ----

    fun observeCategories(): Flow<List<CachedCategory>> = db.categoryDao().observeAll()

    suspend fun refreshCategories(): List<CategoryDto> {
        val list = api().categories()
        db.categoryDao().replaceAll(list.map { CachedCategory(it.id, it.name, it.sortOrder) })
        return list
    }

    private fun dayKey(day: LocalDate) = "day:$day"
}

fun TransactionDto.toCached(): CachedTransaction {
    val instant = OffsetDateTime.parse(occurredAt).toInstant()
    return CachedTransaction(
        id = id,
        voiceEntryId = voiceEntryId,
        occurredAtMillis = instant.toEpochMilli(),
        day = instant.atZone(VN_ZONE).toLocalDate().toString(),
        item = item,
        amountVnd = amountVnd,
        categoryId = categoryId,
        category = category,
        method = method,
        merchant = merchant,
    )
}
