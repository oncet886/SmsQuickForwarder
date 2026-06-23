package com.oncet.smsquickforwarder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
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
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
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

        permissionText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 8)
        }
        root.addView(permissionText)

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
        refreshing = true
        targetInput.setText(SettingsStore.targetPhone(this))
        enabledSwitch.isChecked = SettingsStore.isEnabled(this)
        refreshing = false

        val enabled = SettingsStore.isEnabled(this)
        val targetSet = SettingsStore.targetPhone(this).isNotBlank()
        statusText.text = "当前状态：${if (enabled) "正在转发" else "已暂停"}\n目标手机号：${if (targetSet) "已设置" else "未设置"}"
        permissionText.text = "权限状态：\n${permissionStatus()}"
        serviceText.text = "前台服务：${if (SettingsStore.isServiceRunning(this)) "运行中" else "未运行"}\n通知权限：${notificationStatus()}\n电池优化：${batteryStatus()}"
        logsText.text = "最近日志：\n${ForwardLogStore.listText(this)}"
    }

    private fun permissionStatus(): String {
        return neededPermissions().joinToString("\n") { permission ->
            val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            "${permission.substringAfterLast('.')}：${if (granted) "已授权" else "缺失"}"
        }
    }

    private fun notificationStatus(): String {
        if (Build.VERSION.SDK_INT < 33) return "Android 13 以下无需运行时通知权限"
        return if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            "已授权"
        } else {
            "缺失，前台服务通知可能不可见"
        }
    }

    private fun batteryStatus(): String {
        val pm = getSystemService(PowerManager::class.java)
        return if (pm.isIgnoringBatteryOptimizations(packageName)) "未受系统电池优化限制" else "可能受系统电池优化限制"
    }

    private fun requestNeededPermissions() {
        val missing = neededPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 200)
        refresh()
    }

    private fun neededPermissions(): List<String> {
        val list = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
