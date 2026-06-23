package com.oncet.smsquickforwarder.util

object MessagePrivacyUtils {
    private val codePattern = Regex("""\b\d{4,8}\b""")

    fun maskVerificationCodes(text: String): String = codePattern.replace(text) { "*".repeat(it.value.length) }

    fun previewForExport(body: String, includeFullBody: Boolean): String {
        val preview = body.take(80)
        return if (includeFullBody) preview else maskVerificationCodes(preview)
    }

    fun shouldOutputFullBody(includeFullBody: Boolean): Boolean = includeFullBody
}
