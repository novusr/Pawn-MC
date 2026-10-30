package com.rvdjv.pawnmc.data.log
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/** SQLite history for PocketSAMP's live server console; separate from compiler output. */
object _pocket_store {
    data class Entry(val timestamp: Long, val level: String, val message: String)

    private const val DATABASE_NAME = "console_logging.db"
    private const val TABLE_NAME = "_console_"
    private const val MAX_LINES = 2_000
    private const val KEY_ID = "_id"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_LEVEL = "level"
    private const val KEY_MESSAGE = "message"

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
        val dbHelper = helperFor(context) ?: return
        runCatching {
            dbHelper.writableDatabase.use { db ->
                db.insert(
                    TABLE_NAME,
                    null,
                    ContentValues().apply {
                        put(KEY_TIMESTAMP, System.currentTimeMillis())
                        put(KEY_LEVEL, level)
                        put(KEY_MESSAGE, message)
                    }
                )
                db.execSQL(
                    "DELETE FROM $TABLE_NAME WHERE $KEY_ID NOT IN (" +
                        "SELECT $KEY_ID FROM $TABLE_NAME ORDER BY $KEY_TIMESTAMP DESC LIMIT $MAX_LINES)"
                )
            }
        }
    }

    /** Returns recent console lines, newest first. */
    fun recentEntries(context: Context, limit: Int = 200): List<Entry> {
        val dbHelper = helperFor(context) ?: return emptyList()
        return runCatching {
            dbHelper.readableDatabase.query(
                TABLE_NAME,
                arrayOf(KEY_TIMESTAMP, KEY_LEVEL, KEY_MESSAGE),
                null,
                null,
                null,
                null,
                "$KEY_TIMESTAMP DESC",
                limit.coerceIn(1, MAX_LINES).toString()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Entry(
                                timestamp = cursor.getLong(0),
                                level = cursor.getString(1),
                                message = cursor.getString(2)
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun classify(message: String): String = when {
        message.contains("error", ignoreCase = true) || message.contains("failed", ignoreCase = true) -> "error"
        message.contains("warning", ignoreCase = true) -> "warning"
        else -> "info"
    }
}
