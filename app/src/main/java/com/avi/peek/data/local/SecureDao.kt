package com.avi.peek.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room DAO decorators that seal sensitive fields before every write and unseal
 * them after every read. All repository access goes through these wrappers so
 * plaintext metadata never reaches the database file.
 */
class SecureMediaDao(private val dao: MediaDao) {

    fun observeFeed(): Flow<List<MediaEntity>> = dao.observeFeed().unsealed()
    fun observeForProfile(username: String): Flow<List<MediaEntity>> =
        dao.observeForProfile(username).unsealed()
    fun observeByCodes(codes: List<String>): Flow<List<MediaEntity>> =
        dao.observeByCodes(codes).unsealed()
    fun observeForUsernames(usernames: List<String>): Flow<List<MediaEntity>> =
        dao.observeForUsernames(usernames).unsealed()
    fun observePrivateFeed(): Flow<List<MediaEntity>> = dao.observePrivateFeed().unsealed()
    fun observeSaved(): Flow<List<MediaEntity>> = dao.observeSaved().unsealed()
    fun observeLiked(): Flow<List<MediaEntity>> = dao.observeLiked().unsealed()
    fun observeDownloads(): Flow<List<MediaEntity>> = dao.observeDownloads().unsealed()
    fun observe(code: String): Flow<MediaEntity?> = dao.observe(code).map { it?.unsealMetadata() }
    fun observeAll(): Flow<List<MediaEntity>> = dao.observeAll().unsealed()
    fun observeAudio(title: String): Flow<List<MediaEntity>> = dao.observeAll()
        .map { items ->
            items.map { it.unsealMetadata() }
                .filter { it.audioTitle == title }
                .sortedByDescending { it.takenAt ?: 0L }
        }

    fun observeSavedSince(since: Long): Flow<Int> = dao.observeSavedSince(since)
    fun observeDownloadedSince(since: Long): Flow<Int> = dao.observeDownloadedSince(since)
    fun observeMediaCount(): Flow<Int> = dao.observeMediaCount()
    fun observeSavedCount(): Flow<Int> = dao.observeSavedCount()
    fun observeDownloadCount(): Flow<Int> = dao.observeDownloadCount()

    suspend fun get(code: String): MediaEntity? = dao.get(code)?.unsealMetadata()
    suspend fun forProfile(username: String): List<MediaEntity> =
        dao.forProfile(username).map { it.unsealMetadata() }
    suspend fun byCodes(codes: List<String>): List<MediaEntity> =
        dao.byCodes(codes).map { it.unsealMetadata() }
    suspend fun privateDownloads(): List<MediaEntity> =
        dao.privateDownloads().map { it.unsealMetadata() }
    suspend fun allPrivateMedia(): List<MediaEntity> =
        dao.allPrivateMedia().map { it.unsealMetadata() }
    suspend fun allSaved(): List<MediaEntity> = dao.allSaved().map { it.unsealMetadata() }
    suspend fun allLiked(): List<MediaEntity> = dao.allLiked().map { it.unsealMetadata() }

    suspend fun searchCached(query: String): List<MediaEntity> = dao.allOnce()
        .map { it.unsealMetadata() }
        .filter { item ->
            listOfNotNull(
                item.caption,
                item.username,
                item.locationName,
                item.audioTitle,
            ).any { it.contains(query, ignoreCase = true) }
        }
        .sortedByDescending { it.takenAt ?: 0L }
        .take(30)

    suspend fun upsert(media: MediaEntity) = dao.upsert(media.sealMetadata())
    suspend fun upsertAll(items: List<MediaEntity>) =
        dao.upsertAll(items.map { it.sealMetadata() })

    suspend fun setLiked(code: String, liked: Boolean) = dao.setLiked(code, liked)
    suspend fun setSaved(code: String, saved: Boolean, savedAt: Long?) =
        dao.setSaved(code, saved, savedAt)
    suspend fun setPrivateForProfile(username: String, isPrivate: Boolean) =
        dao.setPrivateForProfile(username, isPrivate)
    suspend fun setDownloaded(
        code: String,
        downloaded: Boolean,
        at: Long?,
        imagePaths: String?,
        videoPath: String?,
    ) = dao.setDownloaded(
        code,
        downloaded,
        at,
        FieldCipher.encrypt(imagePaths),
        FieldCipher.encrypt(videoPath),
    )
    suspend fun updateCaption(code: String, caption: String?) =
        dao.updateCaption(code, FieldCipher.encrypt(caption))
    suspend fun clearDownload(code: String) = dao.clearDownload(code)
    suspend fun delete(code: String) = dao.delete(code)
    suspend fun deleteCachedForProfile(username: String) = dao.deleteCachedForProfile(username)
    suspend fun deleteCachedKeepingLiked(username: String) = dao.deleteCachedKeepingLiked(username)
    suspend fun pruneProfile(username: String, keepCodes: List<String>) =
        dao.pruneProfile(username, keepCodes)
    suspend fun clearCached() = dao.clearCached()
    suspend fun deletePrivateCached() = dao.deletePrivateCached()
    suspend fun deleteAllPrivateMedia() = dao.deleteAllPrivateMedia()

    private fun Flow<List<MediaEntity>>.unsealed(): Flow<List<MediaEntity>> =
        map { items -> items.map { it.unsealMetadata() } }
}

class SecureProfileDao(private val dao: ProfileDao) {

    fun observeFollowing(): Flow<List<ProfileEntity>> = dao.observeFollowing().unsealed()
    fun observeRecentlyViewed(): Flow<List<ProfileEntity>> =
        dao.observeRecentlyViewed().unsealed()
    fun observeViewedSince(since: Long): Flow<List<ProfileEntity>> =
        dao.observeViewedSince(since).unsealed()
    fun observeAll(): Flow<List<ProfileEntity>> = dao.observeAll().unsealed()
    fun observe(username: String): Flow<ProfileEntity?> =
        dao.observe(username).map { it?.unsealMetadata() }
    fun observeFollowingCount(): Flow<Int> = dao.observeFollowingCount()

    suspend fun get(username: String): ProfileEntity? = dao.get(username)?.unsealMetadata()
    suspend fun following(): List<ProfileEntity> = dao.following().map { it.unsealMetadata() }

    suspend fun searchCached(query: String): List<ProfileEntity> = dao.allOnce()
        .map { it.unsealMetadata() }
        .filter { profile ->
            profile.username.contains(query, ignoreCase = true) ||
                profile.fullName.contains(query, ignoreCase = true) ||
                profile.biography.contains(query, ignoreCase = true)
        }
        .sortedWith(compareByDescending<ProfileEntity> { it.isFollowed }.thenByDescending { it.followerCount })
        .take(24)

    suspend fun upsert(profile: ProfileEntity) = dao.upsert(profile.sealMetadata())
    suspend fun setFollowed(username: String, followed: Boolean, followedAt: Long?) =
        dao.setFollowed(username, followed, followedAt)
    suspend fun setViewed(username: String, viewedAt: Long) = dao.setViewed(username, viewedAt)
    suspend fun setError(username: String, error: String?) = dao.setError(username, error)
    suspend fun setPinned(username: String, pinned: Boolean) = dao.setPinned(username, pinned)
    suspend fun setPagination(username: String, hasMore: Boolean, cursor: String?) =
        dao.setPagination(username, hasMore, cursor)
    suspend fun delete(username: String) = dao.delete(username)

    private fun Flow<List<ProfileEntity>>.unsealed(): Flow<List<ProfileEntity>> =
        map { items -> items.map { it.unsealMetadata() } }
}
