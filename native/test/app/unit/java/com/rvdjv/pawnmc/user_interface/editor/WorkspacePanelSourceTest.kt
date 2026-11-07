package com.rvdjv.pawnmc.user_interface.editor.workspace
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class WorkspacePanelSourceTest {
    @Test
    fun compilerOutputRemainsScrollableAcrossBothAxesWithoutLineTruncation() {
        val root: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "app/src/source/main-src").isDirectory }
            ?: File(System.getProperty("user.dir").orEmpty())
        /*
         * The package folder was renamed to lower case; the old path no longer exists
         * and the read threw FileNotFoundException rather than failing an assertion.
         */
        val source = root?.let { File(it, "app/src/source/main-src/java/com/rvdjv/pawnmc/user_interface/editor/workspace/Panel.kt").readText() } ?: ""

        assertTrue(source.contains(".verticalScroll(outputScrollState)"))
        assertTrue(source.contains(".horizontalScroll(outputHorizontalScrollState)"))
        assertTrue(source.contains("softWrap = false"))
        assertTrue("compiler output must not be capped to a fixed number of lines", !source.contains("OUTPUT_MAX_LINES"))
    }
}
