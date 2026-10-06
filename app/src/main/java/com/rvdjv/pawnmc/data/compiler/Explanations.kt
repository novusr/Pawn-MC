package com.rvdjv.pawnmc.data.compiler

import android.content.Context
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.config.TomlData
import java.io.File

/**
 * Loader of the external compiler explanation table.
 *
 * `data/_data_2026_exp.toml` is the single source of truth for the local explanations of
 * Pawn compiler messages. The asset is read once per process and cached; there is
 * deliberately no bundled copy in Kotlin sources, so a message only ever has to be
 * maintained in one place.
 *
 * The file is TOML: one `[explain."<code>"]` table per message code and the
 * languages as leaves, which replaces the old `0x01:<code>:0x02:<explanation>`
 * markers. When a code is listed twice the last entry wins, exactly like the
 * previous in-memory map.
 */
object Explanations {

    private const val ASSET_PATH = "_data_2026_exp.toml"

    private const val FALLBACK_PATH = "data/_data_2026_exp.toml"

    /** Prefix of every explanation table, e.g. `explain.` in `[explain."013"]`. */
    private const val TABLE_PREFIX = "explain"

    @Volatile
    private var cached: Map<String, String>? = null

    /**
     * Application context of the first [load] call, or `null` before it.
     *
     * Kept so [ensureLoaded] can finish the load on whatever thread happens to need
     * the table first, without the caller having to pass a context around.
     */
    @Volatile
    private var appContext: Context? = null

    /**
     * Reads and caches the table from the packaged asset.
     *
     * The caller is expected to be off the main thread: the asset is read, parsed and
     * indexed in one go, which is why the app defers this load instead of doing it
     * during `MainActivity.onCreate`.
     */
    fun load(context: Context) {
        if (cached != null) return
        val n_context = context.applicationContext ?: context
        appContext = n_context
        val assetData = runCatching {
            n_context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrNull()
        cached = parse(assetData ?: readFromFileSystem() ?: "")
    }

    /**
     * Makes sure the table is loaded, loading it on this thread when it is not.
     *
     * Before the background load (started by the app at launch) has finished the
     * table derives itself from the asset on demand, so an early caller still sees
     * real explanations instead of an empty map. Once loaded the check is a single
     * volatile read.
     */
    private fun ensureLoaded() {
        if (cached != null) return
        synchronized(this) {
            if (cached != null) return
            val n_context = appContext
            if (n_context != null) {
                load(n_context)
            } else {
                cached = parse(readFromFileSystem() ?: "")
            }
        }
    }

    /** Explanation for [code], or `null` when the code is not documented locally. */
    fun explanationFor(
        code: String,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): String? {
        ensureLoaded()
        return resolve(cached.orEmpty(), code, language)
    }

    /** Returns every known explanation, mainly for diagnostics and tests. */
    fun allExplanations(): Map<String, String> {
        ensureLoaded()
        return cached.orEmpty()
    }

    internal fun resolve(
        explanations: Map<String, String>,
        code: String,
        language: CompilerConfig.AppLanguage
    ): String? {
        val normalizedCode = code.trim()
        return explanations["$normalizedCode.${language.value}"]
            ?: explanations[normalizedCode]
            ?: explanations["$normalizedCode.${CompilerConfig.AppLanguage.EN.value}"]
    }

    private fun readFromFileSystem(): String? =
        sequenceOf(File(FALLBACK_PATH), File(ASSET_PATH))
            .mapNotNull { file -> runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() }
            .firstOrNull { it.isNotBlank() }

    /**
     * Parses the raw TOML table into a `code -> explanation` map.
     *
     * Every `[explain."<code>"]` table contributes one entry per language leaf,
     * stored as `<code>.<language>`; a leaf named `en` is also stored under the
     * bare `<code>` so an unlocalised message still resolves.
     */
    fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        val document = TomlData.parse(raw)
        val result = linkedMapOf<String, String>()

        document.tablesUnder(TABLE_PREFIX).forEach { (path, entries) ->
            val code = path.removePrefix("$TABLE_PREFIX.")
            if (code.isBlank()) return@forEach
            entries.forEach { (language, explanation) ->
                if (explanation.isBlank()) return@forEach
                result["$code.$language"] = explanation
            }
            entries[CompilerConfig.AppLanguage.EN.value]?.let { english ->
                if (english.isNotBlank()) result[code] = english
            }
        }

        return result
    }
}