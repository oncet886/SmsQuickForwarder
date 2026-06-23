package com.oncet.smsquickforwarder.debug

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telephony.SubscriptionManager
import com.oncet.smsquickforwarder.BuildConfig
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.util.PhoneMaskUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugInfoBuilder {
    fun build(context: Context, includeFullBody: Boolean, includeFullPhone: Boolean): JSONObject {
        val root = JSONObject()
        val errors = JSONArray()
        runCatching {
            root.put("appSettings", appSettings(context))
            root.put("permissions", permissions(context))
            root.put("systemInfo", systemInfo(context))
            root.put("appBuild", appBuild(context))
            root.put("simInfo", simInfo(context))
            root.put("batteryOptimization", batteryOptimization(context))
            root.put("permissionGuidance", permissionGuidance(context))
            root.put("stabilitySelfCheck", stabilitySelfCheck(context))
            root.put("recentEvents", ForwardLogStore.recentEvents(context, 20, includeFullBody, includeFullPhone))
        }.onFailure { e ->
            errors.put("${e.javaClass.simpleName}: ${e.message}")
        }
        root.put("exceptions", errors)
        return root
    }

    fun summaryText(context: Context): String {
        val json = build(context, includeFullBody = false, includeFullPhone = false)
        return buildString {
            append("App 设置\n")
            append(formatObject(json.optJSONObject("appSettings"))).append("\n\n")
            append("构建信息\n")
            append(formatObject(json.optJSONObject("appBuild"))).append("\n\n")
            append("权限状态\n")
            append(permissionSummary(context)).append("\n\n")
            append("系统信息\n")
            append(formatObject(json.optJSONObject("systemInfo"))).append("\n\n")
            append("稳定性自检\n")
            append(formatObject(json.optJSONObject("stabilitySelfCheck"))).append("\n\n")
            append("SIM 信息\n")
            append(formatObject(json.optJSONObject("simInfo"))).append("\n\n")
            append("电池优化\n")
            append(formatObject(json.optJSONObject("batteryOptimization"))).append("\n\n")
            append("最近事件\n")
            append(ForwardLogStore.listText(context, 20)).append("\n\n")
            append("稳定性测试清单\n")
            append(TEST_CHECKLIST)
        }
    }

    fun diagnosticSummary(context: Context): String {
        val target = PhoneMaskUtils.mask(SettingsStore.targetPhone(context))
        val lastSms = ForwardLogStore.findLatestTime(context) { it.optString("eventType") == "sms_received" }
        val lastForward = ForwardLogStore.findLatestTime(context) { it.optString("decision") == "forwarded" }
        val lastError = ForwardLogStore.latestFailureReason(context)
        return buildString {
            append("SMS Quick Forwarder ${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}\n")
            append("Device: ${Build.BRAND} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n")
            append("Forwarding: ${if (SettingsStore.isEnabled(context)) "enabled" else "disabled"}\n")
            append("Target: $target\n")
            append("Receive SMS: ${permissionState(context, Manifest.permission.RECEIVE_SMS)}\n")
            append("Send SMS: ${permissionState(context, Manifest.permission.SEND_SMS)}\n")
            append("Notifications: ${notificationState(context)}\n")
            append("Foreground service: ${if (SettingsStore.isServiceRunning(context)) "running" else "stopped"}\n")
            append("Battery unrestricted: ${if (isBatteryUnrestricted(context)) "yes" else "no"}\n")
            append("Last SMS received: ${lastSms.ifBlank { "none" }}\n")
            append("Last forward success: ${lastForward.ifBlank { "none" }}\n")
            append("Last error: ${lastError.ifBlank { "none" }}")
        }
    }

    private fun appSettings(context: Context) = JSONObject().apply {
        put("forwardingEnabled", SettingsStore.isEnabled(context))
        put("targetPhoneSet", SettingsStore.targetPhone(context).isNotBlank())
        put("loopGuardEnabled", true)
        put("foregroundServiceRunning", SettingsStore.isServiceRunning(context))
    }

    private fun permissions(context: Context) = JSONObject().apply {
        put("RECEIVE_SMS", permissionState(context, Manifest.permission.RECEIVE_SMS))
        put("SEND_SMS", permissionState(context, Manifest.permission.SEND_SMS))
        put("POST_NOTIFICATIONS", notificationState(context))
        put("RECEIVE_BOOT_COMPLETED", manifestDeclared(context, Manifest.permission.RECEIVE_BOOT_COMPLETED))
        put("READ_PHONE_STATE", if (manifestDeclared(context, Manifest.permission.READ_PHONE_STATE)) permissionState(context, Manifest.permission.READ_PHONE_STATE) else "not_declared")
    }

    private fun permissionGuidance(context: Context) = JSONObject().apply {
        put("sendSmsActionRequired", context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
        put("recommendedPath", "设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许")
    }

    private fun permissionSummary(context: Context): String = buildString {
        append("接收短信权限：").append(chinesePermissionState(context, Manifest.permission.RECEIVE_SMS)).append("\n")
        append("发送短信权限：").append(chinesePermissionState(context, Manifest.permission.SEND_SMS)).append("\n")
        append("通知权限：").append(if (notificationState(context) == "granted" || notificationState(context) == "not_required") "已允许" else "未允许")
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            append("\n\n发送短信权限尚未开启。路径：设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许")
        }
    }

    private fun chinesePermissionState(context: Context, permission: String): String =
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "已允许" else "未允许"

    private fun systemInfo(context: Context) = JSONObject().apply {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        put("androidVersion", Build.VERSION.RELEASE)
        put("sdkVersion", Build.VERSION.SDK_INT)
        put("brand", Build.BRAND)
        put("model", Build.MODEL)
        put("appVersionName", packageInfo.versionName ?: "")
        put("appVersionCode", if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode)
        put("currentTime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
    }

    private fun appBuild(context: Context) = JSONObject().apply {
        put("applicationId", BuildConfig.APPLICATION_ID)
        put("versionName", BuildConfig.VERSION_NAME)
        put("versionCode", BuildConfig.VERSION_CODE)
        put("buildType", BuildConfig.BUILD_TYPE)
        put("debuggable", BuildConfig.DEBUG)
        put("gitCommit", BuildConfig.GIT_COMMIT)
        put("buildTime", BuildConfig.BUILD_TIME)
        put("installedPackage", context.packageName)
    }

    private fun simInfo(context: Context) = JSONObject().apply {
        put("defaultSmsSubscriptionId", SubscriptionManager.getDefaultSmsSubscriptionId())
        val active = JSONArray()
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                val manager = context.getSystemService(SubscriptionManager::class.java)
                manager.activeSubscriptionInfoList?.forEach { info ->
                    active.put(JSONObject().apply {
                        put("subscriptionId", info.subscriptionId)
                        put("simSlotIndex", info.simSlotIndex)
                        put("carrierName", info.carrierName?.toString() ?: "")
                        put("displayName", info.displayName?.toString() ?: "")
                    })
                }
            }.onFailure { e ->
                put("activeSubscriptionError", "${e.javaClass.simpleName}: ${e.message}")
            }
        } else {
            put("activeSubscriptionNote", "READ_PHONE_STATE 未授权，无法读取 active subscription 列表")
        }
        put("activeSubscriptions", active)
        put("lastReceivedRawValues", lastRawValues(context))
    }

    private fun lastRawValues(context: Context): JSONObject {
        val events = ForwardLogStore.recentEvents(context, 1, includeFullBody = false, includeFullPhone = false)
        if (events.length() == 0) return JSONObject()
        val last = events.getJSONObject(0)
        return JSONObject().apply {
            put("slot", last.optString("slot"))
            put("simId", last.optString("simId"))
            put("subscription", last.optString("subscription"))
        }
    }

    private fun batteryOptimization(context: Context) = JSONObject().apply {
        val unrestricted = isBatteryUnrestricted(context)
        put("isIgnoringBatteryOptimizations", unrestricted)
        put("restricted", !unrestricted)
    }

    private fun stabilitySelfCheck(context: Context): JSONObject {
        val receiveSms = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val sendSms = context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val targetSet = SettingsStore.targetPhone(context).isNotBlank()
        val serviceRunning = SettingsStore.isServiceRunning(context)
        val forwardingEnabled = SettingsStore.isEnabled(context)
        val batteryOk = isBatteryUnrestricted(context)
        val lastFailureAt = ForwardLogStore.findLatestTime(context) { it.optString("decision").contains("failed", ignoreCase = true) }
        val lastForwardSuccessAt = ForwardLogStore.findLatestTime(context) { it.optString("decision") == "forwarded" }
        val lastRealSmsReceivedAt = ForwardLogStore.findLatestTime(context) { it.optString("eventType") == "sms_received" }
        val lastRealSmsForwardedAt = ForwardLogStore.findLatestTime(context) {
            it.optString("eventType") == "sms_received" && it.optString("decision") == "forwarded"
        }
        val failuresAfterLastSuccess = lastFailureAt.isNotBlank() && (lastForwardSuccessAt.isBlank() || lastFailureAt > lastForwardSuccessAt)
        val hardFail = !receiveSms || !sendSms || !targetSet
        val warning = forwardingEnabled && !serviceRunning || !batteryOk
        val overall = when {
            hardFail -> "不可用"
            warning -> "有警告"
            else -> "正常"
        }
        return JSONObject().apply {
            put("overallStatus", overall)
            put("forwardingEnabled", forwardingEnabled)
            put("receiveSms", receiveSms)
            put("sendSms", sendSms)
            put("postNotifications", notificationState(context))
            put("foregroundServiceRunning", serviceRunning)
            put("batteryOptimizationDisabled", batteryOk)
            put("notificationsAllowed", notificationState(context) == "granted" || notificationState(context) == "not_required")
            put("targetPhoneSet", targetSet)
            put("smsReceiverDeclared", receiverDeclared(context, "SmsReceiver"))
            put("bootReceiverDeclared", receiverDeclared(context, "BootReceiver"))
            put("defaultSmsSubscriptionId", SubscriptionManager.getDefaultSmsSubscriptionId())
            put("lastSmsReceivedAt", lastRealSmsReceivedAt)
            put("lastForwardSuccessAt", lastForwardSuccessAt)
            put("lastFailureAt", lastFailureAt)
            put("lastFailureReason", ForwardLogStore.latestFailureReason(context))
            put("failuresAfterLastSuccess", failuresAfterLastSuccess)
            put("currentFailureActive", failuresAfterLastSuccess)
            put("hasReceivedRealSms", lastRealSmsReceivedAt.isNotBlank())
            put("lastRealSmsReceivedAt", lastRealSmsReceivedAt)
            put("lastRealSmsForwardedAt", lastRealSmsForwardedAt)
            put(
                "realSmsStatus",
                if (lastRealSmsForwardedAt.isNotBlank()) "真实短信转发已验证" else "尚未记录真实短信测试"
            )
            if (lastFailureAt.isNotBlank() && !failuresAfterLastSuccess) {
                put("historicalFailureNote", "历史最近失败：${ForwardLogStore.latestFailureReason(context)}，之后已恢复正常")
            }
        }
    }

    private fun permissionState(context: Context, permission: String): String {
        val declared = manifestDeclared(context, permission)
        val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return when {
            !declared -> "not_declared"
            granted -> "granted"
            else -> "denied"
        }
    }

    private fun notificationState(context: Context): String {
        if (Build.VERSION.SDK_INT < 33) return "not_required"
        return permissionState(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun manifestDeclared(context: Context, permission: String): Boolean {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.contains(permission) == true
    }

    private fun receiverDeclared(context: Context, simpleName: String): Boolean {
        val flags = PackageManager.GET_RECEIVERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        return info.receivers?.any { it.name.endsWith(".$simpleName") } == true
    }

    private fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun formatObject(obj: JSONObject?): String {
        if (obj == null) return ""
        val keys = obj.keys()
        val sb = StringBuilder()
        while (keys.hasNext()) {
            val key = keys.next()
            sb.append(key).append(": ").append(obj.opt(key)).append("\n")
        }
        return sb.toString().trim()
    }

    const val TEST_CHECKLIST = """
1. 前台打开 App 收短信
2. App 切后台收短信
3. 屏幕锁定后收短信
4. 从最近任务划掉 App 后收短信
5. 手机重启后不打开 App，直接收短信
6. 放置 1 小时后收短信
7. 放置 6 小时后收短信
8. 飞行模式下收/发
9. 无蜂窝信号时发送失败和重试
10. 双卡环境下接收和发送
11. 长短信 multipart
12. 目标号码发来的短信防循环
13. 包含 [SMS Forward] 的短信防循环
14. 手机重启后前台服务恢复
15. 日志和 Debug JSON 是否完整
"""
}
