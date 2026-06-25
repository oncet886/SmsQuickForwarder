package com.oncet.smsquickforwarder.rules

data class RuleEvaluation(
    val shouldForward: Boolean,
    val decision: String,
    val reason: String,
    val mode: ForwardMode,
    val normalizedSender: String,
    val includeMatches: List<SmsRule>,
    val excludeMatches: List<SmsRule>,
    val primaryRule: SmsRule?,
    val loopGuardTriggered: Boolean = false
) {
    val matchedRuleNames: List<String> = (includeMatches + excludeMatches)
        .distinctBy { it.id }
        .sortedBy { it.priority }
        .map { it.name }
}
