package com.oncet.smsquickforwarder.util

object PhoneMaskUtils {
    fun mask(phone: String): String {
        if (phone.isBlank()) return ""
        val prefix = if (phone.trim().startsWith("+")) "+" else ""
        val digits = phone.filter { it.isDigit() }
        if (digits.isEmpty()) return phone
        return when {
            digits.length >= 11 && prefix == "+" -> {
                val country = digits.take(1)
                "$prefix$country******${digits.takeLast(4)}"
            }
            digits.length == 10 -> "${digits.take(2)}****${digits.takeLast(4)}"
            digits.length < 7 -> "*".repeat((digits.length - 2).coerceAtLeast(0)) + digits.takeLast(2)
            else -> "${digits.take(2)}****${digits.takeLast(4)}"
        }
    }
}
