package com.sitpro.expensevoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.expensevoice.api.createApiService
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var repo: ExpenseRepository
    private val day = LocalDate.of(2026, 10, 24)

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val api = createApiService(server.url("/").toString(), tokenProvider = { "t" })
        repo = ExpenseRepository({ api }, db)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun tx(id: String) = CachedTransaction(
        id = id, voiceEntryId = "e1", occurredAtMillis = 1_761_264_000_000, day = day.toString(),
        item = "món $id", amountVnd = 10_000, categoryId = 1, category = "Khác", method = null, merchant = null,
    )

    private fun txJson(id: String) = """{"id":"$id","voice_entry_id":"e1","occurred_at":"2026-10-24T01:00:00Z","item":"món $id",
        "amount_vnd":10000,"category_id":1,"category":"Khác","method":null,"merchant":null}"""

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private suspend fun cachedIds() = repo.observeDay(day).first().map { it.id }.sorted()

    @Test
    fun deleteManyDeletesEachOnServerAndInCache() = runTest {
        db.transactionDao().upsert(listOf(tx("a"), tx("b"), tx("c")))
        repeat(2) { respond(204) }

        val result = repo.deleteMany(listOf("a", "c"))

        assertEquals(listOf("a", "c"), result.done)
        assertNull(result.error)
        assertEquals(listOf("b"), cachedIds())
        assertEquals("/api/v1/transactions/a", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/transactions/c", server.takeRequest().url.encodedPath)
    }

    @Test
    fun deleteManyStopsAtFirstFailureAndReportsWhatWasDeleted() = runTest {
        db.transactionDao().upsert(listOf(tx("a"), tx("b"), tx("c")))
        respond(204)
        respond(503)

        val result = repo.deleteMany(listOf("a", "b", "c"))

        assertEquals(listOf("a"), result.done)
        assertNotNull(result.error)
        assertEquals(listOf("b", "c"), cachedIds())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun restoreManyPutsRowsBack() = runTest {
        respond(200, txJson("a"))
        respond(200, txJson("c"))

        val result = repo.restoreMany(listOf("a", "c"))

        assertEquals(listOf("a", "c"), result.done)
        assertNull(result.error)
        assertEquals(listOf("a", "c"), cachedIds())
    }
}
