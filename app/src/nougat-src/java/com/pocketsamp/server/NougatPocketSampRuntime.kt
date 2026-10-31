package com.pocketsamp.server

import android.content.Context
import android.widget.Toast
import com.rvdjv.pawnmc.data.runtime.PocketSampGateway

/**
 * Android Nougat entry point of the PocketSAMP integration.
 *
 * This flavor does not link the `:pocketsamp` module (the runtime needs API 26), so it
 * implements [PocketSampGateway.PocketSampHook] with no PocketSAMP types at all. The
 * gateway picks this hook up automatically: `PocketSampGateway` skips its
 * modern-flavor lookup for the `legacy` application id and falls back to this object.
 */
internal object NougatPocketSampHook : PocketSampGateway.PocketSampHook {

    override fun install(context: Context, onConsoleLine: ((String) -> Unit)?) = Unit

    /** There is no runtime to open, so this explains why the button did nothing. */
    override fun launch(context: Context, language: String, theme: String) {
        Toast.makeText(context, "PocketSAMP requires Android 8.0 or newer", Toast.LENGTH_LONG).show()
    }
}

/** Registered from `PocketSampGateway` when the modern-flavor lookup is skipped. */
internal fun registerNougatPocketSampHook() {
    PocketSampGateway.install(NougatPocketSampHook)
}
