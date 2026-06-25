package com.oncet.smsquickforwarder.rules

import org.json.JSONObject

enum class ForwardMode { ALL, MATCH_ONLY }
enum class RuleType { INCLUDE, EXCLUDE }
enum class RuleField { SENDER, BODY, ANY }
enum class RuleMatchMode { CONTAINS, EQUALS, STARTS_WITH, REGEX }

data class SmsRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val type: RuleType,
    val field: RuleField,
    val matchMode: RuleMatchMode,
    val pattern: String,
    val caseSensitive: Boolean,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("type", type.name)
        put("field", field.name)
        put("matchMode", matchMode.name)
        put("pattern", pattern)
        put("caseSensitive", caseSensitive)
        put("priority", priority)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    fun duplicateKey(): String =
        listOf(type.name, field.name, matchMode.name, pattern.trim(), caseSensitive.toString()).joinToString("|")

    companion object {
        fun fromJson(obj: JSONObject): SmsRule? = runCatching {
            SmsRule(
                id = obj.optString("id").ifBlank { System.currentTimeMillis().toString() },
                name = obj.optString("name"),
                enabled = obj.optBoolean("enabled", true),
                type = RuleType.valueOf(obj.optString("type", RuleType.INCLUDE.name)),
                field = RuleField.valueOf(obj.optString("field", RuleField.BODY.name)),
                matchMode = RuleMatchMode.valueOf(obj.optString("matchMode", RuleMatchMode.CONTAINS.name)),
                pattern = obj.optString("pattern"),
                caseSensitive = obj.optBoolean("caseSensitive", false),
                priority = obj.optInt("priority", 100),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }.getOrNull()
    }
}
