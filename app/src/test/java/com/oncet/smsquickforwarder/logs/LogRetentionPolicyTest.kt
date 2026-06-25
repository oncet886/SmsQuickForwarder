package com.oncet.smsquickforwarder.logs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRetentionPolicyTest {
    @Test
    fun retentionKeepsOnlyWithinRange() {
        val now = 100L * 24L * 60L * 60L * 1000L
        assertTrue(LogRetentionPolicy.shouldKeep(now - 6L * 24L * 60L * 60L * 1000L, now, LogRetentionDays.DAYS_7))
        assertFalse(LogRetentionPolicy.shouldKeep(now - 8L * 24L * 60L * 60L * 1000L, now, LogRetentionDays.DAYS_7))
        assertTrue(LogRetentionPolicy.shouldKeep(0L, now, LogRetentionDays.FOREVER))
    }
}
