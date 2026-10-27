package com.sitpro.expensevoice.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.expensevoice.api.TransactionDto
import com.sitpro.expensevoice.ui.Texts
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun tx(item: String, amount: Long, category: String?, method: String?, occurredAt: String = "2026-10-11T01:00:00Z") =
    TransactionDto(id = item, occurredAt = occurredAt, item = item, amountVnd = amount, category = category, method = method)

/** The day the default fixtures fall on (08:00 in Vietnam). */
private val TODAY = LocalDate.of(2026, 10, 11)

private fun texts() = Texts(ApplicationProvider.getApplicationContext<Context>().resources)

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "vi")
class ResultTextVietnameseTest {
    @Test
    fun singleTransactionGoesInTheTitle() {
        val text = resultText(texts(), listOf(tx("bánh mì", 20_000, "Ăn sáng", "cash")), TODAY)
        assertEquals("✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt", text.title)
        assertEquals(emptyList<String>(), text.lines)
    }

    @Test
    fun severalTransactionsAreGroupedWithTotal() {
        val text = resultText(
            texts(),
            listOf(tx("bánh mì", 20_000, "Ăn sáng", "cash"), tx("cafe", 27_000, "Cafe/Đồ uống", "card")),
            TODAY,
        )
        assertEquals("✓ 2 giao dịch · 47.000đ", text.title)
        assertEquals(
            listOf("Bánh mì · 20.000đ · Ăn sáng · Tiền mặt", "Cafe · 27.000đ · Cafe/Đồ uống · Thẻ"),
            text.lines,
        )
    }

    @Test
    fun earlierDayIsAddedToTheTitle() {
        // Noon on the 9th in Vietnam, recorded on the 11th from the Today screen.
        val single = resultText(texts(), listOf(tx("bánh mì", 20_000, "Ăn sáng", "cash", "2026-10-09T05:00:00Z")), TODAY)
        assertEquals("✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt — 09/10", single.title)

        val many = resultText(
            texts(),
            listOf(
                tx("bánh mì", 20_000, "Ăn sáng", "cash", "2026-10-09T05:00:00Z"),
                tx("cafe", 27_000, "Cafe/Đồ uống", "card", "2026-10-09T05:00:00Z"),
            ),
            TODAY,
        )
        assertEquals("✓ 2 giao dịch · 47.000đ — 09/10", many.title)
    }

    @Test
    fun todayUsesVietnamDayNotUtc() {
        // 2026-10-10 18:00 UTC is already the 11th in Vietnam: no date suffix.
        val text = resultText(texts(), listOf(tx("phở", 50_000, "Ăn tối", null, "2026-10-10T18:00:00Z")), TODAY)
        assertEquals("✓ Phở · 50.000đ · Ăn tối · ?", text.title)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class ResultTextEnglishTest {
    @Test
    fun englishTitleAndTranslatedCategory() {
        val text = resultText(
            texts(),
            listOf(tx("coffee", 27_000, "Cafe/Đồ uống", "card"), tx("parking", 5_000, "Gửi xe", null)),
            TODAY,
        )
        assertEquals("✓ 2 transactions · 32.000đ", text.title)
        assertEquals(listOf("Coffee · 27.000đ · Coffee/Drinks · Card", "Parking · 5.000đ · Parking · ?"), text.lines)
    }
}
