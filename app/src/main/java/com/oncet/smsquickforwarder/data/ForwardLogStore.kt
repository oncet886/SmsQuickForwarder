package com.oncet.smsquickforwarder.data

import android.content.Context
import com.oncet.smsquickforwarder.rules.RuleEvaluation
import com.oncet.smsquickforwarder.util.MessagePrivacyUtils
import com.oncet.smsquickforwarder.util.PhoneMaskUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ForwardLogStore {
    private const val PREF = "logs"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 500

    fun addReceived(
        context: Context,
        intentAction: String,
        sender: String,
        body: String,
        multipartCount: Int,
        slot: String,
        simId: String,
        subscription: String,
        targetPhone: String,
        extrasKeys: JSONArray = JSONArray()
    ): String {
        val item = baseEvent(
            eventType = "sms_received",
            intentAction = intentAction,
            sender = sender,
            body = body,
            multipartCount = multipartCount,
            slot = slot,
            simId = simId,
            subscription = subscription,
            targetPhone = targetPhone,
            extrasKeys = extrasKeys,
            decision = "sms_received"
        )
        return addObject(context, item)
    }

    fun addReceiverEvent(
        context: Context,
        eventType: String,
        intentAction: String,
        extrasKeys: JSONArray = JSONArray(),
        sender: String = "",
        body: String = "",
        multipartCount: Int = 0,
        slot: String = "",
        simId: String = "",
        subscription: String = "",
        targetPhone: String = "",
        errorMessage: String = ""
    ): String {
        val item = baseEvent(
            eventType = eventType,
            intentAction = intentAction,
            sender = sender,
            body = body,
            multipartCount = multipartCount,
            slot = slot,
            simId = simId,
            subscription = subscription,
            targetPhone = targetPhone,
            extrasKeys = extrasKeys,
            decision = eventType,
            errorMessage = errorMessage
        )
        return addObject(context, item)
    }

    fun addTestSend(context: Context, targetPhone: String, body: String): String {
        val item = baseEvent(
            eventType = "test_send",
            intentAction = "manual_test_send",
            sender = "APP_TEST",
            body = body,
            multipartCount = 1,
            slot = "",
            simId = "",
            subscription = "",
            targetPhone = targetPhone,
            extrasKeys = JSONArray(),
            decision = "test_send"
        )
        return addObject(context, item)
    }

    private fun addObject(context: Context, item: JSONObject): String {
        val id = item.optString("eventId")
        val arr = readArray(context)
        val newArr = JSONArray()
        newArr.put(item)
        for (i in 0 until minOf(arr.length(), MAX_ITEMS - 1)) newArr.put(arr.getJSONObject(i))
        saveArray(context, newArr)
        return id
    }

    fun updateDecision(
        context: Context,
        eventId: String?,
        decision: String,
        skipReason: String = "",
        sendResult: String = "",
        errorMessage: String = "",
        retryCount: Int? = null,
        messagePartCount: Int? = null,
        targetPhone: String? = null
    ) {
        if (eventId.isNullOrBlank()) return
        mutate(context, eventId) { obj ->
            obj.put("decision", decision)
            if (skipReason.isNotBlank()) obj.put("skipReason", skipReason)
            if (sendResult.isNotBlank()) obj.put("sendResult", sendResult)
            if (errorMessage.isNotBlank()) obj.put("errorMessage", errorMessage)
            if (retryCount != null) obj.put("retryCount", retryCount)
            if (messagePartCount != null) obj.put("messagePartCount", messagePartCount)
            if (targetPhone != null) obj.put("targetPhone", targetPhone)
            obj.put("updatedAt", now())
        }
    }

    fun updateRuleEvaluation(
        context: Context,
        eventId: String?,
        evaluation: RuleEvaluation,
        durationMs: Long
    ) {
        if (eventId.isNullOrBlank()) return
        mutate(context, eventId) { obj ->
            obj.put("forwardMode", evaluation.mode.name)
            obj.put("normalizedSender", evaluation.normalizedSender)
            obj.put("matchedIncludeRuleIds", JSONArray().apply { evaluation.includeMatches.forEach { put(it.id) } })
            obj.put("matchedExcludeRuleIds", JSONArray().apply { evaluation.excludeMatches.forEach { put(it.id) } })
            obj.put("matchedRuleNames", JSONArray().apply { evaluation.matchedRuleNames.forEach { put(it) } })
            obj.put("primaryMatchedRuleName", evaluation.primaryRule?.name.orEmpty())
            obj.put("finalRuleReason", evaluation.reason)
            obj.put("ruleEvaluationDurationMs", durationMs)
            obj.put("pendingForwardDecision", evaluation.decision)
            if (!evaluation.shouldForward) {
                obj.put("decision", evaluation.decision)
                obj.put("skipReason", evaluation.reason)
            }
            obj.put("updatedAt", now())
        }
    }

    fun markPartResult(
        context: Context,
        eventId: String?,
        partIndex: Int,
        partCount: Int,
        resultCode: Int,
        resultMessage: String,
        retryCount: Int,
        success: Boolean
    ) {
        if (eventId.isNullOrBlank()) return
        mutate(context, eventId) { obj ->
            obj.put("messagePartCount", partCount)
            obj.put("retryCount", retryCount)
            val parts = obj.optJSONArray("sendParts") ?: JSONArray()
            var replaced = false
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.optInt("partIndex") == partIndex) {
                    fillPart(part, resultCode, resultMessage, retryCount, success)
                    replaced = true
                    break
                }
            }
            if (!replaced) {
                parts.put(JSONObject().apply {
                    put("partIndex", partIndex)
                    fillPart(this, resultCode, resultMessage, retryCount, success)
                })
            }
            obj.put("sendParts", parts)

            var successCount = 0
            var failureCount = 0
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                when (part.optString("status")) {
                    "sent" -> successCount += 1
                    "failed" -> failureCount += 1
                }
            }
            when {
                failureCount > 0 -> {
                    obj.put("decision", "failed")
                    obj.put("sendResult", resultMessage)
                    obj.put("errorMessage", resultMessage)
                }
                successCount >= partCount -> {
                    obj.put("decision", obj.optString("pendingForwardDecision").ifBlank { "forwarded" })
                    obj.put("sendResult", "sent")
                    obj.put("errorMessage", "")
                }
                else -> obj.put("sendResult", "sent $successCount/$partCount parts")
            }
            obj.put("updatedAt", now())
        }
    }

    fun recentEvents(
        context: Context,
        limit: Int = 20,
        includeFullBody: Boolean = false,
        includeFullPhone: Boolean = false
    ): JSONArray {
        val arr = readArray(context)
        val out = JSONArray()
        for (i in 0 until minOf(arr.length(), limit)) {
            val obj = JSONObject(arr.getJSONObject(i).toString())
            sanitizeForExport(obj, includeFullBody, includeFullPhone)
            out.put(obj)
        }
        return out
    }

    fun listText(context: Context, limit: Int = 20): String {
        val arr = readArray(context)
        if (arr.length() == 0) return "暂无记录"
        val sb = StringBuilder()
        for (i in 0 until minOf(arr.length(), limit)) {
            val obj = arr.getJSONObject(i)
            sb.append(obj.optString("receivedAt"))
                .append("  ").append(obj.optString("decision"))
                .append("\nFrom: ").append(PhoneMaskUtils.mask(obj.optString("sender")))
                .append(" -> ").append(PhoneMaskUtils.mask(obj.optString("targetPhone")))
                .append("\n").append(MessagePrivacyUtils.maskVerificationCodes(obj.optString("bodyPreview")))
            val ruleName = obj.optString("primaryMatchedRuleName")
            if (ruleName.isNotBlank()) sb.append("\nRule: ").append(ruleName)
            val reason = obj.optString("skipReason")
            val error = obj.optString("errorMessage")
            val result = obj.optString("sendResult")
            if (reason.isNotBlank()) sb.append("\nReason: ").append(reason)
            if (result.isNotBlank()) sb.append("\nResult: ").append(result)
            if (error.isNotBlank()) sb.append("\nError: ").append(error)
            sb.append("\n\n")
        }
        return sb.toString().trim()
    }

    fun clear(context: Context) = saveArray(context, JSONArray())

    fun eventById(context: Context, eventId: String): JSONObject? {
        val arr = readArray(context)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("eventId") == eventId) return JSONObject(obj.toString())
        }
        return null
    }

    fun recentUserEvents(context: Context, limit: Int): JSONArray {
        val arr = readArray(context)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = obj.optString("eventType")
            if (type == "receiver_entered" || type == "ignored_action" || type == "empty_pdu") continue
            out.put(JSONObject(obj.toString()))
            if (out.length() >= limit) break
        }
        return out
    }

    fun countToday(context: Context, predicate: (JSONObject) -> Boolean): Int {
        val today = now().take(10)
        val arr = readArray(context)
        var count = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val date = obj.optString("updatedAt").ifBlank { obj.optString("receivedAt") }.take(10)
            if (date == today && predicate(obj)) count += 1
        }
        return count
    }

    fun findLatestTime(context: Context, predicate: (JSONObject) -> Boolean): String {
        val arr = readArray(context)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (predicate(obj)) return obj.optString("updatedAt").ifBlank { obj.optString("receivedAt") }
        }
        return ""
    }

    fun latestFailureReason(context: Context): String {
        val arr = readArray(context)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("decision").contains("failed", ignoreCase = true)) {
                return obj.optString("errorMessage").ifBlank { obj.optString("skipReason") }
            }
        }
        return ""
    }

    private fun mutate(context: Context, eventId: String, block: (JSONObject) -> Unit) {
        val arr = readArray(context)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("eventId") == eventId) {
                block(obj)
                break
            }
        }
        saveArray(context, arr)
    }

    private fun fillPart(
        part: JSONObject,
        resultCode: Int,
        resultMessage: String,
        retryCount: Int,
        success: Boolean
    ) {
        part.put("sentAt", now())
        part.put("resultCode", resultCode)
        part.put("resultMessage", resultMessage)
        part.put("retryCount", retryCount)
        part.put("status", if (success) "sent" else "failed")
    }

    private fun baseEvent(
        eventType: String,
        intentAction: String,
        sender: String,
        body: String,
        multipartCount: Int,
        slot: String,
        simId: String,
        subscription: String,
        targetPhone: String,
        extrasKeys: JSONArray,
        decision: String,
        errorMessage: String = ""
    ): JSONObject {
        val id = System.currentTimeMillis().toString() + "-" + (100..999).random()
        return JSONObject().apply {
            put("eventId", id)
            put("eventType", eventType)
            put("receivedAt", now())
            put("intentAction", intentAction)
            put("extrasKeys", extrasKeys)
            put("sender", sender)
            put("bodyPreview", body.take(80))
            put("bodyLength", body.length)
            put("fullBody", body)
            put("multipartCount", multipartCount)
            put("slot", slot)
            put("simId", simId)
            put("subscription", subscription)
            put("decision", decision)
            put("skipReason", "")
            put("targetPhone", targetPhone)
            put("messagePartCount", 0)
            put("retryCount", 0)
            put("sendResult", "")
            put("errorMessage", errorMessage)
            put("sendParts", JSONArray())
            put("forwardMode", "")
            put("normalizedSender", "")
            put("matchedIncludeRuleIds", JSONArray())
            put("matchedExcludeRuleIds", JSONArray())
            put("matchedRuleNames", JSONArray())
            put("primaryMatchedRuleName", "")
            put("finalRuleReason", "")
            put("ruleEvaluationDurationMs", 0)
            put("pendingForwardDecision", "")
        }
    }

    private fun readArray(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun saveArray(context: Context, arr: JSONArray) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, arr.toString()).commit()
    }

    private fun sanitizeForExport(obj: JSONObject, includeFullBody: Boolean, includeFullPhone: Boolean) {
        if (!includeFullPhone) {
            obj.put("sender", PhoneMaskUtils.mask(obj.optString("sender")))
            obj.put("targetPhone", PhoneMaskUtils.mask(obj.optString("targetPhone")))
            if (obj.has("originalSender")) obj.put("originalSender", PhoneMaskUtils.mask(obj.optString("originalSender")))
        }
        if (includeFullBody) {
            return
        }
        obj.put("bodyPreview", MessagePrivacyUtils.previewForExport(obj.optString("bodyPreview"), includeFullBody))
        obj.remove("fullBody")
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
