package com.oncet.smsquickforwarder

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.debug.DebugInfoBuilder
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugActivity : Activity() {
    private lateinit var includeBodySwitch: android.widget.Switch
    private lateinit var includePhoneSwitch: android.widget.Switch
    private lateinit var debugText: TextView

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
            text = "调试信息"
            textSize = 22f
        })

        includeBodySwitch = android.widget.Switch(this).apply {
            text = "包含完整短信内容"
            isChecked = false
            setOnCheckedChangeListener { _: CompoundButton, _: Boolean -> refresh() }
        }
        root.addView(includeBodySwitch)

        includePhoneSwitch = android.widget.Switch(this).apply {
            text = "分享时包含完整手机号"
            isChecked = false
        }
        root.addView(includePhoneSwitch)

        root.addView(Button(this).apply {
            text = "分享调试 JSON"
            setOnClickListener { shareJson() }
        })

        root.addView(Button(this).apply {
            text = "复制诊断摘要"
            setOnClickListener { copyDiagnosticSummary() }
        })

        root.addView(Button(this).apply {
            text = "去设置"
            setOnClickListener { openAppSettings() }
        })

        root.addView(Button(this).apply {
            text = "清空调试日志"
            setOnClickListener { confirmClearLogs() }
        })

        root.addView(Button(this).apply {
            text = "电池优化设置"
            setOnClickListener { openBatterySettings() }
        })

        root.addView(Button(this).apply {
            text = "刷新"
            setOnClickListener { refresh() }
        })

        debugText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 18, 0, 0)
        }
        root.addView(debugText)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refresh() {
        debugText.text = DebugInfoBuilder.summaryText(this)
    }

    private fun shareJson() {
        val jsonText = DebugInfoBuilder.build(
            this,
            includeFullBody = includeBodySwitch.isChecked,
            includeFullPhone = includePhoneSwitch.isChecked
        ).toString(2)
        val file = writeDebugJson(jsonText)
        val uri = Uri.parse("content://$packageName.debugfileprovider/${file.name}")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "SmsQuickForwarder Debug JSON")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享调试 JSON"))
    }

    private fun writeDebugJson(jsonText: String): File {
        val dir = File(cacheDir, "debug-share").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "sms-quick-forwarder-debug-$timestamp.json")
        OutputStreamWriter(file.outputStream(), Charsets.UTF_8).use { it.write(jsonText) }
        return file
    }

    private fun copyDiagnosticSummary() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("SMS Quick Forwarder diagnostics", DebugInfoBuilder.diagnosticSummary(this)))
        Toast.makeText(this, "诊断摘要已复制", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearLogs() {
        AlertDialog.Builder(this)
            .setTitle("清空调试日志")
            .setMessage("只清空调试日志，不会清空转发设置。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ ->
                ForwardLogStore.clear(this)
                refresh()
            }
            .show()
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }.onFailure {
            openAppSettings()
        }
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

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
