package com.rvdjv.pawnmc.data.config

import android.content.Context
import kotlin.math.min

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
        if (language == CompilerConfig.AppLanguage.ID) {
            enToIdMap[normalizedKey.lowercase()]?.let { return it }
            enToIdMap[fallback.trim().lowercase()]?.let { return it }
        } else {
            idToEnMap[normalizedKey.lowercase()]?.let { return it }
            idToEnMap[fallback.trim().lowercase()]?.let { return it }
        }

        return fallback
    }

    fun translate(baseKey: String, language: CompilerConfig.AppLanguage, fallback: String): String {
        return get(baseKey, language, fallback)
    }

    companion object {
        private const val ASSET_PATH = "_dat/_extract.dat"

        private val DEFAULT_DATA = """
# External compile-time localization data for PawnMC
0x01:settings.title.id:0x02:Pengaturan
0x01:settings.title.en:0x02:Settings
0x01:settings.general.id:0x02:Umum
0x01:settings.general.en:0x02:General
0x01:settings.compiler.version.id:0x02:Versi Compiler
0x01:settings.compiler.version.en:0x02:Compiler Version
0x01:settings.language.id:0x02:Bahasa
0x01:settings.language.en:0x02:Language
0x01:settings.language.selector.id:0x02:Pilih Bahasa
0x01:settings.language.selector.en:0x02:Select Language
0x01:settings.theme.id:0x02:Tema
0x01:settings.theme.en:0x02:Theme
0x01:settings.theme.selector.id:0x02:Pilih Tema
0x01:settings.theme.selector.en:0x02:Select Theme
0x01:settings.compiler.options.id:0x02:Opsi Compiler
0x01:settings.compiler.options.en:0x02:Compiler Options
0x01:settings.option.semicolons.id:0x02:Titik Koma Wajib
0x01:settings.option.semicolons.en:0x02:Mandatory Semicolons
0x01:settings.option.semicolons.desc.id:0x02:Wajibkan titik koma di akhir setiap pernyataan
0x01:settings.option.semicolons.desc.en:0x02:Require semicolons at the end of statements
0x01:settings.option.parentheses.id:0x02:Tanda Kurung Wajib
0x01:settings.option.parentheses.en:0x02:Mandatory Parentheses
0x01:settings.option.parentheses.desc.id:0x02:Wajibkan tanda kurung pada pernyataan kontrol
0x01:settings.option.parentheses.desc.en:0x02:Require parentheses in control statements
0x01:settings.option.ignorecase.id:0x02:Abaikan Huruf Besar/Kecil
0x01:settings.option.ignorecase.en:0x02:Filesystem
0x01:settings.option.ignorecase.desc.id:0x02:Normalisasi file proyek dan include agar referensi huruf besar/kecil dapat dikompilasi dengan andal.
0x01:settings.option.ignorecase.desc.en:0x02:Normalize project files and includes so mixed-case filenames and #include references compile reliably.
0x01:settings.option.explain.id:0x02:Jelaskan Output
0x01:settings.option.explain.en:0x02:Explain Ouput
0x01:settings.option.explain.desc.id:0x02:Tambahkan penjelasan yang mudah dipahami di samping peringatan, pesan error, dan pesan fatal.
0x01:settings.option.explain.desc.en:0x02:Add human-readable explanations next to warnings, errors, and fatal messages extracted from the compiler log.
0x01:settings.option.customflags.id:0x02:Flag Kustom
0x01:settings.option.customflags.en:0x02:Custom Flags
0x01:settings.option.customflags.desc.id:0x02:Parameter compiler tambahan, dipisahkan spasi
0x01:settings.option.customflags.desc.en:0x02:Extra compiler parameters, space separated (See: /novusr/configuration)
0x01:settings.include.paths.id:0x02:Path Include
0x01:settings.include.paths.en:0x02:Include Paths
0x01:settings.include.add.id:0x02:Tambah Path Include
0x01:settings.include.add.en:0x02:Add Include Path
0x01:settings.include.empty.id:0x02:Belum ada path include yang dikonfigurasi
0x01:settings.include.empty.en:0x02:No include paths configured
0x01:settings.updates.id:0x02:Pembaruan
0x01:settings.updates.en:0x02:Updates
0x01:settings.updates.check.id:0x02:Periksa Pembaruan
0x01:settings.updates.check.en:0x02:Check for updates
0x01:settings.updates.ready.id:0x02:Perbarui PawnMC
0x01:settings.updates.ready.en:0x02:Update PawnMC
0x01:settings.updates.latest.id:0x02:Anda menggunakan versi terbaru
0x01:settings.updates.latest.en:0x02:You're on the latest version
0x01:settings.about.id:0x02:Tentang
0x01:settings.about.en:0x02:About
0x01:settings.close.id:0x02:Tutup
0x01:settings.close.en:0x02:Close
0x01:settings.cancel.id:0x02:Batal
0x01:settings.cancel.en:0x02:Cancel
0x01:settings.save.id:0x02:Simpan
0x01:settings.save.en:0x02:Save
0x01:settings.back.id:0x02:Kembali
0x01:settings.back.en:0x02:Back
        """.trimIndent()

        fun load(context: Context): AppLocalization {
            val assetData = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            }.getOrNull()?.takeIf { it.isNotBlank() }

            val fileData = if (assetData == null) {
                runCatching {
                    val directFile = java.io.File(ASSET_PATH)
                    if (directFile.exists()) directFile.readText() else null
                }.getOrNull()?.takeIf { it.isNotBlank() }
            } else null

            val data = assetData ?: fileData ?: DEFAULT_DATA
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
