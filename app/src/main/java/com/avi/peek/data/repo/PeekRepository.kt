package com.avi.peek.data.repo

import android.content.Context
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.ProfileEntity
import com.avi.peek.data.local.ProfileWithMedia
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GuestErrorKind
import com.avi.peek.data.remote.GuestException
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.remote.MediaChild
import com.avi.peek.data.remote.ScrapedProfile
import com.avi.peek.util.NetworkMonitor
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
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val childSerializer = ListSerializer(MediaChild.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())
    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    val following: Flow<List<ProfileEntity>> = db.profiles().observeFollowing()
    val followingCount: Flow<Int> = db.profiles().observeFollowingCount()
    val savedCount: Flow<Int> = db.media().observeSavedCount()
    val downloadCount: Flow<Int> = db.media().observeDownloadCount()
    val mediaCount: Flow<Int> = db.media().observeMediaCount()
    val saved: Flow<List<MediaEntity>> = db.media().observeSaved()
    val downloaded: Flow<List<MediaEntity>> = db.media().observeDownloads()
    val recentSearches = db.searches().observeRecent()

    val feed: Flow<List<FeedPost>> = combine(
        db.media().observeFeed(),
        db.profiles().observeFollowing(),
    ) { media, profiles ->
        val byName = profiles.associateBy { it.username }
        media.mapNotNull { item -> byName[item.username]?.let { FeedPost(item, it) } }
    }

    val settingsFlow = settings.settings

    fun observeProfile(username: String): Flow<ProfileWithMedia?> = combine(
        db.profiles().observe(username),
        db.media().observeForProfile(username),
    ) { profile, media ->
        profile?.let { ProfileWithMedia(it, media) }
    }

    fun observeMedia(code: String): Flow<MediaEntity?> = db.media().observe(code)

    suspend fun lookup(raw: String): ScrapedProfile {
        val username = GuestInstagramClient.normalizeUsername(raw)
            ?: throw GuestException(GuestErrorKind.NOT_FOUND, "Enter a valid Instagram username or link")
        return client.fetchProfile(username)
    }

    suspend fun follow(rawOrUsername: String): CacheResult {
        val username = GuestInstagramClient.normalizeUsername(rawOrUsername)
            ?: throw GuestException(GuestErrorKind.NOT_FOUND, "Enter a valid Instagram username or link")
        val scraped = client.fetchProfile(username)
        return cacheAndFollow(scraped)
    }

    suspend fun followScraped(scraped: ScrapedProfile): CacheResult = cacheAndFollow(scraped)

    /**
     * Loads a profile for the Explore directory/search without following it.
     * The profile (and its first page of media) is cached so opening it later is instant.
     */
    suspend fun prefetchDirectoryProfile(raw: String): ProfileEntity? = withContext(Dispatchers.IO) {
        val username = GuestInstagramClient.normalizeUsername(raw) ?: return@withContext null
        val cached = db.profiles().get(username)
        if (cached != null && cached.pk != null &&
            System.currentTimeMillis() - cached.lastFetchedAt < 12 * 60 * 60 * 1000
        ) {
            return@withContext cached
        }
        runCatching {
            val scraped = client.fetchProfile(username)
            cacheProfile(scraped, followed = null, followedAt = null, source = "catalog").profile
        }.onFailure {
            cached?.let { old -> db.profiles().setError(username, it.userMessage()) }
        }.getOrNull()
    }

    /** Follows an already cached directory profile without a second network request. */
    suspend fun followCached(username: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = GuestInstagramClient.normalizeUsername(username) ?: return@withContext false
        db.profiles().get(normalized) ?: return@withContext false
        db.profiles().setFollowed(normalized, true, System.currentTimeMillis())
        settings.setLastFullSync(System.currentTimeMillis())
        true
    }

    suspend fun cachedProfile(username: String): ProfileEntity? = withContext(Dispatchers.IO) {
        val normalized = GuestInstagramClient.normalizeUsername(username) ?: return@withContext null
        db.profiles().get(normalized)
    }

    private suspend fun cacheAndFollow(scraped: ScrapedProfile): CacheResult {
        val now = System.currentTimeMillis()
        val result = cacheProfile(scraped, followed = true, followedAt = now)
        settings.setLastFullSync(System.currentTimeMillis())
        return result
    }

    suspend fun unfollow(username: String) {
        db.profiles().setFollowed(username, false, null)
    }

    suspend fun setPinned(username: String, pinned: Boolean) {
        db.profiles().setPinned(username, pinned)
    }

    suspend fun markViewed(username: String) {
        db.profiles().setViewed(username, System.currentTimeMillis())
    }

    suspend fun refreshProfile(username: String): CacheResult = withContext(Dispatchers.IO) {
        try {
            val scraped = client.fetchProfile(username)
            cacheProfile(scraped, followed = null, followedAt = null)
                .also { db.profiles().setError(username, null) }
        } catch (e: GuestException) {
            db.profiles().setError(username, e.userMessage())
            throw e
        }
    }

    /**
     * Merges a fresh public profile payload into the local cache. Local-only state
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
        val existingProfile = db.profiles().get(username)
        val existingMedia = db.media().forProfile(username).associateBy { it.code }
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

        db.profiles().upsert(profile)
        if (cacheMedia) {
            db.media().upsertAll(items)
            if (items.isNotEmpty()) {
                db.media().pruneProfile(username, items.map { it.code })
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
            val existing = db.media().get(code) ?: return@withContext null
            val fetchedAt = existing.detailFetchedAt ?: 0L
            val fresh = fetchedAt > 0 && System.currentTimeMillis() - fetchedAt < 8 * 60 * 60 * 1000
            if (fresh && !force) return@withContext existing

            try {
                val post = client.fetchPost(code)
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
                db.media().upsert(updated)

                val profile = db.profiles().get(existing.username)
                if (profile != null && post.userAvatar != null && profile.profilePicUrl != post.userAvatar) {
                    db.profiles().upsert(
                        profile.copy(
                            profilePicUrl = post.userAvatar,
                            fullName = profile.fullName.ifBlank { existing.userFullName },
                            isVerified = post.userVerified,
                        ),
                    )
                }
                updated
            } catch (e: GuestException) {
                if (existing.displayUrl != null) existing else throw e
            }
        }

    suspend fun syncAll(force: Boolean = false): SyncReport? = withContext(Dispatchers.IO) {
        if (!network.online.value) {
            _syncEvents.tryEmit(SyncEvent.NoConnection)
            return@withContext null
        }
        val currentSettings = settings.settings.first()
        if (currentSettings.wifiOnlySync && !network.wifi.value) {
            _syncEvents.tryEmit(SyncEvent.Failed("Wi-Fi only is on. Connect to Wi-Fi to refresh."))
            return@withContext null
        }
        if (!force && currentSettings.lastFullSync > 0 &&
            System.currentTimeMillis() - currentSettings.lastFullSync < 10 * 60 * 1000
        ) {
            return@withContext SyncReport(emptyMap(), 0)
        }
        if (!syncMutex.tryLock()) return@withContext null

        try {
            val profiles = db.profiles().following()
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
                    val scraped = client.fetchProfile(profile.username)
                    val result = cacheProfile(scraped, followed = null, followedAt = null)
                    if (result.newCodes.isNotEmpty()) newByProfile[profile.username] = result.newCodes
                    db.profiles().setError(profile.username, null)
                } catch (e: Exception) {
                    errors++
                    db.profiles().setError(profile.username, e.userMessage())
                }
                if (index < profiles.lastIndex) delay(700)
            }

            if (currentSettings.enrichFeed && currentSettings.syncDepth > 0) {
                val targets = ArrayList<String>()
                for (profile in profiles) {
                    val cached = db.media().forProfile(profile.username)
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
        db.profiles().setViewed(profileUsername, System.currentTimeMillis())
    }

    suspend fun toggleLike(code: String): Boolean {
        val media = db.media().get(code) ?: return false
        val next = !media.isLiked
        db.media().setLiked(code, next)
        return next
    }

    suspend fun toggleSaved(code: String): Boolean {
        val media = db.media().get(code) ?: return false
        val next = !media.isSaved
        db.media().setSaved(code, next, if (next) System.currentTimeMillis() else null)
        return next
    }

    suspend fun saveProfileLinkToHistory(query: String) {
        db.searches().add(
            com.avi.peek.data.local.SearchHistoryEntity(
                query = query,
                searchedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearSearchHistory() = db.searches().clear()

    suspend fun download(code: String): MediaEntity? = withContext(Dispatchers.IO) {
        if (_downloadProgress.value.containsKey(code)) return@withContext db.media().get(code)
        val before = db.media().get(code)
        if (before?.isDownloaded == true) return@withContext before
        val loaded = loadMediaDetail(code) ?: db.media().get(code) ?: return@withContext null
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

        val dataSaver = settings.settings.first().dataSaver
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
            db.media().setDownloaded(code, true, now, imageJson, videoPath)
            _downloadProgress.update { it - code }
            db.media().get(code)
        } catch (e: Exception) {
            dir.deleteRecursively()
            _downloadProgress.update { it - code }
            throw e
        }
    }

    suspend fun deleteDownload(code: String) {
        downloads.deleteFor(code)
        db.media().clearDownload(code)
    }

    suspend fun deleteCachedMedia() {
        db.media().clearCached()
    }

    suspend fun removeProfile(username: String, deleteRow: Boolean = false) {
        db.profiles().setFollowed(username, false, null)
        db.media().deleteCachedForProfile(username)
        if (deleteRow) db.profiles().delete(username)
    }

    suspend fun storageUsage(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val downloadsSize = downloads.sizeBytes()
        val imageCache = File(context.cacheDir, "image_cache")
        val imageSize = if (imageCache.exists()) {
            imageCache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
        downloadsSize to imageSize
    }

    suspend fun clearEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        downloads.root.deleteRecursively()
        downloads.root.mkdirs()
        File(context.cacheDir, "image_cache").deleteRecursively()
    }

    suspend fun exportBackup(): BackupData = withContext(Dispatchers.IO) {
        val follows = db.profiles().following().map { it.username }
        val saved = db.media().allSaved().map { it.code }
        val liked = db.media().allLiked().map { it.code }
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
        val existing = if (data.saved.isEmpty()) emptyList() else db.media().byCodes(data.saved)
        existing.forEach { db.media().setSaved(it.code, true, System.currentTimeMillis()) }
        if (data.liked.isNotEmpty()) {
            db.media().byCodes(data.liked).forEach { db.media().setLiked(it.code, true) }
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
