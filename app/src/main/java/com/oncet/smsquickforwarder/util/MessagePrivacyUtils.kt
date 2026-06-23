package com.oncet.smsquickforwarder.util

object MessagePrivacyUtils {
    private val codePattern = Regex("""\b\d{4,8}\b""")

    fun maskVerificationCodes(text: String): String = codePattern.replace(text) { "---" }
}
