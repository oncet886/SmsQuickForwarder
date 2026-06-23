package com.oncet.smsquickforwarder.sms

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.data.SettingsStore
import com.oncet.smsquickforwarder.receiver.RetryReceiver
import com.oncet.smsquickforwarder.receiver.SmsSentReceiver
import com.oncet.smsquickforwarder.util.PhoneNumberUtil
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsForwarder {
    private const val MARKER = "[SMS Forward]"

    fun handleIncoming(
        context: Context,
        intentAction: String,
        sender: String,
        body: String,
        multipartCount: Int,
        slot: String,
        simId: String,
        subscription: String,
        extrasKeys: JSONArray = JSONArray()
    ) {
        val target = SettingsStore.targetPhone(context)
        val eventId = ForwardLogStore.addReceived(
            context = context,
            intentAction = intentAction,
            sender = sender,
            body = body,
            multipartCount = multipartCount,
            slot = slot,
            simId = simId,
            subscription = subscription,
            targetPhone = target,
            extrasKeys = extrasKeys
        )
        validateAndSend(context, eventId, sender, body, retryCount = 0)
    }

    fun sendTestMessage(context: Context): String {
        val target = SettingsStore.targetPhone(context)
        val body = "[SMS Forward Test]\nTime: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}"
        val eventId = ForwardLogStore.addTestSend(context, target, body)
        if (target.isBlank()) {
            ForwardLogStore.updateDecision(context, eventId, "skipped_empty_target", "目标手机号为空")
            return eventId
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ForwardLogStore.updateDecision(context, eventId, "failed_no_permission", "缺少 SEND_SMS 权限", targetPhone = target)
            return eventId
        }
        sendRaw(context, eventId, target, body, sender = "APP_TEST", originalBody = body, retryCount = 0, allowRetry = false)
        return eventId
    }

    fun retry(context: Context, eventId: String, sender: String, body: String, retryCount: Int) {
        ForwardLogStore.updateDecision(
            context,
            eventId,
            decision = "retrying",
            sendResult = "retrying",
            retryCount = retryCount
        )
        validateAndSend(context, eventId, sender, body, retryCount)
    }

    private fun validateAndSend(context: Context, eventId: String, sender: String, body: String, retryCount: Int) {
        val target = SettingsStore.targetPhone(context)
        if (!SettingsStore.isEnabled(context)) {
            ForwardLogStore.updateDecision(context, eventId, "skipped_disabled", "转发已暂停", targetPhone = target)
            return
        }
        if (target.isBlank()) {
            ForwardLogStore.updateDecision(context, eventId, "skipped_empty_target", "目标手机号为空", targetPhone = target)
            return
        }
        if (PhoneNumberUtil.same(sender, target)) {
            ForwardLogStore.updateDecision(context, eventId, "skipped_from_target", "来自目标号码，避免循环", targetPhone = target)
            return
        }
        if (body.contains(MARKER, ignoreCase = true)) {
            ForwardLogStore.updateDecision(context, eventId, "skipped_forward_marker", "内容包含 $MARKER，避免循环", targetPhone = target)
            return
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ForwardLogStore.updateDecision(context, eventId, "failed_no_permission", "缺少 SEND_SMS 权限", targetPhone = target)
            return
        }
        send(context, eventId, sender, body, target, retryCount)
    }

    private fun send(context: Context, eventId: String, sender: String, body: String, target: String, retryCount: Int) {
        val forwardBody = buildForwardBody(sender, body)
        sendRaw(context, eventId, target, forwardBody, sender, body, retryCount, allowRetry = true)
    }

    private fun sendRaw(
        context: Context,
        eventId: String,
        target: String,
        smsBody: String,
        sender: String,
        originalBody: String,
        retryCount: Int,
        allowRetry: Boolean
    ) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(smsBody)
            ForwardLogStore.updateDecision(
                context,
                eventId,
                decision = if (retryCount > 0) "retrying" else "sending",
                sendResult = "sending",
                retryCount = retryCount,
                messagePartCount = parts.size,
                targetPhone = target
            )

            val sentIntents = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                val sentIntent = Intent(context, SmsSentReceiver::class.java).apply {
                    action = SmsSentReceiver.ACTION_SMS_SENT
                    putExtra(SmsSentReceiver.EXTRA_EVENT_ID, eventId)
                    putExtra(SmsSentReceiver.EXTRA_PART_INDEX, i)
                    putExtra(SmsSentReceiver.EXTRA_PART_COUNT, parts.size)
                    putExtra(SmsSentReceiver.EXTRA_SENDER, sender)
                    putExtra(SmsSentReceiver.EXTRA_BODY, originalBody)
                    putExtra(SmsSentReceiver.EXTRA_RETRY_COUNT, retryCount)
                    putExtra(SmsSentReceiver.EXTRA_ALLOW_RETRY, allowRetry)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                sentIntents.add(PendingIntent.getBroadcast(context, (eventId + "-$retryCount-$i").hashCode(), sentIntent, flags))
            }
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(target, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(target, null, smsBody, sentIntents.firstOrNull(), null)
            }
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            ForwardLogStore.updateDecision(context, eventId, "failed_send_exception", sendResult = "exception", errorMessage = message)
            if (allowRetry) scheduleRetry(context, eventId, sender, originalBody, retryCount)
        }
    }

    fun scheduleRetry(context: Context, eventId: String, sender: String, body: String, retryCount: Int) {
        if (retryCount >= 2) {
            ForwardLogStore.updateDecision(
                context,
                eventId,
                decision = "failed",
                sendResult = "final_failed",
                errorMessage = "已重试 2 次，仍然失败",
                retryCount = retryCount
            )
            return
        }
        val nextRetry = retryCount + 1
        val delayMs = if (nextRetry == 1) 10_000L else 30_000L
        val intent = Intent(context, RetryReceiver::class.java).apply {
            action = RetryReceiver.ACTION_RETRY_FORWARD
            putExtra(RetryReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(RetryReceiver.EXTRA_SENDER, sender)
            putExtra(RetryReceiver.EXTRA_BODY, body)
            putExtra(RetryReceiver.EXTRA_RETRY_COUNT, nextRetry)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, eventId.hashCode() + nextRetry, intent, flags)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
        ForwardLogStore.updateDecision(
            context,
            eventId,
            decision = "retrying",
            sendResult = "第 $nextRetry 次重试将在 ${delayMs / 1000} 秒后执行",
            retryCount = nextRetry
        )
    }

    private fun buildForwardBody(sender: String, body: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return "$MARKER\nFrom: $sender\nTime: $time\nBody:\n$body"
    }
}
