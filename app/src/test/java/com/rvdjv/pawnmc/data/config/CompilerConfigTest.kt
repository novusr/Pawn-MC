package com.rvdjv.pawnmc.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class CompilerConfigTest {
    @Test
    fun dedupePaths_ignoresCaseAndDuplicateSegments() {
        val input = listOf(
            "com.android/MyServer/pawno/include/",
            "com.android/myserver/pawno/include",
            "com.android/MyServer/qawno/include/",
            "com.android/myserver/qawno/include",
            "com.android/MyServer/includes/"
        )

        val result = CompilerConfig.dedupePaths(input)

        assertEquals(
            listOf(
                "com.android/MyServer/pawno/include/",
                "com.android/MyServer/qawno/include/",
                "com.android/MyServer/includes/"
            ),
            result
        )
    }

    @Test
    fun dedupePaths_prohibitsTwoSameLocationPaths() {
        val paths = listOf(
            "/storage/emulated/0/samp/pawno/include",
            "/storage/emulated/0/samp/pawno/include/",
            "/storage/emulated/0/SAMP/PAWNO/INCLUDE",
            "/storage/emulated/0/samp/gamemodes/../pawno/include",
            "/storage/emulated/0/samp/qawno/include"
        )
        val deduped = CompilerConfig.dedupePaths(paths)
        assertEquals(2, deduped.size)
        assertEquals("/storage/emulated/0/samp/pawno/include/", deduped[0])
        assertEquals("/storage/emulated/0/samp/qawno/include/", deduped[1])
    }
}
