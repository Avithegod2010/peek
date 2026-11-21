package com.avi.peek.data.repo

import android.app.Application
import android.content.Context
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GuestErrorKind
import com.avi.peek.data.remote.GuestException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * Budget arithmetic for [RequestBudget], on the JVM via Robolectric's real
 * DataStore-backed storage.
 *
 * The class reads `System.currentTimeMillis()` directly and offers no clock
 * injection, so wall-clock timestamps are bracketed between two samples taken in
 * the test (`before` / `after`) instead of being faked. Everything asserted here
 * is therefore exact: the derived deadline has to fall inside that bracket.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RequestBudgetTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val settings = SettingsStore(context)
    private val budget = RequestBudget(context, settings)

    private val minute = 60_000L

    @Before
    fun resetBudget() = runBlocking {
        // The DataStore instance is process-wide, so each test starts from a
        // known budget rather than whatever the previous test left behind.
        budget.clearPause()
        budget.resetWindow()
        settings.setRequestBudgetPerHour(DEFAULT_BUDGET)
    }

    @Test
    fun pauseFlagTurnsOverExactlyAtTheDeadline() {
        val now = System.currentTimeMillis()
        assertFalse("no pause recorded", RequestBudgetState(pauseUntil = 0L).paused)
        assertFalse("deadline already reached", RequestBudgetState(pauseUntil = now).paused)
        assertFalse("deadline in the past", RequestBudgetState(pauseUntil = now - 1).paused)
        assertTrue("deadline one tick away", RequestBudgetState(pauseUntil = now + 1).paused)
        assertTrue(RequestBudgetState(pauseUntil = now + minute).paused)
    }

    @Test
    fun rateLimitBackoffEscalatesThenStopsAtTheCap() {
        val expectedMinutes = listOf(5L, 15L, 60L, 360L, 360L)
        val expectedLevels = listOf(1, 2, 3, 4, 4)
        runBlocking {
            expectedMinutes.forEachIndexed { index, waitMinutes ->
                val before = System.currentTimeMillis()
                budget.recordRateLimit("429 attempt ${index + 1}")
                val after = System.currentTimeMillis()
                val state = budget.snapshot()

                assertEquals("backoff level after hit ${index + 1}", expectedLevels[index], state.backoffLevel)
                assertTrue(state.pauseUntil >= before + waitMinutes * minute)
                assertTrue(
                    "hit ${index + 1} paused for ${(state.pauseUntil - before) / minute} min, wanted $waitMinutes",
                    state.pauseUntil <= after + waitMinutes * minute,
                )
                assertTrue("hit ${index + 1} must pause further requests", state.paused)
                assertEquals("429 attempt ${index + 1}", state.lastError)
            }
        }
    }

    @Test
    fun acquireIsRejectedWhilePausedAndRecoversAfterThePauseIsCleared() {
        runBlocking {
            budget.recordRateLimit("slow down")

            val rejected = runCatching { budget.acquire() }.exceptionOrNull()
            assertTrue("expected a GuestException, got $rejected", rejected is GuestException)
            assertEquals(GuestErrorKind.RATE_LIMITED, (rejected as GuestException).kind)
            assertTrue(
                "the message should tell the user how long the pause lasts: ${rejected.message}",
                rejected.message.orEmpty().contains("pausing requests"),
            )

            budget.clearPause()
            val before = System.currentTimeMillis()
            budget.acquire()
            val after = System.currentTimeMillis()

            val state = budget.snapshot()
            assertFalse(state.paused)
            assertEquals(0, state.backoffLevel)
            assertNull(state.lastError)
            assertEquals(1, state.usedThisHour)
            assertTrue("window opened at the first request", state.windowStartedAt in before..after)
            assertEquals(state.windowStartedAt + 60 * minute, state.windowResetsAt)
            assertEquals(0L, state.challengeUntil)
        }
    }

    @Test
    fun hourlyBudgetStopsAcquireAtTheCapAndStartsCountdown() {
        runBlocking {
            settings.setRequestBudgetPerHour(HOURLY_CAP)
        }
        val used = runBlocking {
            repeat(HOURLY_CAP) { budget.acquire() }
            budget.snapshot()
        }
        assertEquals(HOURLY_CAP, used.usedThisHour)
        assertEquals(0, used.backoffLevel)

        val rejected = runBlocking { runCatching { budget.acquire() }.exceptionOrNull() }
        assertTrue("the cap must reject, got $rejected", rejected is GuestException)
        assertEquals(GuestErrorKind.RATE_LIMITED, (rejected as GuestException).kind)
        assertTrue(
            "the message should name the hourly budget: ${rejected.message}",
            rejected.message.orEmpty().contains("hourly request budget ($HOURLY_CAP)"),
        )
        // A rejected request does not consume budget or open a new window.
        val afterRejection = runBlocking { budget.snapshot() }
        assertEquals(HOURLY_CAP, afterRejection.usedThisHour)
        assertEquals(used.windowStartedAt, afterRejection.windowStartedAt)
    }

    @Test
    fun quietHoursWrapPastMidnightAndEqualHoursMeanDisabled() {
        assertFalse("start == end is not a quiet window", budget.inQuietHours(5, 5))
        assertFalse(budget.inQuietHours(0, 0))

        val hour = LocalTime.now().hour
        // A two-to-three hour window well away from the current hour is outside it.
        val outsideStart = (hour + 4) % 24
        val outsideEnd = (hour + 5) % 24
        assertFalse(
            "hour $hour should not be inside [$outsideStart, $outsideEnd)",
            budget.inQuietHours(outsideStart, outsideEnd),
        )

        // A window that straddles the current hour does contain it, including
        // the midnight wrap (start > end).
        val insideStart = (hour + 23) % 24
        val insideEnd = (hour + 2) % 24
        assertTrue(
            "hour $hour should be inside [$insideStart, $insideEnd)",
            budget.inQuietHours(insideStart, insideEnd),
        )
    }

    private companion object {
        const val DEFAULT_BUDGET = 120
        const val HOURLY_CAP = 20
    }
}
