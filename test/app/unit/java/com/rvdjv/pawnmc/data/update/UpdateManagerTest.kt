package com.rvdjv.pawnmc.data.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun compareVersions_handlesGitHubVersionFormats() {
        assertTrue(UpdateManager.compareVersions("1.5.1", "1.4.2") > 0)
        assertTrue(UpdateManager.compareVersions("v1.4.0", "1.4.0") == 0)
        assertTrue(UpdateManager.compareVersions("1.4.1", "1.4.0") > 0)
        assertTrue(UpdateManager.compareVersions("1.4.0-beta", "1.4.0") < 0)
    }

    @Test
    fun parseGitHubRelease_selectsSeparateAndroidFlavorArchives() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.5.100",
              "assets": [
                {
                  "name": "app-android-7-8-debug.zip",
                  "browser_download_url": "https://example.com/app-android-7-8-debug.zip"
                },
                {
                  "name": "app-android-8-15-debug.zip",
                  "browser_download_url": "https://example.com/app-android-8-15-debug.zip"
                }
              ]
            }
            "".trimIndent()
        )

        val release = parseGitHubRelease(json)

        assertEquals("https://example.com/app-android-7-8-debug.zip", release.legacyArchiveUrl)
        assertEquals("https://example.com/app-android-8-15-debug.zip", release.modernArchiveUrl)
        assertEquals("https://example.com/app-android-7-8-debug.zip", release.archiveUrl(isLegacyBuild = true))
        assertEquals("https://example.com/app-android-8-15-debug.zip", release.archiveUrl(isLegacyBuild = false))
        assertEquals(null, release.copy(legacyArchiveUrl = null).archiveUrl(isLegacyBuild = true))
        assertEquals(null, release.copy(modernArchiveUrl = null).archiveUrl(isLegacyBuild = false))
    }

    @Test
    fun parseGitHubRelease_fallsBackToApkForOlderReleases() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.5.100",
              "assets": [
                {
                  "name": "pawnmc-1.5.100-debug.apk",
                  "browser_download_url": "https://example.com/pawnmc-1.5.100-debug.apk"
                }
              ]
            }
            "".trimIndent()
        )

        val release = parseGitHubRelease(json)

        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isLegacyBuild = true))
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isLegacyBuild = false))
    }

    @Test
    fun parseGitHubRelease_readsReleaseNotesAndApkAsset() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.5.100",
              "html_url": "https://github.com/novusr/Pawn-MC/releases/tag/v1.5.100",
              "body": "# Changelog\n\n- fixed ????????\n\n**important**",
              "assets": [
                {
                  "name": "pawnmc-1.5.100-debug.apk",
                  "browser_download_url": "https://example.com/pawnmc-1.5.100-debug.apk"
                }
              ]
            }
            """.trimIndent()
        )

        val release = parseGitHubRelease(json)

        assertEquals("v1.5.100", release.version)
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.legacyArchiveUrl)
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.modernArchiveUrl)
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isLegacyBuild = true))
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isLegacyBuild = false))
        assertTrue(release.bodyMarkdown.contains("fixed ????????"))
        assertTrue(release.bodyMarkdown.contains("important"))
    }
}
