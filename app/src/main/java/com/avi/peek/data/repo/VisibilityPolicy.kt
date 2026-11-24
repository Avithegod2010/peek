package com.avi.peek.data.repo

/**
 * Single rule for whether private cached content may be shown. Keeping it in
 * one place avoids a screen accidentally filtering private rows differently
 * from the feed, bookmarks or search results.
 */
object VisibilityPolicy {
    fun canShow(isPrivate: Boolean, privateContentRevealed: Boolean): Boolean =
        !isPrivate || privateContentRevealed

    fun privateFeedVisible(visible: Boolean): Boolean = visible
}
