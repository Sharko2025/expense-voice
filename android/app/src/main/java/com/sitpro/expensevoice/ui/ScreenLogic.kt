package com.sitpro.expensevoice.ui

import com.sitpro.expensevoice.api.SummaryDto
import com.sitpro.expensevoice.data.CachedTransaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** What the edit sheet holds; strings are exactly what the user typed. */
data class TransactionEdit(
    val item: String,
    val amount: String,
    val categoryId: Int?,
    val method: String?,
    val merchant: String,
) {
    fun error(): String? = when {
        item.isBlank() -> "Nhập tên món"
        parseAmountInput(amount) == null -> "Số tiền không hợp lệ"
        else -> null
    }

    companion object {
        fun from(tx: CachedTransaction) = TransactionEdit(
            item = tx.item,
            amount = formatAmountInput(tx.amountVnd),
            categoryId = tx.categoryId,
            method = tx.method,
            merchant = tx.merchant.orEmpty(),
        )
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
}

fun canGoForward(day: LocalDate, today: LocalDate) = day < today

fun canGoForward(month: YearMonth, current: YearMonth) = month < current

private val WEEKDAYS = mapOf(
    DayOfWeek.MONDAY to "Thứ Hai", DayOfWeek.TUESDAY to "Thứ Ba", DayOfWeek.WEDNESDAY to "Thứ Tư",
    DayOfWeek.THURSDAY to "Thứ Năm", DayOfWeek.FRIDAY to "Thứ Sáu", DayOfWeek.SATURDAY to "Thứ Bảy",
    DayOfWeek.SUNDAY to "Chủ Nhật",
)

fun dayTitle(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Hôm nay"
    today.minusDays(1) -> "Hôm qua"
    else -> "%s, %02d/%02d".format(WEEKDAYS.getValue(day.dayOfWeek), day.dayOfMonth, day.monthValue)
}

fun monthTitle(month: YearMonth) = "Tháng ${month.monthValue}/${month.year}"

data class CategoryBar(val name: String, val totalVnd: Long, val count: Int, val fraction: Float)

/** Bars are relative to the biggest category so the top one fills the row. */
fun categoryBars(summary: SummaryDto): List<CategoryBar> {
    val max = summary.byCategory.maxOfOrNull { it.totalVnd } ?: 0L
    return summary.byCategory.map {
        CategoryBar(it.category ?: "Khác", it.totalVnd, it.count, if (max > 0) it.totalVnd.toFloat() / max else 0f)
    }
}

fun diffText(summary: SummaryDto): String {
    if (summary.diffVnd == 0L) return "Bằng tháng trước"
    val sign = if (summary.diffVnd > 0) "+" else ""
    val pct = summary.diffPct?.let { " (${formatPercent(it)})" }.orEmpty()
    return "$sign${formatVnd(summary.diffVnd)}$pct so với tháng trước"
}
