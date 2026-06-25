package com.oncet.smsquickforwarder.update

data class UpdateInfo(
    val latestVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val publishedAt: String,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long?,
    val sha256Name: String?,
    val sha256Url: String?
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

enum class UpdateFrequency {
    DAILY,
    WEEKLY,
    ON_APP_START_ONLY
}
