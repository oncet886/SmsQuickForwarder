package com.oncet.smsquickforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.service.ForwardForegroundService
import com.oncet.smsquickforwarder.update.UpdateScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            UpdateScheduler.configure(context)
            if (SettingsStore.isEnabled(context)) {
                context.startForegroundService(Intent(context, ForwardForegroundService::class.java))
            }
        }
    }
}
