package com.sitpro.expensevoice.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response as OkResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @Multipart
    @POST("api/v1/voice")
    suspend fun postVoice(
        @Part file: MultipartBody.Part,
        @Part("entry_id") entryId: RequestBody,
        @Part("recorded_at") recordedAt: RequestBody,
        // Only for a recording made for an earlier day; Retrofit leaves out a null part.
        @Part("occurred_on") occurredOn: RequestBody? = null,
    ): Response<VoiceResultDto>

    @GET("api/v1/transactions")
    suspend fun transactions(@Query("from") from: String, @Query("to") to: String): List<TransactionDto>

    @PATCH("api/v1/transactions/{id}")
    suspend fun patchTransaction(@Path("id") id: String, @Body changes: JsonObject): TransactionDto

    @DELETE("api/v1/transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String): Response<Unit>

    @POST("api/v1/transactions/{id}/restore")
    suspend fun restoreTransaction(@Path("id") id: String): TransactionDto

    @POST("api/v1/voice/{entryId}/undo")
    suspend fun undoVoice(@Path("entryId") entryId: String): Response<Unit>

    @GET("api/v1/summary")
    suspend fun summary(@Query("month") month: String): SummaryDto

    @GET("api/v1/categories")
    suspend fun categories(): List<CategoryDto>
}

class AuthInterceptor(private val tokenProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkResponse =
        chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${tokenProvider()}").build())
}

/** `baseUrl` like "https://expense-voice.s-itpro.com" (no trailing slash needed). */
fun createApiService(baseUrl: String, tokenProvider: () -> String): ApiService {
    val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .connectTimeout(10, TimeUnit.SECONDS)
        // Server waits up to 20 s for OpenAI; leave headroom for upload + network.
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
