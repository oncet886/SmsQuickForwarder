package com.oncet.smsquickforwarder.failure

object FailureNotificationPolicy {
    private val nonFailureDecisions = setOf(
        "skipped_disabled",
        "skipped_empty_target",
        "skipped_from_target",
        "skipped_forward_marker",
        "skipped_rule_exclude",
        "skipped_no_include_match"
    )

    fun shouldNotify(
        eventId: String,
        eventType: String,
        decision: String,
        alreadyNotified: Set<String>,
        enabled: Boolean
    ): Boolean {
        if (!enabled || eventId.isBlank() || alreadyNotified.contains(eventId)) return false
        if (eventType == "test_send") return false
        if (decision in nonFailureDecisions || decision.startsWith("skipped")) return false
        return decision.contains("failed", ignoreCase = true) || decision == "failed_no_permission"
    }

    fun shouldMergeRecentFailures(recentFailureCount: Int): Boolean = recentFailureCount > 3

    fun hasActiveFailure(lastFailureAt: String, lastSuccessAt: String): Boolean =
        lastFailureAt.isNotBlank() && (lastSuccessAt.isBlank() || lastFailureAt > lastSuccessAt)
}
