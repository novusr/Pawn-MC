package com.rvdjv.pawnmc.apps_data.data_logging

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rvdjv.pawnmc.apps_data.data_config.AppStorage
import java.io.File

/**
 * One unified SQLite compile-log for the whole app, one row per compile.
 */
object _compiler_store {

    /** Every compile row keeps these cells. */
    data class Entry(
        val timestamp: Long,
        val sourcePath: String,
        val sourceName: String,
        val exitCode: Int,
        val durationMs: Long,
        val compilerVersion: String,
        val output: String
    )

    private const val DB_NAME = "compiler_logging.db"
    private const val TABLE = "_compiler_logging_"

    /** Cell names, kept in one place so the schema and the reads never drift. */
    private const val C_ID = "_id"
    private const val C_TIMESTAMP = "timestamp"
    private const val C_SOURCE_PATH = "source_path"
    private const val C_SOURCE_NAME = "source_name"
    private const val C_EXIT_CODE = "exit_code"
    private const val C_DURATION_MS = "duration_ms"
    private const val C_COMPILER_VERSION = "compiler_version"
    private const val C_OUTPUT = "output"

    /** Row cap; old compiles are pruned after each write. */
    private const val MAX_ROWS = 500

    private class Helper(context: Context, dir: File) :
        SQLiteOpenHelper(context, File(dir, DB_NAME).absolutePath, null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE (" +
                    "$C_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$C_TIMESTAMP INTEGER NOT NULL, " +
                    "$C_SOURCE_PATH TEXT NOT NULL, " +
                    "$C_SOURCE_NAME TEXT NOT NULL, " +
                    "$C_EXIT_CODE INTEGER NOT NULL, " +
                    "$C_DURATION_MS INTEGER NOT NULL, " +
                    "$C_COMPILER_VERSION TEXT NOT NULL, " +
                    "$C_OUTPUT TEXT NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_time ON $TABLE ($C_TIMESTAMP DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_source ON $TABLE ($C_SOURCE_PATH)")
        }

        override fun onConfigure(db: SQLiteDatabase) {
            // WAL lets a read (e.g. the total-count badge) run while a compile row
            // is being written without blocking the writer.
            setWriteAheadLoggingEnabled(true)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /** Lazily opened handle; one per process, guarded against concurrent first use. */
    @Volatile
    private var helper: Helper? = null

    private fun helperFor(context: Context): Helper? {
        helper?.let { return it }
        synchronized(this) {
            helper?.let { return it }
            return runCatching {
                Helper(context.applicationContext, AppStorage.logsRoot(context))
            }.getOrNull().also { helper = it }
        }
    }

    /** Records one finished compile as a single row ("cell") and prunes old rows. */
    fun record(context: Context, entry: Entry) {
        if (!entry.isValid()) return
        // The journal keeps the timeline a crash dump reads; the SQLite row keeps the
        // full output. Only the summary goes into the journal, because a compile log can
        // be thousands of lines by itself.
        com.rvdjv.pawnmc.apps_data.data_crash.ActivityJournal.record(
            context,
            "compile",
            "exit ${entry.exitCode} in ${entry.durationMs} ms  " +
                "${entry.sourceName} (${entry.compilerVersion}, ${entry.output.lines().size} lines)"
        )
        val store = helperFor(context) ?: return
        runCatching {
            store.writableDatabase.use { db ->
                db.insert(TABLE, null, entry.toContentValues())
                pruneOldRows(db)
            }
        }
    }

    private fun Entry.isValid(): Boolean =
        timestamp > 0L && sourcePath.isNotBlank() && sourceName.isNotBlank() &&
            durationMs >= 0L && compilerVersion.isNotBlank()

    private fun Entry.toContentValues() = ContentValues().apply {
        put(C_TIMESTAMP, timestamp)
        put(C_SOURCE_PATH, sourcePath)
        put(C_SOURCE_NAME, sourceName)
        put(C_EXIT_CODE, exitCode)
        put(C_DURATION_MS, durationMs)
        put(C_COMPILER_VERSION, compilerVersion)
        put(C_OUTPUT, output)
    }

    private fun pruneOldRows(db: SQLiteDatabase) {
        db.execSQL(
            "DELETE FROM $TABLE WHERE $C_ID NOT IN (" +
                "SELECT $C_ID FROM $TABLE ORDER BY $C_TIMESTAMP DESC LIMIT $MAX_ROWS)"
        )
    }

    /** Total number of logged compiles; used for the "total file logging" readout. */
    fun totalEntries(context: Context): Int {
        val store = helperFor(context) ?: return 0
        return runCatching { readTotal(store.readableDatabase) }.getOrDefault(0)
    }

    private fun readTotal(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    /** Latest recorded compile rows, newest first, used by any log viewer. */
    fun recentEntries(context: Context, limit: Int = 50): List<Entry> {
        val store = helperFor(context) ?: return emptyList()
        val safeLimit = limit.coerceIn(1, MAX_ROWS)
        return runCatching { readRecent(store.readableDatabase, safeLimit) }.getOrDefault(emptyList())
    }

    private fun readRecent(db: SQLiteDatabase, limit: Int): List<Entry> =
        db.query(TABLE, null, null, null, null, null, "$C_TIMESTAMP DESC", limit.toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toCompilerEntry())
            }
        }

    private fun android.database.Cursor.toCompilerEntry() = Entry(
        timestamp = getLong(getColumnIndexOrThrow(C_TIMESTAMP)),
        sourcePath = getString(getColumnIndexOrThrow(C_SOURCE_PATH)),
        sourceName = getString(getColumnIndexOrThrow(C_SOURCE_NAME)),
        exitCode = getInt(getColumnIndexOrThrow(C_EXIT_CODE)),
        durationMs = getLong(getColumnIndexOrThrow(C_DURATION_MS)),
        compilerVersion = getString(getColumnIndexOrThrow(C_COMPILER_VERSION)),
        output = getString(getColumnIndexOrThrow(C_OUTPUT))
    )
}
