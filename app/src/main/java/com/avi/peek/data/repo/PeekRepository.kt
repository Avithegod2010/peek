package com.avi.peek.data.repo

import android.content.Context
import com.avi.peek.data.local.CommentEntity
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.local.ProfileWithMedia
import com.avi.peek.data.local.SecureCommentDao
import com.avi.peek.data.local.SecureMediaDao
import com.avi.peek.data.local.SecureProfileDao
import com.avi.peek.data.local.SecureSearchDao
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GuestErrorKind
import com.avi.peek.data.remote.GuestException
import com.avi.peek.data.remote.CommentPage
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.remote.MediaChild
import com.avi.peek.data.remote.ScrapedComment
import com.avi.peek.data.remote.ScrapedPost
import com.avi.peek.data.remote.ScrapedProfile
import com.avi.peek.util.NetworkMonitor
import com.avi.peek.util.PrivateAccess
import com.avi.peek.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

data class CacheResult(
    val profile: ProfileEntity,
    val newCodes: List<String>,
)

data class DownloadProgress(
    val code: String,
    val fraction: Float,
    val label: String,
)

class PeekRepository(
    private val context: Context,
    private val db: PeekDatabase,
    private val client: GuestInstagramClient,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val downloads: DownloadStore,
    private val gallery: GallerySaver = GallerySaver(context),
    private val requestLog: RequestLog = RequestLog(context),
    private val accountManager: AccountManager,
    private val requestBudget: RequestBudget,
    private val vault: VaultStore,
) {

    private val secureMedia = SecureMediaDao(db.media())
    private val secureProfiles = SecureProfileDao(db.profiles())
    private val secureComments = SecureCommentDao(db.comments())
    private val secureSearches = SecureSearchDao(db.searches())
    private val json = Json { ignoreUnknownKeys = true }
    private val childSerializer = ListSerializer(MediaChild.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())
    private val syncMutex = Mutex()

    private suspend fun <T> budgeted(
        background: Boolean = false,
        block: suspend () -> T,
    ): T {
        requestBudget.acquire(background)
        return try {
            block().also {
                requestBudget.recordSuccess()
                accountManager.markSuccess()
            }
        } catch (e: GuestException) {
            if (e.kind == GuestErrorKind.RATE_LIMITED) {
                requestBudget.recordRateLimit(e.userMessage())
            }
            if (e.kind == GuestErrorKind.LOGIN_REQUIRED || e.kind == GuestErrorKind.BLOCKED) {
                accountManager.markExpired(e.userMessage())
                if (e.kind == GuestErrorKind.LOGIN_REQUIRED) {
                    requestBudget.recordChallenge(e.userMessage())
                }
            }
            throw e
        }
    }

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val privateVisible: Flow<Boolean> = combine(
        settings.settings.map { !it.privateContentHidden }.distinctUntilChanged(),
        PrivateAccess.revealed,
    ) { notHidden, revealed -> notHidden || revealed }.distinctUntilChanged()

    private fun Flow<List<MediaEntity>>.visibleOnly(): Flow<List<MediaEntity>> =
        combine(this, privateVisible) { items, visible ->
            items.filter { VisibilityPolicy.canShow(it.isPrivate, visible) }
        }

    val following: Flow<List<ProfileEntity>> = secureProfiles.observeFollowing()
    val followingCount: Flow<Int> = secureProfiles.observeFollowingCount()
    val savedCount: Flow<Int> = secureMedia.observeSavedCount()
    val downloadCount: Flow<Int> = secureMedia.observeDownloadCount()
    val mediaCount: Flow<Int> = secureMedia.observeMediaCount()
    val saved: Flow<List<MediaEntity>> = secureMedia.observeSaved().visibleOnly()
    val downloaded: Flow<List<MediaEntity>> = secureMedia.observeDownloads().visibleOnly()
    val liked: Flow<List<MediaEntity>> = secureMedia.observeLiked().visibleOnly()
    val privateFeed: Flow<List<MediaEntity>> = combine(
        secureMedia.observePrivateFeed(),
        privateVisible,
    ) { items, visible ->
        if (VisibilityPolicy.privateFeedVisible(visible)) items else emptyList()
    }
    val recentlyViewed: Flow<List<ProfileEntity>> = secureProfiles.observeRecentlyViewed()
    val recentSearches = secureSearches.observeRecent()

    fun mediaByCodes(codes: List<String>): Flow<List<MediaEntity>> =
        secureMedia.observeByCodes(codes).visibleOnly()

    fun mediaForUsernames(usernames: List<String>): Flow<List<MediaEntity>> =
        secureMedia.observeForUsernames(usernames).visibleOnly()

    suspend fun profileMedia(username: String): List<MediaEntity> = withContext(Dispatchers.IO) {
        secureMedia.forProfile(username)
    }

    suspend fun mediaByCodesOnce(codes: List<String>): List<MediaEntity> = withContext(Dispatchers.IO) {
        if (codes.isEmpty()) emptyList() else secureMedia.byCodes(codes)
    }

    val feed: Flow<List<FeedPost>> = combine(
        secureMedia.observeFeed().visibleOnly(),
        secureProfiles.observeFollowing(),
    ) { media, profiles ->
        val byName = profiles.associateBy { it.username }
        media.mapNotNull { item -> byName[item.username]?.let { FeedPost(item, it) } }
    }

    val settingsFlow = settings.settings

    fun observeProfile(username: String): Flow<ProfileWithMedia?> = combine(
        secureProfiles.observe(username),
        secureMedia.observeForProfile(username),
    ) { profile, media ->
        profile?.let { ProfileWithMedia(it, media) }
    }

    fun observeMedia(code: String): Flow<MediaEntity?> = secureMedia.observe(code)

    fun observeAudio(title: String): Flow<List<MediaEntity>> =
        secureMedia.observeAudio(title).visibleOnly()

    fun savedSince(since: Long): Flow<Int> = secureMedia.observeSavedSince(since)
    fun downloadedSince(since: Long): Flow<Int> = secureMedia.observeDownloadedSince(since)
    fun viewedSince(since: Long): Flow<List<ProfileEntity>> = secureProfiles.observeViewedSince(since)

    suspend fun searchCachedProfiles(query: String): List<ProfileEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else secureProfiles.searchCached(query.trim())
    }

    suspend fun searchCachedMedia(query: String): List<MediaEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val visible = privateVisible.first()
        secureMedia.searchCached(query.trim())
            .filter { VisibilityPolicy.canShow(it.isPrivate, visible) }
    }

    fun extractPostShortcode(raw: String): String? {
        val value = raw.trim()
        val urlMatch = Regex(
            """(?i)(?:instagram\.com)?/(?:p|reel|reels|tv)/([A-Za-z0-9_-]{5,})""",
        ).find(value)
        if (urlMatch != null) return urlMatch.groupValues.getOrNull(1)
        return value.takeIf {
            it.matches(Regex("""[A-Za-z0-9_-]{5,20}""")) && !it.contains(' ')
        }
    }

    /** Resolves a public post/reel URL (or shortcode) to its author profile. */
    suspend fun resolvePostAuthor(raw: String): ProfileEntity? = withContext(Dispatchers.IO) {
        val code = extractPostShortcode(raw) ?: return@withContext null
        val post = runCatching { budgeted { client.fetchPost(code) } }.getOrNull()
            ?: return@withContext null
        val username = post.username.takeIf { it.isNotBlank() } ?: return@withContext null
        prefetchDirectoryProfile(username)
    }

    suspend fun fetchHighlightMedia(highlightId: String): List<MediaChild> = withContext(Dispatchers.IO) {
        accountManager.ensureRestored()
        budgeted { client.fetchHighlightMedia(highlightId) }
    }

    suspend fun fetchStories(username: String): List<MediaChild> = withContext(Dispatchers.IO) {
        accountManager.ensureRestored()
        val profile = secureProfiles.get(username)
            ?: throw GuestException(GuestErrorKind.NOT_FOUND, "Open the profile first")
        val userId = profile.userId ?: profile.pk
            ?: throw GuestException(GuestErrorKind.PARSE, "This profile has no story id")
        budgeted { client.fetchStories(userId) }
    }

    suspend fun cachedComments(code: String): CommentPage? = withContext(Dispatchers.IO) {
        val rows = secureComments.forMedia(code)
        if (rows.isEmpty()) null else {
            val replies = rows.filter { it.parentPk != null }.groupBy { it.parentPk }
            val roots = rows.filter { it.parentPk == null }.map { row ->
                row.toScrapedComment(
                    replies[row.pk].orEmpty().map { it.toScrapedComment(emptyList()) },
                )
            }
            CommentPage(roots, null)
        }
    }

    suspend fun loadComments(
        code: String,
        after: String? = null,
        force: Boolean = false,
    ): CommentPage = withContext(Dispatchers.IO) {
        accountManager.ensureRestored()
        val media = secureMedia.get(code)
            ?: throw GuestException(GuestErrorKind.NOT_FOUND, "Open the post first")
        val pk = media.pk ?: loadMediaDetail(code)?.pk
            ?: throw GuestException(GuestErrorKind.PARSE, "This post has no comments id")
        val cached = if (after == null) cachedComments(code) else null
        if (cached != null && !force) return@withContext cached
        try {
            val page = budgeted { client.fetchComments(pk, after) }
            val rows = page.comments.flatMap { comment ->
                listOf(comment.toEntity(code)) + comment.replies.map { it.toEntity(code) }
            }
            if (after == null) secureComments.clearForMedia(code)
            secureComments.upsertAll(rows)
            page
        } catch (e: GuestException) {
            if (cached != null) cached else throw e
        } catch (e: Exception) {
            if (cached != null) cached else throw e
        }
    }

    private fun ScrapedComment.toEntity(mediaCode: String) = CommentEntity(
        pk = pk,
        mediaCode = mediaCode,
        parentPk = parentPk,
        username = username,
        avatarUrl = avatarUrl,
        text = text,
        createdAt = createdAt,
        likeCount = likeCount,
        verified = verified,
    )

    private fun CommentEntity.toScrapedComment(replies: List<ScrapedComment>) = ScrapedComment(
        pk = pk,
        parentPk = parentPk,
        username = username,
        avatarUrl = avatarUrl,
        text = text,
        createdAt = createdAt,
        likeCount = likeCount,
        verified = verified,
        replies = replies,
    )

    suspend fun lookup(raw: String): ScrapedProfile {
        val username = GuestInstagramClient.normalizeUsername(raw)
            ?: throw GuestException(GuestErrorKind.NOT_FOUND, "Enter a valid Instagram username or link")
        accountManager.ensureRestored()
        return budgeted { client.fetchProfile(username) }
    }

    suspend fun follow(rawOrUsername: String): CacheResult {
        val username = GuestInstagramClient.normalizeUsername(rawOrUsername)
            ?: throw GuestException(GuestErrorKind.NOT_FOUND, "Enter a valid Instagram username or link")
        accountManager.ensureRestored()
        val scraped = budgeted { client.fetchProfile(username) }
        return cacheAndFollow(scraped)
    }

    suspend fun followScraped(scraped: ScrapedProfile): CacheResult = cacheAndFollow(scraped)

    /**
     * Loads a profile for the Explore directory/search without following it.
     * The profile (and its first page of media) is cached so opening it later is instant.
     */
    suspend fun prefetchDirectoryProfile(raw: String): ProfileEntity? = withContext(Dispatchers.IO) {
        accountManager.ensureRestored()
        val username = GuestInstagramClient.normalizeUsername(raw) ?: return@withContext null
        val cached = secureProfiles.get(username)
        if (cached != null && cached.pk != null &&
            System.currentTimeMillis() - cached.lastFetchedAt < 12 * 60 * 60 * 1000
        ) {
            return@withContext cached
        }
        runCatching {
            val scraped = budgeted { client.fetchProfile(username) }
            cacheProfile(scraped, followed = null, followedAt = null, source = "catalog").profile
        }.onFailure {
            cached?.let { old -> secureProfiles.setError(username, it.userMessage()) }
        }.getOrNull()
    }

    /** Follows an already cached directory profile without a second network request. */
    suspend fun followCached(username: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = GuestInstagramClient.normalizeUsername(username) ?: return@withContext false
        secureProfiles.get(normalized) ?: return@withContext false
        secureProfiles.setFollowed(normalized, true, System.currentTimeMillis())
        settings.setLastFullSync(System.currentTimeMillis())
        true
    }

    suspend fun cachedProfile(username: String): ProfileEntity? = withContext(Dispatchers.IO) {
        val normalized = GuestInstagramClient.normalizeUsername(username) ?: return@withContext null
        secureProfiles.get(normalized)
    }

    private suspend fun cacheAndFollow(scraped: ScrapedProfile): CacheResult {
        val now = System.currentTimeMillis()
        val result = cacheProfile(scraped, followed = true, followedAt = now)
        settings.setLastFullSync(System.currentTimeMillis())
        return result
    }

    suspend fun unfollow(username: String) {
        secureProfiles.setFollowed(username, false, null)
    }

    suspend fun setPinned(username: String, pinned: Boolean) {
        secureProfiles.setPinned(username, pinned)
    }

    suspend fun markViewed(username: String) {
        secureProfiles.setViewed(username, System.currentTimeMillis())
    }

    suspend fun refreshProfile(username: String): CacheResult = withContext(Dispatchers.IO) {
        accountManager.ensureRestored()
        try {
            val scraped = budgeted { client.fetchProfile(username) }
            val result = cacheProfile(scraped, followed = null, followedAt = null)
                .also { secureProfiles.setError(username, null) }
            requestLog.record("profile @$username", "ok")
            result
        } catch (e: GuestException) {
            secureProfiles.setError(username, e.userMessage())
            requestLog.record(
                "profile @$username",
                e.userMessage(),
                rateLimited = e.kind == GuestErrorKind.RATE_LIMITED,
            )
            throw e
        }
    }

    /** Appends the next page of a profile grid without dropping earlier pages. */
    suspend fun loadMoreProfilePosts(username: String): ProfilePageResult = withContext(Dispatchers.IO) {
        accountManager.ensureRestored()
        val profile = secureProfiles.get(username)
            ?: throw GuestException(GuestErrorKind.NOT_FOUND, "Open the profile first")
        val after = profile.nextCursor
        if (after.isNullOrBlank()) return@withContext ProfilePageResult(0, false)
        val userId = profile.userId ?: profile.pk
            ?: throw GuestException(GuestErrorKind.PARSE, "This profile has no public user id")

        val page = try {
            budgeted { client.fetchProfilePostsPage(username, userId, after) }.also {
                requestLog.record("older posts @$username", "${it.nodes.size} posts")
            }
        } catch (e: GuestException) {
            requestLog.record(
                "older posts @$username",
                e.userMessage(),
                rateLimited = e.kind == GuestErrorKind.RATE_LIMITED,
            )
            throw e
        }
        val existingMedia = secureMedia.forProfile(username).associateBy { it.code }
        val now = System.currentTimeMillis()
        val items = page.nodes.mapIndexedNotNull { index, node ->
            val old = existingMedia[node.code]
            MediaEntity(
                code = node.code,
                pk = node.pk,
                username = username,
                userAvatar = profile.profilePicUrl ?: old?.userAvatar,
                userFullName = profile.fullName.ifBlank { old?.userFullName.orEmpty() },
                userVerified = profile.isVerified,
                isPrivate = profile.isPrivate,
                mediaType = node.mediaType,
                productType = node.productType ?: old?.productType,
                caption = node.caption ?: old?.caption,
                displayUrl = node.displayUrl ?: old?.displayUrl,
                videoUrl = old?.videoUrl,
                likeCount = old?.likeCount,
                commentCount = old?.commentCount,
                takenAt = old?.takenAt ?: parserDate(node.accessibilityCaption),
                fetchedAt = now,
                detailFetchedAt = old?.detailFetchedAt,
                carouselCount = maxOf(node.carouselCount, old?.carouselCount ?: 0),
                childrenJson = old?.childrenJson,
                width = old?.width,
                height = old?.height,
                accessibilityCaption = node.accessibilityCaption ?: old?.accessibilityCaption,
                locationName = old?.locationName,
                audioTitle = old?.audioTitle,
                audioArtist = old?.audioArtist,
                isLiked = old?.isLiked ?: false,
                isSaved = old?.isSaved ?: false,
                savedAt = old?.savedAt,
                isDownloaded = old?.isDownloaded ?: false,
                downloadedAt = old?.downloadedAt,
                localImagePathsJson = old?.localImagePathsJson,
                localVideoPath = old?.localVideoPath,
                sortIndex = index,
            )
        }
        secureMedia.upsertAll(items)
        secureProfiles.setPagination(username, page.hasNextPage, page.endCursor)
        ProfilePageResult(items.size, page.hasNextPage)
    }

    suspend fun allProfileMediaCodes(username: String): List<String> = withContext(Dispatchers.IO) {
        secureMedia.forProfile(username).map { it.code }
    }

    suspend fun loadAllProfilePosts(
        username: String,
        maxPages: Int = 120,
        onProgress: (loaded: Int, hasMore: Boolean) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        var loaded = secureMedia.forProfile(username).size
        var pages = 0
        var profile = secureProfiles.get(username) ?: throw GuestException(GuestErrorKind.NOT_FOUND)
        if (profile.userId == null && profile.pk == null) {
            refreshProfile(username)
            profile = secureProfiles.get(username) ?: return@withContext 0
        }
        if (profile.nextCursor.isNullOrBlank()) {
            val refreshed = refreshProfile(username)
            profile = refreshed.profile
        }
        while (pages < maxPages) {
            val current = secureProfiles.get(username) ?: break
            if (!current.hasMorePosts || current.nextCursor.isNullOrBlank()) break
            val result = loadMoreProfilePosts(username)
            if (result.added == 0) break
            loaded += result.added
            pages++
            onProgress(loaded, result.hasMore)
            delay(260)
        }
        loaded
    }

    /**
     * Merges a fresh profile payload into the local cache. Local-only state
     * (follow, saves, likes, downloads) is never overwritten.
     */
    private suspend fun cacheProfile(
        scraped: ScrapedProfile,
        followed: Boolean?,
        followedAt: Long?,
        source: String? = null,
        cacheMedia: Boolean = true,
    ): CacheResult = withContext(Dispatchers.IO) {
        val username = scraped.username.lowercase()
        val existingProfile = secureProfiles.get(username)
        val existingMedia = secureMedia.forProfile(username).associateBy { it.code }
        val now = System.currentTimeMillis()

        val profile = ProfileEntity(
            username = username,
            pk = scraped.pk,
            userId = scraped.userId,
            fullName = scraped.fullName,
            biography = scraped.biography,
            profilePicUrl = scraped.profilePicUrl ?: existingProfile?.profilePicUrl,
            isPrivate = scraped.isPrivate,
            isVerified = scraped.isVerified,
            followerCount = scraped.followerCount,
            followingCount = scraped.followingCount,
            postCount = scraped.postCount,
            hasMorePosts = scraped.hasNextPage,
            nextCursor = scraped.endCursor,
            bioLinksJson = json.encodeToString(ListSerializer(com.avi.peek.data.remote.BioLink.serializer()), scraped.bioLinks),
            highlightsJson = json.encodeToString(ListSerializer(com.avi.peek.data.remote.Highlight.serializer()), scraped.highlights),
            hasClips = scraped.hasClips,
            latestReelMedia = scraped.latestReelMedia ?: existingProfile?.latestReelMedia,
            isFollowed = followed ?: existingProfile?.isFollowed ?: false,
            followedAt = followedAt ?: existingProfile?.followedAt,
            isPinned = existingProfile?.isPinned ?: false,
            addedAt = existingProfile?.addedAt ?: now,
            lastFetchedAt = now,
            lastViewedAt = existingProfile?.lastViewedAt,
            lastError = null,
            source = source ?: existingProfile?.source ?: "manual",
        )

        val items = if (!cacheMedia) emptyList() else scraped.media.mapIndexed { index, node ->
            val old = existingMedia[node.code]
            MediaEntity(
                code = node.code,
                pk = node.pk,
                username = username,
                userAvatar = scraped.profilePicUrl ?: old?.userAvatar,
                userFullName = scraped.fullName.ifBlank { old?.userFullName.orEmpty() },
                userVerified = scraped.isVerified,
                isPrivate = scraped.isPrivate,
                mediaType = node.mediaType,
                productType = node.productType ?: old?.productType,
                caption = node.caption ?: old?.caption,
                displayUrl = node.displayUrl ?: old?.displayUrl,
                videoUrl = old?.videoUrl,
                likeCount = old?.likeCount,
                commentCount = old?.commentCount,
                takenAt = old?.takenAt
                    ?: parserDate(node.accessibilityCaption)
                    ?: (scraped.latestReelMedia?.takeIf { index == 0 }),
                fetchedAt = now,
                detailFetchedAt = old?.detailFetchedAt,
                carouselCount = maxOf(node.carouselCount, old?.carouselCount ?: 0),
                childrenJson = old?.childrenJson,
                width = old?.width,
                height = old?.height,
                accessibilityCaption = node.accessibilityCaption ?: old?.accessibilityCaption,
                locationName = old?.locationName,
                audioTitle = old?.audioTitle,
                audioArtist = old?.audioArtist,
                isLiked = old?.isLiked ?: false,
                isSaved = old?.isSaved ?: false,
                savedAt = old?.savedAt,
                isDownloaded = old?.isDownloaded ?: false,
                downloadedAt = old?.downloadedAt,
                localImagePathsJson = old?.localImagePathsJson,
                localVideoPath = old?.localVideoPath,
                sortIndex = index,
            )
        }

        secureProfiles.upsert(profile)
        secureMedia.setPrivateForProfile(username, scraped.isPrivate)
        if (cacheMedia) {
            secureMedia.upsertAll(items)
            // Only prune when this profile's grid fits in one page. Profiles with
            // more pages keep their previously loaded history until the account
            // actually stops returning more posts.
            if (items.isNotEmpty() && !scraped.hasNextPage) {
                secureMedia.pruneProfile(username, items.map { it.code })
            }
        }

        val newCodes = if (existingMedia.isEmpty() || !cacheMedia) {
            emptyList()
        } else {
            scraped.media.map { it.code }.filterNot { existingMedia.containsKey(it) }
        }
        CacheResult(profile, newCodes)
    }

    private fun parserDate(caption: String?): Long? =
        com.avi.peek.data.remote.SsrParser().dateFromAccessibilityCaption(caption)

    suspend fun loadMediaDetail(code: String, force: Boolean = false): MediaEntity? =
        withContext(Dispatchers.IO) {
            val existing = secureMedia.get(code) ?: return@withContext null
            val fetchedAt = existing.detailFetchedAt ?: 0L
            val fresh = fetchedAt > 0 && System.currentTimeMillis() - fetchedAt < 8 * 60 * 60 * 1000
            if (fresh && !force) return@withContext existing

            accountManager.ensureRestored()
            try {
                val post = budgeted { client.fetchPost(code, existing.pk) }
                requestLog.record("post $code", "ok")
                val now = System.currentTimeMillis()
                val children = post.children
                val bestImage = children.firstOrNull()?.imageUrl ?: existing.displayUrl
                val updated = existing.copy(
                    pk = post.pk ?: existing.pk,
                    mediaType = post.mediaType,
                    productType = post.productType ?: existing.productType,
                    caption = post.caption ?: existing.caption,
                    displayUrl = bestImage ?: existing.displayUrl,
                    videoUrl = children.firstOrNull { it.videoUrl != null }?.videoUrl ?: existing.videoUrl,
                    likeCount = post.likeCount ?: existing.likeCount,
                    commentCount = post.commentCount ?: existing.commentCount,
                    takenAt = post.takenAt ?: existing.takenAt,
                    detailFetchedAt = now,
                    carouselCount = maxOf(children.size, existing.carouselCount),
                    childrenJson = json.encodeToString(childSerializer, children),
                    width = post.width ?: existing.width,
                    height = post.height ?: existing.height,
                    accessibilityCaption = post.accessibilityCaption ?: existing.accessibilityCaption,
                    locationName = post.locationName ?: existing.locationName,
                    audioTitle = post.audioTitle ?: existing.audioTitle,
                    audioArtist = post.audioArtist ?: existing.audioArtist,
                )
                secureMedia.upsert(updated)

                val profile = secureProfiles.get(existing.username)
                if (profile != null && post.userAvatar != null && profile.profilePicUrl != post.userAvatar) {
                    secureProfiles.upsert(
                        profile.copy(
                            profilePicUrl = post.userAvatar,
                            fullName = profile.fullName.ifBlank { existing.userFullName },
                            isVerified = post.userVerified,
                        ),
                    )
                }
                updated
            } catch (e: GuestException) {
                requestLog.record(
                    "post $code",
                    e.userMessage(),
                    rateLimited = e.kind == GuestErrorKind.RATE_LIMITED,
                )
                if (existing.displayUrl != null) existing else throw e
            }
        }

    suspend fun syncAll(force: Boolean = false): SyncReport? = withContext(Dispatchers.IO) {
        accountManager.ensureRestored()
        if (!network.online.value) {
            _syncEvents.tryEmit(SyncEvent.NoConnection)
            return@withContext null
        }
        val currentSettings = settings.settings.first()
        if (currentSettings.wifiOnlySync && !network.wifi.value) {
            _syncEvents.tryEmit(SyncEvent.Failed("Wi-Fi only is on. Connect to Wi-Fi to refresh."))
            return@withContext null
        }
        if (currentSettings.quietHoursEnabled &&
            requestBudget.inQuietHours(currentSettings.quietStartHour, currentSettings.quietEndHour)
        ) {
            _syncEvents.tryEmit(
                SyncEvent.Failed("Quiet hours are on. Background sync waits until ${currentSettings.quietEndHour}:00."),
            )
            return@withContext null
        }
        if (!force && currentSettings.lastFullSync > 0 &&
            System.currentTimeMillis() - currentSettings.lastFullSync < 10 * 60 * 1000
        ) {
            return@withContext SyncReport(emptyMap(), 0)
        }
        if (!syncMutex.tryLock()) return@withContext null

        try {
            val profiles = secureProfiles.following()
                .filter { !currentSettings.privateContentHidden || !it.isPrivate }
            if (profiles.isEmpty()) {
                _syncState.value = SyncState(running = false, finishedAt = System.currentTimeMillis())
                return@withContext SyncReport(emptyMap(), 0)
            }

            _syncState.value = SyncState(
                running = true,
                label = "Checking your circle",
                total = profiles.size,
            )
            var errors = 0
            val newByProfile = linkedMapOf<String, List<String>>()

            for ((index, profile) in profiles.withIndex()) {
                _syncState.update {
                    it.copy(current = index, label = "Refreshing @${profile.username}")
                }
                try {
                    val scraped = budgeted(background = true) {
                        client.fetchProfile(profile.username)
                    }
                    val result = cacheProfile(scraped, followed = null, followedAt = null)
                    if (result.newCodes.isNotEmpty()) newByProfile[profile.username] = result.newCodes
                    secureProfiles.setError(profile.username, null)
                } catch (e: Exception) {
                    errors++
                    secureProfiles.setError(profile.username, e.userMessage())
                }
                if (index < profiles.lastIndex) delay(700)
            }

            if (currentSettings.enrichFeed && currentSettings.syncDepth > 0) {
                val targets = ArrayList<String>()
                for (profile in profiles) {
                    val cached = secureMedia.forProfile(profile.username)
                        .sortedBy { it.sortIndex }
                        .take(currentSettings.syncDepth)
                    cached.filter { it.detailFetchedAt == null || it.detailFetchedAt < System.currentTimeMillis() - 12 * 60 * 60 * 1000 }
                        .forEach { targets += it.code }
                }
                if (targets.isNotEmpty()) {
                    _syncState.update {
                        it.copy(label = "Making the feed richer", total = targets.size, current = 0)
                    }
                    for ((index, code) in targets.withIndex()) {
                        try {
                            loadMediaDetail(code, force = true)
                        } catch (_: Exception) {
                            errors++
                        }
                        _syncState.update { it.copy(current = index + 1) }
                        if (index < targets.lastIndex) delay(450)
                    }
                }
            }

            val finishedAt = System.currentTimeMillis()
            settings.setLastFullSync(finishedAt)
            _syncState.value = SyncState(
                running = false,
                newPosts = newByProfile.values.sumOf { it.size },
                errorCount = errors,
                finishedAt = finishedAt,
            )
            _syncEvents.tryEmit(
                SyncEvent.Completed(
                    newPosts = newByProfile.values.sumOf { it.size },
                    errors = errors,
                ),
            )
            SyncReport(newByProfile, errors)
        } finally {
            if (_syncState.value.running) {
                _syncState.value = _syncState.value.copy(running = false, finishedAt = System.currentTimeMillis())
            }
            syncMutex.unlock()
        }
    }

    suspend fun markPostViewed(profileUsername: String) {
        secureProfiles.setViewed(profileUsername, System.currentTimeMillis())
    }

    suspend fun toggleLike(code: String): Boolean {
        val media = secureMedia.get(code) ?: return false
        val next = !media.isLiked
        secureMedia.setLiked(code, next)
        return next
    }

    suspend fun toggleSaved(code: String): Boolean {
        val media = secureMedia.get(code) ?: return false
        val next = !media.isSaved
        secureMedia.setSaved(code, next, if (next) System.currentTimeMillis() else null)
        return next
    }

    suspend fun saveProfileLinkToHistory(query: String) {
        secureSearches.add(
            com.avi.peek.data.local.SearchHistoryEntity(
                query = query,
                searchedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearSearchHistory() = secureSearches.clear()

    suspend fun privateProfileBlock(username: String): String? = withContext(Dispatchers.IO) {
        val profile = secureProfiles.get(username) ?: return@withContext null
        if (!profile.isPrivate) return@withContext null
        val current = settings.settings.first()
        if (current.privateDownloadPolicy == com.avi.peek.data.local.PrivateDownloadPolicy.BLOCK) {
            return@withContext "Private media downloads are off. Enable them in Settings → Privacy & storage."
        }
        if (!PrivateAccess.isRevealed()) {
            return@withContext "Unlock private content to download this profile."
        }
        null
    }

    suspend fun privateDownloadBlock(code: String): String? = withContext(Dispatchers.IO) {
        val media = secureMedia.get(code) ?: return@withContext null
        val profile = secureProfiles.get(media.username)
        val isPrivate = media.isPrivate || profile?.isPrivate == true
        if (!isPrivate) return@withContext null
        val current = settings.settings.first()
        if (current.privateDownloadPolicy == com.avi.peek.data.local.PrivateDownloadPolicy.BLOCK) {
            return@withContext "Private media downloads are off. Enable them in Settings → Privacy & storage."
        }
        if (!PrivateAccess.isRevealed()) {
            return@withContext "Unlock private content to download this post."
        }
        null
    }

    suspend fun privateGalleryBlock(code: String): String? = withContext(Dispatchers.IO) {
        val media = secureMedia.get(code) ?: return@withContext null
        val profile = secureProfiles.get(media.username)
        if (media.isPrivate || profile?.isPrivate == true) {
            "Private media can't be copied to the public gallery. It stays in the private vault."
        } else {
            null
        }
    }

    suspend fun download(code: String): MediaEntity? = withContext(Dispatchers.IO) {
        if (_downloadProgress.value.containsKey(code)) return@withContext secureMedia.get(code)
        val before = secureMedia.get(code)
        before?.let { privateDownloadBlock(code)?.let { reason -> throw IllegalStateException(reason) } }
        if (before?.isDownloaded == true) return@withContext before
        val loaded = loadMediaDetail(code) ?: secureMedia.get(code) ?: return@withContext null
        val children = loaded.children().ifEmpty {
            listOfNotNull(
                loaded.displayUrl?.let { url ->
                    MediaChild(
                        index = 0,
                        mediaType = loaded.mediaType,
                        imageUrl = url,
                        videoUrl = loaded.videoUrl,
                    )
                },
            )
        }
        if (children.isEmpty()) throw GuestException(GuestErrorKind.PARSE, "Nothing to download for this post")

        val currentSettings = settings.settings.first()
        val dataSaver = currentSettings.dataSaver
        val dir = downloads.directoryFor(code)
        dir.deleteRecursively()
        dir.mkdirs()
        _downloadProgress.update {
            it + (code to DownloadProgress(code, 0f, "Preparing"))
        }

        try {
            val imagePaths = mutableListOf<String>()
            var videoPath: String? = null
            var completed = 0f
            val totalSteps = children.size.toFloat()

            children.forEach { child ->
                val poster = if (dataSaver) {
                    child.imageUrlLow ?: child.imageUrl
                } else {
                    child.imageUrl ?: child.imageUrlLow
                }
                if (poster != null) {
                    val file = File(dir, "image_${child.index}.jpg")
                    client.download(poster, file) { done, total ->
                        val partial = if (total > 0) (done.toFloat() / total) else 0f
                        _downloadProgress.update {
                            it + (code to DownloadProgress(code, ((completed + partial) / totalSteps).coerceIn(0f, 1f), "Saving image ${child.index + 1}"))
                        }
                    }
                    imagePaths += file.absolutePath
                }
                val videoUrl = if (dataSaver) {
                    child.videoUrlLow ?: child.videoUrl
                } else {
                    child.videoUrl ?: child.videoUrlLow
                }
                if (videoUrl != null) {
                    val file = File(dir, "video_${child.index}.mp4")
                    client.download(videoUrl, file) { done, total ->
                        val partial = if (total > 0) (done.toFloat() / total) else 0f
                        _downloadProgress.update {
                            it + (code to DownloadProgress(code, ((completed + partial) / totalSteps).coerceIn(0f, 1f), "Saving video ${child.index + 1}"))
                        }
                    }
                    if (videoPath == null) videoPath = file.absolutePath
                }
                completed += 1f
                _downloadProgress.update {
                    it + (code to DownloadProgress(code, (completed / totalSteps).coerceIn(0f, 1f), "Saving ${child.index + 1} of ${children.size}"))
                }
            }

            val now = System.currentTimeMillis()
            val imageJson = json.encodeToString(stringListSerializer, imagePaths)
            secureMedia.setDownloaded(code, true, now, imageJson, videoPath)
            val profile = secureProfiles.get(loaded.username)
            val isPrivate = before?.isPrivate == true || loaded.isPrivate || profile?.isPrivate == true
            if (isPrivate && currentSettings.privateVault) {
                runCatching { vault.seal(listOf(code), downloads.root) }
                    .onFailure { error ->
                        downloads.deleteFor(code)
                        vault.purge(code)
                        secureMedia.clearDownload(code)
                        throw IllegalStateException(
                            "Private vault could not seal this download, so it was removed.",
                            error,
                        )
                    }
            }
            _downloadProgress.update { it - code }
            requestLog.record("download $code", "ok")
            secureMedia.get(code)
        } catch (e: Exception) {
            dir.deleteRecursively()
            _downloadProgress.update { it - code }
            requestLog.record("download $code", e.userMessage() ?: "failed")
            throw e
        }
    }

    /** Downloads every slide first, then copies the files into Pictures/Peek. */
    suspend fun saveToGallery(code: String): GallerySaver.Summary = withContext(Dispatchers.IO) {
        if (gallery.needsLegacyPermission()) throw GallerySaver.PermissionRequired()
        privateGalleryBlock(code)?.let { throw IllegalStateException(it) }
        val media = download(code)
            ?: throw IllegalStateException("That post could not be downloaded")
        val writeSidecar = settings.settings.first().gallerySidecars
        gallery.save(media, writeSidecar)
    }

    suspend fun deleteDownload(code: String) {
        downloads.deleteFor(code)
        vault.purge(code)
        secureMedia.clearDownload(code)
    }

    suspend fun openPrivateVault(): Int = withContext(Dispatchers.IO) {
        val codes = secureMedia.privateDownloads().map { it.code }
        vault.open(codes, downloads.root)
        codes.size
    }

    suspend fun sealPrivateVault(): Int = withContext(Dispatchers.IO) {
        val codes = secureMedia.privateDownloads().map { it.code }
        vault.seal(codes, downloads.root)
        codes.size
    }

    suspend fun privateVaultSize(): Long = withContext(Dispatchers.IO) { vault.sizeBytes() }

    /**
     * Re-encrypts every cached metadata field with a new Keystore key version.
     * Old rows stay readable through their version prefix if the process dies
     * halfway through.
     */
    suspend fun rotateMetadataEncryption(): Int = withContext(Dispatchers.IO) {
        val media = secureMedia.allOnce()
        val profiles = secureProfiles.allOnce()
        val comments = secureComments.allOnce()
        val searches = secureSearches.allOnce()
        val version = com.avi.peek.data.local.FieldCipher.rotateToNextVersion()
        if (media.isNotEmpty()) secureMedia.upsertAll(media)
        profiles.forEach { secureProfiles.upsert(it) }
        if (comments.isNotEmpty()) secureComments.upsertAll(comments)
        searches.forEach { secureSearches.add(it) }
        media.size + profiles.size + comments.size + searches.size
    }

    suspend fun metadataKeyVersion(): Int = withContext(Dispatchers.IO) {
        com.avi.peek.data.local.FieldCipher.currentVersion()
    }

    suspend fun databaseSize(): Long = withContext(Dispatchers.IO) {
        val db = context.getDatabasePath("peek.db")
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm"))
            .sumOf { if (it.exists()) it.length() else 0L }
    }

    suspend fun privateMediaCounts(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val media = secureMedia.allPrivateMedia()
        media.size to media.count { it.isDownloaded }
    }

    suspend fun vaultEntries(): List<VaultEntry> = withContext(Dispatchers.IO) {
        secureMedia.privateDownloads().map { item ->
            val sealed = vault.hasSealed(item.code)
            val bytes = vault.sizeFor(item.code) + downloads.directoryFor(item.code)
                .walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
            VaultEntry(
                username = item.username,
                code = item.code,
                bytes = bytes,
                sealed = sealed,
                caption = item.caption?.take(120),
            )
        }.sortedWith(compareBy({ it.username }, { it.code }))
    }

    suspend fun sealVaultProfile(username: String) = withContext(Dispatchers.IO) {
        val codes = secureMedia.forProfile(username)
            .filter { it.isDownloaded }
            .map { it.code }
        vault.seal(codes, downloads.root)
    }

    suspend fun openVaultProfile(username: String) = withContext(Dispatchers.IO) {
        val codes = secureMedia.forProfile(username)
            .filter { it.isDownloaded }
            .map { it.code }
        vault.open(codes, downloads.root)
    }

    suspend fun deleteVaultProfile(username: String) = withContext(Dispatchers.IO) {
        secureMedia.forProfile(username).filter { it.isDownloaded }.forEach { item ->
            downloads.deleteFor(item.code)
            vault.purge(item.code)
            secureMedia.clearDownload(item.code)
        }
    }

    suspend fun purgePrivateDownloads() = withContext(Dispatchers.IO) {
        secureMedia.privateDownloads().forEach { item ->
            downloads.deleteFor(item.code)
            secureMedia.clearDownload(item.code)
        }
        vault.purge()
    }

    suspend fun purgePrivateContent() = withContext(Dispatchers.IO) {
        purgePrivateDownloads()
        secureMedia.deleteAllPrivateMedia()
    }

    suspend fun deleteCachedMedia() {
        secureMedia.clearCached()
    }

    suspend fun profileStorage(username: String): ProfileStorage = withContext(Dispatchers.IO) {
        val media = secureMedia.forProfile(username)
        var downloadBytes = 0L
        media.filter { it.isDownloaded }.forEach { item ->
            item.localImages().forEach { path -> downloadBytes += File(path).length() }
            item.localVideoPath?.let { path -> downloadBytes += File(path).length() }
        }
        ProfileStorage(
            cachedCount = media.size,
            downloadedCount = media.count { it.isDownloaded },
            savedCount = media.count { it.isSaved },
            likedCount = media.count { it.isLiked },
            keptCount = media.count { it.isSaved || it.isDownloaded || it.isLiked },
            downloadBytes = downloadBytes,
            newestAt = media.mapNotNull { it.takenAt }.maxOrNull(),
            oldestAt = media.mapNotNull { it.takenAt }.minOrNull(),
        )
    }

    suspend fun clearCachedPosts(username: String) = withContext(Dispatchers.IO) {
        secureMedia.deleteCachedKeepingLiked(username)
    }

    suspend fun deleteDownloadsForProfile(username: String) = withContext(Dispatchers.IO) {
        secureMedia.forProfile(username).filter { it.isDownloaded }.forEach { item ->
            downloads.deleteFor(item.code)
            vault.purge(item.code)
            secureMedia.clearDownload(item.code)
        }
    }

    /** Keeps saved, downloaded, liked and the newest [keepNewest] cached posts. */
    suspend fun trimProfileCache(username: String, keepNewest: Int) = withContext(Dispatchers.IO) {
        val media = secureMedia.forProfile(username)
        media.forEachIndexed { index, item ->
            val keep = item.isSaved || item.isDownloaded || item.isLiked || index < keepNewest
            if (!keep) secureMedia.delete(item.code)
        }
    }

    suspend fun removeProfile(username: String, deleteRow: Boolean = false) {
        secureProfiles.setFollowed(username, false, null)
        secureMedia.deleteCachedForProfile(username)
        if (deleteRow) secureProfiles.delete(username)
    }

    suspend fun storageUsage(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val downloadsSize = downloads.sizeBytes() + vault.sizeBytes()
        val imageCache = File(context.cacheDir, "image_cache")
        val imageSize = if (imageCache.exists()) {
            imageCache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
        downloadsSize to imageSize
    }

    suspend fun clearEverything() = withContext(Dispatchers.IO) {
        com.avi.peek.data.local.FieldCipher.clearCache()
        db.clearAllTables()
        downloads.root.deleteRecursively()
        downloads.root.mkdirs()
        vault.purge()
        File(context.cacheDir, "image_cache").deleteRecursively()
    }

    suspend fun exportBackup(): BackupData = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        val follows = secureProfiles.following()
            .filter { current.includePrivateInBackups || !it.isPrivate }
            .map { it.username }
        val saved = secureMedia.allSaved()
            .filter { current.includePrivateInBackups || !it.isPrivate }
            .map { it.code }
        val liked = secureMedia.allLiked()
            .filter { current.includePrivateInBackups || !it.isPrivate }
            .map { it.code }
        BackupData(follows = follows, saved = saved, liked = liked)
    }

    suspend fun importBackup(data: BackupData): ImportSummary = withContext(Dispatchers.IO) {
        var followed = 0
        var failed = 0
        for (username in data.follows.distinct()) {
            try {
                follow(username)
                followed++
            } catch (_: Exception) {
                failed++
            }
            delay(500)
        }
        val existing = if (data.saved.isEmpty()) emptyList() else secureMedia.byCodes(data.saved)
        existing.forEach { secureMedia.setSaved(it.code, true, System.currentTimeMillis()) }
        if (data.liked.isNotEmpty()) {
            secureMedia.byCodes(data.liked).forEach { secureMedia.setLiked(it.code, true) }
        }
        ImportSummary(followed, failed, existing.size)
    }

    suspend fun currentSettings(): com.avi.peek.data.local.PeekSettings = settings.settings.first()
}

data class SyncReport(
    val newCodesByUsername: Map<String, List<String>>,
    val errors: Int,
)

data class ImportSummary(
    val followed: Int,
    val failed: Int,
    val restoredSaved: Int,
)

data class ProfilePageResult(
    val added: Int,
    val hasMore: Boolean,
)

data class VaultEntry(
    val username: String,
    val code: String,
    val bytes: Long,
    val sealed: Boolean,
    val caption: String?,
)

data class ProfileStorage(
    val cachedCount: Int,
    val downloadedCount: Int,
    val savedCount: Int,
    val likedCount: Int,
    val keptCount: Int,
    val downloadBytes: Long,
    val newestAt: Long?,
    val oldestAt: Long?,
)
