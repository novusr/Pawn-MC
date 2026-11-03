package com.rvdjv.pawnmc.user_interface.editor.syntax

import com.rvdjv.pawnmc.apps-data.data_config.CompilerConfig
import com.rvdjv.pawnmc.apps-data.data_syntax.ServerConfig
import io.github.rosemoe.sora.lang.Language
import java.io.File

/** Maps an open file to the language (highlighting + completion) the editor should use. */
object CodeLanguages {

    /** True when [file] is an SA-MP configuration rather than a Pawn script. */
    fun isServerConfig(file: File?): Boolean =
        file?.extension?.lowercase() in ServerConfig.EXTENSIONS

    /** True for a bare path, so callers without a [File] can use the same rule. */
    fun isServerConfig(path: String?): Boolean = isServerConfig(path?.let(::File))

    /**
     * The language for [file]. Pawn stays the default because the editor was built for
     * Pawn scripts; only the listed configuration extensions switch it.
     */
    fun forFile(
        file: File?,
        appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): Language = if (isServerConfig(file)) {
        ServerConfigLanguage(appLanguage)
    } else {
        CodeLanguage(appLanguage)
    }
}
