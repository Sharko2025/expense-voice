package com.sitpro.expensevoice.notify

import com.sitpro.expensevoice.api.TransactionDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultTextTest {
    private fun tx(item: String, amount: Long, category: String?, method: String?) =
        TransactionDto(id = item, occurredAt = "2026-10-11T01:00:00Z", item = item, amountVnd = amount, category = category, method = method)

    @Test
    fun singleTransactionGoesInTheTitle() {
        val text = resultText(listOf(tx("bánh mì", 20_000, "Ăn sáng", "cash")))
        assertEquals("✓ Bánh mì · 20.000đ · Ăn sáng · Tiền mặt", text.title)
        assertEquals(emptyList<String>(), text.lines)
    }

    @Test
    fun severalTransactionsAreGroupedWithTotal() {
        val text = resultText(
            listOf(tx("bánh mì", 20_000, "Ăn sáng", "cash"), tx("cafe", 27_000, "Cafe/Đồ uống", "card"))
        )
        assertEquals("✓ 2 giao dịch · 47.000đ", text.title)
        assertEquals(
            listOf("Bánh mì · 20.000đ · Ăn sáng · Tiền mặt", "Cafe · 27.000đ · Cafe/Đồ uống · Thẻ"),
            text.lines,
        )
    }
}
