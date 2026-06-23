package com.oncet.smsquickforwarder.data

import android.content.Context

object SettingsStore {
    private const val PREF = "settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TARGET = "target"
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_SETUP_GUIDE_SHOWN = "setup_guide_shown"
    private const val KEY_SEND_SMS_REQUESTED = "send_sms_requested"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun targetPhone(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TARGET, "") ?: ""

    fun setTargetPhone(context: Context, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_TARGET, value.trim()).apply()
    }

    fun isServiceRunning(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_SERVICE_RUNNING, false)

    fun setServiceRunning(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()
    }

    fun isSetupGuideShown(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_SETUP_GUIDE_SHOWN, false)

    fun setSetupGuideShown(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_SETUP_GUIDE_SHOWN, value).apply()
    }

    fun wasSendSmsRequested(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_SEND_SMS_REQUESTED, false)

    fun setSendSmsRequested(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEND_SMS_REQUESTED, value).apply()
    }
}
