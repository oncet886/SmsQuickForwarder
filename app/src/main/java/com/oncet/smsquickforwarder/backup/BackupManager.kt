package com.oncet.smsquickforwarder.backup

import android.content.Context
import com.oncet.smsquickforwarder.BuildConfig
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.rules.RuleStore
import com.oncet.smsquickforwarder.update.UpdateFrequency
import com.oncet.smsquickforwarder.update.UpdatePreferences
import com.oncet.smsquickforwarder.update.UpdateScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BackupManager {
    const val SCHEMA_VERSION = 1

    fun exportJson(context: Context): String {
        val rules = JSONArray()
        RuleStore.rules(context).forEach { rules.put(it.toJson()) }
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("appVersionName", BuildConfig.VERSION_NAME)
            .put("appVersionCode", BuildConfig.VERSION_CODE)
            .put("exportedAt", isoNow())
            .put(
                "settings",
                JSONObject()
                    .put("targetPhone", SettingsStore.targetPhone(context))
                    .put("forwardingEnabled", SettingsStore.isEnabled(context))
                    .put("forwardMode", RuleStore.forwardMode(context).name)
                    .put("autoUpdateCheckEnabled", UpdatePreferences.autoCheckEnabled(context))
                    .put("updateCheckFrequency", UpdatePreferences.frequency(context).name)
                    .put("ignoredVersion", UpdatePreferences.ignoredVersion(context))
                    .put("updateNotificationsEnabled", UpdatePreferences.notificationsEnabled(context))
                    .put("failureNotificationsEnabled", SettingsStore.failureNotificationsEnabled(context))
                    .put("logRetention", SettingsStore.logRetention(context))
            )
            .put("rules", rules)
            .toString(2)
    }

    fun writeCacheFile(context: Context, raw: String = exportJson(context)): File {
        val dir = File(context.cacheDir, "backup_exports").apply { mkdirs() }
        val file = File(dir, "SmsQuickForwarder-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.json")
        file.writeText(raw, Charsets.UTF_8)
        return file
    }

    fun preview(raw: String): BackupPreview = BackupValidator.preview(raw)

    fun restore(context: Context, raw: String, mode: RestoreMode): BackupImportPlan {
        val preview = preview(raw)
        if (!preview.valid) return BackupImportPlan(0, 0, 1)
        writeCacheFile(context, exportJson(context))
        val root = JSONObject(raw)
        val settings = root.optJSONObject("settings") ?: JSONObject()
        SettingsStore.setTargetPhone(context, settings.optString("targetPhone", SettingsStore.targetPhone(context)))
        SettingsStore.setEnabled(context, settings.optBoolean("forwardingEnabled", SettingsStore.isEnabled(context)))
        SettingsStore.setFailureNotificationsEnabled(context, settings.optBoolean("failureNotificationsEnabled", true))
        SettingsStore.setLogRetention(context, settings.optString("logRetention", "DAYS_30"))
        runCatching {
            RuleStore.setForwardMode(context, com.oncet.smsquickforwarder.rules.ForwardMode.valueOf(settings.optString("forwardMode", "ALL")))
        }
        UpdatePreferences.setAutoCheckEnabled(context, settings.optBoolean("autoUpdateCheckEnabled", true))
        UpdatePreferences.setNotificationsEnabled(context, settings.optBoolean("updateNotificationsEnabled", true))
        runCatching {
            UpdatePreferences.setFrequency(context, UpdateFrequency.valueOf(settings.optString("updateCheckFrequency", UpdateFrequency.DAILY.name)))
        }
        val ignored = settings.optString("ignoredVersion")
        if (ignored.isNotBlank()) UpdatePreferences.ignoreVersion(context, ignored) else UpdatePreferences.clearIgnoredVersion(context)
        val ruleObj = JSONObject().put("rules", root.optJSONArray("rules") ?: JSONArray())
        val result = RuleStore.importRules(context, ruleObj.toString(), replace = mode == RestoreMode.REPLACE)
        UpdateScheduler.configure(context)
        return BackupImportPlan(result.added, result.duplicate, result.invalid)
    }

    private fun isoNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }
}
