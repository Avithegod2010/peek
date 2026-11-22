package com.avi.peek.data.repo

/**
 * Single rule for whether private cached content may be shown. Keeping it in
 * one place avoids a screen accidentally filtering private rows differently
 * from the feed, bookmarks or search results.
 *
 * Panic mode (`privateContentHidden`) is part of that same decision rather than
 * a second rule each caller re-derives: private rows stay locked whenever panic
 * mode is on and nothing has been revealed yet.
 */
object VisibilityPolicy {
    /**
     * Strict rule: private rows need a reveal. This is the form for callers that
     * have already collapsed the settings and reveal flags into one boolean.
     */
    fun canShow(isPrivate: Boolean, privateContentRevealed: Boolean): Boolean =
        !isPrivate || privateContentRevealed

    /**
     * The rule the app applies: private rows are visible by default, panic mode
     * (`privateContentHidden`) takes that visibility away, and a reveal lifts it
     * for the session. Exactly one of the eight combinations is locked -- private,
     * un-revealed, panic mode on -- and with panic mode on this is the strict
     * two-argument rule.
     */
    fun canShow(
        isPrivate: Boolean,
        privateContentRevealed: Boolean,
        privateContentHidden: Boolean,
    ): Boolean = !isPrivate || privateContentRevealed || !privateContentHidden

    /**
     * The gate a whole private surface switches on -- a private feed, a recap, a
     * single private post. Closed only while panic mode is on and nothing has
     * been revealed.
     */
    fun panicLocksPrivate(privateContentHidden: Boolean, privateContentRevealed: Boolean): Boolean =
        !canShow(
            isPrivate = true,
            privateContentRevealed = privateContentRevealed,
            privateContentHidden = privateContentHidden,
        )

    fun filterMedia(isPrivate: Boolean, visible: Boolean): Boolean =
        canShow(isPrivate, visible)

    fun privateFeedVisible(visible: Boolean): Boolean = visible
}
