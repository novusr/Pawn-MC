package com.rvdjv.pawnmc.data.log

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rvdjv.pawnmc.data.config.AppStorage
import java.io.File

/**
 * One unified SQLite compile-log for the whole app, one row per compile.
 */
object CompileLogStore {

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
        val store = helperFor(context) ?: return
        runCatching {
            store.writableDatabase.use { db ->
                db.insert(
                    TABLE,
                    null,
                    ContentValues().apply {
                        put(C_TIMESTAMP, entry.timestamp)
                        put(C_SOURCE_PATH, entry.sourcePath)
                        put(C_SOURCE_NAME, entry.sourceName)
                        put(C_EXIT_CODE, entry.exitCode)
                        put(C_DURATION_MS, entry.durationMs)
                        put(C_COMPILER_VERSION, entry.compilerVersion)
                        put(C_OUTPUT, entry.output)
                    }
                )
                db.execSQL(
                    "DELETE FROM $TABLE WHERE $C_ID NOT IN (" +
                        "SELECT $C_ID FROM $TABLE ORDER BY $C_TIMESTAMP DESC LIMIT $MAX_ROWS)"
                )
            }
        }
    }



    /** Total number of logged compiles; used for the "total file logging" readout. */
    fun totalEntries(context: Context): Int {
        val store = helperFor(context) ?: return 0
        return runCatching {
            store.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }.getOrDefault(0)
    }

    /** Latest recorded compile rows, newest first, used by any log viewer. */
    fun recentEntries(context: Context, limit: Int = 50): List<Entry> {
        val store = helperFor(context) ?: return emptyList()
        return runCatching {
            store.readableDatabase.query(
                TABLE, null, null, null, null, null,
                "$C_TIMESTAMP DESC", limit.toString()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Entry(
                                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(C_TIMESTAMP)),
                                sourcePath = cursor.getString(cursor.getColumnIndexOrThrow(C_SOURCE_PATH)),
                                sourceName = cursor.getString(cursor.getColumnIndexOrThrow(C_SOURCE_NAME)),
                                exitCode = cursor.getInt(cursor.getColumnIndexOrThrow(C_EXIT_CODE)),
                                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(C_DURATION_MS)),
                                compilerVersion = cursor.getString(cursor.getColumnIndexOrThrow(C_COMPILER_VERSION)),
                                output = cursor.getString(cursor.getColumnIndexOrThrow(C_OUTPUT))
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
