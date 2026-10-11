package com.sitpro.expensevoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun pending(id: String) = PendingUpload(
        entryId = id,
        filePath = "/data/pending/$id.m4a",
        recordedAt = "2026-10-11T08:00:00+07:00",
        createdAtMillis = 1L,
    )

    private fun tx(id: String, day: String, at: Long, entry: String = "e1", amount: Long = 20_000) = CachedTransaction(
        id = id,
        voiceEntryId = entry,
        occurredAtMillis = at,
        day = day,
        item = "bánh mì",
        amountVnd = amount,
        categoryId = 1,
        category = "Ăn sáng",
        method = null,
        merchant = null,
    )

    @Test
    fun pendingInsertCountDelete() = runTest {
        val dao = db.pendingDao()
        dao.insert(pending("a"))
        dao.insert(pending("b"))
        assertEquals(2, dao.countFlow().first())
        dao.delete("a")
        assertEquals(listOf("b"), dao.all().map { it.entryId })
        assertNull(dao.get("a"))
    }

    @Test
    fun pendingInsertSameIdIsIgnored() = runTest {
        val dao = db.pendingDao()
        dao.insert(pending("a"))
        dao.insert(pending("a").copy(filePath = "/other"))
        assertEquals("/data/pending/a.m4a", dao.get("a")!!.filePath)
    }

    @Test
    fun pendingRecordsAttemptsAndBlocking() = runTest {
        val dao = db.pendingDao()
        dao.insert(pending("a"))
        dao.recordFailure("a", "HTTP 502")
        dao.recordFailure("a", "timeout")
        val row = dao.get("a")!!
        assertEquals(2, row.attempts)
        assertEquals("timeout", row.lastError)

        dao.setBlocked("a", true)
        assertTrue(dao.get("a")!!.blocked)
        dao.unblockAll()
        assertEquals(false, dao.get("a")!!.blocked)
    }

    @Test
    fun dayCacheIsReplacedAndOrderedNewestFirst() = runTest {
        val dao = db.transactionDao()
        dao.upsert(listOf(tx("old", "2026-10-11", 100), tx("other-day", "2026-10-10", 50)))
        dao.replaceDay("2026-10-11", listOf(tx("t1", "2026-10-11", 200), tx("t2", "2026-10-11", 300)))

        assertEquals(listOf("t2", "t1"), dao.observeDay("2026-10-11").first().map { it.id })
        assertEquals(listOf("other-day"), dao.observeDay("2026-10-10").first().map { it.id })
    }

    @Test
    fun removeByVoiceEntry() = runTest {
        val dao = db.transactionDao()
        dao.upsert(listOf(tx("t1", "2026-10-11", 1, entry = "e1"), tx("t2", "2026-10-11", 2, entry = "e2")))
        dao.deleteByVoiceEntry("e1")
        assertEquals(listOf("t2"), dao.observeDay("2026-10-11").first().map { it.id })
    }

    @Test
    fun summaryAndCategoriesAndMeta() = runTest {
        db.summaryDao().upsert(CachedSummary("2026-10", "{}", 5L))
        assertEquals(5L, db.summaryDao().observe("2026-10").first()!!.fetchedAtMillis)

        db.categoryDao().replaceAll(listOf(CachedCategory(2, "Ăn trưa", 2), CachedCategory(1, "Ăn sáng", 1)))
        db.categoryDao().replaceAll(listOf(CachedCategory(1, "Ăn sáng", 1), CachedCategory(12, "Khác", 12)))
        assertEquals(listOf("Ăn sáng", "Khác"), db.categoryDao().observeAll().first().map { it.name })

        db.metaDao().put(CacheMeta("day:2026-10-11", 42L))
        assertEquals(42L, db.metaDao().observe("day:2026-10-11").first()!!.fetchedAtMillis)
    }
}
