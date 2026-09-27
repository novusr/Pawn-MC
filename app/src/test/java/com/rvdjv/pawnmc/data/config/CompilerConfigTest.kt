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
}
