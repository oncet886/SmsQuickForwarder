package com.oncet.smsquickforwarder

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.util.PhoneMaskUtils

class LogDetailActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val obj = ForwardLogStore.eventById(this, eventId)
        val text = if (obj == null) {
            "记录不存在"
        } else {
            buildString {
                append("短信记录详情\n\n")
                append("时间：").append(obj.optString("receivedAt")).append("\n")
                append("发送号码：").append(PhoneMaskUtils.mask(obj.optString("sender"))).append("\n")
                append("目标号码：").append(PhoneMaskUtils.mask(obj.optString("targetPhone"))).append("\n")
                append("结果：").append(obj.optString("decision")).append("\n")
                append("内容摘要：").append(obj.optString("bodyPreview")).append("\n\n")
                append("规则判断\n")
                append("模式：").append(obj.optString("forwardMode")).append("\n")
                append("命中：").append(obj.optJSONArray("matchedRuleNames")?.join(", ").orEmpty()).append("\n")
                append("结果：").append(obj.optString("finalRuleReason")).append("\n")
                append("耗时：").append(obj.optLong("ruleEvaluationDurationMs")).append("ms\n\n")
                append("发送结果：").append(obj.optString("sendResult")).append("\n")
                append("失败原因：").append(obj.optString("errorMessage").ifBlank { obj.optString("skipReason") })
            }
        }
        setContentView(ScrollView(this).apply {
            addView(TextView(this@LogDetailActivity).apply {
                setPadding(32, 40, 32, 32)
                textSize = 14f
                this.text = text
            })
        })
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}
