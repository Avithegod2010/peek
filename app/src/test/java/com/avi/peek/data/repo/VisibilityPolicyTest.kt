package com.avi.peek.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VisibilityPolicy] is one rule -- `!isPrivate || revealed` -- so the useful
 * test is its truth table plus the promise that its entry points agree. Keep
 * this file small on purpose; anything larger would only restate the
 * implementation.
 *
 * Panic mode (`privateContentHidden`) is deliberately NOT owned by this policy
 * today: it is applied in the repository query and recomputed independently in
 * `PostScreen`, `PrivateFeedScreen`, `ProfileViewModel` and `ExtraScreens`.
 * Cases here must not assume otherwise until that is unified.
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
    fun privateFeedGatePassesThroughTheRevealFlag() {
        // A gate, not a rule: the caller decides the flag, the policy only
        // refuses to invent one.
        assertFalse(VisibilityPolicy.privateFeedVisible(false))
        assertTrue(VisibilityPolicy.privateFeedVisible(true))
    }
}
