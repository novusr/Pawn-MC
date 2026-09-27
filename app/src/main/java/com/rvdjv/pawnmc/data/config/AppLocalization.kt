package com.rvdjv.pawnmc.data.config

import android.content.Context
import kotlin.math.min

class AppLocalization private constructor(private val entries: Map<String, String>) {

    fun get(key: String, language: CompilerConfig.AppLanguage, fallback: String = key): String {
        val normalizedKey = normalizeKey(key)
        val languageKey = "$normalizedKey.${language.value}"
        return entries[languageKey]
            ?: entries[normalizedKey]
            ?: entries["$normalizedKey.${language.value.lowercase()}"]
            ?: fallback
    }

    fun translate(baseKey: String, language: CompilerConfig.AppLanguage, fallback: String): String {
        return get(baseKey, language, fallback)
    }

    companion object {
        private const val ASSET_PATH = "_dat/_extract.dat"

        fun load(context: Context): AppLocalization {
            val data = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            }.getOrElse {
                ""
            }
            return AppLocalization(parseLocalizationData(data))
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
