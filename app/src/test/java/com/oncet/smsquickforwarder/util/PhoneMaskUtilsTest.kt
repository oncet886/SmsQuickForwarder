package com.oncet.smsquickforwarder.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneMaskUtilsTest {
    @Test
    fun masksUsInternationalNumber() {
        assertEquals("+1******6666", PhoneMaskUtils.mask("+15550106666"))
    }

    @Test
    fun masksTenDigitNumber() {
        assertEquals("55****6666", PhoneMaskUtils.mask("5550106666"))
    }

    @Test
    fun masksShortNumber() {
        assertEquals("***45", PhoneMaskUtils.mask("12345"))
    }

    @Test
    fun keepsBlankNumberBlank() {
        assertEquals("", PhoneMaskUtils.mask(""))
    }
}
