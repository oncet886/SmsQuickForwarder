package com.oncet.smsquickforwarder

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.oncet.smsquickforwarder.ui.SystemBarsInsets
import com.oncet.smsquickforwarder.ui.UiKit
import org.json.JSONArray

class ChangelogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.page(this)
        root.addView(UiKit.title(this, getString(R.string.changelog)))
        root.addView(UiKit.subtitle(this, "离线内置更新记录"))

        val changes = runCatching {
            JSONArray(assets.open("changelog.json").bufferedReader().use { it.readText() })
        }.getOrDefault(JSONArray())

        for (i in 0 until changes.length()) {
            val item = changes.getJSONObject(i)
            val card = UiKit.card(this)
            card.addView(UiKit.body(this, "${item.optString("version")}  ${item.optString("date")}"))
            val arr = item.optJSONArray("changes") ?: JSONArray()
            val text = buildString {
                for (j in 0 until arr.length()) append("- ").append(arr.optString(j)).append("\n")
            }.trim()
            card.addView(UiKit.subtitle(this, text))
            root.addView(card)
        }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        with(SystemBarsInsets) { applyStandardSystemBars(scroll, handleIme = false) }
    }
}
