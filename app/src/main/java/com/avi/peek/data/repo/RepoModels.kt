package com.avi.peek.data.repo

import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.ProfileEntity

data class FeedPost(
    val media: MediaEntity,
    val profile: ProfileEntity,
)

data class SyncState(
    val running: Boolean = false,
    val label: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val newPosts: Int = 0,
    val errorCount: Int = 0,
    val finishedAt: Long = 0L,
)

sealed interface SyncEvent {
    data class Completed(val newPosts: Int, val errors: Int) : SyncEvent
    data class Failed(val message: String) : SyncEvent
    data object NoConnection : SyncEvent
}
