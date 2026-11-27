package com.moonreader.app.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.moonreader.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class AppReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releasePageUrl: String = "",
    val changelog: String = ""
)

object AppUpdaterParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun normalizeGitHubReleaseApiUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().removeSuffix("/")
        if (trimmed.isBlank()) return ""
        val ghRepoMatch = Regex("^https?://github\\.com/([^/]+)/([^/]+)$").find(trimmed)
        if (ghRepoMatch != null) {
            val owner = ghRepoMatch.groupValues[1]
            val repo = ghRepoMatch.groupValues[2].removeSuffix(".git")
            return "https://api.github.com/repos/$owner/$repo/releases/latest"
        }
        return trimmed
    }

    fun parseGitHubRelease(rawJson: String): AppReleaseInfo {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: "0.0.0"
        val releaseNotes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val htmlUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val assets = obj["assets"]?.jsonArray
            ?: throw IOException("Belum ada aset rilis pada GitHub Releases")

        // Prefer signed release APK over debug APK if both are present
        val apkAssets = assets.mapNotNull { it.jsonObject }.filter { asset ->
            asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().endsWith(".apk", ignoreCase = true)
        }
        val pickedAsset = apkAssets.firstOrNull {
            it["name"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("release", ignoreCase = true)
        } ?: apkAssets.firstOrNull()
            ?: throw IOException("File .apk belum tersedia di rilis GitHub ini")

        val downloadUrl = pickedAsset["browser_download_url"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("URL unduhan APK kosong")

        val cleanTag = tagName.removePrefix("v").trim()
        return AppReleaseInfo(
            versionCode = parseVersionCode(cleanTag),
            versionName = cleanTag,
            apkUrl = downloadUrl,
            releasePageUrl = htmlUrl,
            changelog = releaseNotes
        )
    }

    fun isNewerVersion(remoteVersionName: String, currentVersionName: String): Boolean {
        return parseVersionCode(remoteVersionName) > parseVersionCode(currentVersionName)
    }

    internal fun parseVersionCode(versionName: String): Int {
        val parts = versionName.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        if (parts.isEmpty()) return 1
        val major = parts.getOrElse(0) { 1 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10000 + minor * 100 + patch
    }
}

class AppUpdater(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun checkForUpdate(rawRepoOrApiUrl: String): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = AppUpdaterParser.normalizeGitHubReleaseApiUrl(rawRepoOrApiUrl)
            require(apiUrl.startsWith("https://")) {
                "URL GitHub Release harus diawali https://"
            }
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "MoonReader/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 404) {
                        throw IOException("Belum ada Release di repo GitHub ini (HTTP 404)")
                    }
                    throw IOException("GitHub API error HTTP ${resp.code}")
                }
                val body = resp.body?.string() ?: throw IOException("Respons GitHub kosong")
                AppUpdaterParser.parseGitHubRelease(body)
            }
        }
    }

    fun openDownloadInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
