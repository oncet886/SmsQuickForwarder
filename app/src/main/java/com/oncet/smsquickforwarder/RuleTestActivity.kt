package com.oncet.smsquickforwarder

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.rules.RuleEngine
import com.oncet.smsquickforwarder.rules.RuleStore

class RuleTestActivity : Activity() {
    private lateinit var senderInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.test_rules)
            textSize = 22f
        })
        senderInput = EditText(this).apply { hint = "模拟发送号码" }
        bodyInput = EditText(this).apply { hint = "模拟短信正文"; minLines = 4 }
        resultText = TextView(this).apply { setPadding(0, 16, 0, 0) }
        root.addView(senderInput)
        root.addView(bodyInput)
        root.addView(Button(this).apply { text = "测试匹配"; setOnClickListener { test() } })
        root.addView(Button(this).apply {
            text = "填入验证码示例"
            setOnClickListener {
                senderInput.setText("+15550106666")
                bodyInput.setText("Your verification code is 123456")
            }
        })
        root.addView(Button(this).apply {
            text = "填入广告示例"
            setOnClickListener {
                senderInput.setText("12345")
                bodyInput.setText("SALE today. Reply STOP to unsubscribe")
            }
        })
        root.addView(Button(this).apply {
            text = "清空"
            setOnClickListener { senderInput.setText(""); bodyInput.setText(""); resultText.text = "" }
        })
        root.addView(resultText)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun test() {
        val mode = RuleStore.forwardMode(this)
        val result = RuleEngine.evaluate(
            sender = senderInput.text.toString(),
            body = bodyInput.text.toString(),
            targetPhone = SettingsStore.targetPhone(this),
            mode = mode,
            rules = RuleStore.rules(this),
            applyLoopGuard = true
        )
        resultText.text = buildString {
            append("最终结果：").append(if (result.shouldForward) getString(R.string.will_forward) else getString(R.string.will_skip)).append("\n")
            append("原因：").append(result.reason).append("\n")
            append("当前转发模式：").append(mode.name).append("\n")
            append("防循环是否触发：").append(if (result.loopGuardTriggered) "是" else "否").append("\n")
            append("命中的包含规则：").append(result.includeMatches.joinToString { it.name }.ifBlank { "无" }).append("\n")
            append("命中的排除规则：").append(result.excludeMatches.joinToString { it.name }.ifBlank { "无" })
        }
    }
}
