package com.oncet.smsquickforwarder.update

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateCheckWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        if (!UpdatePreferences.autoCheckEnabled(applicationContext)) return Result.success()
        val result = UpdateChecker.create(applicationContext).check()
        UpdatePreferences.recordResult(applicationContext, result)
        if (result is UpdateCheckResult.UpdateAvailable) {
            UpdateNotifier.notifyIfNeeded(applicationContext, result.info)
        }
        return Result.success()
    }
}
