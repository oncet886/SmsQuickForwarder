package com.oncet.smsquickforwarder.rules

object PhoneMatchUtils {
    fun normalizePhoneForMatch(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        val startsPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (startsPlus) "+$digits" else digits
    }

    fun phoneEquals(left: String, right: String): Boolean {
        val a = normalizePhoneForMatch(left)
        val b = normalizePhoneForMatch(right)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val ad = a.filter { it.isDigit() }
        val bd = b.filter { it.isDigit() }
        return ad.length >= 10 && bd.length >= 10 && ad.takeLast(10) == bd.takeLast(10)
    }
}
