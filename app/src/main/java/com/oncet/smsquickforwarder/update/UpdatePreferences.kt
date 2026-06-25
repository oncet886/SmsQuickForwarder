package com.oncet.smsquickforwarder.update

import android.content.Context

object UpdatePreferences {
    private const val PREFS = "updates"
    private const val KEY_AUTO = "autoUpdateCheckEnabled"
    private const val KEY_FREQUENCY = "updateCheckFrequency"
    private const val KEY_IGNORED = "ignoredVersion"
    private const val KEY_LAST_CHECK = "lastUpdateCheckAt"
    private const val KEY_LAST_VERSION = "lastKnownLatestVersion"
    private const val KEY_LAST_URL = "lastKnownReleaseUrl"
    private const val KEY_LAST_NOTES = "lastKnownReleaseNotes"
    private const val KEY_LAST_NOTIFICATION = "lastUpdateNotificationVersion"
    private const val KEY_NOTIFICATIONS = "updateNotificationsEnabled"

    const val DAY_MS = 24L * 60L * 60L * 1000L
    const val WEEK_MS = 7L * DAY_MS

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCheckEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    fun notificationsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    fun frequency(context: Context): UpdateFrequency =
        runCatching { UpdateFrequency.valueOf(prefs(context).getString(KEY_FREQUENCY, UpdateFrequency.DAILY.name) ?: UpdateFrequency.DAILY.name) }
            .getOrDefault(UpdateFrequency.DAILY)

    fun setFrequency(context: Context, frequency: UpdateFrequency) {
        prefs(context).edit().putString(KEY_FREQUENCY, frequency.name).apply()
    }

    fun ignoredVersion(context: Context): String = prefs(context).getString(KEY_IGNORED, "") ?: ""

    fun ignoreVersion(context: Context, version: String) {
        prefs(context).edit().putString(KEY_IGNORED, version).apply()
    }

    fun clearIgnoredVersion(context: Context) {
        prefs(context).edit().putString(KEY_IGNORED, "").apply()
    }

    fun lastCheckAt(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun lastKnownLatestVersion(context: Context): String = prefs(context).getString(KEY_LAST_VERSION, "") ?: ""

    fun lastKnownReleaseUrl(context: Context): String = prefs(context).getString(KEY_LAST_URL, "") ?: ""

    fun lastKnownReleaseNotes(context: Context): String = prefs(context).getString(KEY_LAST_NOTES, "") ?: ""

    fun lastNotificationVersion(context: Context): String = prefs(context).getString(KEY_LAST_NOTIFICATION, "") ?: ""

    fun shouldCheckNow(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        shouldCheckNow(autoCheckEnabled(context), frequency(context), lastCheckAt(context), now)

    fun shouldCheckNow(autoEnabled: Boolean, frequency: UpdateFrequency, lastCheckAt: Long, now: Long): Boolean {
        if (!autoEnabled) return false
        val elapsed = now - lastCheckAt
        return when (frequency) {
            UpdateFrequency.DAILY -> elapsed >= DAY_MS
            UpdateFrequency.WEEKLY -> elapsed >= WEEK_MS
            UpdateFrequency.ON_APP_START_ONLY -> true
        }
    }

    fun recordResult(context: Context, result: UpdateCheckResult, now: Long = System.currentTimeMillis()) {
        val edit = prefs(context).edit().putLong(KEY_LAST_CHECK, now)
        if (result is UpdateCheckResult.UpdateAvailable) {
            edit.putString(KEY_LAST_VERSION, result.info.latestVersionName)
                .putString(KEY_LAST_URL, result.info.releaseUrl)
                .putString(KEY_LAST_NOTES, result.info.releaseNotes)
        }
        edit.apply()
    }

    fun shouldNotifyVersion(
        latestVersion: String,
        ignoredVersion: String,
        lastNotificationVersion: String,
        autoEnabled: Boolean,
        notificationsEnabled: Boolean
    ): Boolean =
        autoEnabled &&
            notificationsEnabled &&
            latestVersion.isNotBlank() &&
            latestVersion != ignoredVersion &&
            latestVersion != lastNotificationVersion

    fun shouldNotify(context: Context, latestVersion: String): Boolean =
        shouldNotifyVersion(
            latestVersion,
            ignoredVersion(context),
            lastNotificationVersion(context),
            autoCheckEnabled(context),
            notificationsEnabled(context)
        )

    fun markNotified(context: Context, version: String) {
        prefs(context).edit().putString(KEY_LAST_NOTIFICATION, version).apply()
    }
}
