package com.rvdjv.pawnmc.apps_data.data_compiler

import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig
import java.io.File

/**
 * Human readable explanation of raw compiler output.
 */
object Explainer {

    private val COMPILER_MESSAGE_REGEX = Regex("""(?i)(warning|error|fatal)\s+(\d+)""")

    /** The `[explain]` marker prefix, localised per app language. */
    private val EXPLAIN_PREFIX = mapOf(
        CompilerConfig.AppLanguage.ID to "[jelaskan]",
        CompilerConfig.AppLanguage.EN to "[explain]",
        CompilerConfig.AppLanguage.ES to "[explicar]",
        CompilerConfig.AppLanguage.RU to "[объяснение]",
        CompilerConfig.AppLanguage.TL to "[paliwanag]",
    )

    /**
     * Quote patterns of the messages that carry a symbol or a file name.
     *
     * The explanation tables use `%s` as a placeholder; the value comes from the
     * quoted text of the compiler line itself, so the explainer renders real
     * symbols ("myVariable") and real paths ("include/glue.inc") instead of a
     * literal `%s`. Applies to `undefined symbol: "x"`, `symbol is never used:
     * "x"`, `cannot read from file "x"`, `cannot write to file "x"` and any
     * other message with the same shape.
     */
    private val QUOTED_SYMBOL_PATTERNS = listOf(
        Regex("""(?i)undefined\s+symbol\s*:?\s*"([^"]+)""""),
        Regex("""(?i)undefined\s+symbol\s*:?\s*'([^']+)'"""),
        Regex("""(?i)symbol\s+is\s+never\s+used\s*:?\s*"([^"]+)""""),
        Regex("""(?i)symbol\s+is\s+never\s+used\s*:?\s*'([^']+)'"""),
        Regex("""(?i)cannot\s+read\s+from\s+file\s+"([^"]+)""""),
        Regex("""(?i)cannot\s+write\s+to\s+file\s+"([^"]+)""""),
        Regex("""(?i)cannot\s+read\s+from\s+file\s*:?\s*(\S+)"""),
        Regex("""(?i)cannot\s+write\s+to\s+file\s*:?\s*(\S+)"""),
        // Generic fall-back: a trailing quoted token on the message line, e.g.
        // "tag mismatch: "x" is an integer" or unknown user messages.
        Regex("""""([^"]+)"""""),
        Regex("""'([^']{2,})'"""),
    )

    private fun noLocalExplanation(language: CompilerConfig.AppLanguage): String = when (language) {
        CompilerConfig.AppLanguage.ID -> "Belum ada penjelasan lokal untuk pesan ini. Cek referensi error Pawn untuk detailnya."
        CompilerConfig.AppLanguage.ES -> "¡Vaya! Aún no tenemos una explicación para este mensaje. Consulta la referencia de errores de Pawn."
        CompilerConfig.AppLanguage.RU -> "Пока не нашли объяснение для этого сообщения. Подробности есть в справочнике ошибок Pawn."
        CompilerConfig.AppLanguage.TL -> "Wala pang paliwanag para sa mensaheng ito. Tingnan ang Pawn error reference para sa detalye."
        CompilerConfig.AppLanguage.EN -> "No explanation for this one yet! Check the Pawn error reference for details."
    }

    /**
     * Extracts the symbol / file name the compiler line quotes and fills the
     * `%s` placeholder of the explanation with it. A symbol is only substituted
     * when it looks like an identifier or a path, so prose quoting (e.g. a
     * comment quoting a sentence) does not leak into the hint.
     */
    internal fun explanationText(line: String, explanation: String): String {
        if (!explanation.contains("%s")) return explanation
        for (pattern in QUOTED_SYMBOL_PATTERNS) {
            val match = pattern.find(line) ?: continue
            val candidate = match.groupValues[1].trim()
            if (candidate.isEmpty()) continue
            return explanation.replace("%s", candidate)
        }
        // No usable symbol found: drop the placeholder instead of rendering a
        // bare "%s" the reader has to interpret.
        return explanation
            .replace(" \"%s\"", "")
            .replace("%s", "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    /** Localised `[explain]` marker for [language]. */
    internal fun explainMarker(language: CompilerConfig.AppLanguage): String =
        EXPLAIN_PREFIX[language] ?: "[explain]"

    /**
     * True when [output] carries an `undefined symbol` hit naming [symbol].
     *
     * Read with the same quoted-symbol patterns the explanation fill uses
     * ([QUOTED_SYMBOL_PATTERNS]), so the recovery decision and the text the user
     * sees are derived from one reading of the compiler output — a message the
     * explainer recognises is exactly the message the switch acts on.
     */
    internal fun explainsUndefinedSymbol(output: String, symbol: String): Boolean {
        if (output.isBlank() || symbol.isBlank()) return false
        val n_symbol = symbol.trim()
        val n_undefinedLines = output.lineSequence().filter { line ->
            line.contains("undefined", ignoreCase = true) &&
                line.contains("symbol", ignoreCase = true)
        }
        for (line in n_undefinedLines) {
            val n_quoted = QUOTED_SYMBOL_PATTERNS.firstNotNullOfOrNull { pattern ->
                pattern.find(line)?.groupValues?.get(1)?.trim()
            }
            if (n_quoted != null && n_quoted.equals(n_symbol, ignoreCase = true)) return true
            // Unquoted spelling: `undefined symbol __addressof` (the newer
            // message shape writes the symbol without quotes).
            if (Regex("(?i)undefined\\s+symbol\\s*:?\\s+" + Regex.escape(n_symbol))
                    .containsMatchIn(line)
            ) return true
        }
        return false
    }

    fun explainCompilerOutput(
        rawText: String,
        cacheDir: File,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): String {
        if (rawText.isBlank()) return rawText

        val n_language = CompilerConfig.getInstanceOrNull()?.n_app_language ?: language
        val marker = explainMarker(n_language)
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
                    builder.appendLine("    $marker $renderedExplanation")
                }
            }
        }

        outputCache.delete()
        return builder.toString()
    }
}
