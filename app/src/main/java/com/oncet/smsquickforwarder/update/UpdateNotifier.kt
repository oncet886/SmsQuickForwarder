package com.oncet.smsquickforwarder.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.oncet.smsquickforwarder.R

object UpdateNotifier {
    private const val CHANNEL_ID = "update_notifications"
    private const val NOTIFICATION_ID = 2001

    fun notifyIfNeeded(context: Context, info: UpdateInfo) {
        if (!UpdatePreferences.shouldNotify(context, info.latestVersionName)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "版本更新", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
        val pendingIntent = PendingIntent.getActivity(
            context,
            info.latestVersionName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${context.getString(R.string.app_name)}有新版本")
            .setContentText("${info.latestVersionName} 已发布，点击查看更新内容")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        UpdatePreferences.markNotified(context, info.latestVersionName)
    }
}
