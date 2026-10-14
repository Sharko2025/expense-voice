package com.sitpro.expensevoice.ui

import com.sitpro.expensevoice.R
import com.sitpro.expensevoice.api.CategoryTotalDto
import com.sitpro.expensevoice.api.SummaryDto
import com.sitpro.expensevoice.data.CachedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLogicTest {
    // 2026-10-11 08:15:42 in Vietnam
    private val originalInstant = Instant.parse("2026-10-11T01:15:42Z")
    private val original = CachedTransaction(
        id = "t1", voiceEntryId = "e1", occurredAtMillis = originalInstant.toEpochMilli(), day = "2026-10-11",
        item = "bánh mì", amountVnd = 20_000, categoryId = 1, category = "Ăn sáng", method = "cash", merchant = null,
    )
    private val now = Instant.parse("2026-10-15T05:00:00Z") // 12:00 in Vietnam

    private fun edit(
        item: String = "bánh mì",
        amount: String = "20.000",
        categoryId: Int? = 1,
        method: String? = "cash",
        merchant: String = "",
        date: LocalDate = LocalDate.of(2026, 10, 11),
        time: LocalTime = LocalTime.of(8, 15),
    ) = TransactionEdit(item, amount, categoryId, method, merchant, date, time)

    @Test
    fun fromTransactionUsesVietnamTime() {
        val e = TransactionEdit.from(original)
        assertEquals(LocalDate.of(2026, 10, 11), e.date)
        assertEquals(LocalTime.of(8, 15), e.time)
        assertEquals("20.000", e.amount)
    }

    @Test
    fun unchangedEditProducesEmptyPatch() {
        assertEquals("{}", buildPatch(original, edit()).toString())
        assertEquals("{}", buildPatch(original, TransactionEdit.from(original)).toString())
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
    fun changedTimeIsSentWithVietnamOffset() {
        assertEquals(
            """{"occurred_at":"2026-10-11T12:30:00+07:00"}""",
            buildPatch(original, edit(time = LocalTime.of(12, 30))).toString(),
        )
    }

    @Test
    fun changedDateIsSent() {
        assertEquals(
            """{"occurred_at":"2026-10-10T23:30:00+07:00"}""",
            buildPatch(original, edit(date = LocalDate.of(2026, 10, 10), time = LocalTime.of(23, 30))).toString(),
        )
    }

    @Test
    fun combinesVietnamDateAndTime() {
        assertEquals(
            Instant.parse("2026-10-11T16:30:00Z"),
            vnInstant(LocalDate.of(2026, 10, 11), LocalTime.of(23, 30)),
        )
    }

    @Test
    fun validation() {
        assertNull(edit().error(now))
        assertEquals(R.string.error_item_required, edit(item = "  ").error(now))
        assertEquals(R.string.error_amount_invalid, edit(amount = "0").error(now))
        assertEquals(R.string.error_amount_invalid, edit(amount = "20k").error(now))
        // now = 12:00 on 15/10 in Vietnam
        assertNull(edit(date = LocalDate.of(2026, 10, 15), time = LocalTime.of(12, 0)).error(now))
        assertEquals(R.string.error_future, edit(date = LocalDate.of(2026, 10, 15), time = LocalTime.of(12, 1)).error(now))
        assertEquals(R.string.error_future, edit(date = LocalDate.of(2026, 10, 16)).error(now))
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
    fun barsAreRelativeToTheLargestCategory() {
        val s = SummaryDto(
            month = "2026-10", totalVnd = 1_500_000, count = 3, prevMonth = "2026-09", prevTotalVnd = 0,
            diffVnd = 0, diffPct = null,
            byCategory = listOf(1_200_000L, 300_000L, 0L).mapIndexed { i, t -> CategoryTotalDto(i, "c$i", t, 1) },
        )
        assertEquals(listOf(1f, 0.25f, 0f), categoryBars(s).map { it.fraction })
    }
}
