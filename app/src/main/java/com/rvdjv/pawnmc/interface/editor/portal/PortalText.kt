package com.rvdjv.pawnmc.`interface`.editor.portal

import android.content.Context
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.config.TomlData

/**
 * Terminal strings and the `docs` markdown, read from the 2026 TOML asset.
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

    /** Flattens the tables into `terminal.<key>.<lang> -> text`. */
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
