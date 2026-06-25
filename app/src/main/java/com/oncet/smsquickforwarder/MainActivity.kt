package com.oncet.smsquickforwarder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.debug.DebugInfoBuilder
import com.oncet.smsquickforwarder.rules.ForwardMode
import com.oncet.smsquickforwarder.rules.RuleStore
import com.oncet.smsquickforwarder.rules.RuleType
import com.oncet.smsquickforwarder.service.ForwardForegroundService
import com.oncet.smsquickforwarder.sms.SmsForwarder
import com.oncet.smsquickforwarder.ui.UiKit
import com.oncet.smsquickforwarder.update.UpdateCheckResult
import com.oncet.smsquickforwarder.update.UpdateChecker
import com.oncet.smsquickforwarder.update.UpdateFrequency
import com.oncet.smsquickforwarder.update.UpdateInfo
import com.oncet.smsquickforwarder.update.UpdateNotifier
import com.oncet.smsquickforwarder.update.UpdatePreferences
import com.oncet.smsquickforwarder.update.UpdateScheduler
import com.oncet.smsquickforwarder.util.MessagePrivacyUtils
import com.oncet.smsquickforwarder.util.PhoneMaskUtils

class MainActivity : Activity() {
    private lateinit var contentRoot: LinearLayout
    private lateinit var navRoot: LinearLayout
    private var currentTab = Tab.HOME
    private var refreshing = false
    private var updateResult: UpdateCheckResult? = null
    private var updateChecking = false
    private var sessionHiddenUpdateVersion = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        render()
        maybeShowFirstRunGuide()
        UpdateScheduler.configure(this)
        scheduleStartupUpdateCheck()
    }

    override fun onResume() {
        super.onResume()
        if (::contentRoot.isInitialized) render()
    }

    private fun buildShell() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.app_background))
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        contentRoot = UiKit.page(this)
        scroll.addView(contentRoot)
        navRoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 10))
            setBackgroundColor(getColor(R.color.surface_card))
        }
        shell.addView(scroll)
        shell.addView(navRoot)
        setContentView(shell)
    }

    private fun render() {
        contentRoot.removeAllViews()
        when (currentTab) {
            Tab.HOME -> renderHome()
            Tab.RULES -> renderRules()
            Tab.LOGS -> renderLogsPreview()
            Tab.ABOUT -> renderAbout()
        }
        renderNav()
    }

    private fun renderNav() {
        navRoot.removeAllViews()
        navRoot.addView(UiKit.navButton(this, getString(R.string.home), currentTab == Tab.HOME) { currentTab = Tab.HOME; render() })
        navRoot.addView(UiKit.navButton(this, getString(R.string.rules), currentTab == Tab.RULES) { currentTab = Tab.RULES; render() })
        navRoot.addView(UiKit.navButton(this, getString(R.string.logs), currentTab == Tab.LOGS) { currentTab = Tab.LOGS; render() })
        navRoot.addView(UiKit.navButton(this, getString(R.string.about), currentTab == Tab.ABOUT) { currentTab = Tab.ABOUT; render() })
    }

    private fun renderHome() {
        contentRoot.addView(UiKit.title(this, getString(R.string.app_name)))
        contentRoot.addView(UiKit.subtitle(this, "只在你开启转发后处理本机收到的普通 SMS"))
        contentRoot.addView(statusCard())
        updatePromptCard()?.let { contentRoot.addView(it) }
        contentRoot.addView(targetCard())
        contentRoot.addView(switchCard())
        contentRoot.addView(quickActionsCard())
        contentRoot.addView(permissionCard())
        contentRoot.addView(recentPreviewCard(3))
        contentRoot.addView(UiKit.subtitle(this, "${getString(R.string.current_version)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
    }

    private fun statusCard(): LinearLayout {
        val target = SettingsStore.targetPhone(this)
        val lastSuccess = ForwardLogStore.findLatestTime(this) { isForwardSuccess(it.optString("decision")) }.ifBlank { "暂无" }
        val todaySuccess = ForwardLogStore.countToday(this) { isForwardSuccess(it.optString("decision")) }
        val todayFailed = ForwardLogStore.countToday(this) { it.optString("decision").contains("failed", ignoreCase = true) }
        val state = currentState()
        return UiKit.card(this).apply {
            val row = UiKit.row(this@MainActivity)
            row.addView(UiKit.body(this@MainActivity, "当前状态").apply {
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(UiKit.tag(this@MainActivity, state.label, state.color))
            addView(row)
            addView(UiKit.subtitle(this@MainActivity, if (SettingsStore.isEnabled(this@MainActivity) && target.isNotBlank()) "收到符合规则的短信后，将自动转发到 ${PhoneMaskUtils.mask(target)}" else "短信转发已暂停或配置未完成"))
            addView(UiKit.divider(this@MainActivity))
            addView(UiKit.body(this@MainActivity, "转发目标：${if (target.isNotBlank()) PhoneMaskUtils.mask(target) else "未设置"}"))
            addView(UiKit.body(this@MainActivity, "最近成功：$lastSuccess"))
            addView(UiKit.body(this@MainActivity, "今日成功 / 失败：$todaySuccess / $todayFailed"))
            addView(UiKit.body(this@MainActivity, "转发模式：${modeLabel(RuleStore.forwardMode(this@MainActivity))}"))
        }
    }

    private fun updatePromptCard(): LinearLayout? {
        val result = updateResult as? UpdateCheckResult.UpdateAvailable ?: return null
        val info = result.info
        if (sessionHiddenUpdateVersion == info.latestVersionName || UpdatePreferences.ignoredVersion(this) == info.latestVersionName) return null
        return UiKit.card(this).apply {
            val row = UiKit.row(this@MainActivity)
            row.addView(UiKit.body(this@MainActivity, "${getString(R.string.update_available)} ${info.latestVersionName}").apply {
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(UiKit.tag(this@MainActivity, "NEW", getColor(R.color.primary)))
            addView(row)
            addView(UiKit.subtitle(this@MainActivity, "当前版本 ${BuildConfig.VERSION_NAME}\n\n本次更新：\n${releaseNotesPreview(info.releaseNotes)}"))
            val actions = UiKit.row(this@MainActivity)
            actions.addView(gridButton(getString(R.string.view_update)) { openUrl(info.releaseUrl) })
            actions.addView(gridButton(getString(R.string.ignore_this_version)) {
                UpdatePreferences.ignoreVersion(this@MainActivity, info.latestVersionName)
                render()
            })
            actions.addView(gridButton(getString(R.string.later)) {
                sessionHiddenUpdateVersion = info.latestVersionName
                render()
            })
            addView(actions)
        }
    }

    private fun targetCard(): LinearLayout {
        val input = EditText(this).apply {
            hint = "目标手机号"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(SettingsStore.targetPhone(this@MainActivity))
        }
        return UiKit.card(this).apply {
            addView(UiKit.body(this@MainActivity, "转发目标"))
            addView(input)
            addView(UiKit.primaryButton(this@MainActivity, "保存目标手机号") {
                SettingsStore.setTargetPhone(this@MainActivity, input.text.toString())
                render()
            })
        }
    }

    private fun switchCard(): LinearLayout {
        val enabledSwitch = Switch(this).apply {
            text = "启用短信转发"
            refreshing = true
            isChecked = SettingsStore.isEnabled(this@MainActivity)
            refreshing = false
            setOnCheckedChangeListener { _, checked ->
                if (refreshing) return@setOnCheckedChangeListener
                if (checked && !canEnableForwarding()) {
                    SettingsStore.setEnabled(this@MainActivity, false)
                    isChecked = false
                    Toast.makeText(this@MainActivity, missingEnableMessage(), Toast.LENGTH_SHORT).show()
                    requestMissingCorePermissions()
                    render()
                    return@setOnCheckedChangeListener
                }
                SettingsStore.setEnabled(this@MainActivity, checked)
                if (checked) startForegroundService(Intent(this@MainActivity, ForwardForegroundService::class.java)) else {
                    stopService(Intent(this@MainActivity, ForwardForegroundService::class.java))
                    SettingsStore.setServiceRunning(this@MainActivity, false)
                }
                render()
            }
        }
        return UiKit.card(this).apply {
            addView(enabledSwitch)
            addView(UiKit.subtitle(this@MainActivity, "关闭后仍保留设置和日志，但不会转发新短信。"))
        }
    }

    private fun quickActionsCard(): LinearLayout = UiKit.card(this).apply {
        addView(UiKit.body(this@MainActivity, "快捷操作"))
        val row1 = UiKit.row(this@MainActivity)
        row1.addView(gridButton("测试发送") {
            SmsForwarder.sendTestMessage(this@MainActivity)
            render()
        })
        row1.addView(gridButton("规则设置") { currentTab = Tab.RULES; render() })
        val row2 = UiKit.row(this@MainActivity)
        row2.addView(gridButton("最近记录") { currentTab = Tab.LOGS; render() })
        row2.addView(gridButton(getString(R.string.debug_info)) { startActivity(Intent(this@MainActivity, DebugActivity::class.java)) })
        addView(row1)
        addView(row2)
    }

    private fun permissionCard(): LinearLayout = UiKit.card(this).apply {
        val row = UiKit.row(this@MainActivity)
        row.addView(UiKit.body(this@MainActivity, "权限与运行条件").apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        row.addView(UiKit.tag(this@MainActivity, if (canForwardNormally()) getString(R.string.ready_to_forward) else getString(R.string.setup_required), if (canForwardNormally()) getColor(R.color.success) else getColor(R.color.warning)))
        addView(row)
        addView(UiKit.divider(this@MainActivity))
        addView(UiKit.body(this@MainActivity, "接收短信：${if (hasReceiveSmsPermission()) "已允许" else "未允许"}"))
        addView(UiKit.body(this@MainActivity, "发送短信：${if (hasSendSmsPermission()) "已允许" else "未允许"}"))
        addView(UiKit.body(this@MainActivity, "通知：${if (hasNotificationPermission()) "已允许" else "未允许"}"))
        addView(UiKit.body(this@MainActivity, "电池后台运行：${if (isBatteryUnrestricted()) "不受限制" else "可能受限制"}"))
        if (!hasSendSmsPermission()) {
            addView(UiKit.subtitle(this@MainActivity, "发送短信权限尚未开启。此权限用于把本机收到的短信转发到你设置的目标号码。"))
            addView(UiKit.primaryButton(this@MainActivity, "开启发送短信权限") { requestSendSmsPermission() })
        }
        val actions = UiKit.row(this@MainActivity)
        actions.addView(gridButton(getString(R.string.setup_help)) { showPermissionHelpDialog() })
        actions.addView(gridButton("重新检查") {
            render()
            Toast.makeText(this@MainActivity, if (canForwardNormally()) "权限状态已更新" else "仍有设置未完成", Toast.LENGTH_SHORT).show()
        })
        addView(actions)
    }

    private fun recentPreviewCard(limit: Int): LinearLayout = UiKit.card(this).apply {
        val row = UiKit.row(this@MainActivity)
        row.addView(UiKit.body(this@MainActivity, getString(R.string.recent_logs)).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        row.addView(UiKit.secondaryButton(this@MainActivity, getString(R.string.view_all)) { currentTab = Tab.LOGS; render() })
        addView(row)
        val events = ForwardLogStore.recentUserEvents(this@MainActivity, limit)
        if (events.length() == 0) {
            addView(UiKit.subtitle(this@MainActivity, "暂无有效短信记录"))
        } else {
            for (i in 0 until events.length()) {
                val obj = events.getJSONObject(i)
                addView(UiKit.divider(this@MainActivity))
                addView(UiKit.body(this@MainActivity, "${obj.optString("receivedAt")}  ${decisionLabel(obj.optString("decision"))}"))
                addView(UiKit.subtitle(this@MainActivity, "${PhoneMaskUtils.mask(obj.optString("sender"))}  ${MessagePrivacyUtils.maskVerificationCodes(obj.optString("bodyPreview"))}\n规则：${obj.optString("primaryMatchedRuleName").ifBlank { "无" }}"))
            }
        }
    }

    private fun renderRules() {
        contentRoot.addView(UiKit.title(this, getString(R.string.rules)))
        val rules = RuleStore.rules(this)
        val mode = RuleStore.forwardMode(this)
        contentRoot.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@MainActivity, "${getString(R.string.forward_mode)}：${modeLabel(mode)}"))
            addView(UiKit.subtitle(this@MainActivity, "已启用 ${rules.count { it.enabled }} · 包含 ${rules.count { it.type == RuleType.INCLUDE }} · 排除 ${rules.count { it.type == RuleType.EXCLUDE }}"))
            addView(UiKit.primaryButton(this@MainActivity, if (mode == ForwardMode.ALL) "切换为仅匹配短信" else "切换为转发全部短信") {
                val next = if (mode == ForwardMode.ALL) ForwardMode.MATCH_ONLY else ForwardMode.ALL
                RuleStore.setForwardMode(this@MainActivity, next)
                if (next == ForwardMode.MATCH_ONLY && rules.none { it.enabled && it.type == RuleType.INCLUDE }) {
                    Toast.makeText(this@MainActivity, "当前没有包含规则，所有短信都会被跳过。", Toast.LENGTH_LONG).show()
                }
                render()
            })
        })
        contentRoot.addView(UiKit.section(this, "主要操作"))
        val actions = UiKit.card(this)
        val row1 = UiKit.row(this)
        row1.addView(gridButton("新建包含规则") { startActivity(Intent(this, RuleEditActivity::class.java).putExtra(RuleEditActivity.EXTRA_TYPE, RuleType.INCLUDE.name)) })
        row1.addView(gridButton("新建排除规则") { startActivity(Intent(this, RuleEditActivity::class.java).putExtra(RuleEditActivity.EXTRA_TYPE, RuleType.EXCLUDE.name)) })
        val row2 = UiKit.row(this)
        row2.addView(gridButton(getString(R.string.test_rules)) { startActivity(Intent(this, RuleTestActivity::class.java)) })
        row2.addView(gridButton("完整规则管理") { startActivity(Intent(this, RulesActivity::class.java)) })
        actions.addView(row1)
        actions.addView(row2)
        contentRoot.addView(actions)
        contentRoot.addView(UiKit.section(this, "规则列表"))
        if (rules.isEmpty()) {
            contentRoot.addView(UiKit.card(this).apply { addView(UiKit.body(this@MainActivity, "暂无规则。默认仍会转发全部普通短信。")) })
        } else {
            rules.take(20).forEach { rule ->
                contentRoot.addView(UiKit.card(this).apply {
                    val row = UiKit.row(this@MainActivity)
                    row.addView(UiKit.body(this@MainActivity, "${rule.priority}. ${rule.name}").apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                    row.addView(UiKit.tag(this@MainActivity, if (rule.type == RuleType.INCLUDE) "包含" else "排除", if (rule.type == RuleType.INCLUDE) getColor(R.color.primary) else getColor(R.color.warning)))
                    addView(row)
                    addView(UiKit.subtitle(this@MainActivity, "${rule.field.name} / ${rule.matchMode.name} / ${rule.pattern.take(48)}\n${if (rule.enabled) "已启用" else "已停用"}"))
                })
            }
        }
        contentRoot.addView(UiKit.section(this, "高级操作"))
        contentRoot.addView(UiKit.card(this).apply {
            val row = UiKit.row(this@MainActivity)
            row.addView(gridButton("复制规则 JSON") {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("rules", RuleStore.exportRules(this@MainActivity, includePatterns = true)))
                Toast.makeText(this@MainActivity, "规则 JSON 已复制", Toast.LENGTH_SHORT).show()
            })
            row.addView(gridButton("粘贴导入规则") { startActivity(Intent(this@MainActivity, RulesActivity::class.java)) })
            addView(row)
        })
    }

    private fun renderLogsPreview() {
        contentRoot.addView(UiKit.title(this, getString(R.string.logs)))
        contentRoot.addView(UiKit.subtitle(this, "最近短信处理记录"))
        contentRoot.addView(recentPreviewCard(20))
        contentRoot.addView(UiKit.primaryButton(this, "打开记录筛选页") { startActivity(Intent(this, LogsActivity::class.java)) })
    }

    private fun renderAbout() {
        contentRoot.addView(UiKit.title(this, getString(R.string.about)))
        contentRoot.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@MainActivity, getString(R.string.app_name)).apply { textSize = 18f })
            addView(UiKit.subtitle(this@MainActivity, "短信快转发 / SMS Quick Forwarder"))
            addView(UiKit.body(this@MainActivity, "${getString(R.string.current_version)}：${BuildConfig.VERSION_NAME}"))
            addView(UiKit.body(this@MainActivity, "${getString(R.string.build_number)}：${BuildConfig.VERSION_CODE}"))
            addView(UiKit.body(this@MainActivity, "applicationId：${BuildConfig.APPLICATION_ID}"))
        })
        contentRoot.addView(updateStatusCard())
        contentRoot.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@MainActivity, "项目与更新"))
            addView(UiKit.primaryButton(this@MainActivity, getString(R.string.changelog)) { startActivity(Intent(this@MainActivity, ChangelogActivity::class.java)) })
            addView(UiKit.secondaryButton(this@MainActivity, getString(R.string.github_repository)) { openUrl("https://github.com/oncet886/SmsQuickForwarder") })
            addView(UiKit.secondaryButton(this@MainActivity, getString(R.string.github_releases)) { openUrl("https://github.com/oncet886/SmsQuickForwarder/releases") })
        })
        contentRoot.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@MainActivity, "调试和支持"))
            addView(UiKit.primaryButton(this@MainActivity, getString(R.string.debug_info)) { startActivity(Intent(this@MainActivity, DebugActivity::class.java)) })
            addView(UiKit.secondaryButton(this@MainActivity, getString(R.string.copy_diagnostic_summary)) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("diagnostics", DebugInfoBuilder.diagnosticSummary(this@MainActivity)))
                Toast.makeText(this@MainActivity, "诊断摘要已复制", Toast.LENGTH_SHORT).show()
            })
        })
        contentRoot.addView(UiKit.card(this).apply {
            addView(UiKit.body(this@MainActivity, "隐私与说明"))
            addView(UiKit.subtitle(this@MainActivity, "网络权限仅用于访问 GitHub Releases 检查新版本，不上传短信、号码、规则或日志。调试 JSON 只有在你主动复制或分享时才会离开 App。自动发送短信可能产生运营商费用。"))
        })
    }

    private fun updateStatusCard(): LinearLayout = UiKit.card(this).apply {
        addView(UiKit.body(this@MainActivity, getString(R.string.version_updates)).apply { textSize = 16f })
        addView(UiKit.body(this@MainActivity, "当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        addView(UiKit.body(this@MainActivity, "自动检查更新：${if (UpdatePreferences.autoCheckEnabled(this@MainActivity)) "开启" else "关闭"}"))
        addView(UiKit.body(this@MainActivity, "检查频率：${frequencyLabel(UpdatePreferences.frequency(this@MainActivity))}"))
        addView(UiKit.body(this@MainActivity, "上次检查：${formatCheckTime(UpdatePreferences.lastCheckAt(this@MainActivity))}"))
        addView(UiKit.body(this@MainActivity, "当前状态：${updateStateLabel()}"))
        val ignored = UpdatePreferences.ignoredVersion(this@MainActivity)
        if (ignored.isNotBlank()) addView(UiKit.subtitle(this@MainActivity, "已忽略版本：$ignored"))
        val row1 = UiKit.row(this@MainActivity)
        row1.addView(gridButton(if (updateChecking) "检查中..." else getString(R.string.check_now)) { manualCheckUpdates() })
        row1.addView(gridButton(getString(R.string.github_releases)) { openUrl("https://github.com/oncet886/SmsQuickForwarder/releases") })
        val row2 = UiKit.row(this@MainActivity)
        row2.addView(gridButton(getString(R.string.update_settings)) { showUpdateSettingsDialog() })
        val latestUrl = (updateResult as? UpdateCheckResult.UpdateAvailable)?.info?.releaseUrl ?: UpdatePreferences.lastKnownReleaseUrl(this@MainActivity)
        row2.addView(gridButton(getString(R.string.view_update)) {
            openUrl(latestUrl.ifBlank { "https://github.com/oncet886/SmsQuickForwarder/releases" })
        })
        addView(row1)
        addView(row2)
    }

    private fun gridButton(text: String, onClick: () -> Unit) = UiKit.secondaryButton(this, text, onClick).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(UiKit.dp(this@MainActivity, 4), UiKit.dp(this@MainActivity, 5), UiKit.dp(this@MainActivity, 4), UiKit.dp(this@MainActivity, 5))
        }
    }

    private fun currentState(): State = when {
        !hasReceiveSmsPermission() || !hasSendSmsPermission() -> State(getString(R.string.permission_required), getColor(R.color.warning))
        SettingsStore.targetPhone(this).isBlank() -> State(getString(R.string.setup_incomplete), getColor(R.color.warning))
        SettingsStore.isEnabled(this) -> State(getString(R.string.running), getColor(R.color.success))
        else -> State(getString(R.string.paused), getColor(R.color.muted))
    }

    private fun decisionLabel(decision: String): String = when {
        isForwardSuccess(decision) -> "已转发"
        decision.contains("failed", ignoreCase = true) -> "失败"
        decision.startsWith("skipped") -> "已跳过"
        else -> decision
    }

    private fun isForwardSuccess(decision: String): Boolean =
        decision == "forwarded" || decision == "forwarded_rule_match" || decision == "forwarded_all_mode"

    private fun modeLabel(mode: ForwardMode): String =
        if (mode == ForwardMode.ALL) getString(R.string.forward_all_sms) else getString(R.string.forward_matched_sms_only)

    private fun maybeShowFirstRunGuide() {
        if (SettingsStore.isSetupGuideShown(this)) return
        SettingsStore.setSetupGuideShown(this, true)
        AlertDialog.Builder(this)
            .setTitle("开始使用")
            .setMessage("1. 允许接收短信\n用于识别本机收到的普通短信。\n\n2. 允许发送短信\n用于把短信转发到目标号码。\n\n3. 设置电池为不受限制\n避免锁屏或后台时停止转发。")
            .setPositiveButton("开始设置") { _, _ -> requestNeededPermissions() }
            .show()
    }

    private fun requestSendSmsPermission() {
        if (hasSendSmsPermission()) {
            render()
            return
        }
        val permanentlyDenied = SettingsStore.wasSendSmsRequested(this) && !shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)
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
        if (missing.contains(Manifest.permission.SEND_SMS)) SettingsStore.setSendSmsRequested(this, true)
        requestPermissions(missing.toTypedArray(), REQUEST_CORE_PERMISSIONS)
    }

    private fun requestNeededPermissions() {
        val missing = neededPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.contains(Manifest.permission.SEND_SMS)) SettingsStore.setSendSmsRequested(this, true)
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_ALL_PERMISSIONS)
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
        val sendIndex = permissions.indexOf(Manifest.permission.SEND_SMS)
        if (sendIndex >= 0 && !hasSendSmsPermission() && SettingsStore.wasSendSmsRequested(this) && !shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
            showSendSmsSettingsDialog()
        }
    }

    private fun showSendSmsSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要开启发送短信权限")
            .setMessage("SMS Quick Forwarder 需要发送短信权限，才能把收到的短信转发到目标号码。\n\n请按以下步骤开启：\n\n1. 打开应用信息\n2. 点击‘权限’\n3. 点击‘短信’\n4. 选择‘允许’\n\n三星手机通常路径为：\n设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许")
            .setPositiveButton("打开应用设置") { _, _ -> openAppSettings() }
            .setNegativeButton("暂不设置", null)
            .show()
    }

    private fun showPermissionHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("短信权限设置")
            .setMessage("三星手机：\n设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许\n\n其他安卓手机：\n设置 → 应用管理 → SMS Quick Forwarder → 权限管理 → 短信 → 允许\n\n如果页面中只显示‘短信’，进入后选择‘允许’即可。\n\n完成后返回 App，状态会自动刷新。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "无法自动打开设置，请手动进入：设置 → 应用 → SMS Quick Forwarder → 权限 → 短信 → 允许", Toast.LENGTH_LONG).show()
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleStartupUpdateCheck() {
        if (!UpdatePreferences.shouldCheckNow(this)) return
        Thread {
            runCatching {
                Thread.sleep(2_000)
                val result = UpdateChecker.create(this).check()
                UpdatePreferences.recordResult(this, result)
                if (result is UpdateCheckResult.UpdateAvailable) UpdateNotifier.notifyIfNeeded(this, result.info)
                runOnUiThread {
                    updateResult = result
                    if (::contentRoot.isInitialized) render()
                }
            }
        }.start()
    }

    private fun manualCheckUpdates() {
        if (updateChecking) return
        updateChecking = true
        render()
        Thread {
            val result = UpdateChecker.create(this).check()
            UpdatePreferences.recordResult(this, result)
            if (result is UpdateCheckResult.UpdateAvailable) UpdateNotifier.notifyIfNeeded(this, result.info)
            runOnUiThread {
                updateChecking = false
                updateResult = result
                Toast.makeText(this, updateStateLabel(result), Toast.LENGTH_SHORT).show()
                render()
            }
        }.start()
    }

    private fun showUpdateSettingsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@MainActivity, 8), UiKit.dp(this@MainActivity, 6), UiKit.dp(this@MainActivity, 8), 0)
        }
        val autoSwitch = Switch(this).apply {
            text = "自动检查更新"
            isChecked = UpdatePreferences.autoCheckEnabled(this@MainActivity)
        }
        val notificationSwitch = Switch(this).apply {
            text = "后台通知"
            isChecked = UpdatePreferences.notificationsEnabled(this@MainActivity)
        }
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val daily = frequencyRadio("每天", UpdateFrequency.DAILY)
        val weekly = frequencyRadio("每周", UpdateFrequency.WEEKLY)
        val startup = frequencyRadio("仅启动时", UpdateFrequency.ON_APP_START_ONLY)
        group.addView(daily)
        group.addView(weekly)
        group.addView(startup)
        when (UpdatePreferences.frequency(this)) {
            UpdateFrequency.DAILY -> daily.isChecked = true
            UpdateFrequency.WEEKLY -> weekly.isChecked = true
            UpdateFrequency.ON_APP_START_ONLY -> startup.isChecked = true
        }
        container.addView(autoSwitch)
        container.addView(UiKit.subtitle(this, "检查 GitHub Releases，只读取公开版本信息。"))
        container.addView(notificationSwitch)
        if (!hasNotificationPermission()) container.addView(UiKit.subtitle(this, "通知权限未允许时，只显示 App 内提醒。"))
        container.addView(UiKit.body(this, "检查频率"))
        container.addView(group)
        val ignored = UpdatePreferences.ignoredVersion(this)
        container.addView(UiKit.subtitle(this, "已忽略版本：${ignored.ifBlank { "无" }}"))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_settings))
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                UpdatePreferences.setAutoCheckEnabled(this, autoSwitch.isChecked)
                UpdatePreferences.setNotificationsEnabled(this, notificationSwitch.isChecked)
                UpdatePreferences.setFrequency(this, selectedFrequency(group.checkedRadioButtonId))
                UpdateScheduler.configure(this)
                render()
            }
            .setNeutralButton("清除忽略版本") { _, _ ->
                UpdatePreferences.clearIgnoredVersion(this)
                render()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun frequencyRadio(label: String, frequency: UpdateFrequency): RadioButton =
        RadioButton(this).apply {
            text = label
            id = frequency.ordinal + 10_000
        }

    private fun selectedFrequency(checkedId: Int): UpdateFrequency =
        UpdateFrequency.values().firstOrNull { it.ordinal + 10_000 == checkedId } ?: UpdateFrequency.DAILY

    private fun frequencyLabel(frequency: UpdateFrequency): String = when (frequency) {
        UpdateFrequency.DAILY -> "每天"
        UpdateFrequency.WEEKLY -> "每周"
        UpdateFrequency.ON_APP_START_ONLY -> "仅启动时"
    }

    private fun updateStateLabel(result: UpdateCheckResult? = updateResult): String = when (result) {
        is UpdateCheckResult.UpdateAvailable -> "发现新版本 ${result.info.latestVersionName}"
        is UpdateCheckResult.UpToDate -> getString(R.string.up_to_date)
        is UpdateCheckResult.Error -> "检查失败：${result.message}"
        null -> if (UpdatePreferences.lastKnownLatestVersion(this).isBlank()) "尚未检查" else "最近发现 ${UpdatePreferences.lastKnownLatestVersion(this)}"
    }

    private fun releaseNotesPreview(notes: String): String {
        val lines = notes.lines().map { it.trim().trimStart('-', '*').trim() }.filter { it.isNotBlank() }
        return lines.take(4).joinToString("\n") { "- $it" }.ifBlank { "- 查看 GitHub Release 获取详情" }
    }

    private fun formatCheckTime(timestamp: Long): String =
        if (timestamp <= 0L) "尚未检查" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(timestamp))

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

    private fun isBatteryUnrestricted(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private enum class Tab { HOME, RULES, LOGS, ABOUT }
    private data class State(val label: String, val color: Int)

    companion object {
        private const val REQUEST_SEND_SMS = 301
        private const val REQUEST_CORE_PERMISSIONS = 302
        private const val REQUEST_ALL_PERMISSIONS = 303
    }
}
