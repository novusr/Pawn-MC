package com.rvdjv.pawnmc.data.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the `_dat/_explain.dat` parser.
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