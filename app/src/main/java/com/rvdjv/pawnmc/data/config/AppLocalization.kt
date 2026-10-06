package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.res.Configuration
import kotlin.math.min
import java.io.File
import java.util.Locale

class AppLocalization private constructor(private val entries: Map<String, String>) {

    private val enToIdMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((k, v) in entries) {
            if (k.endsWith(".en")) {
                val base = k.removeSuffix(".en")
                val idVal = entries["$base.id"]
                if (idVal != null) {
                    map[v.trim().lowercase()] = idVal
                }
            }
        }
        map
    }

    /** English text -> Spanish (Argentina) text. */
    private val enToEsMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((k, v) in entries) {
            if (k.endsWith(".en")) {
                val base = k.removeSuffix(".en")
                val esVal = entries["$base.es"]
                if (esVal != null) {
                    map[v.trim().lowercase()] = esVal
                }
            }
        }
        map
    }

    /** English text -> Russian text. */
    private val enToRuMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((k, v) in entries) {
            if (k.endsWith(".en")) {
                val base = k.removeSuffix(".en")
                val ruVal = entries["$base.ru"]
                if (ruVal != null) {
                    map[v.trim().lowercase()] = ruVal
                }
            }
        }
        map
    }

    /** Russian text -> English text. */
    private val ruToEnMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((k, v) in entries) {
            if (k.endsWith(".ru")) {
                val base = k.removeSuffix(".ru")
                val enVal = entries["$base.en"]
                if (enVal != null) {
                    map[v.trim().lowercase()] = enVal
                }
            }
        }
        map
    }

    /** Spanish (Argentina) text -> English text. */
    private val esToEnMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((k, v) in entries) {
            if (k.endsWith(".es")) {
                val base = k.removeSuffix(".es")
                val enVal = entries["$base.en"]
                if (enVal != null) {
                    map[v.trim().lowercase()] = enVal
                }
            }
        }
        map
    }

    private val idToEnMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((k, v) in entries) {
            if (k.endsWith(".id")) {
                val base = k.removeSuffix(".id")
                val enVal = entries["$base.en"]
                if (enVal != null) {
                    map[v.trim().lowercase()] = enVal
                }
            }
        }
        map
    }

    fun get(key: String, language: CompilerConfig.AppLanguage, fallback: String = key): String {
        val normalizedKey = normalizeKey(key)
        val languageKey = "$normalizedKey.${language.value}"
        val direct = entries[languageKey]
            ?: entries["$normalizedKey.${language.value.lowercase()}"]
            ?: entries[normalizedKey]
        if (direct != null) return direct

        // Value or phrase-based fallback
        when (language) {
            CompilerConfig.AppLanguage.ID -> {
                enToIdMap[normalizedKey.lowercase()]?.let { return it }
                enToIdMap[fallback.trim().lowercase()]?.let { return it }
            }
            CompilerConfig.AppLanguage.ES -> {
                enToEsMap[normalizedKey.lowercase()]?.let { return it }
                enToEsMap[fallback.trim().lowercase()]?.let { return it }
            }
            CompilerConfig.AppLanguage.RU -> {
                enToRuMap[normalizedKey.lowercase()]?.let { return it }
                enToRuMap[fallback.trim().lowercase()]?.let { return it }
            }
            CompilerConfig.AppLanguage.EN -> {
                idToEnMap[normalizedKey.lowercase()]?.let { return it }
                esToEnMap[normalizedKey.lowercase()]?.let { return it }
                ruToEnMap[normalizedKey.lowercase()]?.let { return it }
                idToEnMap[fallback.trim().lowercase()]?.let { return it }
                esToEnMap[fallback.trim().lowercase()]?.let { return it }
                ruToEnMap[fallback.trim().lowercase()]?.let { return it }
            }
        }

        return fallback
    }

    fun translate(baseKey: String, language: CompilerConfig.AppLanguage, fallback: String): String {
        return get(baseKey, language, fallback)
    }

    companion object {
        /**
         * Localisation table shipped as an asset.
         *
         * `_data_2026_ect.toml` replaces the old `_data_mc_26_extract.dat`. Every
         * table is named after the setting it belongs to, so a lookup key is the
         * same dotted name the UI already uses, the languages are the leaves, and
         * the old `0x01` / `0x02` markers are gone.
         */
        private const val ASSET_PATH = "_data_2026_ect.toml"

        private val FALLBACK_PATHS = listOf(
            "data/_data_2026_ect.toml",
            ASSET_PATH
        )

        /**
         * Application context of the first [load] call, or `null` before it.
         *
         * Kept so [cached] can finish the load on whatever thread happens to need the
         * strings first, without the caller having to pass a context around.
         */
        @Volatile
        private var appContext: Context? = null

        @Volatile
        private var cached: AppLocalization? = null

        fun localizedContext(context: Context, language: CompilerConfig.AppLanguage): Context {
            val locale = Locale.forLanguageTag(language.localeTag())
            Locale.setDefault(locale)
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return context.createConfigurationContext(configuration)
        }

        /**
         * Reads the localisation table from `data/_data_2026_ect.toml` only.
         *
         * The asset is the single source of truth; there is deliberately no bundled copy
         * and no comparison between the asset and anything else, so a string only ever has
         * to be maintained in one place.
         */
        fun load(context: Context): AppLocalization {
            val n_context = context.applicationContext ?: context
            appContext = n_context
            val assetData = runCatching {
                n_context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            }.getOrNull()?.takeIf { it.isNotBlank() }

            val fileData = if (assetData == null) {
                FALLBACK_PATHS.asSequence()
                    // `java.io::File` cannot be used as a callable reference: Kotlin reads
                    // `java.io` as a package and stops there, so the constructor has to be
                    // invoked through a lambda.
                    .map { path -> File(path) }
                    .mapNotNull { file -> runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() }
                    .firstOrNull { it.isNotBlank() }
            } else null

            return AppLocalization(parseLocalizationData(assetData ?: fileData.orEmpty()))
        }

        /**
         * The shared localisation table, loaded on demand when it is not ready yet.
         *
         * Each screen calls this from `remember`, so without the cache every screen
         * would re-read and re-parse the whole asset. The main screen only warms the
         * cache up in the background; the first caller that gets there first derives
         * it itself, so no screen can ever observe an empty table.
         */
        fun shared(context: Context): AppLocalization {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                return load(context).also { cached = it }
            }
        }

        /**
         * Flattens the TOML tables into the `dotted.key.<lang> -> value` map the rest
         * of the class works with.
         *
         * `[settings.general]` with `en = "General"` becomes `settings.general.en =
         * General`, which is exactly the shape the language lookup and the
         * phrase-based fallbacks expect, so nothing downstream had to change when
         * the storage format moved from `.dat` to TOML.
         */
        fun parseLocalizationData(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()

            val document = TomlData.parse(raw)
            val result = linkedMapOf<String, String>()
            document.tablesUnder("").forEach { (table, entries) ->
                val normalizedTable = normalizeKey(table)
                if (normalizedTable.isBlank()) return@forEach
                entries.forEach { (language, value) ->
                    result["$normalizedTable.$language"] = value
                }
            }
            return result
        }

        private fun normalizeKey(key: String): String {
            return key.trim()
                .removePrefix("/")
                .replace("\\", "/")
                .trimEnd('/')
                .trim()
        }
    }
}
