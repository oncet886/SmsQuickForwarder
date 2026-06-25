package com.oncet.smsquickforwarder.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    const val WORK_NAME = "sms_quick_forwarder_update_check"

    fun configure(context: Context) {
        val auto = UpdatePreferences.autoCheckEnabled(context)
        val frequency = UpdatePreferences.frequency(context)
        val manager = WorkManager.getInstance(context)
        if (!shouldSchedule(auto, frequency)) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val intervalHours = if (frequency == UpdateFrequency.WEEKLY) 7L * 24L else 24L
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun shouldSchedule(autoEnabled: Boolean, frequency: UpdateFrequency): Boolean =
        autoEnabled && frequency != UpdateFrequency.ON_APP_START_ONLY
}
