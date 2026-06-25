package com.oncet.smsquickforwarder.update

import android.content.Context
import com.oncet.smsquickforwarder.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val ignoredVersion: String = ""
) {
    fun check(): UpdateCheckResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SmsQuickForwarder/$currentVersion")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                UpdateCheckResult.Error("GitHub 请求失败")
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseReleaseJson(body, currentVersion, ignoredVersion)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "网络不可用")
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/oncet886/SmsQuickForwarder/releases/latest"
        const val REPO_RELEASE_URL_PREFIX = "https://github.com/oncet886/SmsQuickForwarder/"

        fun create(context: Context): UpdateChecker =
            UpdateChecker(BuildConfig.VERSION_NAME, UpdatePreferences.ignoredVersion(context))

        fun parseReleaseJson(json: String, currentVersion: String, ignoredVersion: String = ""): UpdateCheckResult {
            return try {
                val root = JSONObject(json)
                if (root.optBoolean("draft") || root.optBoolean("prerelease")) {
                    return UpdateCheckResult.UpToDate(currentVersion)
                }
                val tag = root.optString("tag_name")
                val latestVersion = VersionComparator.normalize(tag)
                    ?: return UpdateCheckResult.Error("返回格式异常")
                if (latestVersion == ignoredVersion) {
                    return UpdateCheckResult.UpToDate(currentVersion)
                }
                if (!VersionComparator.isRemoteNewer(currentVersion, latestVersion)) {
                    return UpdateCheckResult.UpToDate(currentVersion)
                }
                val releaseUrl = root.optString("html_url")
                if (!releaseUrl.startsWith(REPO_RELEASE_URL_PREFIX)) {
                    return UpdateCheckResult.Error("Release 地址异常")
                }
                val assets = root.optJSONArray("assets")
                var apkName: String? = null
                var apkUrl: String? = null
                var apkSize: Long? = null
                var shaName: String? = null
                var shaUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        if (!url.startsWith(REPO_RELEASE_URL_PREFIX)) continue
                        if (name.endsWith("-release.apk") && apkName == null) {
                            apkName = name
                            apkUrl = url
                            apkSize = asset.optLong("size").takeIf { it > 0 }
                        } else if (name.endsWith("-release.apk.sha256") && shaName == null) {
                            shaName = name
                            shaUrl = url
                        }
                    }
                }
                UpdateCheckResult.UpdateAvailable(
                    UpdateInfo(
                        latestVersionName = latestVersion,
                        releaseTitle = root.optString("name").ifBlank { "SmsQuickForwarder v$latestVersion" },
                        releaseNotes = root.optString("body"),
                        releaseUrl = releaseUrl,
                        publishedAt = root.optString("published_at"),
                        apkName = apkName,
                        apkUrl = apkUrl,
                        apkSize = apkSize,
                        sha256Name = shaName,
                        sha256Url = shaUrl
                    )
                )
            } catch (e: Exception) {
                UpdateCheckResult.Error("返回格式异常")
            }
        }
    }
}
