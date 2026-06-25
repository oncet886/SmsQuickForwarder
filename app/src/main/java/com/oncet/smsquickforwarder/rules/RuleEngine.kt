package com.oncet.smsquickforwarder.rules

object RuleEngine {
    const val FORWARD_MARKER = "[SMS Forward]"

    fun evaluate(
        sender: String,
        body: String,
        targetPhone: String,
        mode: ForwardMode,
        rules: List<SmsRule>,
        applyLoopGuard: Boolean = true
    ): RuleEvaluation {
        val normalizedSender = PhoneMatchUtils.normalizePhoneForMatch(sender)
        if (applyLoopGuard && targetPhone.isNotBlank() && PhoneMatchUtils.phoneEquals(sender, targetPhone)) {
            return RuleEvaluation(false, "skipped_from_target", "来自目标号码，避免循环", mode, normalizedSender, emptyList(), emptyList(), null, true)
        }
        if (applyLoopGuard && body.contains(FORWARD_MARKER, ignoreCase = true)) {
            return RuleEvaluation(false, "skipped_forward_marker", "内容包含 $FORWARD_MARKER，避免循环", mode, normalizedSender, emptyList(), emptyList(), null, true)
        }

        val enabledRules = rules.filter { it.enabled }.sortedBy { it.priority }
        val includeMatches = enabledRules.filter { it.type == RuleType.INCLUDE && matches(it, sender, body) }
        val excludeMatches = enabledRules.filter { it.type == RuleType.EXCLUDE && matches(it, sender, body) }

        if (excludeMatches.isNotEmpty()) {
            val primary = excludeMatches.minByOrNull { it.priority }
            return RuleEvaluation(false, "skipped_rule_exclude", "命中排除规则：${primary?.name.orEmpty()}", mode, normalizedSender, includeMatches, excludeMatches, primary)
        }

        return when (mode) {
            ForwardMode.ALL -> {
                val primary = includeMatches.minByOrNull { it.priority }
                RuleEvaluation(true, if (primary != null) "forwarded_rule_match" else "forwarded_all_mode", if (primary != null) "命中包含规则：${primary.name}" else "转发全部短信模式", mode, normalizedSender, includeMatches, excludeMatches, primary)
            }
            ForwardMode.MATCH_ONLY -> {
                val primary = includeMatches.minByOrNull { it.priority }
                if (primary != null) {
                    RuleEvaluation(true, "forwarded_rule_match", "命中包含规则：${primary.name}", mode, normalizedSender, includeMatches, excludeMatches, primary)
                } else {
                    RuleEvaluation(false, "skipped_no_include_match", "没有命中包含规则", mode, normalizedSender, includeMatches, excludeMatches, null)
                }
            }
        }
    }

    fun isRegexValid(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

    private fun matches(rule: SmsRule, sender: String, body: String): Boolean {
        val senderMatched = rule.field == RuleField.SENDER || rule.field == RuleField.ANY
        val bodyMatched = rule.field == RuleField.BODY || rule.field == RuleField.ANY
        return (senderMatched && matchValue(rule, sender, isPhone = true)) ||
            (bodyMatched && matchValue(rule, body, isPhone = false))
    }

    private fun matchValue(rule: SmsRule, value: String, isPhone: Boolean): Boolean {
        if (isPhone) {
            val normalizedValue = PhoneMatchUtils.normalizePhoneForMatch(value)
            val normalizedPattern = PhoneMatchUtils.normalizePhoneForMatch(rule.pattern)
            return when (rule.matchMode) {
                RuleMatchMode.CONTAINS -> normalizedValue.contains(normalizedPattern)
                RuleMatchMode.EQUALS -> PhoneMatchUtils.phoneEquals(normalizedValue, normalizedPattern)
                RuleMatchMode.STARTS_WITH -> normalizedValue.startsWith(normalizedPattern)
                RuleMatchMode.REGEX -> runCatching { Regex(rule.pattern).containsMatchIn(normalizedValue) }.getOrDefault(false)
            }
        }
        val option = if (rule.caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
        val left = if (rule.caseSensitive) value else value.lowercase()
        val right = if (rule.caseSensitive) rule.pattern else rule.pattern.lowercase()
        return when (rule.matchMode) {
            RuleMatchMode.CONTAINS -> left.contains(right)
            RuleMatchMode.EQUALS -> left == right
            RuleMatchMode.STARTS_WITH -> left.startsWith(right)
            RuleMatchMode.REGEX -> runCatching { Regex(rule.pattern, option).containsMatchIn(value) }.getOrDefault(false)
        }
    }
}
