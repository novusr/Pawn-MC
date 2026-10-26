package com.rvdjv.pawnmc.`interface`.editor.workspace
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class WorkspacePanelSourceTest {
    @Test
    fun compilerOutputRemainsScrollableAcrossBothAxesWithoutLineTruncation() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: File(System.getProperty("user.dir"))
        /*
         * The package folder was renamed to lower case; the old path no longer exists
         * and the read threw FileNotFoundException rather than failing an assertion.
         */
        val source = File(root, "app/src/main/java/com/rvdjv/pawnmc/interface/editor/workspace/Panel.kt").readText()

        assertTrue(source.contains(".verticalScroll(outputScrollState)"))
        assertTrue(source.contains(".horizontalScroll(outputHorizontalScrollState)"))
        assertTrue(source.contains("softWrap = false"))
        assertTrue("compiler output must not be capped to a fixed number of lines", !source.contains("OUTPUT_MAX_LINES"))
    }
}
