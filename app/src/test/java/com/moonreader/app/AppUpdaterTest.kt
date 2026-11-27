package com.moonreader.app

import com.moonreader.app.data.local.AppUpdaterParser
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun `normalizeUpdateUrl converts GitHub repo URL to latest release API`() {
        assertEquals(
            "https://api.github.com/repos/user123/MoonReader/releases/latest",
            AppUpdaterParser.normalizeUpdateUrl("https://github.com/user123/MoonReader/")
        )
        assertEquals(
            "http://192.168.1.21:8787/version.json",
            AppUpdaterParser.normalizeUpdateUrl("http://192.168.1.21:8787/version.json")
        )
    }

    @Test
    fun `parseReleaseInfo supports local OTA version json and GitHub Releases json`() {
        val localJson = """
            {
              "versionCode": 2,
              "versionName": "1.0.1",
              "apkUrl": "http://192.168.1.21:8787/app-debug.apk",
              "changelog": "Build terbaru dari Mac"
            }
        """.trimIndent()
        val local = AppUpdaterParser.parseReleaseInfo(localJson)
        assertEquals(2, local.versionCode)
        assertEquals("1.0.1", local.versionName)
        assertEquals("http://192.168.1.21:8787/app-debug.apk", local.apkUrl)

        val ghJson = """
            {
              "tag_name": "v1.2.3",
              "body": "Fitur baru",
              "assets": [
                {
                  "name": "MoonReader-v1.2.3.apk",
                  "browser_download_url": "https://github.com/u/r/releases/download/v1.2.3/MoonReader.apk"
                }
              ]
            }
        """.trimIndent()
        val gh = AppUpdaterParser.parseReleaseInfo(ghJson)
        assertEquals(10203, gh.versionCode)
        assertEquals("1.2.3", gh.versionName)
        assertEquals("https://github.com/u/r/releases/download/v1.2.3/MoonReader.apk", gh.apkUrl)
    }
}
