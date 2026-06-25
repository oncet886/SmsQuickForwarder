package com.oncet.smsquickforwarder.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(VersionComparator.isRemoteNewer("0.1.7", "0.1.8"))
        assertTrue(VersionComparator.isRemoteNewer("0.1.9", "0.1.10"))
        assertTrue(VersionComparator.isRemoteNewer("0.9.9", "1.0"))
        assertEquals(0, VersionComparator.compare("v0.1.8", "0.1.8"))
        assertFalse(VersionComparator.isRemoteNewer("0.1.8", "0.1.8"))
    }

    @Test
    fun invalidVersionFailsSafely() {
        assertNull(VersionComparator.compare("0.1.beta", "0.1.8"))
        assertFalse(VersionComparator.isRemoteNewer("0.1.8", "release-latest"))
    }
}
