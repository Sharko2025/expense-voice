package com.sitpro.expensevoice.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Language-independent formatting. Localized text is covered by TextsTest. */
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
    fun timeIsShownInVietnamTime() {
        // 16:30 UTC = 23:30 in Ho Chi Minh City
        assertEquals("23:30", formatTime(Instant.parse("2026-10-10T16:30:00Z")))
        assertEquals("08:15", formatTime(Instant.parse("2026-10-10T08:15:00+07:00")))
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
    fun percentUsesGivenDecimalSeparator() {
        assertEquals("+15%", formatPercent(15.0, ','))
        assertEquals("-33,3%", formatPercent(-33.3, ','))
        assertEquals("-33.3%", formatPercent(-33.3, '.'))
        assertEquals("0%", formatPercent(0.0, '.'))
    }
}
