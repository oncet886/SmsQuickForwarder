package com.oncet.smsquickforwarder.rules

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RuleStore {
    private const val PREF = "rules"
    private const val KEY_MODE = "forward_mode"
    private const val KEY_ITEMS = "items"

    fun forwardMode(context: Context): ForwardMode {
        val raw = prefs(context).getString(KEY_MODE, ForwardMode.ALL.name) ?: ForwardMode.ALL.name
        return runCatching { ForwardMode.valueOf(raw) }.getOrDefault(ForwardMode.ALL)
    }

    fun setForwardMode(context: Context, mode: ForwardMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun rules(context: Context): List<SmsRule> {
        val raw = prefs(context).getString(KEY_ITEMS, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = mutableListOf<SmsRule>()
        for (i in 0 until arr.length()) SmsRule.fromJson(arr.optJSONObject(i) ?: JSONObject())?.let(out::add)
        return out.sortedWith(compareBy<SmsRule> { it.priority }.thenBy { it.createdAt })
    }

    fun saveRule(context: Context, rule: SmsRule) {
        val list = rules(context).toMutableList()
        val index = list.indexOfFirst { it.id == rule.id }
        if (index >= 0) list[index] = rule else list.add(rule)
        saveRules(context, normalizePriorities(list.sortedBy { it.priority }))
    }

    fun deleteRule(context: Context, id: String) {
        saveRules(context, normalizePriorities(rules(context).filterNot { it.id == id }))
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val now = System.currentTimeMillis()
        saveRules(context, rules(context).map { if (it.id == id) it.copy(enabled = enabled, updatedAt = now) else it })
    }

    fun move(context: Context, id: String, direction: Int) {
        val list = rules(context).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        saveRules(context, normalizePriorities(list))
    }

    fun duplicateExists(context: Context, candidate: SmsRule): Boolean =
        rules(context).any { it.id != candidate.id && it.duplicateKey() == candidate.duplicateKey() }

    fun exportRules(context: Context, includePatterns: Boolean = true): String {
        val arr = JSONArray()
        rules(context).forEach { rule ->
            val obj = rule.toJson()
            if (!includePatterns) obj.put("pattern", maskPattern(rule.pattern))
            arr.put(obj)
        }
        return JSONObject().put("forwardMode", forwardMode(context).name).put("rules", arr).toString(2)
    }

    fun importRules(context: Context, raw: String, replace: Boolean): ImportResult {
        val parsed = runCatching {
            val trimmed = raw.trim()
            val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("rules") ?: JSONArray()
            val list = mutableListOf<SmsRule>()
            var invalid = 0
            for (i in 0 until arr.length()) {
                val rule = SmsRule.fromJson(arr.optJSONObject(i) ?: JSONObject())
                if (rule != null && rule.name.isNotBlank() && rule.pattern.isNotBlank()) list.add(rule) else invalid += 1
            }
            list to invalid
        }.getOrElse { return ImportResult(0, 0, 1) }
        val existing = if (replace) emptyList() else rules(context)
        val keys = existing.map { it.duplicateKey() }.toMutableSet()
        val merged = existing.toMutableList()
        var added = 0
        var duplicate = 0
        parsed.first.forEach { rule ->
            if (keys.contains(rule.duplicateKey())) {
                duplicate += 1
            } else {
                keys.add(rule.duplicateKey())
                merged.add(rule.copy(id = System.currentTimeMillis().toString() + "-$added"))
                added += 1
            }
        }
        saveRules(context, normalizePriorities(merged))
        return ImportResult(added, duplicate, parsed.second)
    }

    fun summaryJson(context: Context, includeSensitivePatterns: Boolean): JSONObject {
        val list = rules(context)
        val arr = JSONArray()
        list.forEach { rule ->
            arr.put(JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("enabled", rule.enabled)
                put("type", rule.type.name)
                put("field", rule.field.name)
                put("matchMode", rule.matchMode.name)
                put("pattern", if (includeSensitivePatterns) rule.pattern else maskPattern(rule.pattern))
                put("caseSensitive", rule.caseSensitive)
                put("priority", rule.priority)
            })
        }
        return JSONObject().apply {
            put("forwardMode", forwardMode(context).name)
            put("totalRules", list.size)
            put("enabledRules", list.count { it.enabled })
            put("includeRules", list.count { it.type == RuleType.INCLUDE })
            put("excludeRules", list.count { it.type == RuleType.EXCLUDE })
            put("rules", arr)
        }
    }

    private fun saveRules(context: Context, list: List<SmsRule>) {
        val arr = JSONArray()
        list.sortedBy { it.priority }.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun normalizePriorities(list: List<SmsRule>): List<SmsRule> {
        val now = System.currentTimeMillis()
        return list.mapIndexed { index, rule -> rule.copy(priority = index + 1, updatedAt = now) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun maskPattern(value: String): String {
        if (value.isBlank()) return ""
        if (value.length <= 2) return "*".repeat(value.length)
        return value.take(1) + "*".repeat((value.length - 2).coerceAtLeast(1)) + value.takeLast(1)
    }
}

data class ImportResult(val added: Int, val duplicate: Int, val invalid: Int)
