package com.oncet.smsquickforwarder

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.ui.UiKit
import com.oncet.smsquickforwarder.util.MessagePrivacyUtils
import com.oncet.smsquickforwarder.util.PhoneMaskUtils
import org.json.JSONObject

class LogsActivity : Activity() {
    private lateinit var root: LinearLayout
    private var filter = "all"
    private var dateFilter = "all"
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = UiKit.page(this)
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(UiKit.title(this, getString(R.string.logs)))
        root.addView(UiKit.subtitle(this, "短信处理历史，默认隐藏内部调试事件"))
        root.addView(statsCard())
        root.addView(searchCard())
        val filters = UiKit.row(this)
        listOf("all" to "全部", "forwarded" to "已转发", "skipped" to "已跳过", "failed" to "失败").forEach { item ->
            filters.addView(UiKit.navButton(this, item.second, filter == item.first) {
                filter = item.first
                render()
            })
        }
        root.addView(filters)
        val dates = UiKit.row(this)
        listOf("all" to "全部", "today" to "今天", "7d" to "7 天", "30d" to "30 天").forEach { item ->
            dates.addView(UiKit.navButton(this, item.second, dateFilter == item.first) {
                dateFilter = item.first
                render()
            })
        }
        root.addView(dates)

        val events = ForwardLogStore.searchUserEvents(this, query, filter, dateFilter, 200)
        var count = 0
        for (i in 0 until events.length()) {
            val obj = events.getJSONObject(i)
            addEvent(obj)
            count += 1
        }
        if (count == 0) root.addView(UiKit.card(this).apply { addView(UiKit.body(this@LogsActivity, "暂无符合条件的记录")) })
        root.addView(clearCard())
    }

    private fun statsCard(): LinearLayout = UiKit.card(this).apply {
        addView(UiKit.body(this@LogsActivity, "日志统计"))
        addView(UiKit.subtitle(this@LogsActivity, "总数 ${ForwardLogStore.totalCount(this@LogsActivity)} · 今日转发 ${ForwardLogStore.countToday(this@LogsActivity) { isForwardSuccess(it.optString("decision")) }} · 今日跳过 ${ForwardLogStore.countToday(this@LogsActivity) { it.optString("decision").startsWith("skipped") }} · 今日失败 ${ForwardLogStore.countToday(this@LogsActivity) { it.optString("decision").contains("failed", true) }}\n估算占用 ${ForwardLogStore.estimatedBytes(this@LogsActivity)} bytes"))
    }

    private fun searchCard(): LinearLayout = UiKit.card(this).apply {
        addView(UiKit.body(this@LogsActivity, "搜索"))
        val input = EditText(this@LogsActivity).apply {
            hint = "号码、内容摘要、规则或失败原因"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        addView(input)
        addView(UiKit.secondaryButton(this@LogsActivity, "执行搜索") { render() })
    }

    private fun addEvent(obj: JSONObject) {
        val card = UiKit.card(this)
        card.addView(UiKit.body(this, "${obj.optString("receivedAt")}  ${decisionLabel(obj.optString("decision"))}"))
        card.addView(UiKit.subtitle(this, "${PhoneMaskUtils.mask(obj.optString("sender"))}  ${MessagePrivacyUtils.maskVerificationCodes(obj.optString("bodyPreview"))}"))
        val rule = obj.optString("primaryMatchedRuleName").ifBlank { "无" }
        card.addView(UiKit.subtitle(this, "匹配规则：$rule"))
        card.setOnClickListener {
            startActivity(Intent(this, LogDetailActivity::class.java).apply {
                putExtra(LogDetailActivity.EXTRA_EVENT_ID, obj.optString("eventId"))
            })
        }
        root.addView(card)
    }

    private fun clearCard(): LinearLayout = UiKit.card(this).apply {
        addView(UiKit.body(this@LogsActivity, "日志管理"))
        val row1 = UiKit.row(this@LogsActivity)
        row1.addView(actionButton("清空全部") {
            confirm("清空全部日志？不会删除规则和设置。") {
                ForwardLogStore.clear(this@LogsActivity)
                Toast.makeText(this@LogsActivity, "日志已清空", Toast.LENGTH_SHORT).show()
                render()
            }
        })
        row1.addView(actionButton("清空成功记录") {
            confirm("仅清空已成功转发记录？失败和跳过记录会保留。") {
                ForwardLogStore.clearSuccessful(this@LogsActivity)
                render()
            }
        })
        val row2 = UiKit.row(this@LogsActivity)
        row2.addView(actionButton("清空 30 天前") {
            confirm("清空 30 天前的日志？不会删除规则和设置。") {
                ForwardLogStore.clearOlderThanDays(this@LogsActivity, 30)
                render()
            }
        })
        addView(row1)
        addView(row2)
    }

    private fun actionButton(text: String, onClick: () -> Unit) = UiKit.secondaryButton(this, text, onClick).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(UiKit.dp(this@LogsActivity, 4), UiKit.dp(this@LogsActivity, 4), UiKit.dp(this@LogsActivity, 4), UiKit.dp(this@LogsActivity, 4))
        }
    }

    private fun confirm(message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("确认操作")
            .setMessage(message)
            .setPositiveButton("确认") { _, _ -> action() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun decisionLabel(decision: String): String = when {
        isForwardSuccess(decision) -> "已转发"
        decision.contains("failed", ignoreCase = true) -> "失败"
        decision.startsWith("skipped") -> "已跳过"
        else -> decision
    }

    private fun isForwardSuccess(decision: String): Boolean =
        decision == "forwarded" || decision == "forwarded_rule_match" || decision == "forwarded_all_mode"
}
