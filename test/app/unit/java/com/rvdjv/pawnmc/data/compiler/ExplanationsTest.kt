package com.rvdjv.pawnmc.data.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.rvdjv.pawnmc.data.config.CompilerConfig

/**
 * Regression tests for the `data/_data_2026_exp.toml` parser.
 *
 * The explanations themselves are external data, so only the decoding contract is
 * asserted here: every `[explain."<code>"]` table is flattened into
 * `code.language` entries, an `en` leaf also answers for the bare code, and a
 * later table for the same code wins.
 */
class ExplanationsTest {

    @Test
    fun parsesTomlTables() {
        val parsed = Explanations.parse(
            """
            # comment line
            [explain."017"]
            en = "Undefined symbol."

            [explain."100"]
            en = "Cannot read from file."
            """.trimIndent()
        )

        assertEquals("Undefined symbol.", parsed["017"])
        assertEquals("Cannot read from file.", parsed["100"])
    }

    @Test
    fun lastEntryOfDuplicatedCodeWins() {
        val parsed = Explanations.parse(
            """
            [explain."217"]
            en = "First wording."

            [explain."217"]
            en = "Second wording."
            """.trimIndent()
        )

        assertEquals("Second wording.", parsed["217"])
    }

    @Test
    fun resolvesLocalizedExplanationWithEnglishFallback() {
        val parsed = Explanations.parse(
            """
            [explain."013"]
            en = "No entry point was found."
            id = "Titik masuk program tidak ditemukan."
            es = "No se encontro el punto de entrada."

            [explain."017"]
            en = "Undefined symbol."
            """.trimIndent()
        )

        assertEquals(
            "Titik masuk program tidak ditemukan.",
            Explanations.resolve(parsed, "013", CompilerConfig.AppLanguage.ID)
        )
        assertEquals(
            "No se encontro el punto de entrada.",
            Explanations.resolve(parsed, "013", CompilerConfig.AppLanguage.ES)
        )
        assertEquals(
            "No entry point was found.",
            Explanations.resolve(parsed, "013", CompilerConfig.AppLanguage.EN)
        )
        assertEquals(
            "Undefined symbol.",
            Explanations.resolve(parsed, "017", CompilerConfig.AppLanguage.ID)
        )
        assertEquals(
            "Undefined symbol.",
            Explanations.resolve(parsed, "017", CompilerConfig.AppLanguage.ES)
        )
    }

    @Test
    fun ignoresBlankAndMalformedLines() {
        val parsed = Explanations.parse(
            """

            # only a comment
            no-separator-here
            [explain."999"]
            en = "unterminated
            """.trimIndent()
        )

        assertTrue(parsed.isEmpty())
    }
}