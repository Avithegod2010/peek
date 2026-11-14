package com.avi.peek.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-only switch for private content. The persistent "hide private
 * content" setting is separate; revealing here lasts until the process dies,
 * the user hides private content again, or the auto-lock timer expires.
 */
object PrivateAccess {
    private val _revealed = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lockJob: Job? = null

    @Volatile
    private var autoLockMillis: Long = DEFAULT_AUTO_LOCK_MINUTES * 60_000L

    fun setAutoLockMinutes(minutes: Int) {
        autoLockMillis = minutes.coerceIn(0, 60) * 60_000L
        if (_revealed.value && autoLockMillis == 0L) {
            lockJob?.cancel()
        }
    }

    fun reveal() {
        _revealed.value = true
        lockJob?.cancel()
        val timeout = autoLockMillis
        if (timeout > 0L) {
            lockJob = scope.launch {
                delay(timeout)
                hide()
            }
        }
    }

    fun hide() {
        lockJob?.cancel()
        lockJob = null
        _revealed.value = false
    }

    fun isRevealed(): Boolean = _revealed.value

    private const val DEFAULT_AUTO_LOCK_MINUTES = 2
}
