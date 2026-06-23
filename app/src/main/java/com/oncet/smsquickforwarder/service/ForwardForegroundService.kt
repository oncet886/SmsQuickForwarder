package com.oncet.smsquickforwarder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.oncet.smsquickforwarder.MainActivity
import com.oncet.smsquickforwarder.R
import com.oncet.smsquickforwarder.data.SettingsStore

class ForwardForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "短信转发运行状态", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SettingsStore.isEnabled(this)) {
            SettingsStore.setServiceRunning(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1001, buildNotification())
        SettingsStore.setServiceRunning(this, true)
        return START_STICKY
    }

    override fun onDestroy() {
        SettingsStore.setServiceRunning(this, false)
        super.onDestroy()
    }

    private fun buildNotification() = android.app.Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("短信转发已启用")
        .setContentText("转发到：${SettingsStore.targetPhone(this)}")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        const val CHANNEL_ID = "sms_forward_running"
    }
}
