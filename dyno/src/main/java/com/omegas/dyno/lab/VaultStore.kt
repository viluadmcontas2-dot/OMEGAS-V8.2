package com.omegas.dyno.lab

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class VaultStore(context: Context) :
    SQLiteOpenHelper(context, "dyno-vault.db", null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY, started_at_ms INTEGER NOT NULL,
                started_mono_ns INTEGER NOT NULL, ended_at_ms INTEGER,
                ended_mono_ns INTEGER, state TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE observations (
                id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id),
                wall_ms INTEGER NOT NULL, mono_ns INTEGER NOT NULL, source TEXT NOT NULL, data_json TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX observations_by_session ON observations(session_id, id)")
        db.execSQL("""
            CREATE TABLE events (
                id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id),
                wall_ms INTEGER NOT NULL, mono_ns INTEGER NOT NULL, kind TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id),
                wall_ms INTEGER NOT NULL, text TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Migração da versão $oldVersion para $newVersion indisponível")
    }

    fun recoverInterrupted(passive: Boolean = false) {
        writableDatabase.execSQL(
            "UPDATE sessions SET state = 'interrupted' WHERE state = ?", arrayOf(captureState(passive))
        )
    }

    fun start(stamp: Stamp, passive: Boolean = false): Long = transaction { db ->
        val id = db.insertOrThrow("sessions", null, ContentValues().apply {
            put("started_at_ms", stamp.wallMs)
            put("started_mono_ns", stamp.monoNs)
            put("state", captureState(passive))
        })
        event(db, id, stamp, "capture_started")
        id
    }

    fun stop(id: Long, stamp: Stamp, passive: Boolean = false) = transaction { db ->
        val count = db.update("sessions", ContentValues().apply {
            put("ended_at_ms", stamp.wallMs)
            put("ended_mono_ns", stamp.monoNs)
            put("state", "complete")
        }, "id = ? AND state = ?", arrayOf(id.toString(), captureState(passive)))
        check(count == 1)
        event(db, id, stamp, "capture_stopped")
    }

    fun observe(id: Long, observation: Observation) =
        insertObservation(id, observation.stamp, observation.source.key, observation.json())

    fun observePassiveObd(id: Long, stamp: Stamp, hex: String, size: Int, readAgeNs: Long) {
        val source = "OBD_RAW_PASSIVE"
        insertObservation(id, stamp, source, JSONObject()
            .put("source", source)
            .put("hex", hex)
            .put("size", size)
            .put("serialReadAgeNs", readAgeNs)
            .put("monoNs", stamp.monoNs)
            .put("utc", java.time.Instant.ofEpochMilli(stamp.wallMs).toString())
            .put("t", stamp.wallMs))
    }

    private fun captureState(passive: Boolean) = if (passive) "capturing_passive" else "capturing"

    private fun insertObservation(id: Long, stamp: Stamp, source: String, data: JSONObject) {
        writableDatabase.insertOrThrow("observations", null, ContentValues().apply {
            put("session_id", id)
            put("wall_ms", stamp.wallMs)
            put("mono_ns", stamp.monoNs)
            put("source", source)
            put("data_json", data.toString())
        })
    }

    fun sessions(): JSONArray {
        val sessions = JSONArray()
        readableDatabase.rawQuery("""
            SELECT sessions.*, (SELECT COUNT(*) FROM observations WHERE session_id = sessions.id) AS observation_count
            FROM sessions ORDER BY id DESC
        """.trimIndent(), null).use { rows ->
            while (rows.moveToNext()) sessions.put(row(rows))
        }
        return sessions
    }

    // Called on the vault worker. Freeze the export in a private file before handing it to a document provider.
    fun exportZip(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("dyno-vault.json"))
            val writer = OutputStreamWriter(zip, Charsets.UTF_8).buffered()
            writer.write("{\"schemaVersion\":$VERSION")
            transaction { db ->
                for (table in listOf("sessions", "observations", "events", "notes")) {
                    writer.write(",\"$table\":[")
                    writeRows(db, table, writer)
                    writer.write("]")
                }
            }
            writer.write("}")
            writer.flush()
            zip.closeEntry()
        }
    }

    private fun writeRows(db: SQLiteDatabase, table: String, writer: Writer) {
        db.query(table, null, null, null, null, null, "id ASC").use { rows ->
            while (rows.moveToNext()) {
                if (!rows.isFirst) writer.write(",")
                writer.write(row(rows).toString())
            }
        }
    }

    private fun row(cursor: Cursor): JSONObject = JSONObject().apply {
        for (index in 0 until cursor.columnCount) {
            val key = cursor.getColumnName(index)
            when {
                key == "data_json" -> put("data", JSONObject(cursor.getString(index)))
                cursor.isNull(index) -> put(key, UNKNOWN)
                cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER -> put(key, cursor.getLong(index))
                else -> put(key, cursor.getString(index))
            }
        }
    }

    private fun event(db: SQLiteDatabase, id: Long, stamp: Stamp, kind: String) {
        db.insertOrThrow("events", null, ContentValues().apply {
            put("session_id", id)
            put("wall_ms", stamp.wallMs)
            put("mono_ns", stamp.monoNs)
            put("kind", kind)
        })
    }

    private fun <T> transaction(action: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val result = action(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    companion object { private const val VERSION = 1 }
}
