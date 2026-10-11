package com.sitpro.expensevoice.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
}

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("voice_entry_id") val voiceEntryId: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    val item: String,
    @SerialName("amount_vnd") val amountVnd: Long,
    @SerialName("category_id") val categoryId: Int? = null,
    val category: String? = null,
    val method: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class VoiceResultDto(
    @SerialName("entry_id") val entryId: String,
    @SerialName("raw_text") val rawText: String? = null,
    val transactions: List<TransactionDto>,
)

@Serializable
data class CategoryDto(val id: Int, val name: String, @SerialName("sort_order") val sortOrder: Int? = null)

@Serializable
data class CategoryTotalDto(
    @SerialName("category_id") val categoryId: Int? = null,
    val category: String? = null,
    @SerialName("total_vnd") val totalVnd: Long,
    val count: Int,
)

@Serializable
data class SummaryDto(
    val month: String,
    @SerialName("total_vnd") val totalVnd: Long,
    val count: Int,
    @SerialName("prev_month") val prevMonth: String,
    @SerialName("prev_total_vnd") val prevTotalVnd: Long,
    @SerialName("diff_vnd") val diffVnd: Long,
    @SerialName("diff_pct") val diffPct: Double? = null,
    @SerialName("by_category") val byCategory: List<CategoryTotalDto>,
)

/** Server error body. `message` is only set when `detail` is a plain string (our own Vietnamese messages). */
data class ApiError(val message: String?, val rawText: String?)

fun parseError(body: String?): ApiError {
    val obj = runCatching { ApiJson.parseToJsonElement(body.orEmpty()) as? JsonObject }.getOrNull()
        ?: return ApiError(null, null)
    val detail = (obj["detail"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val rawText = (obj["raw_text"] as? JsonPrimitive)?.contentOrNull
    return ApiError(detail, rawText)
}
