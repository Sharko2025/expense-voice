package com.sitpro.expensevoice.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An app update must never drop recordings still waiting in the queue.
 * Builds a version-1 database from the exported schema, then lets the real AppDatabase open it:
 * Room runs the migration and fails the open if the result doesn't match the current schema.
 * (MigrationTestHelper rejects Windows paths, so it isn't used here.)
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun v1To2KeepsPendingUploadsAndAddsOccurredOn() = runTest {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            createV1(db)
            db.execSQL(
                "INSERT INTO pending_uploads (entryId, filePath, recordedAt, createdAtMillis, attempts, lastError, blocked) " +
                    "VALUES ('e1', '/p/e1.m4a', '2026-10-27T08:00:00+07:00', 1, 2, 'IOException', 0)"
            )
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.path).allowMainThreadQueries().build()
        try {
            val row = room.pendingDao().get("e1")!!
            assertEquals(2, row.attempts)
            assertEquals("/p/e1.m4a", row.filePath)
            assertNull(row.occurredOn)
        } finally {
            room.close()
        }
    }

    /** Tables and Room's identity hash exactly as version 1 shipped (schemas/…/1.json). */
    private fun createV1(db: SQLiteDatabase) {
        val schema = File("schemas/${AppDatabase::class.java.name}/1.json")
        val database = Json.parseToJsonElement(schema.readText()).jsonObject["database"]!!.jsonObject
        database["entities"]!!.jsonArray.forEach { entity ->
            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
            db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        }
        database["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = 1
    }
}
