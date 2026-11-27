package com.moonreader.app

import com.moonreader.app.data.local.AppUpdaterParser
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun `normalizeGitHubReleaseApiUrl converts GitHub repo URL to latest release API`() {
        assertEquals(
            "https://api.github.com/repos/prastowoardi/MoonReader/releases/latest",
            AppUpdaterParser.normalizeGitHubReleaseApiUrl("https://github.com/prastowoardi/MoonReader")
        )
        assertEquals(
            "https://api.github.com/repos/prastowoardi/MoonReader/releases/latest",
            AppUpdaterParser.normalizeGitHubReleaseApiUrl("https://github.com/prastowoardi/MoonReader.git")
        )
    }

    @Test
    fun `parseGitHubRelease prefers release APK asset over debug APK`() {
        val ghJson = """
            {
              "tag_name": "v1.2.3",
              "html_url": "https://github.com/prastowoardi/MoonReader/releases/tag/v1.2.3",
              "body": "Rilis terbaru",
              "assets": [
                {
                  "name": "app-debug.apk",
                  "browser_download_url": "https://github.com/prastowoardi/MoonReader/releases/download/v1.2.3/app-debug.apk"
                },
                {
                  "name": "MoonReader-v1.2.3-release.apk",
                  "browser_download_url": "https://github.com/prastowoardi/MoonReader/releases/download/v1.2.3/MoonReader-v1.2.3-release.apk"
                }
              ]
            }
        """.trimIndent()
        val gh = AppUpdaterParser.parseGitHubRelease(ghJson)
        assertEquals(10203, gh.versionCode)
        assertEquals("1.2.3", gh.versionName)
        assertEquals(
            "https://github.com/prastowoardi/MoonReader/releases/download/v1.2.3/MoonReader-v1.2.3-release.apk",
            gh.apkUrl
        )
    }

    @Test
    fun `isNewerVersion compares semantic version tags accurately`() {
        org.junit.Assert.assertTrue(AppUpdaterParser.isNewerVersion("1.0.2", "1.0.0"))
        org.junit.Assert.assertFalse(AppUpdaterParser.isNewerVersion("1.0.2", "1.0.2"))
        org.junit.Assert.assertFalse(AppUpdaterParser.isNewerVersion("1.0.1", "1.0.2"))
    }
}
