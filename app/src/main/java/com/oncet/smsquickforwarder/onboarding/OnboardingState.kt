package com.oncet.smsquickforwarder.onboarding

data class OnboardingSnapshot(
    val completed: Boolean,
    val hasTarget: Boolean,
    val hasExistingLogs: Boolean,
    val hasExistingRules: Boolean,
    val legacyGuideShown: Boolean
)

object OnboardingState {
    fun shouldShow(snapshot: OnboardingSnapshot): Boolean {
        if (snapshot.completed) return false
        val looksLikeUpgrade = snapshot.hasTarget || snapshot.hasExistingLogs || snapshot.hasExistingRules || snapshot.legacyGuideShown
        return !looksLikeUpgrade
    }

    fun canComplete(target: String, receiveGranted: Boolean, sendGranted: Boolean): Boolean =
        target.trim().isNotBlank() && receiveGranted && sendGranted
}
