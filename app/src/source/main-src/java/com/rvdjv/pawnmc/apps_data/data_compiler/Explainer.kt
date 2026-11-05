package com.rvdjv.pawnmc.apps_data.data_compiler

import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig
import java.io.File

/**
 * Human readable explanation of raw compiler output.
 */
object Explainer {

    private val COMPILER_MESSAGE_REGEX = Regex("""(?i)(warning|error|fatal)\s+(\d+)""")

    private val SYMBOL_WARNING_REGEX = Regex("(?i)symbol is never used:\\s*(?:\"([^\"]+)\"|'([^']+)'|(\\S+))")

    private fun noLocalExplanation(language: CompilerConfig.AppLanguage): String = when (language) {
        CompilerConfig.AppLanguage.ID -> "Belum ada penjelasan lokal untuk pesan ini. Cek referensi error Pawn untuk detailnya."
        CompilerConfig.AppLanguage.ES -> "¡Vaya! Aún no tenemos una explicación para este mensaje. Consulta la referencia de errores de Pawn."
        CompilerConfig.AppLanguage.RU -> "Пока не нашли объяснение для этого сообщения. Подробности есть в справочнике ошибок Pawn."
        CompilerConfig.AppLanguage.TL -> "Wala pang paliwanag para sa mensaheng ito. Tingnan ang Pawn error reference para sa detalye."
        CompilerConfig.AppLanguage.EN -> "No explanation for this one yet! Check the Pawn error reference for details."
    }

    private fun explanationText(line: String, explanation: String): String {
        val match = SYMBOL_WARNING_REGEX.find(line) ?: return explanation
        val symbol = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return explanation
        return explanation.replace("%s", symbol)
    }

    fun explainCompilerOutput(
        rawText: String,
        cacheDir: File,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): String {
        if (rawText.isBlank()) return rawText

        val n_language = CompilerConfig.getInstanceOrNull()?.n_app_language ?: language
        val outputCache = File(cacheDir, "compiler_output_cache_${System.nanoTime()}.log")
        outputCache.writeText(rawText)

        val builder = StringBuilder()
        outputCache.useLines { lines ->
            lines.forEach { line ->
                builder.appendLine(line)
                val match = COMPILER_MESSAGE_REGEX.find(line)
                if (match != null) {
                    val rawCode = match.groupValues[2]
                    val code = rawCode.trimStart('0').ifEmpty { "0" }
                    val explanation = Explanations.explanationFor(code, n_language)
                        ?: Explanations.explanationFor(rawCode, n_language)
                        ?: noLocalExplanation(n_language)
                    val renderedExplanation = explanationText(line, explanation)
                    // Marker with explicit padding so interfaces that colourise the
                    // token by substring keep matching it even when the hint text is
                    // long enough to wrap.
                    builder.appendLine("    [explain] $renderedExplanation")
                }
            }
        }

        outputCache.delete()
        return builder.toString()
    }
}