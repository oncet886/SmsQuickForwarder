package com.oncet.smsquickforwarder

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import com.oncet.smsquickforwarder.rules.RuleEngine
import com.oncet.smsquickforwarder.rules.RuleField
import com.oncet.smsquickforwarder.rules.RuleMatchMode
import com.oncet.smsquickforwarder.rules.RuleStore
import com.oncet.smsquickforwarder.rules.RuleType
import com.oncet.smsquickforwarder.rules.SmsRule
import com.oncet.smsquickforwarder.ui.SystemBarsInsets

class RuleEditActivity : Activity() {
    private lateinit var nameInput: EditText
    private lateinit var patternInput: EditText
    private lateinit var priorityInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var fieldSpinner: Spinner
    private lateinit var modeSpinner: Spinner
    private lateinit var caseBox: CheckBox
    private lateinit var enabledBox: CheckBox
    private var editingId: String? = null
    private var createdAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = intent.getStringExtra(EXTRA_RULE_ID)
        createdAt = System.currentTimeMillis()
        buildUi()
        loadInitial()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }
        nameInput = edit("规则名称")
        patternInput = edit("关键词 / 表达式")
        priorityInput = edit("优先级，数字越小越优先")
        typeSpinner = spinner(RuleType.values().map { if (it == RuleType.INCLUDE) "包含" else "排除" })
        fieldSpinner = spinner(listOf("号码", "正文", "任意"))
        modeSpinner = spinner(listOf("包含", "完全相同", "以此开头", "正则表达式"))
        caseBox = CheckBox(this).apply { text = "区分大小写" }
        enabledBox = CheckBox(this).apply { text = "启用"; isChecked = true }

        root.addView(nameInput)
        root.addView(typeSpinner)
        root.addView(fieldSpinner)
        root.addView(modeSpinner)
        root.addView(patternInput)
        root.addView(caseBox)
        root.addView(priorityInput)
        root.addView(enabledBox)
        root.addView(Button(this).apply {
            text = "保存"
            setOnClickListener { save() }
        })
        root.addView(Button(this).apply {
            text = "取消"
            setOnClickListener { finish() }
        })
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        with(SystemBarsInsets) { applyStandardSystemBars(scroll, handleIme = true) }
    }

    private fun loadInitial() {
        val existing = editingId?.let { id -> RuleStore.rules(this).firstOrNull { it.id == id } }
        val rule = existing ?: templateRule()
        createdAt = rule.createdAt
        nameInput.setText(rule.name)
        patternInput.setText(rule.pattern)
        priorityInput.setText(rule.priority.toString())
        typeSpinner.setSelection(if (rule.type == RuleType.INCLUDE) 0 else 1)
        fieldSpinner.setSelection(when (rule.field) { RuleField.SENDER -> 0; RuleField.BODY -> 1; RuleField.ANY -> 2 })
        modeSpinner.setSelection(when (rule.matchMode) { RuleMatchMode.CONTAINS -> 0; RuleMatchMode.EQUALS -> 1; RuleMatchMode.STARTS_WITH -> 2; RuleMatchMode.REGEX -> 3 })
        caseBox.isChecked = rule.caseSensitive
        enabledBox.isChecked = rule.enabled
    }

    private fun templateRule(): SmsRule {
        val type = runCatching { RuleType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: RuleType.INCLUDE.name) }.getOrDefault(RuleType.INCLUDE)
        val template = intent.getStringExtra(EXTRA_TEMPLATE).orEmpty()
        val now = System.currentTimeMillis()
        val nextPriority = RuleStore.rules(this).size + 1
        val pair = when (template) {
            "verification" -> "验证码" to "verification code|security code|one-time code|OTP|验证码|动态码|校验码|登录代码"
            "bank" -> "银行" to "bank|银行"
            "delivery" -> "快递" to "delivery|快递|包裹"
            "bill" -> "账单" to "bill|账单"
            "login" -> "登录提醒" to "login|登录"
            "unsubscribe" -> "广告退订" to "STOP to unsubscribe"
            "sender" -> "指定号码" to ""
            else -> "" to ""
        }
        return SmsRule(
            id = editingId ?: "$now",
            name = pair.first,
            enabled = true,
            type = type,
            field = if (template == "sender") RuleField.SENDER else RuleField.BODY,
            matchMode = if (template == "verification") RuleMatchMode.REGEX else RuleMatchMode.CONTAINS,
            pattern = pair.second,
            caseSensitive = false,
            priority = nextPriority,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        val pattern = patternInput.text.toString().trim()
        if (name.isBlank()) {
            nameInput.error = "名称不能为空"
            return
        }
        if (pattern.isBlank()) {
            patternInput.error = "pattern 不能为空"
            return
        }
        val matchMode = when (modeSpinner.selectedItemPosition) {
            1 -> RuleMatchMode.EQUALS
            2 -> RuleMatchMode.STARTS_WITH
            3 -> RuleMatchMode.REGEX
            else -> RuleMatchMode.CONTAINS
        }
        if (matchMode == RuleMatchMode.REGEX && !RuleEngine.isRegexValid(pattern)) {
            patternInput.error = "正则表达式无效"
            return
        }
        val now = System.currentTimeMillis()
        val rule = SmsRule(
            id = editingId ?: "$now",
            name = name,
            enabled = enabledBox.isChecked,
            type = if (typeSpinner.selectedItemPosition == 0) RuleType.INCLUDE else RuleType.EXCLUDE,
            field = when (fieldSpinner.selectedItemPosition) { 0 -> RuleField.SENDER; 1 -> RuleField.BODY; else -> RuleField.ANY },
            matchMode = matchMode,
            pattern = pattern,
            caseSensitive = caseBox.isChecked,
            priority = priorityInput.text.toString().toIntOrNull() ?: 100,
            createdAt = createdAt,
            updatedAt = now
        )
        if (RuleStore.duplicateExists(this, rule)) {
            AlertDialog.Builder(this)
                .setTitle("发现重复规则")
                .setMessage("已有相同规则，仍要保存吗？")
                .setPositiveButton("保存") { _, _ -> persist(rule) }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        persist(rule)
    }

    private fun persist(rule: SmsRule) {
        RuleStore.saveRule(this, rule)
        Toast.makeText(this, getString(R.string.rule_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun edit(hint: String) = EditText(this).apply {
        this.hint = hint
        setPadding(0, 8, 0, 8)
    }

    private fun spinner(values: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@RuleEditActivity, android.R.layout.simple_spinner_dropdown_item, values)
        visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_TYPE = "type"
        const val EXTRA_TEMPLATE = "template"
    }
}
