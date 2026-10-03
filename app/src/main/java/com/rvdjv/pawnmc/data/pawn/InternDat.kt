package com.rvdjv.pawnmc.data.pawn

import android.content.Context
import java.io.File

internal data class PawnInternalSymbol(
    val include: String,
    val kind: PawnItemKind,
    val name: String,
    val description: String,
)

internal data class InternDatset(
    val items: List<_item>,
    val symbols: List<PawnInternalSymbol>,
)

internal object InternDat {

    private const val ASSET_PATH = "_internal.dat"
    private const val FALLBACK_PATH = "_dat/_internal.dat"

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

        raw.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val clean = line.replace("\u0000", "")
                if (!clean.startsWith("0x01:")) return@forEach
                val splitIndex = clean.indexOf(":0x02:")
                if (splitIndex < 0) return@forEach

                val key = clean.substring(5, splitIndex).split('|')
                val description = clean.substring(splitIndex + 6).trim()
                if (description.isEmpty()) return@forEach

                when {
                    key.size == 3 && key[0] == "item" -> {
                        val kind = runCatching { PawnItemKind.valueOf(key[1]) }.getOrNull()
                            ?: return@forEach
                        items += _item(key[2], description, kind)
                    }
                    key.size == 3 && key[0] in setOf("native", "forward") -> {
                        symbols += PawnInternalSymbol(
                            include = key[1],
                            kind = if (key[0] == "native") PawnItemKind.FUNCTION else PawnItemKind.CALLBACK,
                            name = key[2],
                            description = description,
                        )
                    }
                }
            }

        return InternDatset(items, symbols)
    }

    private fun readFromFileSystem(): String? =
        sequenceOf(File(FALLBACK_PATH), File("../$FALLBACK_PATH"), File("_internal.dat"))
            .mapNotNull { file -> runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull() }
            .firstOrNull { it.isNotBlank() }
}