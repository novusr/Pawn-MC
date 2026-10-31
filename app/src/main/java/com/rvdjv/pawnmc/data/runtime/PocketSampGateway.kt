package com.rvdjv.pawnmc.data.runtime

import android.content.Context
import android.util.Log

/**
 * The only place the shared `main` source set talks to the embedded PocketSAMP runtime.
 *
 * `main` is compiled for both flavors, and only the `newer` flavor links the
 * `:pocketsamp` module (its `ServerApp` and `Locales` types do not exist for `nougat`).
 * A source set cannot see a sibling flavor's classes, so the two things that need
 * PocketSAMP types — mapping the language/theme settings onto `Locales`, and hooking the
 * runtime console into the compile log — live behind [PocketSampHook], which each flavor
 * supplies in `src/<flavor>-src`.
 *
 * With no hook installed (unit tests, or a flavor that did not provide one) the calls
 * degrade to no-ops with a log line instead of crashing.
 */
object PocketSampGateway {

    private const val TAG = "PocketSamp"

    /** Flavor-supplied integration, installed once from the app entry point. */
    interface PocketSampHook {
        /** Crash handler and console mirror; idempotent. */
        fun install(context: Context, onConsoleLine: ((String) -> Unit)?)

        /** Opens the runtime UI in [language] (`en`, `id`, …) with [theme] (`dark`, …). */
        fun launch(context: Context, language: String, theme: String)
    }

    @Volatile
    private var hook: PocketSampHook? = null

    /**
     * Resolves the flavor implementation once.
     *
     * `com.pocketsamp.server.NewerPocketSampHook` is reflective on purpose: that class only
     * exists in the `newer` flavor's source set, so a direct reference here would not
     * compile for `nougat`. The `legacy` suffix marks the Nougat flavor (it ships with
     * `applicationIdSuffix = ".legacy"`), so the lookup is skipped there entirely.
     *
     * The registration function is invoked once; afterwards [hook] answers immediately, so
     * the reflection hop happens at most once per process.
     */
    private fun resolveHook(): PocketSampHook? {
        hook?.let { return it }
        // The Nougat flavor has no runtime: its hook only reports the API requirement.
        if (com.rvdjv.pawnmc.BuildConfig.APPLICATION_ID.endsWith(".legacy")) return null
        return runCatching {
            Class.forName("com.pocketsamp.server.NewerPocketSampHookKt")
                .getDeclaredMethod("registerNewerPocketSampHook")
                .invoke(null)
            hook
        }.onFailure { Log.w(TAG, "Modern flavor shipped without a PocketSAMP hook", it) }
            .getOrNull()
    }

    /** Registers a flavor implementation; the reflective lookup above uses this. */
    fun install(hook: PocketSampHook?) {
        this.hook = hook
    }

    /** True when this build actually embeds the runtime. */
    fun isAvailable(): Boolean = resolveHook() != null

    /** Installs the runtime pieces and mirrors its console into [onConsoleLine]. */
    fun prepare(context: Context, onConsoleLine: (String) -> Unit) {
        val n_hook = resolveHook()
        if (n_hook == null) {
            Log.i(TAG, "No PocketSAMP in this build; console mirroring skipped.")
            return
        }
        runCatching { n_hook.install(context.applicationContext, onConsoleLine) }
            .onFailure { Log.e(TAG, "PocketSAMP install failed", it) }
    }

    /** Opens the runtime UI, following the user's language and theme settings. */
    fun launch(context: Context, language: String, theme: String) {
        val n_hook = resolveHook()
        if (n_hook == null) {
            Log.w(TAG, "PocketSAMP is not embedded in this build.")
            return
        }
        runCatching { n_hook.launch(context, language, theme) }
            .onFailure { Log.e(TAG, "PocketSAMP launch failed", it) }
    }
}
