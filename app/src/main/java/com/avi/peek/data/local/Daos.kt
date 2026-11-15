package com.avi.peek.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE isFollowed = 1 ORDER BY isPinned DESC, latestReelMedia DESC, username ASC")
    fun observeFollowing(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isFollowed = 1 ORDER BY isPinned DESC, latestReelMedia DESC, username ASC")
    suspend fun following(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE lastViewedAt IS NOT NULL ORDER BY lastViewedAt DESC LIMIT 24")
    fun observeRecentlyViewed(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE lastViewedAt >= :since ORDER BY lastViewedAt DESC")
    fun observeViewedSince(since: Long): Flow<List<ProfileEntity>>

    @Query(
        """SELECT * FROM profiles WHERE username LIKE '%' || :query || '%'
        OR fullName LIKE '%' || :query || '%'
        OR biography LIKE '%' || :query || '%'
        ORDER BY isFollowed DESC, followerCount DESC LIMIT 24""",
    )
    suspend fun searchCached(query: String): List<ProfileEntity>

    @Query("SELECT * FROM profiles ORDER BY isFollowed DESC, followedAt DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE username = :username LIMIT 1")
    fun observe(username: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE username = :username LIMIT 1")
    suspend fun get(username: String): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Query("UPDATE profiles SET isFollowed = :followed, followedAt = :followedAt WHERE username = :username")
    suspend fun setFollowed(username: String, followed: Boolean, followedAt: Long?)

    @Query("UPDATE profiles SET lastViewedAt = :viewedAt WHERE username = :username")
    suspend fun setViewed(username: String, viewedAt: Long)

    @Query("UPDATE profiles SET lastError = :error WHERE username = :username")
    suspend fun setError(username: String, error: String?)

    @Query("UPDATE profiles SET isPinned = :pinned WHERE username = :username")
    suspend fun setPinned(username: String, pinned: Boolean)

    @Query("UPDATE profiles SET hasMorePosts = :hasMore, nextCursor = :cursor WHERE username = :username")
    suspend fun setPagination(username: String, hasMore: Boolean, cursor: String?)

    @Query("DELETE FROM profiles WHERE username = :username")
    suspend fun delete(username: String)

    @Query("SELECT COUNT(*) FROM profiles WHERE isFollowed = 1")
    fun observeFollowingCount(): Flow<Int>

    @Query("SELECT * FROM profiles")
    suspend fun allOnce(): List<ProfileEntity>
}

@Dao
interface MediaDao {
    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN profiles p ON p.username = m.username
        WHERE p.isFollowed = 1
        ORDER BY COALESCE(m.takenAt, 0) DESC, m.sortIndex ASC
        LIMIT 400
        """,
    )
    fun observeFeed(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE username = :username ORDER BY COALESCE(takenAt, 0) DESC, sortIndex ASC")
    fun observeForProfile(username: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE username = :username ORDER BY COALESCE(takenAt, 0) DESC, sortIndex ASC")
    suspend fun forProfile(username: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE code IN (:codes)")
    fun observeByCodes(codes: List<String>): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE username IN (:usernames) ORDER BY COALESCE(takenAt, 0) DESC")
    fun observeForUsernames(usernames: List<String>): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN profiles p ON p.username = m.username
        WHERE p.isFollowed = 1 AND p.isPrivate = 1
        ORDER BY COALESCE(m.takenAt, 0) DESC, m.sortIndex ASC
        """,
    )
    fun observePrivateFeed(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isPrivate = 1 AND isDownloaded = 1")
    suspend fun privateDownloads(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE isPrivate = 1")
    suspend fun allPrivateMedia(): List<MediaEntity>

    @Query("UPDATE media SET isPrivate = :isPrivate WHERE username = :username")
    suspend fun setPrivateForProfile(username: String, isPrivate: Boolean)

    @Query("SELECT * FROM media WHERE isSaved = 1 ORDER BY savedAt DESC")
    fun observeSaved(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isLiked = 1 ORDER BY COALESCE(takenAt, 0) DESC")
    fun observeLiked(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE audioTitle = :title ORDER BY COALESCE(takenAt, 0) DESC")
    fun observeAudio(title: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE caption LIKE '%' || :query || '%' ORDER BY COALESCE(takenAt, 0) DESC LIMIT 30")
    suspend fun searchCached(query: String): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media WHERE savedAt >= :since")
    fun observeSavedSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE downloadedAt >= :since")
    fun observeDownloadedSince(since: Long): Flow<Int>

    @Query("SELECT * FROM media WHERE isDownloaded = 1 ORDER BY downloadedAt DESC")
    fun observeDownloads(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE code = :code LIMIT 1")
    fun observe(code: String): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE code = :code LIMIT 1")
    suspend fun get(code: String): MediaEntity?

    @Query("SELECT * FROM media WHERE productType IN ('clips', 'reels') AND COALESCE(videoUrl, '') != '' ORDER BY COALESCE(takenAt, 0) DESC")
    fun observeCachedReels(): Flow<List<MediaEntity>>

    @Upsert
    suspend fun upsert(media: MediaEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaEntity>)

    @Query("UPDATE media SET isLiked = :liked WHERE code = :code")
    suspend fun setLiked(code: String, liked: Boolean)

    @Query("UPDATE media SET isSaved = :saved, savedAt = :savedAt WHERE code = :code")
    suspend fun setSaved(code: String, saved: Boolean, savedAt: Long?)

    @Query("UPDATE media SET isDownloaded = :downloaded, downloadedAt = :at, localImagePathsJson = :imagePaths, localVideoPath = :videoPath WHERE code = :code")
    suspend fun setDownloaded(
        code: String,
        downloaded: Boolean,
        at: Long?,
        imagePaths: String?,
        videoPath: String?,
    )

    @Query("UPDATE media SET isDownloaded = 0, downloadedAt = NULL, localImagePathsJson = NULL, localVideoPath = NULL WHERE code = :code")
    suspend fun clearDownload(code: String)

    @Query("UPDATE media SET caption = :caption, detailFetchedAt = NULL WHERE code = :code")
    suspend fun updateCaption(code: String, caption: String?)

    @Query("DELETE FROM media WHERE code = :code")
    suspend fun delete(code: String)

    @Query("DELETE FROM media WHERE username = :username AND isSaved = 0 AND isDownloaded = 0")
    suspend fun deleteCachedForProfile(username: String)

    @Query("DELETE FROM media WHERE username = :username AND isSaved = 0 AND isDownloaded = 0 AND isLiked = 0")
    suspend fun deleteCachedKeepingLiked(username: String)

    @Query("DELETE FROM media WHERE username = :username AND code NOT IN (:keepCodes) AND isSaved = 0 AND isDownloaded = 0 AND isLiked = 0")
    suspend fun pruneProfile(username: String, keepCodes: List<String>)

    @Query("DELETE FROM media WHERE isSaved = 0 AND isDownloaded = 0")
    suspend fun clearCached()

    @Query("DELETE FROM media WHERE isPrivate = 1 AND isSaved = 0 AND isLiked = 0")
    suspend fun deletePrivateCached()

    @Query("DELETE FROM media WHERE isPrivate = 1")
    suspend fun deleteAllPrivateMedia()

    @Query("SELECT COUNT(*) FROM media")
    fun observeMediaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE isSaved = 1")
    fun observeSavedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE isDownloaded = 1")
    fun observeDownloadCount(): Flow<Int>

    @Query("SELECT * FROM media WHERE isSaved = 1")
    suspend fun allSaved(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE isLiked = 1")
    suspend fun allLiked(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE code IN (:codes)")
    suspend fun byCodes(codes: List<String>): List<MediaEntity>

    @Query("SELECT * FROM media ORDER BY COALESCE(takenAt, 0) DESC")
    fun observeAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media")
    suspend fun allOnce(): List<MediaEntity>
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE mediaCode = :mediaCode ORDER BY COALESCE(createdAt, 0) ASC")
    fun observeForMedia(mediaCode: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE mediaCode = :mediaCode ORDER BY COALESCE(createdAt, 0) ASC")
    suspend fun forMedia(mediaCode: String): List<CommentEntity>

    @Upsert
    suspend fun upsertAll(comments: List<CommentEntity>)

    @Query("SELECT * FROM comments")
    suspend fun allOnce(): List<CommentEntity>

    @Query("DELETE FROM comments WHERE mediaCode = :mediaCode")
    suspend fun clearForMedia(mediaCode: String)
}

@Dao
interface SearchDao {
    @Query("SELECT * FROM searches ORDER BY searchedAt DESC LIMIT 10")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: SearchHistoryEntity)

    @Query("DELETE FROM searches")
    suspend fun clear()

    @Query("DELETE FROM searches WHERE query = :query")
    suspend fun remove(query: String)

    @Query("SELECT * FROM searches")
    suspend fun allOnce(): List<SearchHistoryEntity>
}

