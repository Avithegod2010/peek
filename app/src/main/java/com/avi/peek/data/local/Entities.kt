package com.avi.peek.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.avi.peek.data.remote.BioLink
import com.avi.peek.data.remote.Highlight
import com.avi.peek.data.remote.MediaChild
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val localJson = Json { ignoreUnknownKeys = true }

@Entity(
    tableName = "profiles",
    indices = [
        Index("isFollowed"),
        Index("lastFetchedAt"),
        Index("lastViewedAt"),
        Index("isFollowed", "isPinned", "latestReelMedia"),
    ],
)
data class ProfileEntity(
    @PrimaryKey val username: String,
    val pk: String? = null,
    val userId: String? = null,
    val fullName: String = "",
    val biography: String = "",
    val profilePicUrl: String? = null,
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val postCount: Long = 0,
    val hasMorePosts: Boolean = false,
    val nextCursor: String? = null,
    val bioLinksJson: String = "[]",
    val highlightsJson: String = "[]",
    val hasClips: Boolean = false,
    val latestReelMedia: Long? = null,
    val isFollowed: Boolean = false,
    val followedAt: Long? = null,
    val isPinned: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastFetchedAt: Long = 0,
    val lastViewedAt: Long? = null,
    val lastError: String? = null,
    val source: String = "manual",
) {
    fun bioLinks(): List<BioLink> = runCatching {
        localJson.decodeFromString(ListSerializer(BioLink.serializer()), bioLinksJson)
    }.getOrDefault(emptyList())

    fun highlights(): List<Highlight> = runCatching {
        localJson.decodeFromString(ListSerializer(Highlight.serializer()), highlightsJson)
    }.getOrDefault(emptyList())
}

@Entity(
    tableName = "media",
    indices = [
        Index("username"),
        Index("takenAt"),
        Index("isSaved"),
        Index("isDownloaded"),
        Index("isPrivate"),
        Index("username", "takenAt"),
        Index("isLiked"),
        Index("downloadedAt"),
        Index("isSaved", "savedAt"),
        Index("isPrivate", "takenAt"),
        Index("audioTitle"),
        Index("productType"),
    ],
)
data class MediaEntity(
    @PrimaryKey val code: String,
    val pk: String? = null,
    val username: String,
    val userAvatar: String? = null,
    val userFullName: String = "",
    val userVerified: Boolean = false,
    val isPrivate: Boolean = false,
    val mediaType: Int = 1,
    val productType: String? = null,
    val caption: String? = null,
    val displayUrl: String? = null,
    val videoUrl: String? = null,
    val likeCount: Long? = null,
    val commentCount: Long? = null,
    val takenAt: Long? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
    val detailFetchedAt: Long? = null,
    val carouselCount: Int = 0,
    val childrenJson: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val accessibilityCaption: String? = null,
    val locationName: String? = null,
    val audioTitle: String? = null,
    val audioArtist: String? = null,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val savedAt: Long? = null,
    val isDownloaded: Boolean = false,
    val downloadedAt: Long? = null,
    val localImagePathsJson: String? = null,
    val localVideoPath: String? = null,
    val sortIndex: Int = 0,
) {
    val isVideo: Boolean get() = mediaType == 2
    val isReel: Boolean get() = productType == "clips" || productType == "reels"
    val isCarousel: Boolean get() = mediaType == 8 || carouselCount > 1

    fun children(): List<MediaChild> = runCatching {
        childrenJson?.let {
            localJson.decodeFromString(ListSerializer(MediaChild.serializer()), it)
        }
    }.getOrNull().orEmpty()

    fun localImages(): List<String> = runCatching {
        localImagePathsJson?.let {
            localJson.decodeFromString(ListSerializer(String.serializer()), it)
        }
    }.getOrNull().orEmpty()

    val displayThumb: String? get() = displayUrl
}

@Entity(
    tableName = "comments",
    primaryKeys = ["pk"],
    indices = [Index("mediaCode"), Index("parentPk")],
)
data class CommentEntity(
    val pk: String,
    val mediaCode: String,
    val parentPk: String? = null,
    val username: String,
    val avatarUrl: String? = null,
    val text: String,
    val createdAt: Long? = null,
    val likeCount: Long = 0L,
    val verified: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "searches", indices = [Index("searchedAt")])
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

data class ProfileWithMedia(
    val profile: ProfileEntity,
    val media: List<MediaEntity>,
)
