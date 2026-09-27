package com.rvdjv.pawnmc.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class CompilerConfigTest {
    @Test
    fun dedupePaths_ignoresCaseAndDuplicateSegments() {
        val input = listOf(
            "C:/Projects/MyServer/pawno/include/",
            "c:/projects/myserver/pawno/include",
            "C:/Projects/MyServer/qawno/include/",
            "c:/projects/myserver/qawno/include",
            "C:/Projects/MyServer/includes/"
        )

        val result = CompilerConfig.dedupePaths(input)

        assertEquals(
            listOf(
                "C:/Projects/MyServer/pawno/include/",
                "C:/Projects/MyServer/qawno/include/",
                "C:/Projects/MyServer/includes/"
            ),
            result
        )
    }
}
