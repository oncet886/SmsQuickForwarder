package com.oncet.smsquickforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.oncet.smsquickforwarder.data.ForwardLogStore
import com.oncet.smsquickforwarder.sms.SmsForwarder
import org.json.JSONArray

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ForwardLogStore.addReceiverEvent(context, "receiver_entered", intent.action ?: "", extrasKeys(intent))
        try {
            val slot = rawExtra(intent, "slot", "slotId", "phone")
            val simId = rawExtra(intent, "simId")
            val subscription = rawExtra(intent, "subscription", "subscription_id", "android.telephony.extra.SUBSCRIPTION_INDEX")

            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                ForwardLogStore.addReceiverEvent(
                    context = context,
                    eventType = "ignored_action",
                    intentAction = intent.action ?: "",
                    extrasKeys = extrasKeys(intent),
                    slot = slot,
                    simId = simId,
                    subscription = subscription
                )
                return
            }

            val messages = try {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } catch (e: Exception) {
                ForwardLogStore.addReceiverEvent(
                    context = context,
                    eventType = "empty_pdu",
                    intentAction = intent.action ?: "",
                    extrasKeys = extrasKeys(intent),
                    slot = slot,
                    simId = simId,
                    subscription = subscription,
                    errorMessage = e.message ?: e.javaClass.simpleName
                )
                return
            }

            if (messages.isNullOrEmpty()) {
                ForwardLogStore.addReceiverEvent(
                    context = context,
                    eventType = "empty_pdu",
                    intentAction = intent.action ?: "",
                    extrasKeys = extrasKeys(intent),
                    slot = slot,
                    simId = simId,
                    subscription = subscription,
                    errorMessage = "getMessagesFromIntent returned empty"
                )
                return
            }

            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
            val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
            SmsForwarder.handleIncoming(
                context = context,
                intentAction = intent.action ?: "",
                sender = sender,
                body = body,
                multipartCount = messages.size,
                slot = slot,
                simId = simId,
                subscription = subscription,
                extrasKeys = extrasKeys(intent)
            )
        } catch (e: Exception) {
            ForwardLogStore.addReceiverEvent(
                context = context,
                eventType = "failed",
                intentAction = intent.action ?: "",
                extrasKeys = extrasKeys(intent),
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun extrasKeys(intent: Intent): JSONArray {
        val arr = JSONArray()
        intent.extras?.keySet()?.sorted()?.forEach { arr.put(it) }
        return arr
    }

    @Suppress("DEPRECATION")
    private fun rawExtra(intent: Intent, vararg names: String): String {
        for (name in names) {
            if (intent.hasExtra(name)) return intent.extras?.get(name)?.toString() ?: ""
        }
        return ""
    }
}
