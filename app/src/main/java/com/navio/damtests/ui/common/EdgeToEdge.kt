package com.navio.damtests.ui.common

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Edge-to-edge for every screen: the app draws behind the status and
 * navigation bars (mandatory from Android 15) on every Android version, and
 * each screen pads its own content with the real bar sizes.
 *
 * @param darkTop true for screens whose top is the navy header, so the status
 *   bar icons are light; otherwise they follow the theme (dark in light mode).
 */
fun ComponentActivity.setupEdgeToEdge(darkTop: Boolean) {
    val transparent = Color.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = if (darkTop) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.auto(transparent, transparent)
        }
    )
}

/** Switches the status bar icons when the top of the screen changes (e.g. bottom navigation tabs). */
fun Activity.setStatusBarDarkTop(darkTop: Boolean, nightMode: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
        !darkTop && !nightMode
}

/**
 * Adds the height of the system bars to this view's padding: the status bar on
 * top and/or the navigation bar at the bottom, on top of the padding set in XML.
 */
fun View.padForSystemBars(top: Boolean = false, bottom: Boolean = false) {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            top = initialTop + if (top) bars.top else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0
        )
        insets
    }
}
