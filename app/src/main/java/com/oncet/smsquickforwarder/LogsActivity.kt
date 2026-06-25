package com.oncet.smsquickforwarder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.ui.UiKit
import com.oncet.smsquickforwarder.util.MessagePrivacyUtils
import com.oncet.smsquickforwarder.util.PhoneMaskUtils
import org.json.JSONObject

class LogsActivity : Activity() {
    private lateinit var root: LinearLayout
    private var filter = "all"

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
        val filters = UiKit.row(this)
        listOf("all" to "全部", "forwarded" to "已转发", "skipped" to "已跳过", "failed" to "失败").forEach { item ->
            filters.addView(UiKit.navButton(this, item.second, filter == item.first) {
                filter = item.first
                render()
            })
        }
        root.addView(filters)

        val events = ForwardLogStore.recentUserEvents(this, 80)
        var count = 0
        for (i in 0 until events.length()) {
            val obj = events.getJSONObject(i)
            if (!matchesFilter(obj)) continue
            addEvent(obj)
            count += 1
        }
        if (count == 0) root.addView(UiKit.card(this).apply { addView(UiKit.body(this@LogsActivity, "暂无符合条件的记录")) })
    }

    private fun matchesFilter(obj: JSONObject): Boolean {
        val decision = obj.optString("decision")
        return when (filter) {
            "forwarded" -> isForwardSuccess(decision)
            "skipped" -> decision.startsWith("skipped")
            "failed" -> decision.contains("failed", ignoreCase = true)
            else -> true
        }
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

    private fun decisionLabel(decision: String): String = when {
        isForwardSuccess(decision) -> "已转发"
        decision.contains("failed", ignoreCase = true) -> "失败"
        decision.startsWith("skipped") -> "已跳过"
        else -> decision
    }

    private fun isForwardSuccess(decision: String): Boolean =
        decision == "forwarded" || decision == "forwarded_rule_match" || decision == "forwarded_all_mode"
}
