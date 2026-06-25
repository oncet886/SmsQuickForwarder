package com.oncet.smsquickforwarder.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import com.oncet.smsquickforwarder.ui.UiKit

class HealthCheckActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = UiKit.page(this)
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        val report = HealthCheckEngine(this).run()
        root.removeAllViews()
        root.addView(UiKit.title(this, "运行健康检查"))
        root.addView(UiKit.card(this).apply {
            val text = when (report.overall) {
                HealthSeverity.OK -> "运行状态良好"
                HealthSeverity.WARNING -> "发现 ${report.warningCount} 项需要注意"
                HealthSeverity.ACTION_REQUIRED -> "发现 ${report.actionRequiredCount} 项需要处理"
            }
            addView(UiKit.body(this@HealthCheckActivity, text))
        })
        report.items.forEach { item ->
            root.addView(UiKit.card(this).apply {
                val color = when (item.severity) {
                    HealthSeverity.OK -> getColor(com.oncet.smsquickforwarder.R.color.success)
                    HealthSeverity.WARNING -> getColor(com.oncet.smsquickforwarder.R.color.warning)
                    HealthSeverity.ACTION_REQUIRED -> getColor(com.oncet.smsquickforwarder.R.color.danger)
                }
                val row = UiKit.row(this@HealthCheckActivity)
                row.addView(UiKit.body(this@HealthCheckActivity, item.name).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(UiKit.tag(this@HealthCheckActivity, severityLabel(item.severity), color))
                addView(row)
                addView(UiKit.subtitle(this@HealthCheckActivity, item.summary))
                if (item.actionLabel.isNotBlank()) {
                    addView(UiKit.secondaryButton(this@HealthCheckActivity, item.actionLabel) { openAppSettings() })
                }
            })
        }
    }

    private fun severityLabel(severity: HealthSeverity): String = when (severity) {
        HealthSeverity.OK -> "正常"
        HealthSeverity.WARNING -> "注意"
        HealthSeverity.ACTION_REQUIRED -> "处理"
    }

    private fun openAppSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }
}
