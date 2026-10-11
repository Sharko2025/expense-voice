package com.sitpro.expensevoice.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/** All dates and times are shown in Vietnam time, regardless of the phone's time zone setting. */
val VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(VN_ZONE)

fun groupThousands(value: Long): String {
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (value < 0) "-$grouped" else grouped
}

fun formatVnd(amount: Long): String = groupThousands(amount) + "đ"

fun formatAmountInput(amount: Long): String = groupThousands(amount)

/** Accepts "20000", "20.000", " 1.200.000 ". Returns null for anything that is not a positive whole amount. */
fun parseAmountInput(text: String): Long? {
    val trimmed = text.trim()
    if (!Regex("""\d{1,3}(\.\d{3})*|\d+""").matches(trimmed)) return null
    val value = trimmed.replace(".", "").toLongOrNull() ?: return null
    return value.takeIf { it > 0 }
}

fun methodLabel(method: String?): String = when (method) {
    "cash" -> "Tiền mặt"
    "card" -> "Thẻ"
    "transfer" -> "Chuyển khoản"
    else -> "?"
}

fun formatTime(instant: Instant): String = TIME_FORMAT.format(instant)

/** "Bánh mì · 20.000đ · Ăn sáng · Tiền mặt" — used in notifications. */
fun transactionLine(item: String, amountVnd: Long, category: String?, method: String?): String =
    listOfNotNull(item.replaceFirstChar { it.uppercase() }, formatVnd(amountVnd), category, methodLabel(method))
        .joinToString(" · ")

/** 15.0 -> "+15%", -33.3 -> "-33,3%" (Vietnamese decimal comma). */
fun formatPercent(pct: Double): String {
    val tenths = (pct * 10).roundToLong()
    val sign = if (tenths > 0) "+" else if (tenths < 0) "-" else ""
    val whole = abs(tenths) / 10
    val frac = abs(tenths) % 10
    return sign + whole + (if (frac != 0L) ",$frac" else "") + "%"
}
