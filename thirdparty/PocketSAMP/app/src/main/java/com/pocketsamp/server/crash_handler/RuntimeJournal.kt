package com.pocketsamp.server.crash_handler

import android.content.Context
import android.util.Log

/**
 * Journal sink for the runtime's own activities.
 *
 * The host (PawnMC) owns the crash folder and the activity journal; PocketSAMP is a
 * library module here and cannot see the host's classes. This interface is the seam the
 * host fills in when it installs its crash bridge, and every call degrades to a no-op
 * when nothing is installed (standalone builds, unit tests).
 */
object RuntimeJournal {

    /** Writes one journal line for [tag]; supplied by the host. */
    fun interface Sink {
        fun record(context: Context, tag: String, message: String)
    }

    @Volatile
    private var sink: Sink? = null

    fun install(external: Sink?) {
        sink = external
    }

    /**
     * Records [message] under `server`. Never throws: journaling must not be the reason
     * a screen fails, and most call sites run on the main thread.
     */
    fun record(context: Context, message: String, tag: String = "pocketsamp") {
        val target = sink ?: return
        runCatching { target.record(context.applicationContext, tag, message) }
            .onFailure { Log.w("RuntimeJournal", "journal sink failed", it) }
    }
}
