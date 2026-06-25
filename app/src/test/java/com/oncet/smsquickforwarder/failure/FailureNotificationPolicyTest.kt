package com.oncet.smsquickforwarder.failure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureNotificationPolicyTest {
    @Test
    fun realFailuresNotifyButSkipsDoNot() {
        assertTrue(FailureNotificationPolicy.shouldNotify("1", "sms_received", "failed", emptySet(), true))
        assertTrue(FailureNotificationPolicy.shouldNotify("1", "sms_received", "failed_no_permission", emptySet(), true))
        assertFalse(FailureNotificationPolicy.shouldNotify("1", "sms_received", "skipped_rule_exclude", emptySet(), true))
        assertFalse(FailureNotificationPolicy.shouldNotify("1", "test_send", "failed", emptySet(), true))
    }

    @Test
    fun duplicateAndDisabledNotificationsAreSuppressed() {
        assertFalse(FailureNotificationPolicy.shouldNotify("1", "sms_received", "failed", setOf("1"), true))
        assertFalse(FailureNotificationPolicy.shouldNotify("1", "sms_received", "failed", emptySet(), false))
        assertTrue(FailureNotificationPolicy.shouldMergeRecentFailures(4))
    }

    @Test
    fun successAfterFailureClearsActiveFailure() {
        assertTrue(FailureNotificationPolicy.hasActiveFailure("2026-06-25 09:32:00", ""))
        assertFalse(FailureNotificationPolicy.hasActiveFailure("2026-06-25 09:32:00", "2026-06-25 09:40:00"))
    }
}
