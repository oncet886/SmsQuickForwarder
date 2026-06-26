package com.oncet.smsquickforwarder.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.oncet.smsquickforwarder.R
import com.oncet.smsquickforwarder.service.ForwardForegroundService
import com.oncet.smsquickforwarder.ui.SystemBarsInsets
import com.oncet.smsquickforwarder.ui.UiKit

class BackupActivity : Activity() {
    private lateinit var root: LinearLayout
    private val currentBackup: String get() = BackupManager.exportJson(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = UiKit.page(this)
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        with(SystemBarsInsets) { applyStandardSystemBars(scroll, handleIme = true) }
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(UiKit.title(this, "备份与恢复"))
        root.addView(UiKit.subtitle(this, "仅备份设置和规则，不包含短信正文、日志详情、历史号码或任何密码。"))
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@BackupActivity, "导出配置"))
            addView(UiKit.primaryButton(this@BackupActivity, "复制 JSON 文本") {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SmsQuickForwarder backup", currentBackup))
                Toast.makeText(this@BackupActivity, "配置备份 JSON 已复制", Toast.LENGTH_SHORT).show()
            })
            addView(UiKit.secondaryButton(this@BackupActivity, "分享 JSON 文件") { shareBackupFile() })
            addView(UiKit.secondaryButton(this@BackupActivity, "保存为 JSON 文件") { saveBackupFile() })
        })
        root.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@BackupActivity, "恢复配置"))
            addView(UiKit.subtitle(this@BackupActivity, "粘贴备份 JSON 后会先预览，不会直接覆盖。"))
            addView(UiKit.primaryButton(this@BackupActivity, "粘贴导入 JSON") { showPasteImportDialog() })
        })
    }

    private fun shareBackupFile() {
        val file = BackupManager.writeCacheFile(this)
        val uri = FileProvider.getUriForFile(this, "${packageName}.debugfileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("sms_backup_json", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享配置备份"))
    }

    private fun saveBackupFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, BackupManager.writeCacheFile(this@BackupActivity).name)
        }
        runCatching { startActivityForResult(intent, 701) }.onFailure { shareBackupFile() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 701 && resultCode == RESULT_OK) {
            data?.data?.let { uri -> writeUri(uri, currentBackup) }
        }
    }

    private fun writeUri(uri: Uri, raw: String) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(raw.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "配置备份已保存", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show() }
    }

    private fun showPasteImportDialog() {
        val input = EditText(this).apply {
            minLines = 8
            hint = "{ ... }"
        }
        AlertDialog.Builder(this)
            .setTitle("粘贴备份 JSON")
            .setView(input)
            .setPositiveButton("预览") { _, _ -> showPreview(input.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPreview(raw: String) {
        val preview = BackupManager.preview(raw)
        AlertDialog.Builder(this)
            .setTitle(if (preview.valid) "恢复预览" else "无法恢复")
            .setMessage("版本：${preview.appVersionName}\n导出时间：${preview.exportedAt}\n目标号码：${if (preview.hasTargetPhone) "包含" else "无"}\n规则数量：${preview.ruleCount}\n未知字段：${preview.unknownFieldCount}\n状态：${preview.message}")
            .setPositiveButton("合并恢复") { _, _ -> restore(raw, RestoreMode.MERGE) }
            .setNeutralButton("全部替换") { _, _ -> confirmReplace(raw) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmReplace(raw: String) {
        AlertDialog.Builder(this)
            .setTitle("确认全部替换")
            .setMessage("将替换当前设置和规则。恢复前会自动创建当前配置快照，不会恢复或删除日志。")
            .setPositiveButton("确认替换") { _, _ -> restore(raw, RestoreMode.REPLACE) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun restore(raw: String, mode: RestoreMode) {
        val result = BackupManager.restore(this, raw, mode)
        if (com.oncet.smsquickforwarder.data.SettingsStore.isEnabled(this)) {
            startForegroundService(Intent(this, ForwardForegroundService::class.java))
        } else {
            stopService(Intent(this, ForwardForegroundService::class.java))
        }
        Toast.makeText(this, "恢复完成：新增 ${result.validRules}，重复 ${result.duplicateRules}，无效 ${result.invalidRules}", Toast.LENGTH_LONG).show()
        render()
    }
}
