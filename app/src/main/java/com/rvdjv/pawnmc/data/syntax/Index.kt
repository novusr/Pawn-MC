package com.rvdjv.pawnmc.data.syntax

/**
 * Native and forward symbols grouped by the SA-MP include file that declares them.
 * Names and descriptions are loaded from `data/_data_2026_intern.toml`.
 */
internal data class PawnIncludeSymbols(
    val include: String,
    val natives: List<PawnInternalSymbol>,
    val forwards: List<PawnInternalSymbol>,
)

internal object PawnIndex {

    val INCLUDES: List<PawnIncludeSymbols> by lazy {
        InternDat.dataset().symbols
            .groupBy { it.include }
            .map { (include, symbols) ->
                PawnIncludeSymbols(
                    include = include,
                    natives = symbols.filter { it.kind == PawnItemKind.FUNCTION },
                    forwards = symbols.filter { it.kind == PawnItemKind.CALLBACK },
                )
            }
    }
}