package com.pocketsamp.server

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pocketsamp.server.crash_handler.Handler

/**
 * Entry point PawnMC uses to embed PocketSAMP.
 *
 * The upstream project was a standalone application with its own `Application` class;
 * inside PawnMC it is a library module hosted in the host activity, so the two things
 * the application class used to do move here:
 *
 *  - [install] replays the global crash handler (the isolated `:crash` process shows
 *    the report and restarts cleanly);
 *  - [launch] starts [MainActivity] on top of the caller's task, which is how
 *    the PawnMC main screen opens the runtime and how the back button returns to it.
 *
 * Nothing here renames or rebrands the module: the class, namespace and strings keep
 * the upstream PocketSAMP identity.
 */
object SampApp {

    /**
     * Installs the process-wide pieces PocketSAMP expects. Call once from the host
     * `Application.onCreate` (or the hosting activity) before the UI is shown.
     */
    fun install(context: Context) {
        val app = context.applicationContext as? Application ?: return
        Handler.install(app)
    }

    /**
     * Opens the PocketSAMP runtime UI. The host stays in the task below, so the
     * system back button (or the activity finishing itself) returns to the main
     * PawnMC interface without any extra wiring.
     */
    fun launch(context: Context) {
        val activity = context as? androidx.activity.ComponentActivity
            ?: throw IllegalArgumentException("PocketSAMP must be launched from an Activity context")
        activity.startActivity(Intent(activity, MainActivity::class.java))
    }
}
