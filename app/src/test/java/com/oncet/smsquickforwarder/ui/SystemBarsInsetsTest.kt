package com.oncet.smsquickforwarder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarsInsetsTest {
    @Test
    fun preservesOriginalPaddingAndAddsInsets() {
        val result = SystemBarsInsets.calculatePadding(
            original = PaddingSnapshot(1, 2, 3, 4),
            insets = InsetsSnapshot(statusBarsTop = 10, navigationBarsBottom = 20, systemLeft = 5, systemRight = 6),
            includeTop = true,
            includeBottom = true,
            handleIme = false
        )
        assertEquals(PaddingSnapshot(6, 12, 9, 24), result)
    }

    @Test
    fun displayCutoutCanDriveTopInset() {
        val result = SystemBarsInsets.calculatePadding(
            original = PaddingSnapshot(0, 8, 0, 0),
            insets = InsetsSnapshot(statusBarsTop = 20, displayCutoutTop = 34),
            includeTop = true,
            includeBottom = false,
            handleIme = false
        )
        assertEquals(42, result.top)
    }

    @Test
    fun imeUsesMaxOfKeyboardAndNavigationBar() {
        val result = SystemBarsInsets.calculatePadding(
            original = PaddingSnapshot(0, 0, 0, 12),
            insets = InsetsSnapshot(navigationBarsBottom = 30, imeBottom = 200),
            includeTop = false,
            includeBottom = true,
            handleIme = true
        )
        assertEquals(212, result.bottom)
    }

    @Test
    fun bottomCanBeExcludedForMainRoot() {
        val result = SystemBarsInsets.calculatePadding(
            original = PaddingSnapshot(0, 0, 0, 10),
            insets = InsetsSnapshot(navigationBarsBottom = 50),
            includeTop = false,
            includeBottom = false,
            handleIme = true
        )
        assertEquals(10, result.bottom)
    }

    @Test
    fun landscapeSideInsetsAreApplied() {
        val result = SystemBarsInsets.calculatePadding(
            original = PaddingSnapshot(8, 8, 8, 8),
            insets = InsetsSnapshot(systemLeft = 24, systemRight = 16),
            includeTop = false,
            includeBottom = false,
            handleIme = false
        )
        assertEquals(32, result.left)
        assertEquals(24, result.right)
    }
}
