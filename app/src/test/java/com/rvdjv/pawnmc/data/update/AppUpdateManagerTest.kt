package com.rvdjv.pawnmc.data.update

import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun compareVersions_handlesGitHubVersionFormats() {
        assertTrue(AppUpdateManager.compareVersions("1.5.1", "1.4.2") > 0)
        assertTrue(AppUpdateManager.compareVersions("v1.4.0", "1.4.0") == 0)
        assertTrue(AppUpdateManager.compareVersions("1.4.1", "1.4.0") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.4.0-beta", "1.4.0") < 0)
    }
}
