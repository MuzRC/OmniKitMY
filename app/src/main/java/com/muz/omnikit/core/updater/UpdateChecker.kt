package com.muz.omnikit.core.updater

import com.muz.omnikit.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val fromVersionJson = runCatching { fetchFromVersionJson() }.getOrNull()
        val info = fromVersionJson ?: runCatching { fetchFromGitHubReleases() }.getOrElse { err ->
            return@withContext Result.failure(err)
        }

        if (info == null || info.apkUrl.isBlank()) {
            return@withContext Result.success(null)
        }

        val isNewer = isNewerVersion(
            remoteCode = info.versionCode,
            remoteName = info.versionName,
            localCode = BuildConfig.VERSION_CODE,
            localName = BuildConfig.VERSION_NAME
        )

        Result.success(if (isNewer) info else null)
    }

    private fun fetchFromVersionJson(): UpdateInfo? {
        val raw = httpGetString(UpdateConfig.VERSION_JSON_URL) ?: return null
        val json = JSONObject(raw)

        val apkUrl = json.optString("apkUrl")
            .ifBlank { json.optString("apk_url") }
            .ifBlank { json.optString("downloadUrl") }
            .ifBlank { json.optString("download_url") }
            .ifBlank { json.optString("url") }
        if (apkUrl.isBlank()) return null

        val versionName = json.optString("versionName")
            .ifBlank { json.optString("version_name") }
            .ifBlank { json.optString("version") }
            .removePrefix("v")
            .removePrefix("V")
            .ifBlank { "1.0.0" }

        val versionCode = when {
            json.has("versionCode") -> json.optInt("versionCode", 0)
            json.has("version_code") -> json.optInt("version_code", 0)
            else -> 0
        }

        val notes = json.optString("releaseNotes")
            .ifBlank { json.optString("release_notes") }
            .ifBlank { json.optString("changelog") }
            .ifBlank { json.optString("notes") }

        val size = json.optLong("size", json.optLong("fileSize", -1L))

        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            releaseNotes = notes,
            fileSizeBytes = size
        )
    }

    private fun fetchFromGitHubReleases(): UpdateInfo? {
        val raw = httpGetString(UpdateConfig.GITHUB_RELEASES_API) ?: return null
        val json = JSONObject(raw)
        val tagName = json.optString("tag_name").removePrefix("v").removePrefix("V")
        val body = json.optString("body")
        val assets = json.optJSONArray("assets") ?: return null

        var apkUrl = ""
        var apkSize = -1L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                apkSize = asset.optLong("size", -1L)
                break
            }
        }
        if (apkUrl.isBlank()) return null

        return UpdateInfo(
            versionCode = 0,
            versionName = tagName.ifBlank { "1.0.0" },
            apkUrl = apkUrl,
            releaseNotes = body,
            fileSizeBytes = apkSize
        )
    }

    private fun httpGetString(urlString: String): String? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OmniKitMY-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun isNewerVersion(
        remoteCode: Int,
        remoteName: String,
        localCode: Int,
        localName: String
    ): Boolean {
        if (remoteCode > 0 && remoteCode > localCode) return true
        val rParts = remoteName.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val lParts = localName.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
