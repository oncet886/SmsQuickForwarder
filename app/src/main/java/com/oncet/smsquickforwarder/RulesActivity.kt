package com.oncet.smsquickforwarder

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.oncet.smsquickforwarder.rules.ForwardMode
import com.oncet.smsquickforwarder.rules.RuleStore
import com.oncet.smsquickforwarder.rules.RuleType
import com.oncet.smsquickforwarder.rules.SmsRule

class RulesActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()
        val rules = RuleStore.rules(this)
        val mode = RuleStore.forwardMode(this)
        root.addView(TextView(this).apply {
            text = getString(R.string.rule_settings)
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "当前模式：${modeLabel(mode)}\n已启用：${rules.count { it.enabled }}  包含：${rules.count { it.type == RuleType.INCLUDE }}  排除：${rules.count { it.type == RuleType.EXCLUDE }}"
            setPadding(0, 12, 0, 12)
        })
        root.addView(Button(this).apply {
            text = if (mode == ForwardMode.ALL) "切换为：仅转发符合规则的短信" else "切换为：转发全部短信"
            setOnClickListener {
                val next = if (mode == ForwardMode.ALL) ForwardMode.MATCH_ONLY else ForwardMode.ALL
                RuleStore.setForwardMode(this@RulesActivity, next)
                if (next == ForwardMode.MATCH_ONLY && rules.none { it.enabled && it.type == RuleType.INCLUDE }) {
                    Toast.makeText(this@RulesActivity, "当前没有包含规则，所有短信都会被跳过。", Toast.LENGTH_LONG).show()
                }
                render()
            }
        })
        root.addView(Button(this).apply {
            text = "新建包含规则"
            setOnClickListener { openEditor(RuleType.INCLUDE) }
        })
        root.addView(Button(this).apply {
            text = "新建排除规则"
            setOnClickListener { openEditor(RuleType.EXCLUDE) }
        })
        root.addView(TextView(this).apply {
            text = "快捷规则"
            textSize = 16f
            setPadding(0, 18, 0, 6)
        })
        addTemplateButton("验证码", "verification", RuleType.INCLUDE)
        addTemplateButton("银行", "bank", RuleType.INCLUDE)
        addTemplateButton("快递", "delivery", RuleType.INCLUDE)
        addTemplateButton("账单", "bill", RuleType.INCLUDE)
        addTemplateButton("登录提醒", "login", RuleType.INCLUDE)
        addTemplateButton("广告退订", "unsubscribe", RuleType.EXCLUDE)
        addTemplateButton("指定号码", "sender", RuleType.EXCLUDE)
        root.addView(Button(this).apply {
            text = "复制规则 JSON"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("rules", RuleStore.exportRules(this@RulesActivity, includePatterns = true)))
                Toast.makeText(this@RulesActivity, "规则 JSON 已复制", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "粘贴导入规则 JSON"
            setOnClickListener { showImportDialog() }
        })
        root.addView(TextView(this).apply {
            text = "规则列表"
            textSize = 16f
            setPadding(0, 18, 0, 6)
        })
        if (rules.isEmpty()) {
            root.addView(TextView(this).apply { text = "暂无规则。默认仍会转发全部普通短信。" })
        } else {
            rules.forEach { addRuleRow(it) }
        }
    }

    private fun addRuleRow(rule: SmsRule) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }
        box.addView(TextView(this).apply {
            text = "${rule.priority}. ${rule.name}  ${if (rule.type == RuleType.INCLUDE) "包含" else "排除"}\n${fieldLabel(rule)} / ${matchLabel(rule)} / ${rule.pattern}\n状态：${if (rule.enabled) "启用" else "停用"}"
        })
        box.addView(Switch(this).apply {
            text = "启用"
            isChecked = rule.enabled
            setOnCheckedChangeListener { _, checked -> RuleStore.setEnabled(this@RulesActivity, rule.id, checked); render() }
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "编辑"; setOnClickListener { openEditor(rule.type, rule.id) } })
        actions.addView(Button(this).apply { text = "上移"; setOnClickListener { RuleStore.move(this@RulesActivity, rule.id, -1); render() } })
        actions.addView(Button(this).apply { text = "下移"; setOnClickListener { RuleStore.move(this@RulesActivity, rule.id, 1); render() } })
        actions.addView(Button(this).apply { text = "删除"; setOnClickListener { confirmDelete(rule) } })
        box.addView(actions)
        root.addView(box)
    }

    private fun showImportDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "粘贴规则 JSON"
            minLines = 5
        }
        AlertDialog.Builder(this)
            .setTitle("导入规则")
            .setView(input)
            .setPositiveButton("合并导入") { _, _ ->
                val result = RuleStore.importRules(this, input.text.toString(), replace = false)
                Toast.makeText(this, "新增 ${result.added}，重复 ${result.duplicate}，无效 ${result.invalid}", Toast.LENGTH_LONG).show()
                render()
            }
            .setNeutralButton("全部替换") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("确认全部替换")
                    .setMessage("这会删除现有规则并导入粘贴内容。")
                    .setPositiveButton("替换") { _, _ ->
                        val result = RuleStore.importRules(this, input.text.toString(), replace = true)
                        Toast.makeText(this, "新增 ${result.added}，无效 ${result.invalid}", Toast.LENGTH_LONG).show()
                        render()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(rule: SmsRule) {
        AlertDialog.Builder(this)
            .setTitle("删除规则")
            .setMessage("确定删除“${rule.name}”？")
            .setPositiveButton("删除") { _, _ -> RuleStore.deleteRule(this, rule.id); render() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addTemplateButton(label: String, template: String, type: RuleType) {
        root.addView(Button(this).apply {
            text = label
            setOnClickListener { openEditor(type, template = template) }
        })
    }

    private fun openEditor(type: RuleType, id: String? = null, template: String? = null) {
        startActivity(Intent(this, RuleEditActivity::class.java).apply {
            putExtra(RuleEditActivity.EXTRA_TYPE, type.name)
            if (id != null) putExtra(RuleEditActivity.EXTRA_RULE_ID, id)
            if (template != null) putExtra(RuleEditActivity.EXTRA_TEMPLATE, template)
        })
    }

    private fun modeLabel(mode: ForwardMode) = if (mode == ForwardMode.ALL) getString(R.string.forward_all_sms) else getString(R.string.forward_matched_sms_only)
    private fun fieldLabel(rule: SmsRule) = when (rule.field.name) { "SENDER" -> "号码"; "BODY" -> "正文"; else -> "任意" }
    private fun matchLabel(rule: SmsRule) = when (rule.matchMode.name) { "EQUALS" -> "完全相同"; "STARTS_WITH" -> "以此开头"; "REGEX" -> "正则表达式"; else -> "包含" }
}
