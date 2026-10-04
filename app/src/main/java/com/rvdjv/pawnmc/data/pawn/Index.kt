package com.rvdjv.pawnmc.data.pawn

/**
 * Native and forward symbols grouped by the SA-MP include file that declares them.
 * Names and descriptions are loaded from `_dat/_dat_internal.dat`.
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