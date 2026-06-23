package com.oncet.smsquickforwarder

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
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.service.ForwardForegroundService
import com.oncet.smsquickforwarder.sms.SmsForwarder

class MainActivity : Activity() {
    private lateinit var enabledSwitch: Switch
    private lateinit var targetInput: EditText
    private lateinit var statusText: TextView
    private lateinit var permissionText: TextView
    private lateinit var serviceText: TextView
    private lateinit var logsText: TextView
    private lateinit var sendSmsButton: Button
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
        maybeShowFirstRunGuide()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "Sms Quick Forwarder"
            textSize = 22f
            gravity = Gravity.CENTER_VERTICAL
        })

        root.addView(TextView(this).apply {
            text = "本 App 只会在你开启转发后，将本机收到的短信转发到你设置的手机号。请只在你拥有或被授权管理的设备上使用。"
            textSize = 14f
            setPadding(0, 18, 0, 18)
        })

        statusText = TextView(this).apply { textSize = 16f }
        root.addView(statusText)

        targetInput = EditText(this).apply {
            hint = "输入转发目标手机号，例如 +1602xxxxxxx"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        root.addView(targetInput)

        root.addView(Button(this).apply {
            text = "保存目标手机号"
            setOnClickListener {
                SettingsStore.setTargetPhone(this@MainActivity, targetInput.text.toString())
                refresh()
            }
        })

        enabledSwitch = Switch(this).apply {
            text = "启用短信转发 / 暂停短信转发"
            setOnCheckedChangeListener { _, checked ->
                if (refreshing) return@setOnCheckedChangeListener
                if (checked && !canEnableForwarding()) {
                    SettingsStore.setEnabled(this@MainActivity, false)
                    setSwitchWithoutCallback(false)
                    Toast.makeText(this@MainActivity, missingEnableMessage(), Toast.LENGTH_SHORT).show()
                    requestMissingCorePermissions()
                    refresh()
                    return@setOnCheckedChangeListener
                }
                SettingsStore.setEnabled(this@MainActivity, checked)
                if (checked) {
                    startForegroundService(Intent(this@MainActivity, ForwardForegroundService::class.java))
                } else {
                    stopService(Intent(this@MainActivity, ForwardForegroundService::class.java))
                    SettingsStore.setServiceRunning(this@MainActivity, false)
                }
                refresh()
            }
        }
        root.addView(enabledSwitch)

        val permissionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 4)
        }
        permissionHeader.addView(TextView(this).apply {
            text = "权限与运行条件"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        permissionHeader.addView(Button(this).apply {
            text = "设置帮助"
            setOnClickListener { showPermissionHelpDialog() }
        })
        root.addView(permissionHeader)

        permissionText = TextView(this).apply { textSize = 14f }
        root.addView(permissionText)

        sendSmsButton = Button(this).apply {
            text = "开启发送短信权限"
            setOnClickListener { requestSendSmsPermission() }
        }
        root.addView(sendSmsButton)

        root.addView(Button(this).apply {
            text = "重新检查"
            setOnClickListener {
                refresh()
                Toast.makeText(
                    this@MainActivity,
                    if (canForwardNormally()) "权限状态已更新" else "仍有设置未完成",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        root.addView(Button(this).apply {
            text = "申请/检查权限"
            setOnClickListener { requestNeededPermissions() }
        })

        serviceText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 8)
        }
        root.addView(serviceText)

        root.addView(Button(this).apply {
            text = "电池优化设置"
            setOnClickListener { openBatterySettings() }
        })

        root.addView(Button(this).apply {
            text = "测试发送"
            setOnClickListener {
                SmsForwarder.sendTestMessage(this@MainActivity)
                refresh()
            }
        })

        root.addView(Button(this).apply {
            text = "调试信息"
            setOnClickListener { startActivity(Intent(this@MainActivity, DebugActivity::class.java)) }
        })

        root.addView(Button(this).apply {
            text = "刷新日志"
            setOnClickListener { refresh() }
        })

        root.addView(Button(this).apply {
            text = "清空日志"
            setOnClickListener {
                ForwardLogStore.clear(this@MainActivity)
                refresh()
            }
        })

        logsText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 20, 0, 0)
        }
        root.addView(logsText)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refresh() {
        setSwitchWithoutCallback(SettingsStore.isEnabled(this))
        targetInput.setText(SettingsStore.targetPhone(this))

        val enabled = SettingsStore.isEnabled(this)
        val targetSet = SettingsStore.targetPhone(this).isNotBlank()
        statusText.text = "当前状态：${if (enabled) "正在转发" else "已暂停"}\n目标手机号：${if (targetSet) "已设置" else "未设置"}"
        permissionText.text = permissionCardText()
        sendSmsButton.visibility = if (hasSendSmsPermission()) View.GONE else View.VISIBLE
        serviceText.text = "前台服务：${if (SettingsStore.isServiceRunning(this)) "运行中" else "未运行"}\n通知权限：${notificationStatus()}\n电池优化：${batteryStatus()}"
        logsText.text = "最近日志：\n${ForwardLogStore.listText(this)}"
    }

    private fun setSwitchWithoutCallback(value: Boolean) {
        refreshing = true
        enabledSwitch.isChecked = value
        refreshing = false
    }

    private fun permissionCardText(): String = buildString {
        append("接收短信：").append(if (hasReceiveSmsPermission()) "已允许" else "未允许").append("\n")
        append("发送短信：").append(if (hasSendSmsPermission()) "已允许" else "未允许").append("\n")
        append("通知：").append(if (hasNotificationPermission()) "已允许" else "未允许").append("\n")
        append("电池后台运行：").append(if (isBatteryUnrestricted()) "不受限制" else "可能受限制").append("\n")
        append("综合状态：").append(if (canForwardNormally()) "可以正常转发" else "需要完成设置")
        if (!hasSendSmsPermission()) {
            append("\n\n发送短信权限尚未开启。此权限用于把本机收到的短信转发到你设置的目标号码。")
            append("\n路径：设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许")
        }
    }

    private fun maybeShowFirstRunGuide() {
        if (SettingsStore.isSetupGuideShown(this)) return
        SettingsStore.setSetupGuideShown(this, true)
        AlertDialog.Builder(this)
            .setTitle("开始使用")
            .setMessage(
                "1. 允许接收短信\n用于识别本机收到的普通短信。\n\n" +
                    "2. 允许发送短信\n用于把短信转发到目标号码。\n\n" +
                    "3. 设置电池为不受限制\n避免锁屏或后台时停止转发。"
            )
            .setPositiveButton("开始设置") { _, _ -> requestNeededPermissions() }
            .show()
    }

    private fun requestSendSmsPermission() {
        if (hasSendSmsPermission()) {
            refresh()
            return
        }
        val permanentlyDenied = SettingsStore.wasSendSmsRequested(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)
        if (permanentlyDenied) {
            showSendSmsSettingsDialog()
            return
        }
        SettingsStore.setSendSmsRequested(this, true)
        requestPermissions(arrayOf(Manifest.permission.SEND_SMS), REQUEST_SEND_SMS)
    }

    private fun requestMissingCorePermissions() {
        val missing = mutableListOf<String>()
        if (!hasReceiveSmsPermission()) missing.add(Manifest.permission.RECEIVE_SMS)
        if (!hasSendSmsPermission()) missing.add(Manifest.permission.SEND_SMS)
        if (missing.isEmpty()) return
        if (missing.contains(Manifest.permission.SEND_SMS)) {
            SettingsStore.setSendSmsRequested(this, true)
        }
        requestPermissions(missing.toTypedArray(), REQUEST_CORE_PERMISSIONS)
    }

    private fun requestNeededPermissions() {
        val missing = neededPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.contains(Manifest.permission.SEND_SMS)) {
            SettingsStore.setSendSmsRequested(this, true)
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_ALL_PERMISSIONS)
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        val sendIndex = permissions.indexOf(Manifest.permission.SEND_SMS)
        if (sendIndex >= 0 && !hasSendSmsPermission() && SettingsStore.wasSendSmsRequested(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)
        ) {
            showSendSmsSettingsDialog()
        }
    }

    private fun showSendSmsSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要开启发送短信权限")
            .setMessage(
                "SMS Quick Forwarder 需要发送短信权限，才能把收到的短信转发到目标号码。\n\n" +
                    "请按以下步骤开启：\n\n" +
                    "1. 打开应用信息\n" +
                    "2. 点击‘权限’\n" +
                    "3. 点击‘短信’\n" +
                    "4. 选择‘允许’\n\n" +
                    "三星手机通常路径为：\n设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许"
            )
            .setPositiveButton("打开应用设置") { _, _ -> openAppSettings() }
            .setNegativeButton("暂不设置", null)
            .show()
    }

    private fun showPermissionHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("短信权限设置")
            .setMessage(
                "三星手机：\n设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许\n\n" +
                    "其他安卓手机：\n设置 → 应用管理 → SMS Quick Forwarder → 权限管理 → 短信 → 允许\n\n" +
                    "如果页面中只显示‘短信’，进入后选择‘允许’即可。\n\n" +
                    "完成后返回 App，状态会自动刷新。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(
                this,
                "无法自动打开设置，请手动进入：设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }.onFailure { openAppSettings() }
    }

    private fun canEnableForwarding(): Boolean =
        SettingsStore.targetPhone(this).isNotBlank() && hasReceiveSmsPermission() && hasSendSmsPermission()

    private fun canForwardNormally(): Boolean =
        SettingsStore.targetPhone(this).isNotBlank() && hasReceiveSmsPermission() && hasSendSmsPermission() && hasNotificationPermission()

    private fun missingEnableMessage(): String = when {
        SettingsStore.targetPhone(this).isBlank() -> "请先设置目标手机号"
        !hasReceiveSmsPermission() && !hasSendSmsPermission() -> "请先开启接收和发送短信权限"
        !hasSendSmsPermission() -> "请先开启发送短信权限"
        !hasReceiveSmsPermission() -> "请先开启接收短信权限"
        else -> "仍有设置未完成"
    }

    private fun neededPermissions(): List<String> {
        val list = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list
    }

    private fun hasReceiveSmsPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hasSendSmsPermission(): Boolean =
        checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificationStatus(): String =
        if (hasNotificationPermission()) "已授权" else "缺失，前台服务通知可能不可见"

    private fun isBatteryUnrestricted(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun batteryStatus(): String =
        if (isBatteryUnrestricted()) "未受系统电池优化限制" else "可能受系统电池优化限制"

    override fun onResume() {
        super.onResume()
        refresh()
    }

    companion object {
        private const val REQUEST_SEND_SMS = 301
        private const val REQUEST_CORE_PERMISSIONS = 302
        private const val REQUEST_ALL_PERMISSIONS = 303
    }
}
