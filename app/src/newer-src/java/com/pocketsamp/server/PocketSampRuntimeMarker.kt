package com.pocketsamp.server

/** Marker keeping the PocketSAMP compile-time API confined to the modern flavor. */
internal object PocketSampRuntimeMarker

/**
 * Host-facing PocketSAMP entry point for the modern flavor.
 *
 * The commands that need PocketSAMP's own types (language and theme enums, the
 * console sink) are exposed here, so the shared `main` source set never imports
 * the `:pocketsamp` module directly. The Nougat flavor ships a no-op twin of this
 * object, which is why both flavors can compile the same call sites.
 */
internal object ServerApp {

    /**
     * Installs the process-wide pieces PocketSAMP expects and mirrors its console
     * into [onConsoleLine]. Safe to call on every launch.
     */
    fun install(
        context: android.content.Context,
        onConsoleLine: ((String) -> Unit)? = null
    ) {
        com.pocketsamp.server.ServerApp.install(context)
        if (onConsoleLine != null) {
            com.pocketsamp.server.service_handler.RuntimeService.setConsoleLogSink(onConsoleLine)
        }
    }

    /** Opens the embedded runtime UI; [language] and [theme] use the config value strings. */
    fun launch(context: android.content.Context, language: String, theme: String) {
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
        com.pocketsamp.server.ServerApp.launch(context)
    }
}
