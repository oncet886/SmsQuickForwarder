package com.oncet.smsquickforwarder.logs

enum class LogRetentionDays(val days: Int) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
    FOREVER(-1);

    companion object {
        fun fromName(value: String?): LogRetentionDays =
            values().firstOrNull { it.name == value } ?: DAYS_30
    }
}

object LogRetentionPolicy {
    fun shouldKeep(eventMillis: Long, nowMillis: Long, retention: LogRetentionDays): Boolean {
        if (retention == LogRetentionDays.FOREVER) return true
        val cutoff = nowMillis - retention.days * 24L * 60L * 60L * 1000L
        return eventMillis >= cutoff
    }
}
