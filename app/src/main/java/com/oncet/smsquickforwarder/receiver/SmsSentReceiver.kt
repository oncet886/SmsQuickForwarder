package com.oncet.smsquickforwarder.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.failure.FailureNotifier
import com.oncet.smsquickforwarder.sms.SmsForwarder

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val sender = intent.getStringExtra(EXTRA_SENDER) ?: ""
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val retryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 0)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1)
        val allowRetry = intent.getBooleanExtra(EXTRA_ALLOW_RETRY, true)
        val success = resultCode == Activity.RESULT_OK
        val resultMessage = resultMessage(resultCode)

        ForwardLogStore.markPartResult(
            context = context,
            eventId = eventId,
            partIndex = partIndex,
            partCount = partCount,
            resultCode = resultCode,
            resultMessage = resultMessage,
            retryCount = retryCount,
            success = success
        )

        if (!success && allowRetry && !eventId.isNullOrBlank()) {
            FailureNotifier.notifyIfNeeded(context, eventId)
            SmsForwarder.scheduleRetry(context, eventId, sender, body, retryCount)
        }
    }

    private fun resultMessage(code: Int): String = when (code) {
        Activity.RESULT_OK -> "发送成功"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "通用失败"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "无服务"
        SmsManager.RESULT_ERROR_NULL_PDU -> "PDU 错误"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "无线电关闭"
        else -> "未知错误：$code"
    }

    companion object {
        const val ACTION_SMS_SENT = "com.oncet.smsquickforwarder.ACTION_SMS_SENT"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_BODY = "body"
        const val EXTRA_RETRY_COUNT = "retry_count"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_PART_COUNT = "part_count"
        const val EXTRA_ALLOW_RETRY = "allow_retry"
    }
}
