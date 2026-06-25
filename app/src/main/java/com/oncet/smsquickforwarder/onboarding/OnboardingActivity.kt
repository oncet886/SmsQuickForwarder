package com.oncet.smsquickforwarder.onboarding

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.oncet.smsquickforwarder.R
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.service.ForwardForegroundService
import com.oncet.smsquickforwarder.sms.SmsForwarder
import com.oncet.smsquickforwarder.ui.UiKit
import com.oncet.smsquickforwarder.util.PhoneMaskUtils

class OnboardingActivity : Activity() {
    private lateinit var root: LinearLayout
    private var step = 0
    private var testEventId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = OnboardingPreferences.step(this)
        root = UiKit.page(this)
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(UiKit.title(this, "配置向导"))
        root.addView(UiKit.subtitle(this, "第 ${step + 1} / 6 步"))
        when (step) {
            0 -> welcomeStep()
            1 -> targetStep()
            2 -> permissionStep()
            3 -> backgroundStep()
            4 -> testStep()
            else -> completeStep()
        }
    }

    private fun welcomeStep() {
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@OnboardingActivity, "欢迎使用短信快转发"))
            addView(UiKit.subtitle(this@OnboardingActivity, "本 App 自动接收普通 SMS，并按规则转发到指定号码。不支持 RCS。自动发送短信可能产生运营商费用。"))
            addView(UiKit.primaryButton(this@OnboardingActivity, "开始配置") { next() })
            addView(UiKit.secondaryButton(this@OnboardingActivity, "稍后配置") { finish() })
        })
    }

    private fun targetStep() {
        val input = EditText(this).apply {
            hint = "+1 602-555-0108"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(SettingsStore.targetPhone(this@OnboardingActivity))
        }
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@OnboardingActivity, "设置转发目标手机号"))
            addView(input)
            addView(UiKit.subtitle(this@OnboardingActivity, "保存前会二次确认，联系人不会被读取。"))
            addView(UiKit.primaryButton(this@OnboardingActivity, "保存并继续") {
                val value = input.text.toString().trim()
                if (!isValidPhone(value)) {
                    Toast.makeText(this@OnboardingActivity, "请输入有效手机号", Toast.LENGTH_SHORT).show()
                    return@primaryButton
                }
                AlertDialog.Builder(this@OnboardingActivity)
                    .setTitle("确认目标号码")
                    .setMessage("短信将转发到：${PhoneMaskUtils.mask(value)}")
                    .setPositiveButton("确认") { _, _ ->
                        SettingsStore.setTargetPhone(this@OnboardingActivity, value)
                        next()
                    }
                    .setNegativeButton("返回修改", null)
                    .show()
            })
        })
    }

    private fun permissionStep() {
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@OnboardingActivity, "权限配置"))
            permissionRow(this, "接收短信", Manifest.permission.RECEIVE_SMS, 401)
            permissionRow(this, "发送短信", Manifest.permission.SEND_SMS, 402)
            if (Build.VERSION.SDK_INT >= 33) permissionRow(this, "通知", Manifest.permission.POST_NOTIFICATIONS, 403)
            addView(UiKit.primaryButton(this@OnboardingActivity, "下一步") { next() })
        })
    }

    private fun permissionRow(container: LinearLayout, label: String, permission: String, code: Int) {
        val row = UiKit.row(this)
        row.addView(UiKit.body(this, "$label：${if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "已允许" else "未允许"}").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(UiKit.secondaryButton(this, "去授权") { requestPermissions(arrayOf(permission), code) })
        container.addView(row)
    }

    private fun backgroundStep() {
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@OnboardingActivity, "后台运行"))
            addView(UiKit.subtitle(this@OnboardingActivity, "为避免锁屏或长时间待机后漏转，建议设置为不受限制。"))
            addView(UiKit.body(this@OnboardingActivity, "前台服务：${if (SettingsStore.isServiceRunning(this@OnboardingActivity)) "运行中" else "未运行"}"))
            addView(UiKit.body(this@OnboardingActivity, "电池优化：${if (batteryOk()) "不受限制" else "可能受限制"}"))
            addView(UiKit.secondaryButton(this@OnboardingActivity, "打开电池设置") { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } })
            addView(UiKit.secondaryButton(this@OnboardingActivity, "重新检查") { render() })
            addView(UiKit.primaryButton(this@OnboardingActivity, "下一步") { next() })
        })
    }

    private fun testStep() {
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@OnboardingActivity, "测试发送"))
            addView(UiKit.subtitle(this@OnboardingActivity, if (testEventId.isBlank()) "发送一条测试短信到目标号码。" else "测试事件：$testEventId"))
            addView(UiKit.primaryButton(this@OnboardingActivity, "发送测试短信") {
                testEventId = SmsForwarder.sendTestMessage(this@OnboardingActivity)
                Toast.makeText(this@OnboardingActivity, "已提交测试发送", Toast.LENGTH_SHORT).show()
                render()
            })
            addView(UiKit.secondaryButton(this@OnboardingActivity, "重新检查权限") { render() })
            addView(UiKit.primaryButton(this@OnboardingActivity, "下一步") { next() })
        })
    }

    private fun completeStep() {
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@OnboardingActivity, "配置完成"))
            addView(UiKit.subtitle(this@OnboardingActivity, "短信转发已准备就绪。"))
            addView(UiKit.body(this@OnboardingActivity, "目标号码：${PhoneMaskUtils.mask(SettingsStore.targetPhone(this@OnboardingActivity))}"))
            addView(UiKit.body(this@OnboardingActivity, "接收权限：${perm(Manifest.permission.RECEIVE_SMS)}"))
            addView(UiKit.body(this@OnboardingActivity, "发送权限：${perm(Manifest.permission.SEND_SMS)}"))
            addView(UiKit.body(this@OnboardingActivity, "通知权限：${if (Build.VERSION.SDK_INT < 33) "无需授权" else perm(Manifest.permission.POST_NOTIFICATIONS)}"))
            addView(UiKit.body(this@OnboardingActivity, "后台运行：${if (batteryOk()) "不受限制" else "建议继续设置"}"))
            addView(UiKit.body(this@OnboardingActivity, "测试发送：${if (testEventId.isBlank()) "未测试" else "已提交"}"))
            addView(UiKit.primaryButton(this@OnboardingActivity, "进入首页") {
                OnboardingPreferences.markCompleted(this@OnboardingActivity)
                if (SettingsStore.isEnabled(this@OnboardingActivity)) startForegroundService(Intent(this@OnboardingActivity, ForwardForegroundService::class.java))
                finish()
            })
        })
    }

    private fun next() {
        step = (step + 1).coerceAtMost(5)
        OnboardingPreferences.setStep(this, step)
        render()
    }

    private fun perm(permission: String): String =
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "已允许" else "未允许"

    private fun batteryOk(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun isValidPhone(value: String): Boolean =
        value.filter { it.isDigit() }.length >= 7 && value.length <= 32
}
