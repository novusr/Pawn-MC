package com.pocketsamp.server

import android.content.Context
import com.rvdjv.pawnmc.data.runtime.PocketSampGateway

/** Marker keeping the PocketSAMP compile-time API confined to the modern flavor. */
internal object PocketSampRuntimeMarker

/**
 * Modern-flavor implementation of [PocketSampGateway.PocketSampHook].
 *
 * This file is the only place in PawnMC that names PocketSAMP's own types (`ServerApp`,
 * `Locales`, `RuntimeService`). It lives in `src/newer-src`, so it is compiled only for
 * the flavor that links `:pocketsamp`; `main` sees the hook interface instead, which is
 * what lets both flavors compile the same call sites.
 *
 * `PocketSampBootstrap` registers it the first time the gateway is asked to do something.
 */
internal object NewerPocketSampHook : PocketSampGateway.PocketSampHook {

    override fun install(context: Context, onConsoleLine: ((String) -> Unit)?) {
        ServerApp.install(context)
        if (onConsoleLine != null) {
            com.pocketsamp.server.service_handler.RuntimeService.setConsoleLogSink(onConsoleLine)
        }
    }

    override fun launch(context: Context, language: String, theme: String) {
        Locales.setLanguage(
            when (language) {
                "id" -> Locales.Lang.ID
                "es" -> Locales.Lang.ES
                "ru" -> Locales.Lang.RU
                "tl" -> Locales.Lang.TL
                else -> Locales.Lang.EN
            }
        )
        Locales.setTheme(
            when (theme) {
                "light" -> Locales.Theme.LIGHT
                "dark" -> Locales.Theme.DARK
                else -> Locales.Theme.SYSTEM
            }
        )
        ServerApp.launch(context)
    }
}

/** Registered automatically when this file's class loader initialises it. */
internal fun registerNewerPocketSampHook() {
    PocketSampGateway.install(NewerPocketSampHook)
}
