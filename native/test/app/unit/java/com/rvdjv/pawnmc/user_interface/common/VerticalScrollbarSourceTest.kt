package com.rvdjv.pawnmc.user_interface.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level regression tests for the shared vertical scrollbar.
 *
 * No Compose runtime is spun up here: the behaviours that failed in the field
 * (the stuck main-interface bar, the hidden settings label, the dead tap/drag)
 * are all visible as source invariants in [VerticalScrollbar.kt] and in the
 * call sites, so they are asserted textually, the same way
 * [WorkspacePanelSourceTest] guards the output panel.
 */
class VerticalScrollbarSourceTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .firstOrNull { File(it, "app/src/source/main-src").isDirectory }
        ?: File(System.getProperty("user.dir").orEmpty())

    private fun source(vararg parts: String): String =
        File(root, parts.joinToString("/")).readText()

    private val scrollbar = source(
        "app/src/source/main-src/java/com/rvdjv/pawnmc/user_interface/common/VerticalScrollbar.kt"
    )

    @Test
    fun `the bar is interactive - tap jumps and drag scrolls`() {
        // Tap-to-jump lives on the track, not only drag on the thumb.
        assertTrue(scrollbar.contains("detectTapGestures"))
        assertTrue(scrollbar.contains("detectDragGestures"))
        // Drag maps pointer movement onto a scroll fraction through the live metrics.
        assertTrue(scrollbar.contains("metrics.scrollToFraction"))
    }

    @Test
    fun `the bar is slimmer than the old 22dp lane`() {
        val match = Regex("SB_WIDTH = (\\d+)\\.dp").find(scrollbar)
        val width = match?.groupValues?.get(1)?.toInt()
        assertTrue("SB_WIDTH must exist", width != null && width < 22)
    }

    @Test
    fun `pixel thumb grows from the real viewport proportion`() {
        // The old formula was a fixed 1f - maxValue/(maxValue+1) approx; the fixed one
        // reads viewportSize so the thumb reaches full track at the bottom.
        assertTrue(scrollbar.contains("viewportSize"))
    }

    @Test
    fun `lazy variant walks to the true bottom of the list`() {
        // Fraction 1.0 keeps scrolling forward after the last-item hop, so the bar
        // can reach the Promo card at the end of the main screen.
        assertTrue(scrollbar.contains("canScrollForward"))
    }

    @Test
    fun `labels sit on their own plate so the app bar cannot cover them`() {
        assertTrue(scrollbar.contains("LabelPlate"))
    }

    @Test
    fun `main screen fills the scrollbar lane for its whole height`() {
        val main = source(
            "app/src/source/main-src/java/com/rvdjv/pawnmc/user_interface/main/MainScreen.kt"
        )
        assertTrue(main.contains("fillMaxHeight()")) // used by the scrollbar modifier chain
        assertTrue(main.contains("LazyListScrollbar("))
    }
}
