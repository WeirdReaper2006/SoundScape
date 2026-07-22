package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Guards a dismiss/navigate/toggle action against rapid repeated taps.
 *
 * This locks on the action itself rather than a fixed time window: a flat debounce (e.g. 400ms)
 * can elapse before the previous action has actually taken effect under load, letting a second
 * tap fire while the first is still settling and corrupting whatever state it touches (nav back
 * stack, dialog visibility, overlay state). Instead, [settledKeys] should be the state(s) that
 * change once the guarded action has actually completed (e.g. the dialog's own visibility flag,
 * or a nav controller's current route) - the lock only releases once one of them changes, however
 * long that takes. The try/catch is a defensive backstop in case the underlying call still throws
 * for an overlapping invocation.
 */
@Composable
fun rememberActionGuard(vararg settledKeys: Any?): (() -> Unit) -> Unit {
    var inFlight by remember { mutableStateOf(false) }
    LaunchedEffect(*settledKeys) {
        inFlight = false
    }
    return { action ->
        if (!inFlight) {
            inFlight = true
            try {
                action()
            } catch (e: IllegalStateException) {
                inFlight = false
            } catch (e: IllegalArgumentException) {
                inFlight = false
            }
        }
    }
}
