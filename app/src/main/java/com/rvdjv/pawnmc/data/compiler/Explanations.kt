package com.rvdjv.pawnmc.data.compiler

import android.content.Context
import java.io.File

/**
 * Loader of the external compiler explanation table.
 *
 * `_dat/_explain.dat` is the single source of truth for the local explanations of
 * Pawn compiler messages. The asset is read once per process and cached; there is
 * deliberately no bundled copy in Kotlin sources, so a message only ever has to be
 * maintained in one place.
 *
 * The table uses the same encoding as `_dat/_extract.dat`:
 * `0x01:<code>:0x02:<explanation>`, `#` starts a comment line. When a code is
 * listed twice the last entry wins, exactly like the previous in-memory map.
 */
object Explanations {

    private const val ASSET_PATH = "_explain.dat"

    private const val FALLBACK_PATH = "_dat/_explain.dat"

    @Volatile
    private var cached: Map<String, String>? = null

    /** Reads and caches the table from the packaged asset. */
    fun load(context: Context) {
        if (cached != null) return
        val appContext = context.applicationContext ?: context
        val assetData = runCatching {
            appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrNull()
        cached = parse(assetData ?: readFromFileSystem() ?: "")
    }

    /** Explanation for [code], or `null` when the code is not documented locally. */
    fun explanationFor(
        code: String,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): String? = resolve(cached.orEmpty(), code, language)

    /** Returns every known explanation, mainly for diagnostics and tests. */
    fun allExplanations(): Map<String, String> = cached.orEmpty()

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
     * Parses the raw table into a `code -> explanation` map.
     *
     * Blank lines and lines starting with `#` are ignored. Both the `0x01:..:0x02:`
     * form and a plain `code=explanation` form are accepted so the file stays easy
     * to edit by hand.
     */
    fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        val result = linkedMapOf<String, String>()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val clean = line.replace("\u0000", "")
                val entry = when {
                    clean.startsWith("0x01:") && clean.contains(":0x02:") -> {
                        val keyValue = clean.removePrefix("0x01:")
                        val splitIndex = keyValue.indexOf(":0x02:")
                        if (splitIndex < 0) null else {
                            keyValue.substring(0, splitIndex).trim() to
                                keyValue.substring(splitIndex + 6).trim()
                        }
                    }
                    clean.contains("=") -> {
                        val index = clean.indexOf('=')
                        clean.substring(0, index).trim() to clean.substring(index + 1).trim()
                    }
                    else -> null
                }

                if (entry != null) {
                    val code = entry.first.trim()
                    val explanation = entry.second.trim()
                    if (code.isNotEmpty() && explanation.isNotEmpty()) {
                        result[code] = explanation
                    }
                }
            }
        return result
    }
}