package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.res.Configuration
import kotlin.math.min
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
        private const val ASSET_PATH = "_data_mc_26_extract.dat"

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
         * Reads the localisation table from `data/_data_mc_26_extract.dat` only.
         *
         * The asset is the single source of truth; there is deliberately no bundled copy
         * and no comparison between the asset and anything else, so a string only ever has
         * to be maintained in one place.
         */
        fun load(context: Context): AppLocalization {
            val assetData = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            }.getOrNull()?.takeIf { it.isNotBlank() }

            val fileData = if (assetData == null) {
                sequenceOf(java.io.File("data/_data_mc_26_extract.dat"), java.io.File(ASSET_PATH))
                    .mapNotNull { file -> runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() }
                    .firstOrNull { it.isNotBlank() }
            } else null

            return AppLocalization(parseLocalizationData(assetData ?: fileData.orEmpty()))
        }

        fun parseLocalizationData(raw: String): Map<String, String> {
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
                                val key = keyValue.substring(0, splitIndex).trim()
                                val value = keyValue.substring(splitIndex + 6).trim()
                                key to value
                            }
                        }
                        clean.contains("=") -> {
                            val index = clean.indexOf('=')
                            val key = clean.substring(0, index).trim()
                            val value = clean.substring(index + 1).trim()
                            key to value
                        }
                        else -> null
                    }

                    if (entry != null) {
                        val key = normalizeKey(entry.first)
                        if (key.isNotBlank()) {
                            result[key] = entry.second
                        }
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
