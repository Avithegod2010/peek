package com.avi.peek.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class MediaChild(
    val index: Int = 0,
    val mediaType: Int = 1,
    val imageUrl: String? = null,
    val imageUrlLow: String? = null,
    val videoUrl: String? = null,
    val videoUrlLow: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val accessibilityCaption: String? = null,
)

@Serializable
data class BioLink(
    val title: String,
    val url: String,
)

@Serializable
data class Highlight(
    val id: String,
    val title: String,
    val coverUrl: String?,
)

data class ProfileNode(
    val code: String,
    val pk: String,
    val mediaType: Int,
    val productType: String?,
    val caption: String?,
    val displayUrl: String?,
    val accessibilityCaption: String?,
    val carouselCount: Int,
)

data class ScrapedProfile(
    val pk: String,
    val userId: String?,
    val username: String,
    val fullName: String,
    val biography: String,
    val profilePicUrl: String?,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val followerCount: Long,
    val followingCount: Long,
    val postCount: Long = 0L,
    val bioLinks: List<BioLink>,
    val highlights: List<Highlight>,
    val latestReelMedia: Long?,
    val hasClips: Boolean,
    val media: List<ProfileNode>,
    val hasNextPage: Boolean,
    val endCursor: String?,
    val relatedProfiles: List<RelatedProfile> = emptyList(),
)

data class ProfilePostsPage(
    val nodes: List<ProfileNode>,
    val hasNextPage: Boolean,
    val endCursor: String?,
)

data class RelatedProfile(
    val username: String,
    val fullName: String,
    val profilePicUrl: String?,
    val isVerified: Boolean,
)

data class ViewerIdentity(
    val username: String?,
    val userId: String?,
)

data class ScrapedPost(
    val code: String,
    val pk: String?,
    val username: String,
    val userPk: String?,
    val userAvatar: String?,
    val userVerified: Boolean,
    val mediaType: Int,
    val productType: String?,
    val caption: String?,
    val takenAt: Long?,
    val likeCount: Long?,
    val commentCount: Long?,
    val locationName: String?,
    val audioTitle: String?,
    val audioArtist: String?,
    val children: List<MediaChild>,
    val accessibilityCaption: String?,
    val width: Int?,
    val height: Int?,
)

enum class GuestErrorKind {
    NETWORK,
    NOT_FOUND,
    RATE_LIMITED,
    LOGIN_REQUIRED,
    BLOCKED,
    PARSE,
    SERVER,
    UNKNOWN,
}

class GuestException(
    val kind: GuestErrorKind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
