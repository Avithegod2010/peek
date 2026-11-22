package com.avi.peek.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VisibilityPolicy] is one rule -- `!isPrivate || revealed` -- so the useful
 * test is its truth table plus the promise that its entry points agree. Keep
 * this file small on purpose; anything larger would only restate the
 * implementation.
 *
 * Panic mode (`privateContentHidden`) is part of the same decision, not a
 * separate rule: `canShow` has an overload that takes it as a third argument,
 * so the two-argument form callers already use keeps its meaning, and
 * `panicLocksPrivate` is the whole-surface gate built on top of it.
 */
class VisibilityPolicyTest {

    @Test
    fun privateProfileStaysHiddenUntilItIsRevealed() {
        // Private profile, nothing unlocked yet: hidden.
        assertFalse(VisibilityPolicy.canShow(isPrivate = true, privateContentRevealed = false))
        // Public content does not depend on the reveal flag at all.
        assertTrue(VisibilityPolicy.canShow(isPrivate = false, privateContentRevealed = false))
        // Only a confirmed reveal shows private rows.
        assertTrue(VisibilityPolicy.canShow(isPrivate = true, privateContentRevealed = true))
    }

    @Test
    fun privateMediaRowsAreFilteredExactlyLikeTheFeedDecision() {
        for (isPrivate in listOf(false, true)) {
            for (revealed in listOf(false, true)) {
                val feed = VisibilityPolicy.canShow(isPrivate, revealed)
                val media = VisibilityPolicy.filterMedia(isPrivate, revealed)
                assertTrue(
                    "filterMedia disagreed with canShow for isPrivate=$isPrivate revealed=$revealed",
                    feed == media,
                )
                if (isPrivate && !revealed) {
                    assertFalse("private media leaked while locked", media)
                }
            }
        }
    }

    @Test
    fun panicModeGatesPrivateRowsAndNothingElse() {
        // The one locked cell of the eight: private, un-revealed, panic on.
        assertFalse(
            VisibilityPolicy.canShow(
                isPrivate = true,
                privateContentRevealed = false,
                privateContentHidden = true,
            ),
        )
        for (isPrivate in listOf(false, true)) {
            for (revealed in listOf(false, true)) {
                val label = "isPrivate=$isPrivate revealed=$revealed"
                // Panic mode on reduces to the strict two-argument rule.
                assertEquals(
                    "panic mode on must match the two-argument rule ($label)",
                    VisibilityPolicy.canShow(isPrivate, revealed),
                    VisibilityPolicy.canShow(isPrivate, revealed, privateContentHidden = true),
                )
                // Panic mode off only ever adds visibility, and never removes it.
                assertTrue(
                    "panic mode off leaves every row visible ($label)",
                    VisibilityPolicy.canShow(isPrivate, revealed, privateContentHidden = false),
                )
            }
        }
    }

    @Test
    fun thePanicGateAgreesWithThePerItemDecision() {
        for (hidden in listOf(false, true)) {
            for (revealed in listOf(false, true)) {
                val gate = VisibilityPolicy.panicLocksPrivate(hidden, revealed)
                assertEquals(
                    "gate disagreed with canShow for hidden=$hidden revealed=$revealed",
                    !VisibilityPolicy.canShow(
                        isPrivate = true,
                        privateContentRevealed = revealed,
                        privateContentHidden = hidden,
                    ),
                    gate,
                )
                assertEquals("gate is hidden && !revealed", hidden && !revealed, gate)
            }
        }
    }

    @Test
    fun privateFeedGatePassesThroughTheRevealFlag() {
        // A gate, not a rule: the caller decides the flag, the policy only
        // refuses to invent one.
        assertFalse(VisibilityPolicy.privateFeedVisible(false))
        assertTrue(VisibilityPolicy.privateFeedVisible(true))
    }
}
