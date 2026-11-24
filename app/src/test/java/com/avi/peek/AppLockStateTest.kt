package com.avi.peek

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.avi.peek.util.AppLockState
import com.avi.peek.util.PeekProcessObserver
import com.avi.peek.util.PrivateAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app lock is a promise about the person holding the phone, so it has to
 * end when Peek leaves the screen — a process-scoped unlock that survives
 * backgrounding means the lock only ever fires on a cold start.
 */
class AppLockStateTest {

    private class StubOwner : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = throw UnsupportedOperationException("the observer must not touch the owner")
    }

    @Before
    fun reset() {
        AppLockState.lockNow()
    }

    @Test
    fun unlockStartsLockedAndCanBeRevoked() {
        assertFalse("a fresh process must be locked", AppLockState.unlocked.value)
        AppLockState.unlock()
        assertTrue(AppLockState.unlocked.value)
        AppLockState.lockNow()
        assertFalse(AppLockState.unlocked.value)
    }

    @Test
    fun processStopRevokesTheAppLock() {
        AppLockState.unlock()
        PeekProcessObserver().onStop(StubOwner())
        assertFalse("backgrounding must re-arm the app lock", AppLockState.unlocked.value)
    }

    @Test
    fun processStopAlsoHidesRevealedPrivateContent() {
        PeekProcessObserver().onStop(StubOwner())
        assertFalse(PrivateAccess.isRevealed())
    }
}
