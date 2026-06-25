package com.oncet.smsquickforwarder

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.ui.UiKit
import com.oncet.smsquickforwarder.util.PhoneMaskUtils

class LogDetailActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val obj = ForwardLogStore.eventById(this, eventId)
        val root = UiKit.page(this)
        root.addView(UiKit.title(this, "记录详情"))
        if (obj == null) {
            root.addView(UiKit.card(this).apply { addView(UiKit.body(this@LogDetailActivity, "记录不存在")) })
        } else {
            root.addView(UiKit.card(this).apply {
                addView(UiKit.body(this@LogDetailActivity, "时间：${obj.optString("receivedAt")}"))
                addView(UiKit.body(this@LogDetailActivity, "发送号码：${PhoneMaskUtils.mask(obj.optString("sender"))}"))
                addView(UiKit.body(this@LogDetailActivity, "目标号码：${PhoneMaskUtils.mask(obj.optString("targetPhone"))}"))
                addView(UiKit.body(this@LogDetailActivity, "结果：${obj.optString("decision")}"))
                addView(UiKit.subtitle(this@LogDetailActivity, "内容摘要：${obj.optString("bodyPreview")}"))
            })
            root.addView(UiKit.card(this).apply {
                addView(UiKit.body(this@LogDetailActivity, "规则判断"))
                addView(UiKit.divider(this@LogDetailActivity))
                addView(UiKit.body(this@LogDetailActivity, "模式：${obj.optString("forwardMode")}"))
                addView(UiKit.body(this@LogDetailActivity, "命中：${obj.optJSONArray("matchedRuleNames")?.join(", ").orEmpty().ifBlank { "无" }}"))
                addView(UiKit.body(this@LogDetailActivity, "结果：${obj.optString("finalRuleReason")}"))
                addView(UiKit.body(this@LogDetailActivity, "耗时：${obj.optLong("ruleEvaluationDurationMs")}ms"))
            })
            root.addView(UiKit.card(this).apply {
                addView(UiKit.body(this@LogDetailActivity, "发送结果：${obj.optString("sendResult")}"))
                addView(UiKit.body(this@LogDetailActivity, "失败原因：${obj.optString("errorMessage").ifBlank { obj.optString("skipReason").ifBlank { "无" } }}"))
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}
