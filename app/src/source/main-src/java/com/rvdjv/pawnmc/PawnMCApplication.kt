package com.rvdjv.pawnmc

import android.app.Application
import android.content.Context
import android.util.Log
import com.rvdjv.pawnmc.apps_data.data_crash.ActivityJournal
import com.rvdjv.pawnmc.apps_data.data_crash.CrashHandler
import com.rvdjv.pawnmc.apps_data.data_crash.Minidump
import java.lang.ref.WeakReference

/**
 * Process entry point: the only place that runs before every activity, service and
 * worker, and therefore the only place a crash handler can be installed early enough.
 *
 * Installing from an activity was too late twice over: a crash while the process starts
 * (theme resolution, locale preparation) produced no report at all, and the embedded
 * PocketSAMP service, which outlives every activity, ran unprotected between the back
 * press that left the runtime UI and the next launch.
 *
 * Order matters and is deliberate:
 *
 *  1. [CrashHandler.install] first, so everything after it is covered;
 *  2. [ActivityJournal.startSession], so the journal has a session header before any
 *     screen records something into it;
 *  3. dump recovery and the startup line, both cheap and non-blocking.
 */
class PawnMCApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = WeakReference(this)

        // 1. Crash coverage before anything else can fail.
        CrashHandler.install(this)
        CrashHandler.processStartedAt = System.currentTimeMillis()

        // 2. A fresh journal session, so a restart is visible in the file.
        ActivityJournal.startSession(this)

        // 3. Housekeeping runs on the journal's own background thread.
        ActivityJournal.record(this, "app", "process start pid=${android.os.Process.myPid()} version=${BuildConfig.VERSION_NAME}")
        runCatching {
            val moved = Minidump.migratePrivateDumps(this)
            if (moved > 0) {
                ActivityJournal.record(this, "crash", "moved $moved private dump(s) to Documents/PawnMC/crashreport")
            }
        }.onFailure { Log.w(TAG, "dump recovery failed", it) }
    }

    override fun onTerminate() {
        // Only called on emulators and in tests, but it is the one place a clean shutdown
        // can be recorded, which is what makes "no exit line" meaningful.
        ActivityJournal.record(this, "app", "process end (clean)")
        ActivityJournal.flushAndAwait()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "PawnMCApp"

        /**
         * Application context for code that has no context of its own (the minidump
         * writer's RAM query). A weak reference, so a leaked static can never keep the
         * application alive.
         */
        @Volatile
        var appContext: WeakReference<Context>? = null
            private set
    }
}
