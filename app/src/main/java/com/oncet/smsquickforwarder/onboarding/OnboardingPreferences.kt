package com.oncet.smsquickforwarder.onboarding

import android.content.Context
import com.oncet.smsquickforwarder.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OnboardingPreferences {
    private const val PREF = "onboarding"
    private const val KEY_COMPLETED = "onboardingCompleted"
    private const val KEY_COMPLETED_AT = "onboardingCompletedAt"
    private const val KEY_VERSION = "onboardingVersion"
    private const val KEY_STEP = "onboardingStep"

    fun completed(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_COMPLETED, false)

    fun markCompleted(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putString(KEY_COMPLETED_AT, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .putString(KEY_VERSION, BuildConfig.VERSION_NAME)
            .putInt(KEY_STEP, 5)
            .apply()
    }

    fun markUpgradeCompleted(context: Context) {
        if (!completed(context)) markCompleted(context)
    }

    fun step(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_STEP, 0).coerceIn(0, 5)

    fun setStep(context: Context, step: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_STEP, step.coerceIn(0, 5)).apply()
    }
}
