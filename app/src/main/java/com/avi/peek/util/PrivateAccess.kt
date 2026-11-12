package com.avi.peek.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-only switch for private content. The persistent "hide private
 * content" setting is separate; revealing here lasts until the process dies or
 * the user hides private content again.
 */
object PrivateAccess {
    private val _revealed = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    fun reveal() {
        _revealed.value = true
    }

    fun hide() {
        _revealed.value = false
    }

    fun isRevealed(): Boolean = _revealed.value
}
