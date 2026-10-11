package com.sitpro.expensevoice.ui

import com.sitpro.expensevoice.api.CategoryTotalDto
import com.sitpro.expensevoice.api.SummaryDto
import com.sitpro.expensevoice.data.CachedTransaction
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLogicTest {
    private val original = CachedTransaction(
        id = "t1", voiceEntryId = "e1", occurredAtMillis = 0, day = "2026-10-11", item = "bánh mì",
        amountVnd = 20_000, categoryId = 1, category = "Ăn sáng", method = "cash", merchant = null,
    )

    private fun edit(
        item: String = "bánh mì",
        amount: String = "20.000",
        categoryId: Int? = 1,
        method: String? = "cash",
        merchant: String = "",
    ) = TransactionEdit(item, amount, categoryId, method, merchant)

    @Test
    fun unchangedEditProducesEmptyPatch() {
        assertEquals("{}", buildPatch(original, edit()).toString())
    }

    @Test
    fun onlyChangedFieldsAreSent() {
        val patch = buildPatch(original, edit(amount = "25.000", merchant = " FamilyMart "))
        assertEquals("""{"amount_vnd":25000,"merchant":"FamilyMart"}""", patch.toString())
    }

    @Test
    fun clearingMethodSendsExplicitNull() {
        assertEquals("""{"method":null}""", buildPatch(original, edit(method = null)).toString())
    }

    @Test
    fun itemIsTrimmed() {
        assertEquals("""{"item":"xôi gà"}""", buildPatch(original, edit(item = "  xôi gà ")).toString())
    }

    @Test
    fun validation() {
        assertNull(edit().error())
        assertEquals("Nhập tên món", edit(item = "  ").error())
        assertEquals("Số tiền không hợp lệ", edit(amount = "0").error())
        assertEquals("Số tiền không hợp lệ", edit(amount = "20k").error())
    }

    @Test
    fun dayNavigationStopsAtToday() {
        val today = LocalDate.of(2026, 10, 11)
        assertTrue(canGoForward(LocalDate.of(2026, 10, 10), today))
        assertFalse(canGoForward(today, today))
        assertTrue(canGoForward(YearMonth.of(2026, 9), YearMonth.of(2026, 10)))
        assertFalse(canGoForward(YearMonth.of(2026, 10), YearMonth.of(2026, 10)))
    }

    @Test
    fun dayTitle() {
        val today = LocalDate.of(2026, 10, 11)
        assertEquals("Hôm nay", dayTitle(today, today))
        assertEquals("Hôm qua", dayTitle(today.minusDays(1), today))
        assertEquals("Thứ Năm, 08/10", dayTitle(LocalDate.of(2026, 10, 8), today))
        assertEquals("Chủ Nhật, 04/10", dayTitle(LocalDate.of(2026, 10, 4), today))
    }

    @Test
    fun monthTitle() {
        assertEquals("Tháng 10/2026", monthTitle(YearMonth.of(2026, 10)))
    }

    private fun summary(diffVnd: Long, diffPct: Double?, vararg totals: Long) = SummaryDto(
        month = "2026-10", totalVnd = totals.sum(), count = totals.size, prevMonth = "2026-09",
        prevTotalVnd = totals.sum() - diffVnd, diffVnd = diffVnd, diffPct = diffPct,
        byCategory = totals.mapIndexed { i, t -> CategoryTotalDto(i, "c$i", t, 1) },
    )

    @Test
    fun barsAreRelativeToTheLargestCategory() {
        val bars = categoryBars(summary(0, null, 1_200_000, 300_000, 0))
        assertEquals(listOf(1f, 0.25f, 0f), bars.map { it.fraction })
    }

    @Test
    fun diffText() {
        assertEquals("+450.000đ (+15%) so với tháng trước", diffText(summary(450_000, 15.0, 1)))
        assertEquals("-100.000đ (-33,3%) so với tháng trước", diffText(summary(-100_000, -33.3, 1)))
        assertEquals("+50.000đ so với tháng trước", diffText(summary(50_000, null, 1)))
        assertEquals("Bằng tháng trước", diffText(summary(0, 0.0, 1)))
    }
}
