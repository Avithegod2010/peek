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

data class NewPostNotice(
    val username: String,
    val count: Int,
    val latestCode: String,
)

data class StarterAccount(
    val username: String,
    val label: String,
    val category: String,
)

val STARTER_PACK = listOf(
    StarterAccount("nasa", "NASA", "Space"),
    StarterAccount("natgeo", "National Geographic", "Nature"),
    StarterAccount("bbcearth", "BBC Earth", "Nature"),
    StarterAccount("nasawebb", "NASA Webb", "Space"),
    StarterAccount("google", "Google", "Tech"),
    StarterAccount("netflix", "Netflix", "Film"),
    StarterAccount("f1", "Formula 1", "Sport"),
    StarterAccount("louvre", "Louvre Museum", "Art"),
    StarterAccount("voguemagazine", "Vogue", "Fashion"),
    StarterAccount("spotify", "Spotify", "Music"),
    StarterAccount("unitednations", "United Nations", "World"),
    StarterAccount("instagram", "Instagram", "Product"),
)
