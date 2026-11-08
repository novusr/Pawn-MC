package com.rvdjv.pawnmc.apps_data.data_compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig

/**
 * Regression tests for the `data/_explainer.toml` parser.
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
            local_en = "Undefined symbol."

            [explain."100"]
            local_en = "Cannot read from file."
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
            local_en = "First wording."

            [explain."217"]
            local_en = "Second wording."
            """.trimIndent()
        )

        assertEquals("Second wording.", parsed["217"])
    }

    @Test
    fun resolvesLocalizedExplanationWithEnglishFallback() {
        val parsed = Explanations.parse(
            """
            [explain."013"]
            local_en = "No entry point was found."
            local_id = "Titik masuk program tidak ditemukan."
            local_es = "No se encontro el punto de entrada."

            [explain."017"]
            local_en = "Undefined symbol."
            """.trimIndent()
        )

        assertEquals(
            "Titik masuk program tidak ditemukan.",
            Explanations.resolve(parsed, "013", ContentConfig.AppLanguage.ID)
        )
        assertEquals(
            "No se encontro el punto de entrada.",
            Explanations.resolve(parsed, "013", ContentConfig.AppLanguage.ES)
        )
        assertEquals(
            "No entry point was found.",
            Explanations.resolve(parsed, "013", ContentConfig.AppLanguage.EN)
        )
        assertEquals(
            "Undefined symbol.",
            Explanations.resolve(parsed, "017", ContentConfig.AppLanguage.ID)
        )
        assertEquals(
            "Undefined symbol.",
            Explanations.resolve(parsed, "017", ContentConfig.AppLanguage.ES)
        )
    }

    @Test
    fun ignoresBlankAndMalformedLines() {
        val parsed = Explanations.parse(
            """

            # only a comment
            no-separator-here
            [explain."999"]
            local_en = "unterminated
            """.trimIndent()
        )

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `localized field names are flattened to language keys`() {
        val parsed = Explanations.parse(
            """
            [explain."203"]
            local_en = "Warning 203: symbol is never used: \"%s\"."
            local_id = "Simbol \"%s\" tidak digunakan."
            """.trimIndent()
        )
        assertEquals("Warning 203: symbol is never used: \"%s\".", parsed["203.en"])
        assertEquals("Simbol \"%s\" tidak digunakan.", parsed["203.id"])
    }
}
