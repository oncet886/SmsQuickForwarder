package com.oncet.smsquickforwarder.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun notificationIsDeduplicated() {
        assertTrue(UpdatePreferences.shouldNotifyVersion("0.1.8", "", "", autoEnabled = true, notificationsEnabled = true))
        assertFalse(UpdatePreferences.shouldNotifyVersion("0.1.8", "", "0.1.8", autoEnabled = true, notificationsEnabled = true))
        assertFalse(UpdatePreferences.shouldNotifyVersion("0.1.8", "0.1.8", "", autoEnabled = true, notificationsEnabled = true))
        assertFalse(UpdatePreferences.shouldNotifyVersion("0.1.8", "", "", autoEnabled = false, notificationsEnabled = true))
    }

    @Test
    fun schedulingRespectsAutoAndFrequency() {
        assertTrue(UpdateScheduler.shouldSchedule(true, UpdateFrequency.DAILY))
        assertTrue(UpdateScheduler.shouldSchedule(true, UpdateFrequency.WEEKLY))
        assertFalse(UpdateScheduler.shouldSchedule(true, UpdateFrequency.ON_APP_START_ONLY))
        assertFalse(UpdateScheduler.shouldSchedule(false, UpdateFrequency.DAILY))
    }

    @Test
    fun startupCheckFrequencyLogic() {
        val now = 10_000L
        assertTrue(UpdatePreferences.shouldCheckNow(true, UpdateFrequency.ON_APP_START_ONLY, now, now))
        assertFalse(UpdatePreferences.shouldCheckNow(false, UpdateFrequency.DAILY, 0L, now))
        assertFalse(UpdatePreferences.shouldCheckNow(true, UpdateFrequency.DAILY, now - 1000L, now))
        assertTrue(UpdatePreferences.shouldCheckNow(true, UpdateFrequency.DAILY, now - UpdatePreferences.DAY_MS, now))
        assertTrue(UpdatePreferences.shouldCheckNow(true, UpdateFrequency.WEEKLY, now - UpdatePreferences.WEEK_MS, now))
    }
}
