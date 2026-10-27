package com.sitpro.expensevoice.ui

import androidx.annotation.StringRes
import com.sitpro.expensevoice.R
import com.sitpro.expensevoice.api.SummaryDto
import com.sitpro.expensevoice.data.CachedTransaction
import com.sitpro.expensevoice.data.PendingUpload
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun vnInstant(date: LocalDate, time: LocalTime): Instant = date.atTime(time).atZone(VN_ZONE).toInstant()

/** What the edit sheet holds; strings are exactly what the user typed. Date and time are Vietnam time. */
data class TransactionEdit(
    val item: String,
    val amount: String,
    val categoryId: Int?,
    val method: String?,
    val merchant: String,
    val date: LocalDate,
    val time: LocalTime,
) {
    val occurredAt: Instant get() = vnInstant(date, time)

    @StringRes
    fun error(now: Instant = Instant.now()): Int? = when {
        item.isBlank() -> R.string.error_item_required
        parseAmountInput(amount) == null -> R.string.error_amount_invalid
        occurredAt > now -> R.string.error_future
        else -> null
    }

    companion object {
        fun from(tx: CachedTransaction): TransactionEdit {
            val local = Instant.ofEpochMilli(tx.occurredAtMillis).atZone(VN_ZONE)
            return TransactionEdit(
                item = tx.item,
                amount = formatAmountInput(tx.amountVnd),
                categoryId = tx.categoryId,
                method = tx.method,
                merchant = tx.merchant.orEmpty(),
                date = local.toLocalDate(),
                time = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
            )
        }
    }
}

/** PATCH body with only the fields that changed; null clears method/merchant (call only when error() == null). */
fun buildPatch(original: CachedTransaction, edit: TransactionEdit): JsonObject = buildJsonObject {
    val item = edit.item.trim()
    if (item != original.item) put("item", JsonPrimitive(item))
    val amount = parseAmountInput(edit.amount)
    if (amount != null && amount != original.amountVnd) put("amount_vnd", JsonPrimitive(amount))
    if (edit.categoryId != original.categoryId) put("category_id", edit.categoryId?.let(::JsonPrimitive) ?: JsonNull)
    if (edit.method != original.method) put("method", edit.method?.let(::JsonPrimitive) ?: JsonNull)
    val merchant = edit.merchant.trim().ifEmpty { null }
    if (merchant != original.merchant) put("merchant", merchant?.let(::JsonPrimitive) ?: JsonNull)
    // The sheet edits to the minute, so seconds of the original don't count as a change.
    val originalMinute = Instant.ofEpochMilli(original.occurredAtMillis).truncatedTo(ChronoUnit.MINUTES)
    if (edit.occurredAt != originalMinute) {
        put("occurred_at", JsonPrimitive(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(edit.occurredAt.atZone(VN_ZONE))))
    }
}

fun canGoForward(day: LocalDate, today: LocalDate) = day < today

fun canGoForward(month: YearMonth, current: YearMonth) = month < current

/** `name` is the server (Vietnamese) category name; translate with Texts.category when showing it. */
data class CategoryBar(val categoryId: Int?, val name: String?, val totalVnd: Long, val count: Int, val fraction: Float)

/** Bars are relative to the biggest category so the top one fills the row. */
fun categoryBars(summary: SummaryDto): List<CategoryBar> {
    val max = summary.byCategory.maxOfOrNull { it.totalVnd } ?: 0L
    return summary.byCategory.map {
        CategoryBar(it.categoryId, it.category, it.totalVnd, it.count, if (max > 0) it.totalVnd.toFloat() / max else 0f)
    }
}

/** A month's transactions per category id (null = no category), each list in the order given (newest first). */
fun transactionsByCategory(list: List<CachedTransaction>): Map<Int?, List<CachedTransaction>> = list.groupBy { it.categoryId }

/** The day the in-app mic records for: the viewed day if it's an earlier one on the Today tab, else null (= now). */
fun recordDay(viewed: LocalDate, today: LocalDate, onTodayTab: Boolean): LocalDate? =
    viewed.takeIf { onTodayTab && it < today }

/** The in-app strip for recordings not processed yet. */
data class PendingBanner(val kind: Kind, val count: Int) {
    enum class Kind { PROCESSING, OFFLINE, BLOCKED }
}

/** Rows that can still be sent decide the strip; blocked rows (401) only show once nothing else is waiting. */
fun pendingBanner(rows: List<PendingUpload>, online: Boolean): PendingBanner? {
    val sendable = rows.count { !it.blocked }
    return when {
        sendable > 0 -> PendingBanner(if (online) PendingBanner.Kind.PROCESSING else PendingBanner.Kind.OFFLINE, sendable)
        rows.isNotEmpty() -> PendingBanner(PendingBanner.Kind.BLOCKED, rows.size)
        else -> null
    }
}
