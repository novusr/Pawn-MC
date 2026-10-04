package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

/**
 * Human readable explanation of raw compiler output.
 *
 * Every warning/error line is copied verbatim and followed by an indented local
 * hint when the message code is documented in `_dat/_dat_explain.dat`. The wording of
 * every hint lives in that asset, never in this file.
 */
object Explainer {

    private val COMPILER_MESSAGE_REGEX = Regex("""(?i)(warning|error|fatal)\s+(\d+)""")

    private const val NO_LOCAL_EXPLANATION =
        "No local explanation found for this compiler message. " +
        "Check the Pawn error reference for details."

    fun explainCompilerOutput(
        rawText: String,
        cacheDir: File,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): String {
        if (rawText.isBlank()) return rawText

        val outputCache = File(cacheDir, "compiler_output_cache_${System.nanoTime()}.log")
        outputCache.writeText(rawText)

        val builder = StringBuilder()
        outputCache.useLines { lines ->
            lines.forEach { line ->
                builder.appendLine(line)
                val match = COMPILER_MESSAGE_REGEX.find(line)
                if (match != null) {
                    val code = match.groupValues[2].trimStart('0')
                    val explanation = Explanations.explanationFor(code, language)
                        ?: Explanations.explanationFor(match.groupValues[2], language)
                        ?: NO_LOCAL_EXPLANATION
                    builder.appendLine("    [explain] $explanation")
                }
            }
        }

        outputCache.delete()
        return builder.toString()
    }
}