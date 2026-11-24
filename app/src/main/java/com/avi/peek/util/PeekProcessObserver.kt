package com.avi.peek.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Process-wide background hook. Every activity is stopped here — screen off,
 * home, recents, another app — so this is the one place that revokes the
 * process-scoped unlocks: revealed private content is re-sealed and the app
 * lock is re-armed.
 *
 * Until now only [PrivateAccess.hide] ran on stop, so satisfying the app lock
 * once was enough for the rest of the process's life: reopening Peek from
 * recents hours later showed follows, saves and downloads with no prompt, even
 * though the lock screen promises otherwise.
 */
class PeekProcessObserver(
    private val onBackground: () -> Unit = {
        PrivateAccess.hide()
        AppLockState.lockNow()
    },
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        onBackground()
    }
}
