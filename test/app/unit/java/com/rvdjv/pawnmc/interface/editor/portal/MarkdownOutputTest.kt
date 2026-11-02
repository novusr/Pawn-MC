package com.rvdjv.pawnmc.`interface`.editor.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownOutputTest {

    @Test
    fun `fenced code lines merge into a single block with its language`() {
        val blocks = parseMarkdownBlocks(
            """
            # Title
            ```pawn
            main() {
                print("hi");
            }
            ```
            """.trimIndent()
        )

        assertEquals(MarkdownBlock.Heading(1, "Title"), blocks[0])
        val code = blocks.filterIsInstance<MarkdownBlock.Code>().single()
        assertEquals("pawn", code.language)
        assertEquals("main() {\n    print(\"hi\");\n}", code.code)
    }

    @Test
    fun `blank line inside a code block is kept as code`() {
        val blocks = parseMarkdownBlocks(
            """
            ```
            first

            second
            ```
            """.trimIndent()
        )

        val code = blocks.filterIsInstance<MarkdownBlock.Code>().single()
        assertTrue(code.code.contains("first"))
        assertTrue(code.code.contains("second"))
    }

    @Test
    fun `headings keep their level and bullets lose the marker`() {
        val blocks = parseMarkdownBlocks(
            """
            ## Options
            - `switch` picks a compiler
            * plain bullet
            """.trimIndent()
        )

        assertEquals(MarkdownBlock.Heading(2, "Options"), blocks[0])
        assertEquals(MarkdownBlock.Bullet("`switch` picks a compiler"), blocks[1])
        assertEquals(MarkdownBlock.Bullet("plain bullet"), blocks[2])
    }

    @Test
    fun `repeated blank lines collapse into one spacer`() {
        val blocks = parseMarkdownBlocks("first\n\n\n\nsecond")
        assertEquals(1, blocks.count { it is MarkdownBlock.Spacer })
    }
}
