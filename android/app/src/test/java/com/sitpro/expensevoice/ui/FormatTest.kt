package com.sitpro.expensevoice.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun formatsVndWithDotSeparators() {
        assertEquals("0đ", formatVnd(0))
        assertEquals("5.000đ", formatVnd(5_000))
        assertEquals("20.000đ", formatVnd(20_000))
        assertEquals("1.200.000đ", formatVnd(1_200_000))
        assertEquals("5.000.000.000đ", formatVnd(5_000_000_000))
        assertEquals("-15.000đ", formatVnd(-15_000))
    }

    @Test
    fun methodLabels() {
        assertEquals("Tiền mặt", methodLabel("cash"))
        assertEquals("Thẻ", methodLabel("card"))
        assertEquals("Chuyển khoản", methodLabel("transfer"))
        assertEquals("?", methodLabel(null))
        assertEquals("?", methodLabel("momo"))
    }

    @Test
    fun timeIsShownInVietnamTime() {
        // 16:30 UTC = 23:30 in Ho Chi Minh City
        assertEquals("23:30", formatTime(Instant.parse("2026-10-10T16:30:00Z")))
        assertEquals("08:15", formatTime(Instant.parse("2026-10-10T08:15:00+07:00")))
    }

    @Test
    fun notificationLineMatchesSpec() {
        assertEquals(
            "Bánh mì · 20.000đ · Ăn sáng · Tiền mặt",
            transactionLine(item = "bánh mì", amountVnd = 20_000, category = "Ăn sáng", method = "cash"),
        )
        assertEquals(
            "Gửi xe · 5.000đ · Gửi xe · ?",
            transactionLine(item = "gửi xe", amountVnd = 5_000, category = "Gửi xe", method = null),
        )
        assertEquals("Xôi · 15.000đ · ?", transactionLine(item = "xôi", amountVnd = 15_000, category = null, method = null))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(20_000L, parseAmountInput("20000"))
        assertEquals(20_000L, parseAmountInput("20.000"))
        assertEquals(1_200_000L, parseAmountInput(" 1.200.000 "))
        assertNull(parseAmountInput(""))
        assertNull(parseAmountInput("0"))
        assertNull(parseAmountInput("-5000"))
        assertNull(parseAmountInput("20k"))
        assertNull(parseAmountInput("12,5"))
    }

    @Test
    fun formatsAmountForEditing() {
        assertEquals("20.000", formatAmountInput(20_000))
        assertEquals("1.200.000", formatAmountInput(1_200_000))
    }

    @Test
    fun percentDiff() {
        assertEquals("+15%", formatPercent(15.0))
        assertEquals("-33,3%", formatPercent(-33.3))
        assertEquals("0%", formatPercent(0.0))
    }
}
