package com.sitpro.expensevoice.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiServiceTest {
    private val server = MockWebServer()
    private lateinit var api: ApiService

    @Before
    fun setUp() {
        server.start()
        api = createApiService(server.url("/").toString().trimEnd('/'), tokenProvider = { "secret-token" })
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "") =
        server.enqueue(
            MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()
        )

    private val txJson = """
        {"id":"7b1a8f0e-0000-4000-8000-000000000001","voice_entry_id":"7b1a8f0e-0000-4000-8000-0000000000aa",
         "occurred_at":"2026-10-10T01:15:00Z","item":"bánh mì","amount_vnd":20000,"category_id":1,
         "category":"Ăn sáng","method":"cash","merchant":null,"note":null,
         "created_at":"2026-10-10T01:15:01Z","updated_at":"2026-10-10T01:15:01Z"}
    """.trimIndent()

    @Test
    fun sendsBearerToken() = runTest {
        respond(200, "[]")
        api.categories()
        assertEquals("Bearer secret-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun postVoiceSendsMultipartAndParsesResult() = runTest {
        respond(
            200,
            """{"entry_id":"7b1a8f0e-0000-4000-8000-0000000000aa","raw_text":"bánh mì 20k","transactions":[$txJson]}""",
        )
        val resp = api.postVoice(
            file = MultipartBody.Part.createFormData(
                "file", "a.m4a", byteArrayOf(1, 2, 3).toRequestBody("audio/mp4".toMediaType())
            ),
            entryId = "7b1a8f0e-0000-4000-8000-0000000000aa".toRequestBody(),
            recordedAt = "2026-10-10T08:15:00+07:00".toRequestBody(),
        )
        val body = resp.body()!!
        assertEquals("bánh mì 20k", body.rawText)
        assertEquals(20_000L, body.transactions.single().amountVnd)
        assertEquals("cash", body.transactions.single().method)

        val req = server.takeRequest()
        assertEquals("/api/v1/voice", req.url.encodedPath)
        val sent = req.body!!.utf8()
        assertTrue(sent.contains("name=\"entry_id\""))
        assertTrue(sent.contains("name=\"recorded_at\""))
        assertTrue(sent.contains("filename=\"a.m4a\""))
    }

    @Test
    fun parses422WithRawText() = runTest {
        respond(422, """{"detail":"Không hiểu, nói lại nhé","raw_text":"hôm nay trời đẹp"}""")
        val resp = api.postVoice(
            file = MultipartBody.Part.createFormData("file", "a.m4a", byteArrayOf(1).toRequestBody()),
            entryId = "x".toRequestBody(),
            recordedAt = "y".toRequestBody(),
        )
        val err = parseError(resp.errorBody()?.string())
        assertEquals("Không hiểu, nói lại nhé", err.message)
        assertEquals("hôm nay trời đẹp", err.rawText)
    }

    @Test
    fun parsesValidationErrorList() {
        val err = parseError("""{"detail":[{"loc":["body","x"],"msg":"bad"}]}""")
        assertNull(err.rawText)
        assertNull(err.message)
    }

    @Test
    fun parsesGarbageErrorBody() {
        val err = parseError("<html>502 Bad Gateway</html>")
        assertNull(err.message)
        assertNull(err.rawText)
    }

    @Test
    fun listTransactionsUsesDateQuery() = runTest {
        respond(200, "[$txJson]")
        val list = api.transactions(from = "2026-10-10", to = "2026-10-10")
        assertEquals("bánh mì", list.single().item)
        assertEquals("/api/v1/transactions?from=2026-10-10&to=2026-10-10", server.takeRequest().url.pathAndQuery())
    }

    @Test
    fun patchSendsExplicitNulls() = runTest {
        respond(200, txJson)
        api.patchTransaction(
            "7b1a8f0e-0000-4000-8000-000000000001",
            buildJsonObject {
                put("amount_vnd", JsonPrimitive(25000))
                put("method", JsonNull)
            },
        )
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("""{"amount_vnd":25000,"method":null}""", req.body!!.utf8())
    }

    @Test
    fun summaryParses() = runTest {
        respond(
            200,
            """{"month":"2026-10","total_vnd":1272000,"count":4,"prev_month":"2026-09","prev_total_vnd":0,
               "diff_vnd":1272000,"diff_pct":null,
               "by_category":[{"category_id":7,"category":"Đi chợ/Siêu thị","total_vnd":1200000,"count":1}]}""",
        )
        val s = api.summary("2026-10")
        assertEquals(1_272_000L, s.totalVnd)
        assertNull(s.diffPct)
        assertEquals("Đi chợ/Siêu thị", s.byCategory.single().category)
        assertEquals("/api/v1/summary?month=2026-10", server.takeRequest().url.pathAndQuery())
    }

    @Test
    fun deleteRestoreUndoPaths() = runTest {
        respond(204)
        respond(200, txJson)
        respond(204)
        api.deleteTransaction("t1")
        api.restoreTransaction("t1")
        api.undoVoice("e1")
        assertEquals("DELETE /api/v1/transactions/t1", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(
            "POST /api/v1/transactions/t1/restore",
            server.takeRequest().let { "${it.method} ${it.url.encodedPath}" },
        )
        assertEquals("POST /api/v1/voice/e1/undo", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun ignoresUnknownJsonFields() = runTest {
        respond(200, """[{"id":1,"name":"Ăn sáng","sort_order":1,"future_field":true}]""")
        assertEquals("Ăn sáng", api.categories().single().name)
    }
}

private fun HttpUrl.pathAndQuery() = encodedPath + (encodedQuery?.let { "?$it" } ?: "")
