package com.oncet.smsquickforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oncet.smsquickforwarder.sms.SmsForwarder

class RetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RETRY_FORWARD) return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        SmsForwarder.retry(
            context = context,
            eventId = eventId,
            sender = intent.getStringExtra(EXTRA_SENDER) ?: "",
            body = intent.getStringExtra(EXTRA_BODY) ?: "",
            retryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 0)
        )
    }

    companion object {
        const val ACTION_RETRY_FORWARD = "com.oncet.smsquickforwarder.ACTION_RETRY_FORWARD"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_BODY = "body"
        const val EXTRA_RETRY_COUNT = "retry_count"
    }
}
