package com.oncet.smsquickforwarder.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneMaskUtilsTest {
    @Test
    fun masksUsInternationalNumber() {
        assertEquals("+1******6666", PhoneMaskUtils.mask("+12397106666"))
    }

    @Test
    fun masksTenDigitNumber() {
        assertEquals("23****6666", PhoneMaskUtils.mask("2397106666"))
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
