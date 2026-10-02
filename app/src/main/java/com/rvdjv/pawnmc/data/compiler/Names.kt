package com.rvdjv.pawnmc.data.compiler

/**
 * Shared constants of the compiler layer.
 *
 * Every file of the compiler package reuses these names so a binary name, folder
 * name or extension is only declared in one place.
 */
internal object Names {

    const val STR_PAWNO_DIR_NAME = "pawno"
    const val STR_QAWNO_DIR_NAME = "qawno"

    const val STR_PAWNCC_BINARY_NAME = "pawncc"
    const val STR_PAWNCC_FILE_NAME = "pawncc.exe"

    const val STR_INCLUDE_DIR_NAME = "include"

    const val STR_PAWN_FILE_EXTENSION = "pawn"
    const val STR_PWN_FILE_EXTENSION = "pwn"
    const val STR_PAWN_SOURCE_SHORT_EXTENSION = "p"
    const val STR_PAWN_INCLUDE_EXTENSION = "inc"

    /** Suffix of the untouched copy created next to a converted folder. */
    const val BACKUP_DIR_SUFFIX = ".backup"

    const val SSCANF_NO_NICE_FEATURES_FLAG = "SSCANF_NO_NICE_FEATURES=1"

    const val AUTO_FALLBACK_ERROR_THRESHOLD = 5

    /** Extensions whose static include references are rewritten. */
    val CONVERTIBLE_EXTENSIONS = setOf(
        STR_PAWN_FILE_EXTENSION,
        STR_PWN_FILE_EXTENSION,
        STR_PAWN_SOURCE_SHORT_EXTENSION,
        STR_PAWN_INCLUDE_EXTENSION
    )

    val INCLUDE_PATH_VARIANTS = listOf(
        "$STR_PAWNO_DIR_NAME/$STR_INCLUDE_DIR_NAME",
        "$STR_QAWNO_DIR_NAME/$STR_INCLUDE_DIR_NAME",
        "includes"
    )

    val COMPILER_BINARY_NAMES = listOf(
        STR_PAWNCC_FILE_NAME,
        STR_PAWNCC_BINARY_NAME
    )
}