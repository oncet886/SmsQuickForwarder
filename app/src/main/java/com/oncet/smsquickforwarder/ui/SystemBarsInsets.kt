package com.oncet.smsquickforwarder.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject

data class PaddingSnapshot(val left: Int, val top: Int, val right: Int, val bottom: Int)
data class InsetsSnapshot(
    val statusBarsTop: Int = 0,
    val navigationBarsBottom: Int = 0,
    val displayCutoutTop: Int = 0,
    val imeBottom: Int = 0,
    val systemLeft: Int = 0,
    val systemRight: Int = 0
)

object SystemBarsInsets {
    private const val PREF = "window_insets"

    fun Activity.applyStandardSystemBars(
        root: View,
        scrollView: ScrollView? = root as? ScrollView,
        handleIme: Boolean = false
    ) {
        configureBars()
        scrollView?.clipToPadding = false
        val original = root.paddingSnapshot()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val snapshot = snapshotFrom(insets)
            record(this, snapshot)
            val calculated = calculatePadding(
                original = original,
                insets = snapshot,
                includeTop = true,
                includeBottom = true,
                handleIme = handleIme
            )
            view.setPadding(calculated.left, calculated.top, calculated.right, calculated.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun Activity.applyMainSystemBars(root: View, scrollView: ScrollView, bottomNavigation: View) {
        configureBars()
        scrollView.clipToPadding = false
        val rootOriginal = root.paddingSnapshot()
        val navOriginal = bottomNavigation.paddingSnapshot()
        val scrollOriginal = scrollView.paddingSnapshot()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val snapshot = snapshotFrom(insets)
            record(this, snapshot)
            val rootPadding = calculatePadding(rootOriginal, snapshot, includeTop = true, includeBottom = false, handleIme = false)
            view.setPadding(rootPadding.left, rootPadding.top, rootPadding.right, rootPadding.bottom)
            val navBottom = navOriginal.bottom + snapshot.navigationBarsBottom
            bottomNavigation.setPadding(navOriginal.left, navOriginal.top, navOriginal.right, navBottom)
            val scrollBottom = scrollOriginal.bottom + snapshot.imeBottom.coerceAtLeast(0)
            scrollView.setPadding(scrollOriginal.left, scrollOriginal.top, scrollOriginal.right, scrollBottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun calculatePadding(
        original: PaddingSnapshot,
        insets: InsetsSnapshot,
        includeTop: Boolean,
        includeBottom: Boolean,
        handleIme: Boolean
    ): PaddingSnapshot {
        val topInset = if (includeTop) maxOf(insets.statusBarsTop, insets.displayCutoutTop) else 0
        val bottomInset = when {
            !includeBottom -> 0
            handleIme -> maxOf(insets.navigationBarsBottom, insets.imeBottom)
            else -> insets.navigationBarsBottom
        }
        return PaddingSnapshot(
            left = original.left + insets.systemLeft,
            top = original.top + topInset,
            right = original.right + insets.systemRight,
            bottom = original.bottom + bottomInset
        )
    }

    fun lastInsetsJson(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("statusBarsTop", prefs.getInt("statusBarsTop", 0))
            put("navigationBarsBottom", prefs.getInt("navigationBarsBottom", 0))
            put("displayCutoutTop", prefs.getInt("displayCutoutTop", 0))
            put("imeBottom", prefs.getInt("imeBottom", 0))
            put("screenWidth", context.resources.displayMetrics.widthPixels)
            put("screenHeight", context.resources.displayMetrics.heightPixels)
            put("orientation", if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
            put("navigationModeApproximation", if (prefs.getInt("navigationBarsBottom", 0) > UiKit.dp(context, 40)) "three_button_or_large_nav" else "gesture_or_small_nav")
        }
    }

    private fun Activity.configureBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
    }

    private fun snapshotFrom(insets: WindowInsetsCompat): InsetsSnapshot {
        val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val system = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        return InsetsSnapshot(
            statusBarsTop = status.top,
            navigationBarsBottom = nav.bottom,
            displayCutoutTop = cutout.top,
            imeBottom = ime.bottom,
            systemLeft = system.left,
            systemRight = system.right
        )
    }

    private fun record(context: Context, snapshot: InsetsSnapshot) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("statusBarsTop", snapshot.statusBarsTop)
            .putInt("navigationBarsBottom", snapshot.navigationBarsBottom)
            .putInt("displayCutoutTop", snapshot.displayCutoutTop)
            .putInt("imeBottom", snapshot.imeBottom)
            .apply()
    }

    private fun View.paddingSnapshot(): PaddingSnapshot =
        PaddingSnapshot(paddingLeft, paddingTop, paddingRight, paddingBottom)
}
