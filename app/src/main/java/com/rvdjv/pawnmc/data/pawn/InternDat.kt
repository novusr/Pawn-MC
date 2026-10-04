package com.rvdjv.pawnmc.data.pawn

import android.content.Context
import java.io.File

internal data class PawnInternalSymbol(
    val include: String,
    val kind: PawnItemKind,
    val name: String,
    val description: String,
    val descriptionId: String? = null,
    val descriptionEs: String? = null,
)

internal data class InternDatset(
    val items: List<_item>,
    val symbols: List<PawnInternalSymbol>,
)

internal object InternDat {

    private const val ASSET_PATH = "_dat_internal.dat"
    private const val FALLBACK_PATH = "_dat/_dat_internal.dat"

    @Volatile
    private var cached: InternDatset? = null

    fun load(context: Context) {
        val appContext = context.applicationContext ?: context
        val assetData = runCatching {
            appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrNull()
        cached = parse(assetData ?: readFromFileSystem().orEmpty())
    }

    fun dataset(): InternDatset = cached ?: synchronized(this) {
        cached ?: parse(readFromFileSystem().orEmpty()).also { cached = it }
    }

    internal fun parse(raw: String): InternDatset {
        val items = mutableListOf<_item>()
        val symbols = mutableListOf<PawnInternalSymbol>()
        val rows = raw.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val clean = line.replace("\u0000", "")
                if (!clean.startsWith("0x01:")) return@mapNotNull null
                val splitIndex = clean.indexOf(":0x02:")
                if (splitIndex < 0) return@mapNotNull null

                val key = clean.substring(5, splitIndex).split('|')
                key to clean.substring(splitIndex + 6).trim()
            }
            .toList()

        fun translationTable(section: String): Map<String, String> =
            rows.mapNotNull { (key, value) ->
                if (key.size != 1 || key[0] != section) return@mapNotNull null
                val pair = value.split(":0x03:", limit = 2)
                if (pair.size != 2 || pair[0].isBlank() || pair[1].isBlank()) null
                else pair[0].trim() to pair[1].trim()
            }.toMap()

        val descriptionTranslations = translationTable("description.id")
        val descriptionTranslationsEs = translationTable("description.es")

        fun localizedTranslation(
            table: Map<String, String>,
            description: String
        ): String? = table[description]
            ?: description.substringBefore(": ").takeIf { it != description }?.let { prefix ->
                table[prefix]?.let { "$it: ${description.substringAfter(": ")}" }
            }

        rows.forEach { (key, rawDescription) ->
            if (key.size == 1 && (key[0] == "description.id" || key[0] == "description.es")) return@forEach
            val descriptionParts = rawDescription.split(":0x03:", limit = 2)
            val description = descriptionParts.first().trim()
            val descriptionId = descriptionParts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                ?: localizedTranslation(descriptionTranslations, description)
            val descriptionEs = localizedTranslation(descriptionTranslationsEs, description)
            if (description.isEmpty()) return@forEach

            when {
                key.size == 3 && key[0] == "item" -> {
                    val kind = runCatching { PawnItemKind.valueOf(key[1]) }.getOrNull()
                        ?: return@forEach
                    items += _item(key[2], description, kind, descriptionId, descriptionEs)
                }
                key.size == 3 && key[0] in setOf("native", "forward") -> {
                    symbols += PawnInternalSymbol(
                        include = key[1],
                        kind = if (key[0] == "native") PawnItemKind.FUNCTION else PawnItemKind.CALLBACK,
                        name = key[2],
                        description = description,
                        descriptionId = descriptionId,
                        descriptionEs = descriptionEs,
                    )
                }
            }
        }

        return InternDatset(items, symbols)
    }

    private fun readFromFileSystem(): String? =
        sequenceOf(File(FALLBACK_PATH), File("../$FALLBACK_PATH"), File("_dat_internal.dat"))
            .mapNotNull { file -> runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull() }
            .firstOrNull { it.isNotBlank() }
}