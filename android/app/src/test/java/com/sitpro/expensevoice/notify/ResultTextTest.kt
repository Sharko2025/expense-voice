package com.sitpro.expensevoice.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.expensevoice.api.TransactionDto
import com.sitpro.expensevoice.ui.Texts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun tx(item: String, amount: Long, category: String?, method: String?) =
    TransactionDto(id = item, occurredAt = "2026-10-11T01:00:00Z", item = item, amountVnd = amount, category = category, method = method)

private fun texts() = Texts(ApplicationProvider.getApplicationContext<Context>().resources)

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "vi")
class ResultTextVietnameseTest {
    @Test
    fun singleTransactionGoesInTheTitle() {
        val text = resultText(texts(), listOf(tx("bánh mì", 20_000, "Ăn sáng", "cash")))
        assertEquals("✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt", text.title)
        assertEquals(emptyList<String>(), text.lines)
    }

    @Test
    fun severalTransactionsAreGroupedWithTotal() {
        val text = resultText(
            texts(),
            listOf(tx("bánh mì", 20_000, "Ăn sáng", "cash"), tx("cafe", 27_000, "Cafe/Đồ uống", "card")),
        )
        assertEquals("✓ 2 giao dịch · 47.000đ", text.title)
        assertEquals(
            listOf("Bánh mì · 20.000đ · Ăn sáng · Tiền mặt", "Cafe · 27.000đ · Cafe/Đồ uống · Thẻ"),
            text.lines,
        )
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
        )
        assertEquals("✓ 2 transactions · 32.000đ", text.title)
        assertEquals(listOf("Coffee · 27.000đ · Coffee/Drinks · Card", "Parking · 5.000đ · Parking · ?"), text.lines)
    }
}
