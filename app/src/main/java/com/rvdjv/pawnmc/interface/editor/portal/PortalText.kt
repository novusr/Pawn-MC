package com.rvdjv.pawnmc.`interface`.editor.portal

import android.content.Context
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.config.TomlData

/**
 * Terminal strings and the `docs` markdown, read from the 2026 TOML asset.
 *
 * Every table is named after the terminal key (`[terminal.help]`, `[terminal.docs]`,
 * ...) and the languages are the leaves, so a lookup asks for the same dotted name
 * the console already uses.
 *
 * The table is parsed once per process and cached: it is a few kilobytes and does
 * not change while the app runs, while every rendered line asks for a string.
 */
internal object PortalText {

    private const val ASSET_PATH = "_data_2026_term.toml"

    @Volatile
    private var cached: Map<String, String>? = null

    /** Looks up [key] for [language], falling back to English and then to the key. */
    fun get(context: Context, key: String, language: CompilerConfig.AppLanguage): String {
        val entries = cached ?: synchronized(this) {
            cached ?: runCatching {
                context.applicationContext.assets.open(ASSET_PATH)
                    .bufferedReader().use { parse(it.readText()) }
            }.getOrDefault(emptyMap()).also { cached = it }
        }
        return entries["$key.${language.value}"]
            ?: entries["$key.en"]
            ?: key
    }

    /**
     * Flattens the tables into `terminal.<key>.<lang> -> text`.
     *
     * The values are stored as single-line TOML strings, so the literal escape
     * sequences that carry the console layout are expanded into real line breaks
     * here, once, before anything renders them.
     */
    internal fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        val document = TomlData.parse(raw)
        val result = linkedMapOf<String, String>()
        document.tablesUnder("").forEach { (table, entries) ->
            if (table.isBlank()) return@forEach
            entries.forEach { (language, value) ->
                result["$table.$language"] = value.replace("\\n", "\n")
            }
        }
        return result
    }
}
