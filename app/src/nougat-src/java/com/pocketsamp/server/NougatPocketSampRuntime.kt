package com.pocketsamp.server

import android.content.Context
import android.widget.Toast

/**
 * Android Nougat compatibility entry point; Android 8+ uses the embedded PocketSAMP runtime.
 *
 * The Nougat flavor does not link the `:pocketsamp` module (the runtime needs API 26),
 * so it provides the same host-facing API as the modern flavor's `ServerApp`, with no
 * dependency on any PocketSAMP type. That keeps the shared `main` source set compiling
 * for both flavors without a source-set split.
 */
internal object ServerApp {
    fun install(context: Context, onConsoleLine: ((String) -> Unit)? = null) = Unit

    /** [language] and [theme] are ignored: this flavor has no runtime to open. */
    fun launch(context: Context, language: String, theme: String) {
        Toast.makeText(context, "PocketSAMP requires Android 8.0 or newer", Toast.LENGTH_LONG).show()
    }
}
