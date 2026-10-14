package com.sitpro.expensevoice.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.sitpro.expensevoice.api.CategoryTotalDto
import com.sitpro.expensevoice.api.SummaryDto
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun texts() = Texts(ApplicationProvider.getApplicationContext<Context>().resources)

private fun summary(diffVnd: Long, diffPct: Double?) = SummaryDto(
    month = "2026-10", totalVnd = 1, count = 1, prevMonth = "2026-09", prevTotalVnd = 1 - diffVnd,
    diffVnd = diffVnd, diffPct = diffPct, byCategory = listOf(CategoryTotalDto(1, "Ăn sáng", 1, 1)),
)

private val TODAY = LocalDate.of(2026, 10, 15) // Thursday

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class TextsEnglishTest {
    private val t = texts()

    @Test fun methods() {
        assertEquals(listOf("Cash", "Card", "Transfer", "?"), listOf("cash", "card", "transfer", null).map(t::method))
    }

    @Test fun categoriesAreTranslatedUnknownKept() {
        assertEquals("Breakfast", t.category("Ăn sáng"))
        assertEquals("Coffee/Drinks", t.category("Cafe/Đồ uống"))
        assertEquals("Groceries", t.category("Đi chợ/Siêu thị"))
        assertEquals("Other", t.category("Khác"))
        assertEquals("Ăn vặt", t.category("Ăn vặt"))
        assertEquals(null, t.category(null))
    }

    @Test fun notificationLine() {
        assertEquals("Banh mi · 20.000đ · Breakfast · Cash", t.line("banh mi", 20_000, "Ăn sáng", "cash"))
        assertEquals("Coffee · 27.000đ · ?", t.line("coffee", 27_000, null, null))
    }

    @Test fun dayTitles() {
        assertEquals("Today", t.dayTitle(TODAY, TODAY))
        assertEquals("Yesterday", t.dayTitle(TODAY.minusDays(1), TODAY))
        assertEquals("Mon, 12 Oct", t.dayTitle(LocalDate.of(2026, 10, 12), TODAY))
        assertEquals("12 Oct", t.shortDate(LocalDate.of(2026, 10, 12)))
    }

    @Test fun monthTitle() = assertEquals("October 2026", t.monthTitle(YearMonth.of(2026, 10)))

    @Test fun diffs() {
        assertEquals("+450.000đ (+15%) vs last month", t.diff(summary(450_000, 15.0)))
        assertEquals("-100.000đ (-33.3%) vs last month", t.diff(summary(-100_000, -33.3)))
        assertEquals("+50.000đ vs last month", t.diff(summary(50_000, null)))
        assertEquals("Same as last month", t.diff(summary(0, 0.0)))
    }

    @Test fun counts() {
        assertEquals("1 transaction", t.transactionCount(1))
        assertEquals("3 transactions", t.transactionCount(3))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "vi")
class TextsVietnameseTest {
    private val t = texts()

    @Test fun methods() {
        assertEquals(listOf("Tiền mặt", "Thẻ", "Chuyển khoản", "?"), listOf("cash", "card", "transfer", null).map(t::method))
    }

    @Test fun categoriesStayVietnamese() = assertEquals("Ăn sáng", t.category("Ăn sáng"))

    @Test fun notificationLineMatchesSpec() =
        assertEquals("Bánh mì · 20.000đ · Ăn sáng · Tiền mặt", t.line("bánh mì", 20_000, "Ăn sáng", "cash"))

    @Test fun dayTitles() {
        assertEquals("Hôm nay", t.dayTitle(TODAY, TODAY))
        assertEquals("Hôm qua", t.dayTitle(TODAY.minusDays(1), TODAY))
        assertEquals("Thứ Năm, 08/10", t.dayTitle(LocalDate.of(2026, 10, 8), TODAY))
        assertEquals("Chủ Nhật, 04/10", t.dayTitle(LocalDate.of(2026, 10, 4), TODAY))
        assertEquals("12/10", t.shortDate(LocalDate.of(2026, 10, 12)))
    }

    @Test fun monthTitle() = assertEquals("Tháng 10/2026", t.monthTitle(YearMonth.of(2026, 10)))

    @Test fun diffs() {
        assertEquals("-100.000đ (-33,3%) so với tháng trước", t.diff(summary(-100_000, -33.3)))
        assertEquals("Bằng tháng trước", t.diff(summary(0, 0.0)))
    }

    @Test fun counts() = assertEquals("3 giao dịch", t.transactionCount(3))
}
