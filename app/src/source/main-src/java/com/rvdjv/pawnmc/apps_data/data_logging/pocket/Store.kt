package com.rvdjv.pawnmc.apps_data.data_logging
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/** SQLite history for PocketSAMP's live server console; separate from compiler output. */
object _pocket_store {
    /** One validated row in the PocketSAMP console history. */
    data class Entry(val timestamp: Long, val level: String, val message: String)

    private object Schema {
        const val DATABASE_NAME = "console_logging.db"
        const val TABLE_NAME = "_console_"
        const val MAX_LINES = 2_000
        const val KEY_ID = "_id"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_LEVEL = "level"
        const val KEY_MESSAGE = "message"
    }

    private const val DATABASE_NAME = Schema.DATABASE_NAME
    private const val TABLE_NAME = Schema.TABLE_NAME
    private const val MAX_LINES = Schema.MAX_LINES
    private const val KEY_ID = Schema.KEY_ID
    private const val KEY_TIMESTAMP = Schema.KEY_TIMESTAMP
    private const val KEY_LEVEL = Schema.KEY_LEVEL
    private const val KEY_MESSAGE = Schema.KEY_MESSAGE

    private class Helper(context: Context, file: File) :
        SQLiteOpenHelper(context, file.absolutePath, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE_NAME (" +
                    "$KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$KEY_TIMESTAMP INTEGER NOT NULL, " +
                    "$KEY_LEVEL TEXT NOT NULL, " +
                    "$KEY_MESSAGE TEXT NOT NULL)"
            )
            db.execSQL("CREATE INDEX idx_pocketsamp_time ON $TABLE_NAME ($KEY_TIMESTAMP DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    @Volatile
    private var helper: Helper? = null
    private fun helperFor(context: Context): Helper? {
        helper?.let { return it }
        synchronized(this) {
            helper?.let { return it }
            return runCatching {
                val externalRoot = context.applicationContext.getExternalFilesDir(null)
                val root = externalRoot?.let { File(it, "PawnMC/logs") }
                    ?: File(context.applicationContext.filesDir, "PawnMC/logs")
                root.mkdirs()
                Helper(context.applicationContext, File(root, DATABASE_NAME))
            }.getOrNull().also { helper = it }
        }
    }

    /** Store one console line without involving compiler-specific fields or retention. */
    fun record(context: Context, message: String, level: String = classify(message)) {
        if (message.isBlank()) return
        // Mirror into the activity journal: the console is the runtime's own record, the
        // journal is the timeline a crash dump reads, and they need not contain the same
        // lines. Content lines are sampled so a chatty server cannot drown the journal.
        if (shouldJournal(message)) {
            com.rvdjv.pawnmc.apps_data.data_crash.ActivityJournal.record(context, "server", message)
        }
        val dbHelper = helperFor(context) ?: return
        val entry = Entry(System.currentTimeMillis(), normalizeLevel(level), message)
        runCatching {
            dbHelper.writableDatabase.use { db ->
                db.insert(TABLE_NAME, null, entry.toContentValues())
                pruneOldRows(db)
            }
        }
    }

    private fun Entry.toContentValues() = ContentValues().apply {
        put(KEY_TIMESTAMP, timestamp)
        put(KEY_LEVEL, level)
        put(KEY_MESSAGE, message)
    }

    private fun normalizeLevel(level: String): String =
        level.lowercase().takeIf { it in setOf("error", "warning", "info") } ?: "info"

    private fun pruneOldRows(db: SQLiteDatabase) {
        db.execSQL(
            "DELETE FROM $TABLE_NAME WHERE $KEY_ID NOT IN (" +
                "SELECT $KEY_ID FROM $TABLE_NAME ORDER BY $KEY_TIMESTAMP DESC LIMIT $MAX_LINES)"
        )
    }

    /** Returns recent console lines, newest first. */
    fun recentEntries(context: Context, limit: Int = 200): List<Entry> {
        val dbHelper = helperFor(context) ?: return emptyList()
        return runCatching { readRecent(dbHelper.readableDatabase, limit.coerceIn(1, MAX_LINES)) }
            .getOrDefault(emptyList())
    }

    private fun readRecent(db: SQLiteDatabase, limit: Int): List<Entry> =
        db.query(
            TABLE_NAME, arrayOf(KEY_TIMESTAMP, KEY_LEVEL, KEY_MESSAGE), null, null, null, null,
            "$KEY_TIMESTAMP DESC", limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
                }
            }
        }

    /**
     * Keeps noteworthy console lines and samples the rest.
     *
     * Error/warning lines always survive; ordinary chatter is taken one line in eight,
     * which still shows the shape of a session without filling a phone's storage.
     */
    private fun shouldJournal(message: String): Boolean {
        val noteworthy = message.contains("error", ignoreCase = true) ||
            message.contains("warning", ignoreCase = true) ||
            message.contains("failed", ignoreCase = true) ||
            message.contains("crash", ignoreCase = true) ||
            message.contains("exit", ignoreCase = true) ||
            message.contains("start", ignoreCase = true) ||
            message.contains("stop", ignoreCase = true)
        if (noteworthy) return true
        sampleCounter = (sampleCounter + 1) and 7
        return sampleCounter == 0
    }

    /** Counter of the sampling above; only touched from the journal's serial writer. */
    @Volatile
    private var sampleCounter = 0

    private fun classify(message: String): String = when {
        message.contains("error", ignoreCase = true) || message.contains("failed", ignoreCase = true) -> "error"
        message.contains("warning", ignoreCase = true) -> "warning"
        else -> "info"
    }
}
