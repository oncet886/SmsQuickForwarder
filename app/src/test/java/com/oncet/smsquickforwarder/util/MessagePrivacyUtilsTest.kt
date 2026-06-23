package com.oncet.smsquickforwarder.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePrivacyUtilsTest {
    @Test
    fun masksVerificationCodesByDefault() {
        assertEquals("code: ******", MessagePrivacyUtils.maskVerificationCodes("code: 847291"))
        assertEquals("verification code ******", MessagePrivacyUtils.maskVerificationCodes("verification code 123456"))
    }

    @Test
    fun defaultExportDoesNotOutputFullBody() {
        assertFalse(MessagePrivacyUtils.shouldOutputFullBody(includeFullBody = false))
    }

    @Test
    fun fullBodySwitchAllowsFullBody() {
        assertTrue(MessagePrivacyUtils.shouldOutputFullBody(includeFullBody = true))
        assertEquals("code: 847291", MessagePrivacyUtils.previewForExport("code: 847291", includeFullBody = true))
    }

    @Test
    fun defaultPreviewIsLimitedAndMasked() {
        val body = "verification code 123456 " + "a".repeat(100)
        val preview = MessagePrivacyUtils.previewForExport(body, includeFullBody = false)
        assertEquals(80, preview.length)
        assertTrue(preview.contains("******"))
    }
}
