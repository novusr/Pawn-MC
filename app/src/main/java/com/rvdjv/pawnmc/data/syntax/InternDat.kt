package com.rvdjv.pawnmc.data.syntax

import android.content.Context
import com.rvdjv.pawnmc.data.config.TomlData
import java.io.File

internal data class PawnInternalSymbol(
    val include: String,
    val kind: PawnItemKind,
    val name: String,
    val description: String,
    val descriptionId: String? = null,
    val descriptionEs: String? = null,
    val descriptionRu: String? = null,
)

internal data class InternDatset(
    val items: List<_item>,
    val symbols: List<PawnInternalSymbol>,
)

internal object InternDat {

    private const val ASSET_PATH = "_data_2026_intern.toml"
    private const val FALLBACK_PATH = "data/_data_2026_intern.toml"

    /** Language leaf keys of every symbol entry. */
    private const val EN = "en"
    private const val ID = "id"

    /** Table holding the language items, e.g. `[item.KEYWORD]`. */
    private const val ITEM_PREFIX = "item"

    /** Table holding the include natives, e.g. `[native."a_samp.inc"]`. */
    private const val NATIVE_PREFIX = "native"

    /** Table holding the include forwards, e.g. `[forward."a_samp.inc"]`. */
    private const val FORWARD_PREFIX = "forward"

    @Volatile
    private var cached: InternDatset? = null

    /**
     * Application context of the first [load] call, or `null` before it.
     *
     * Kept so [dataset] can finish the load on whatever thread happens to need the
     * symbols first, without the caller having to pass a context around.
     */
    @Volatile
    private var appContext: Context? = null

    /**
     * Reads, parses and caches the symbol table from the packaged asset.
     *
     * The caller is expected to be off the main thread: this file is the largest of
     * the packaged tables and the parse builds one tiny map per symbol, so the app
     * warms it up in the background instead of during `MainActivity.onCreate`.
     */
    fun load(context: Context) {
        val n_context = context.applicationContext ?: context
        appContext = n_context
        val assetData = runCatching {
            n_context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrNull()
        cached = parse(assetData ?: readFromFileSystem().orEmpty())
    }

    /**
     * The parsed table, loaded on demand when the background warm-up has not finished.
     *
     * Deriving the table here keeps an early editor caller correct instead of handing
     * it empty symbols; once loaded the check is a single volatile read.
     */
    fun dataset(): InternDatset = cached ?: synchronized(this) {
        cached ?: run {
            val n_context = appContext
            if (n_context != null) {
                load(n_context)
            } else {
                cached = parse(readFromFileSystem().orEmpty())
            }
            cached ?: InternDatset(emptyList(), emptyList())
        }
    }

    /**
     * Parses the TOML symbol table.
     *
     * The old `0x01:item|KIND|name:0x02:...` rows are now real tables:
     * `[item.KEYWORD]`, `[native."a_samp.inc"]` and `[forward."a_samp.inc"]`,
     * with the symbol name as the key and `{ en = "...", id = "..." }` as the
     * value, so the kind and the include are read from the table name instead of
     * being encoded in a numeric marker.
     */
    internal fun parse(raw: String): InternDatset {
        if (raw.isBlank()) return InternDatset(emptyList(), emptyList())

        val document = TomlData.parse(raw)
        val items = mutableListOf<_item>()
        val symbols = mutableListOf<PawnInternalSymbol>()

        document.inlineTablesUnder(ITEM_PREFIX).forEach { (path, entries) ->
            val kindName = path.removePrefix("$ITEM_PREFIX.")
            val kind = runCatching { PawnItemKind.valueOf(kindName) }.getOrNull() ?: return@forEach
            entries.forEach { (name, fields) ->
                val description = fields[EN] ?: return@forEach
                if (description.isBlank()) return@forEach
                items += _item(
                    name = name,
                    description = description,
                    kind = kind,
                    descriptionId = fields[ID],
                )
            }
        }

        val includePrefixes = listOf(
            NATIVE_PREFIX to PawnItemKind.FUNCTION,
            FORWARD_PREFIX to PawnItemKind.CALLBACK,
        )
        includePrefixes.forEach { (prefix, kind) ->
            document.inlineTablesUnder(prefix).forEach { (path, entries) ->
                val include = path.removePrefix("$prefix.")
                if (include.isBlank()) return@forEach
                entries.forEach { (name, fields) ->
                    val description = fields[EN] ?: return@forEach
                    if (description.isBlank()) return@forEach
                    symbols += PawnInternalSymbol(
                        include = include,
                        kind = kind,
                        name = name,
                        description = description,
                        descriptionId = fields[ID],
                    )
                }
            }
        }

        return InternDatset(items, symbols)
    }

    private fun readFromFileSystem(): String? =
        sequenceOf(File(FALLBACK_PATH), File("../$FALLBACK_PATH"), File(ASSET_PATH))
            .mapNotNull { file -> runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull() }
            .firstOrNull { it.isNotBlank() }

    /**
     * Symbols mined from the file the user has open, merged after the shipped table.
     *
     * A project can declare its own natives and forwards (`native my_ext();`,
     * `forward OnCustomHook();`); highlighting those as plain identifiers made the
     * editor look like it did not understand the file it was showing. The scan is a
     * line-based regex pass, not a parser: it never has to be perfect, it only has to
     * be cheap enough to run on every load and conservative enough to never invent a
     * symbol the compiler would not accept.
     *
     * @param sourceText the buffer as shown in the editor
     * @return one symbol per declaration, in file order
     */
    internal fun symbolsFromSource(sourceText: String): List<PawnInternalSymbol> {
        if (sourceText.isBlank()) return emptyList()

        val found = mutableListOf<PawnInternalSymbol>()
        val seen = mutableSetOf<String>()
        sourceText.lineSequence().forEach { rawLine ->
            // A declaration has to start the statement, so a commented-out or indented
            // mention inside a string is naturally skipped.
            val line = rawLine.trim()
            for ((keyword, kind) in SOURCE_DECLARATIONS) {
                if (!line.startsWith(keyword)) continue
                val rest = line.removePrefix(keyword).trimStart()
                val name = rest.takeWhile { it.isLetterOrDigit() || it == '_' }
                if (name.isEmpty() || name.first().isDigit()) continue
                if (seen.add(name)) {
                    found += PawnInternalSymbol(
                        include = "",
                        kind = kind,
                        name = name,
                        description = line.removeSuffix(";").trim(),
                    )
                }
            }
        }
        return found
    }

    /** Declaration keywords that introduce an externally visible symbol. */
    private val SOURCE_DECLARATIONS = listOf(
        "native" to PawnItemKind.FUNCTION,
        "forward" to PawnItemKind.CALLBACK,
    )
}