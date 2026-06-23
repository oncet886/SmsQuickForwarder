package com.oncet.smsquickforwarder.util

object PhoneNumberUtil {
    fun normalize(s: String): String = s.filter { it.isDigit() || it == '+' }
    fun same(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isBlank() || nb.isBlank()) return false
        return na == nb || na.takeLast(10) == nb.takeLast(10)
    }
}
