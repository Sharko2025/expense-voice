package com.sitpro.expensevoice.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.expensevoice.api.TransactionDto
import com.sitpro.expensevoice.api.createApiService
import com.sitpro.expensevoice.data.AppDatabase
import com.sitpro.expensevoice.data.ExpenseRepository
import com.sitpro.expensevoice.data.PendingUpload
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

class FakeResults : ResultSink {
    val events = mutableListOf<String>()
    /** Kept apart from [events] so outcome assertions stay about outcomes. */
    val processing = mutableListOf<String>()
    override fun processing(entryId: String) {
        processing += entryId
    }
    override fun success(entryId: String, transactions: List<TransactionDto>) {
        events += "success:$entryId:${transactions.size}"
    }
    override fun notUnderstood(entryId: String, rawText: String?) {
        events += "not-understood:$entryId:$rawText"
    }
    override fun rejected(entryId: String, reason: Rejection) {
        events += "rejected:$entryId:$reason"
    }
    override fun unauthorized(entryId: String) {
        events += "unauthorized:$entryId"
    }
    override fun undone(entryId: String) {
        events += "undone:$entryId"
    }
}

@RunWith(AndroidJUnit4::class)
class UploadProcessorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var results: FakeResults
    private lateinit var processor: UploadProcessor

    private val entryId = "7b1a8f0e-0000-4000-8000-0000000000aa"

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        results = FakeResults()
        val api = createApiService(server.url("/").toString(), tokenProvider = { "t" })
        processor = UploadProcessor({ api }, db.pendingDao(), ExpenseRepository({ api }, db), results)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private suspend fun queueRecording(): File {
        val file = tmp.newFile("$entryId.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        db.pendingDao().insert(PendingUpload(entryId, file.path, "2026-10-11T08:00:00+07:00", 1L))
        return file
    }

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private val okBody = """
        {"entry_id":"$entryId","raw_text":"bánh mì 20k","transactions":[
          {"id":"t1","voice_entry_id":"$entryId","occurred_at":"2026-10-11T01:00:00Z","item":"bánh mì",
           "amount_vnd":20000,"category_id":1,"category":"Ăn sáng","method":"cash","merchant":null}]}
    """.trimIndent()

    @Test
    fun successCachesTransactionsDeletesFileAndNotifies() = runTest {
        val file = queueRecording()
        respond(200, okBody)

        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))

        assertFalse(file.exists())
        assertNull(db.pendingDao().get(entryId))
        assertEquals(listOf("success:$entryId:1"), results.events)
        val cached = db.transactionDao().observeDay("2026-10-11").first()
        assertEquals(listOf("t1"), cached.map { it.id })

        val req = server.takeRequest()
        val body = req.body!!.utf8()
        assertTrue(body.contains(entryId))
        assertTrue(body.contains("2026-10-11T08:00:00+07:00"))
    }

    @Test
    fun notUnderstoodDeletesAndShowsRawText() = runTest {
        val file = queueRecording()
        respond(422, """{"detail":"Không hiểu, nói lại nhé","raw_text":"hôm nay trời đẹp"}""")

        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))
        assertFalse(file.exists())
        assertNull(db.pendingDao().get(entryId))
        assertEquals(listOf("not-understood:$entryId:hôm nay trời đẹp"), results.events)
    }

    @Test
    fun otherClientErrorIsDroppedWithMessage() = runTest {
        val file = queueRecording()
        respond(422, """{"detail":"File ghi âm rỗng"}""")

        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))
        assertFalse(file.exists())
        assertEquals(listOf("rejected:$entryId:INVALID_RECORDING"), results.events)
    }

    @Test
    fun unexpectedClientErrorIsDropped() = runTest {
        val file = queueRecording()
        respond(413, "too big")
        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))
        assertFalse(file.exists())
        assertEquals(listOf("rejected:$entryId:SERVER_ERROR"), results.events)
    }

    @Test
    fun serverErrorsAreRetriedAndKeepTheFile() = runTest {
        for (code in listOf(502, 409, 500, 503)) {
            val file = queueRecording()
            respond(code, """{"detail":"x"}""")
            assertEquals(UploadProcessor.Step.RETRY, processor.process(entryId))
            assertTrue(file.exists())
            assertNotNull(db.pendingDao().get(entryId))
            db.pendingDao().delete(entryId)
            file.delete()
        }
        assertEquals(emptyList<String>(), results.events)
    }

    @Test
    fun networkFailureIsRetried() = runTest {
        val file = queueRecording()
        server.close() // connection refused

        assertEquals(UploadProcessor.Step.RETRY, processor.process(entryId))
        assertTrue(file.exists())
        assertEquals(1, db.pendingDao().get(entryId)!!.attempts)
    }

    @Test
    fun malformedSuccessBodyIsRetried() = runTest {
        val file = queueRecording()
        respond(200, "<html>oops</html>")
        assertEquals(UploadProcessor.Step.RETRY, processor.process(entryId))
        assertTrue(file.exists())
    }

    @Test
    fun unauthorizedBlocksAndKeepsTheRecording() = runTest {
        val file = queueRecording()
        respond(401, """{"detail":"Token không hợp lệ"}""")

        assertEquals(UploadProcessor.Step.BLOCKED, processor.process(entryId))
        assertTrue(file.exists())
        assertTrue(db.pendingDao().get(entryId)!!.blocked)
        assertEquals(listOf("unauthorized:$entryId"), results.events)
    }

    @Test
    fun reportsProcessingBeforeEachSendAttempt() = runTest {
        queueRecording()
        respond(503)
        processor.process(entryId)
        respond(200, okBody)
        processor.process(entryId)
        assertEquals(listOf(entryId, entryId), results.processing)
    }

    @Test
    fun missingFileIsNeverReportedAsProcessing() = runTest {
        queueRecording().delete()
        processor.process(entryId)
        assertEquals(emptyList<String>(), results.processing)
    }

    @Test
    fun missingRowIsDone() = runTest {
        assertEquals(UploadProcessor.Step.DONE, processor.process("unknown"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun missingFileDropsTheRow() = runTest {
        val file = queueRecording()
        file.delete()
        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))
        assertNull(db.pendingDao().get(entryId))
        assertEquals(listOf("rejected:$entryId:MISSING_FILE"), results.events)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun undoRemovesCacheAndNotifies() = runTest {
        queueRecording()
        respond(200, okBody)
        processor.process(entryId)
        results.events.clear()

        respond(204)
        assertEquals(UploadProcessor.Step.DONE, processor.undo(entryId))
        assertEquals(emptyList<Any>(), db.transactionDao().observeDay("2026-10-11").first())
        assertEquals(listOf("undone:$entryId"), results.events)
        server.takeRequest() // the original POST /voice
        assertEquals("/api/v1/voice/$entryId/undo", server.takeRequest().url.encodedPath)
    }

    @Test
    fun undoRetriesOnServerError() = runTest {
        respond(503)
        assertEquals(UploadProcessor.Step.RETRY, processor.undo(entryId))
    }

    @Test
    fun undoOfUnknownEntryIsDone() = runTest {
        respond(404, """{"detail":"Không tìm thấy lần ghi âm"}""")
        assertEquals(UploadProcessor.Step.DONE, processor.undo(entryId))
    }
}
