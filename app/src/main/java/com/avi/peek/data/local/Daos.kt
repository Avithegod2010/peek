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

    @Query("DELETE FROM profiles WHERE username = :username")
    suspend fun delete(username: String)

    @Query("SELECT COUNT(*) FROM profiles WHERE isFollowed = 1")
    fun observeFollowingCount(): Flow<Int>
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

    @Query("SELECT * FROM media WHERE isSaved = 1 ORDER BY savedAt DESC")
    fun observeSaved(): Flow<List<MediaEntity>>

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

    @Query("DELETE FROM media WHERE username = :username AND code NOT IN (:keepCodes) AND isSaved = 0 AND isDownloaded = 0 AND isLiked = 0")
    suspend fun pruneProfile(username: String, keepCodes: List<String>)

    @Query("DELETE FROM media WHERE isSaved = 0 AND isDownloaded = 0")
    suspend fun clearCached()

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
}

