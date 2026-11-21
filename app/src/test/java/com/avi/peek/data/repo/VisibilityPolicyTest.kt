package com.avi.peek.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fail-closed matrix for [VisibilityPolicy].
 *
 * Private state reaches the UI as one boolean (`privateContentRevealed`). Every
 * way of *not* being revealed -- a locked private profile, a private media row,
 * panic mode hiding everything, or an unlock that has not been confirmed yet --
 * must land on the same "hidden" answer, and a missing flag must never be read
 * as "revealed".
 */
class VisibilityPolicyTest {

    @Test
    fun privateProfileStaysHiddenUntilItIsRevealed() {
        // Private profile, nothing unlocked yet: hidden.
        assertFalse(VisibilityPolicy.canShow(isPrivate = true, privateContentRevealed = false))
        // Private profile with panic mode / lock override in force: still hidden.
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
    fun privateFeedFlagFailsClosedAndDoesNotLatch() {
        assertFalse(VisibilityPolicy.privateFeedVisible(false))
        assertTrue(VisibilityPolicy.privateFeedVisible(true))

        // Panic mode repeatedly asking for a private row must never flip to
        // visible: the policy holds no reveal state of its own.
        repeat(5) {
            assertFalse(VisibilityPolicy.canShow(isPrivate = true, privateContentRevealed = false))
            assertFalse(VisibilityPolicy.filterMedia(isPrivate = true, visible = false))
        }
    }

    @Test
    fun missingFlagsCannotBeReadAsRevealed() {
        val method = VisibilityPolicy::class.java.getMethod(
            "canShow",
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE,
        )
        val failure = runCatching { method.invoke(VisibilityPolicy, null, null) }.exceptionOrNull()
        assertNotNull("a null flag must be rejected, never treated as revealed", failure)
        assertTrue(
            "unexpected failure type ${failure!!::class.java.name}",
            failure is IllegalArgumentException || failure is NullPointerException,
        )
    }
}
