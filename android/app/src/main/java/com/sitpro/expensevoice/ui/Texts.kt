package com.sitpro.expensevoice.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.sitpro.expensevoice.R
import com.sitpro.expensevoice.api.SummaryDto
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Server category names (Vietnamese, from the categories table) → display strings. */
private val CATEGORY_NAMES: Map<String, Int> = mapOf(
    "Ăn sáng" to R.string.cat_breakfast,
    "Ăn trưa" to R.string.cat_lunch,
    "Ăn tối" to R.string.cat_dinner,
    "Cafe/Đồ uống" to R.string.cat_coffee,
    "Gửi xe" to R.string.cat_parking,
    "Xăng/Di chuyển" to R.string.cat_transport,
    "Đi chợ/Siêu thị" to R.string.cat_groceries,
    "Mua sắm" to R.string.cat_shopping,
    "Hóa đơn" to R.string.cat_bills,
    "Giải trí" to R.string.cat_entertainment,
    "Sức khỏe" to R.string.cat_health,
    "Khác" to R.string.cat_other,
)

/** All user-visible text that depends on the app language (English default, Vietnamese in values-vi). */
class Texts(private val res: Resources) {
    private val locale = res.configuration.locales[0]
    private fun pattern(@StringRes id: Int) = DateTimeFormatter.ofPattern(res.getString(id), locale)

    fun get(@StringRes id: Int, vararg args: Any): String = res.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int): String = res.getQuantityString(id, count, count)

    fun method(method: String?): String = when (method) {
        "cash" -> res.getString(R.string.method_cash)
        "card" -> res.getString(R.string.method_card)
        "transfer" -> res.getString(R.string.method_transfer)
        else -> "?"
    }

    /** Unknown names (a category added on the server later) are shown as-is. */
    fun category(name: String?): String? = name?.let { CATEGORY_NAMES[it]?.let(res::getString) ?: it }

    /** "Bánh mì · 20.000đ · Ăn sáng · Tiền mặt" — notification line. */
    fun line(item: String, amountVnd: Long, category: String?, method: String?): String =
        listOfNotNull(item.replaceFirstChar { it.uppercase() }, formatVnd(amountVnd), category(category), method(method))
            .joinToString(" · ")

    fun dayTitle(day: LocalDate, today: LocalDate): String = when (day) {
        today -> res.getString(R.string.tab_today)
        today.minusDays(1) -> res.getString(R.string.yesterday)
        else -> fullDate(day)
    }

    /** "Thứ Bảy, 25/10" — the day name always, never "Hôm qua". */
    fun fullDate(day: LocalDate): String = pattern(R.string.pattern_day_title).format(day).replaceFirstChar { it.uppercase() }

    fun shortDate(day: LocalDate): String = pattern(R.string.pattern_short_date).format(day)

    fun monthTitle(month: YearMonth): String =
        pattern(R.string.pattern_month_title).format(month.atDay(1)).replaceFirstChar { it.uppercase() }

    fun updatedAt(millis: Long): String =
        res.getString(R.string.updated_at, pattern(R.string.pattern_updated).withZone(VN_ZONE).format(Instant.ofEpochMilli(millis)))

    fun transactionCount(n: Int): String = res.getQuantityString(R.plurals.transaction_count, n, n)

    fun percent(pct: Double): String = formatPercent(pct, res.getString(R.string.decimal_separator).first())

    fun diff(summary: SummaryDto): String {
        if (summary.diffVnd == 0L) return res.getString(R.string.diff_same)
        val sign = if (summary.diffVnd > 0) "+" else ""
        val pct = summary.diffPct?.let { " (${percent(it)})" }.orEmpty()
        return res.getString(R.string.diff_vs_last, "$sign${formatVnd(summary.diffVnd)}$pct")
    }
}

/** Recomputed when the app language changes. */
@Composable
fun rememberTexts(): Texts {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { Texts(context.resources) }
}
