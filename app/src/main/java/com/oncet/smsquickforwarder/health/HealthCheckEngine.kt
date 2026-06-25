package com.oncet.smsquickforwarder.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.failure.FailureNotificationPolicy
import com.oncet.smsquickforwarder.rules.ForwardMode
import com.oncet.smsquickforwarder.rules.RuleStore
import com.oncet.smsquickforwarder.rules.RuleType
import com.oncet.smsquickforwarder.update.UpdatePreferences
import com.oncet.smsquickforwarder.update.UpdateScheduler

class HealthCheckEngine(private val context: Context) {
    fun run(): HealthReport {
        val items = mutableListOf<HealthCheckItem>()
        items.add(item("转发总开关", SettingsStore.isEnabled(context), "已开启", "已暂停", HealthSeverity.WARNING))
        items.add(item("目标号码", SettingsStore.targetPhone(context).isNotBlank(), "已设置", "未设置目标号码", HealthSeverity.ACTION_REQUIRED))
        items.add(permissionItem("接收短信权限", Manifest.permission.RECEIVE_SMS))
        items.add(permissionItem("发送短信权限", Manifest.permission.SEND_SMS))
        items.add(HealthCheckItem("通知权限", if (hasNotificationPermission()) HealthSeverity.OK else HealthSeverity.WARNING, if (hasNotificationPermission()) "已允许" else "未允许，通知不可用", "去授权"))
        items.add(HealthCheckItem("SmsReceiver", HealthSeverity.OK, "已在 Manifest 声明"))
        items.add(HealthCheckItem("BootReceiver", HealthSeverity.OK, "已在 Manifest 声明"))
        items.add(HealthCheckItem("前台服务", if (!SettingsStore.isEnabled(context) || SettingsStore.isServiceRunning(context)) HealthSeverity.OK else HealthSeverity.WARNING, if (SettingsStore.isServiceRunning(context)) "运行中" else "未运行"))
        items.add(HealthCheckItem("电池优化", if (batteryOk()) HealthSeverity.OK else HealthSeverity.WARNING, if (batteryOk()) "不受限制" else "可能受限制", "打开设置"))
        val lastSms = ForwardLogStore.findLatestTime(context) { it.optString("eventType") == "sms_received" }
        val lastSuccess = ForwardLogStore.findLatestTime(context) { isForwardSuccess(it.optString("decision")) }
        val lastFailure = ForwardLogStore.findLatestTime(context) { it.optString("decision").contains("failed", ignoreCase = true) }
        items.add(HealthCheckItem("最近真实短信接收", HealthSeverity.OK, lastSms.ifBlank { "尚未记录真实短信测试" }))
        items.add(HealthCheckItem("最近转发成功", HealthSeverity.OK, lastSuccess.ifBlank { "尚未记录成功转发" }))
        val activeFailure = FailureNotificationPolicy.hasActiveFailure(lastFailure, lastSuccess)
        items.add(HealthCheckItem("最近失败", if (activeFailure) HealthSeverity.ACTION_REQUIRED else HealthSeverity.OK, if (lastFailure.isBlank()) "无失败记录" else "${ForwardLogStore.latestFailureReason(context)} ${if (activeFailure) "" else "，之后已恢复"}"))
        items.add(HealthCheckItem("自动更新检查", if (UpdatePreferences.autoCheckEnabled(context)) HealthSeverity.OK else HealthSeverity.WARNING, if (UpdatePreferences.autoCheckEnabled(context)) "已开启" else "已关闭"))
        val mode = RuleStore.forwardMode(context)
        val includeOk = mode != ForwardMode.MATCH_ONLY || RuleStore.rules(context).any { it.enabled && it.type == RuleType.INCLUDE }
        items.add(HealthCheckItem("当前规则模式", if (includeOk) HealthSeverity.OK else HealthSeverity.ACTION_REQUIRED, if (includeOk) mode.name else "仅匹配模式下没有启用的包含规则"))
        items.add(HealthCheckItem("日志存储", if (ForwardLogStore.totalCount(context) >= 0) HealthSeverity.OK else HealthSeverity.WARNING, "当前 ${ForwardLogStore.totalCount(context)} 条"))
        return HealthReport(HealthRules.overall(items), items)
    }

    private fun permissionItem(name: String, permission: String): HealthCheckItem {
        val ok = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return HealthCheckItem(name, if (ok) HealthSeverity.OK else HealthSeverity.ACTION_REQUIRED, if (ok) "已允许" else "未允许，无法自动转发", "去授权")
    }

    private fun item(name: String, ok: Boolean, okText: String, badText: String, badSeverity: HealthSeverity): HealthCheckItem =
        HealthCheckItem(name, if (ok) HealthSeverity.OK else badSeverity, if (ok) okText else badText)

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    private fun isForwardSuccess(decision: String): Boolean =
        decision == "forwarded" || decision == "forwarded_rule_match" || decision == "forwarded_all_mode"
}
