package com.oncet.smsquickforwarder.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun newInstallShouldEnterOnboarding() {
        assertTrue(OnboardingState.shouldShow(OnboardingSnapshot(false, false, false, false, false)))
    }

    @Test
    fun upgradeUserShouldNotBeForcedIntoOnboarding() {
        assertFalse(OnboardingState.shouldShow(OnboardingSnapshot(false, true, false, false, false)))
        assertFalse(OnboardingState.shouldShow(OnboardingSnapshot(false, false, true, false, false)))
        assertFalse(OnboardingState.shouldShow(OnboardingSnapshot(false, false, false, true, false)))
    }

    @Test
    fun completionRequiresTargetAndCorePermissions() {
        assertFalse(OnboardingState.canComplete("", true, true))
        assertFalse(OnboardingState.canComplete("+16025550108", true, false))
        assertTrue(OnboardingState.canComplete("+16025550108", true, true))
    }
}
