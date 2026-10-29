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

        assertEquals("https://example.com/app-android-7-8-debug.zip", release.nougatArchiveUrl)
        assertEquals("https://example.com/app-android-8-15-debug.zip", release.newerArchiveUrl)
        assertEquals("https://example.com/app-android-7-8-debug.zip", release.archiveUrl(isNougatBuild = true))
        assertEquals("https://example.com/app-android-8-15-debug.zip", release.archiveUrl(isNougatBuild = false))
        assertEquals(null, release.copy(nougatArchiveUrl = null).archiveUrl(isNougatBuild = true))
        assertEquals(null, release.copy(newerArchiveUrl = null).archiveUrl(isNougatBuild = false))
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

        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isNougatBuild = true))
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isNougatBuild = false))
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
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.nougatArchiveUrl)
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.newerArchiveUrl)
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isNougatBuild = true))
        assertEquals("https://example.com/pawnmc-1.5.100-debug.apk", release.archiveUrl(isNougatBuild = false))
        assertTrue(release.bodyMarkdown.contains("fixed ????????"))
        assertTrue(release.bodyMarkdown.contains("important"))
    }
}
