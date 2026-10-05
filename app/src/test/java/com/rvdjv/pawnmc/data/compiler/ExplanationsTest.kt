package com.rvdjv.pawnmc.data.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.rvdjv.pawnmc.data.config.CompilerConfig

/**
 * Regression tests for the `data/_data_mc_26_explain.dat` parser.
 *
 * The explanations themselves are external data, so only the decoding contract is
 * asserted here: the last entry of a duplicated code wins, comments are ignored and
 * both the `0x01:..:0x02:` and `code=explanation` forms are accepted.
 */
class ExplanationsTest {

    @Test
    fun parsesEncodedEntries() {
        val parsed = Explanations.parse(
            """
            # comment line
            0x01:017:0x02:Undefined symbol.
            0x01:100:0x02:Cannot read from file.
            """.trimIndent()
        )

        assertEquals(2, parsed.size)
        assertEquals("Undefined symbol.", parsed["017"])
        assertEquals("Cannot read from file.", parsed["100"])
    }

    @Test
    fun lastEntryOfDuplicatedCodeWins() {
        val parsed = Explanations.parse(
            """
            0x01:217:0x02:First wording.
            0x01:217:0x02:Second wording.
            """.trimIndent()
        )

        assertEquals("Second wording.", parsed["217"])
    }

    @Test
    fun acceptsPlainAssignmentForm() {
        val parsed = Explanations.parse("021=Symbol already defined.")

        assertEquals("Symbol already defined.", parsed["021"])
    }

    @Test
    fun resolvesLocalizedExplanationWithEnglishFallback() {
        val parsed = Explanations.parse(
            """
            0x01:013.en:0x02:No entry point was found.
            0x01:013.id:0x02:Titik masuk program tidak ditemukan.
            0x01:013.es:0x02:No se encontro el punto de entrada.
            0x01:017:0x02:Undefined symbol.
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
            """.trimIndent()
        )

        assertTrue(parsed.isEmpty())
    }
}