package com.avi.peek.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-only record of whether the app lock has been satisfied.
 *
 * It lives outside the lock composable so the process lifecycle can revoke an
 * unlock: [com.avi.peek.ui.AppLockGate] observes this flow, so clearing it while
 * Peek is in the background re-renders the lock screen instead of leaving the
 * last composition on screen. Nothing is persisted, so a cold start is locked.
 */
object AppLockState {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    /** Forget the unlock; the next foreground shows the lock screen again. */
    fun lockNow() {
        _unlocked.value = false
    }
}
