package com.nobi.scan.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

@Composable
internal fun rememberNobiHaptics(): NobiHaptics {
    val view = LocalView.current
    return remember(view) { NobiHaptics(view) }
}

// Focus feedback belongs to the field. The IME handles feedback for its own keys.
internal fun Modifier.hapticOnFocus(haptics: NobiHaptics): Modifier = onFocusChanged {
    if (it.isFocused) haptics.tap()
}

internal class NobiHaptics(private val view: View) {
    fun tap() = pulse(HapticFeedbackConstantsCompat.CONTEXT_CLICK)
    fun step() = pulse(HapticFeedbackConstantsCompat.SEGMENT_TICK)
    fun grab() = pulse(HapticFeedbackConstantsCompat.DRAG_START)
    fun confirm() = pulse(HapticFeedbackConstantsCompat.CONFIRM)
    // Some devices silently drop the platform TOGGLE_OFF effect.
    fun toggle(on: Boolean) = pulse(
        if (on) HapticFeedbackConstantsCompat.TOGGLE_ON else HapticFeedbackConstantsCompat.CONTEXT_CLICK
    )

    fun onTap(action: () -> Unit): () -> Unit = {
        tap()
        action()
    }

    fun onTapIfEnabled(enabled: Boolean, action: () -> Unit): () -> Unit = {
        if (enabled) {
            tap()
            action()
        }
    }

    private fun pulse(effect: Int) {
        ViewCompat.performHapticFeedback(view, effect)
    }
}
