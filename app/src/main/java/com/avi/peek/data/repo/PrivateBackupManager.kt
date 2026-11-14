package com.avi.peek.data.repo

import android.content.Context
import android.net.Uri
import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.SecureMediaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

@Serializable
data class PrivateBackupData(
    val app: String = "Peek",
    val kind: String = "private-vault",
    val version: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val media: List<PrivateMediaRecord> = emptyList(),
    val files: List<PrivateFileRecord> = emptyList(),
)

@Serializable
data class PrivateMediaRecord(
    val code: String,
    val username: String,
    val pk: String? = null,
    val caption: String? = null,
    val displayUrl: String? = null,
    val videoUrl: String? = null,
    val takenAt: Long? = null,
    val mediaType: Int = 1,
    val productType: String? = null,
    val childrenJson: String? = null,
    val locationName: String? = null,
    val audioTitle: String? = null,
    val audioArtist: String? = null,
    val likeCount: Long? = null,
    val commentCount: Long? = null,
)

@Serializable
data class PrivateFileRecord(
    val code: String,
    val name: String,
    val dataBase64: String,
)

/**
 * Passphrase-encrypted private vault backup, version 2. It contains the
 * metadata Peek needs to restore private rows plus the already-sealed vault
 * bytes. It never stores the passphrase.
 */
class PrivateBackupManager(
    private val context: Context,
    private val db: PeekDatabase,
) {
    private val mediaDao = SecureMediaDao(db.media())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaSerializer = ListSerializer(PrivateMediaRecord.serializer())
    private val fileSerializer = ListSerializer(PrivateFileRecord.serializer())
    private val vaultRoot = File(context.filesDir, "private_vault")

    suspend fun exportTo(uri: Uri, passphrase: String): Int = withContext(Dispatchers.IO) {
        require(passphrase.isNotBlank()) { "Choose a backup passphrase" }
        val media = mediaDao.allPrivateMedia().filter { it.isDownloaded }
        val records = media.map { item ->
            PrivateMediaRecord(
                code = item.code,
                username = item.username,
                pk = item.pk,
                caption = item.caption,
                displayUrl = item.displayUrl,
                videoUrl = item.videoUrl,
                takenAt = item.takenAt,
                mediaType = item.mediaType,
                productType = item.productType,
                childrenJson = item.childrenJson,
                locationName = item.locationName,
                audioTitle = item.audioTitle,
                audioArtist = item.audioArtist,
                likeCount = item.likeCount,
                commentCount = item.commentCount,
            )
        }
        val files = media.flatMap { item ->
            File(vaultRoot, item.code).listFiles().orEmpty()
                .filter { it.isFile }
                .map { file ->
                    PrivateFileRecord(
                        code = item.code,
                        name = file.name,
                        dataBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
                    )
                }
        }
        val data = PrivateBackupData(media = records, files = files)
        val plain = json.encodeToString(PrivateBackupData.serializer(), data).toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(plain, passphrase.toCharArray())
        context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted) }
            ?: error("Could not open that file for writing")
        media.size
    }

    suspend fun importFrom(uri: Uri, passphrase: String): Int = withContext(Dispatchers.IO) {
        require(passphrase.isNotBlank()) { "Enter the backup passphrase" }
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read that file")
        val plain = BackupCrypto.decrypt(raw, passphrase.toCharArray())
        val data = runCatching {
            json.decodeFromString(PrivateBackupData.serializer(), plain.toString(Charsets.UTF_8))
        }.getOrElse { throw IllegalArgumentException("That doesn't look like a Peek private vault backup") }
        if (data.app != "Peek" || data.kind != "private-vault") {
            throw IllegalArgumentException("That is not a Peek private vault backup")
        }
        val filesByCode = data.files.groupBy { it.code }
        data.media.forEach { record ->
            val destination = File(vaultRoot, record.code).apply { mkdirs() }
            filesByCode[record.code].orEmpty().forEach { file ->
                val target = File(destination, file.name)
                target.writeBytes(Base64.getDecoder().decode(file.dataBase64))
            }
            val imageNames = filesByCode[record.code].orEmpty()
                .filter { it.name.startsWith("image_") }
                .map { it.name.removeSuffix(".vault") }
            val videoName = filesByCode[record.code].orEmpty()
                .firstOrNull { it.name.startsWith("video_") }
                ?.name
                ?.removeSuffix(".vault")
            val downloadDir = File(context.filesDir, "downloads/${record.code}")
            val existing = mediaDao.get(record.code)
            mediaDao.upsert(
                MediaEntity(
                    code = record.code,
                    pk = record.pk ?: existing?.pk,
                    username = record.username,
                    userAvatar = existing?.userAvatar,
                    userFullName = existing?.userFullName.orEmpty(),
                    userVerified = existing?.userVerified ?: false,
                    isPrivate = true,
                    mediaType = record.mediaType,
                    productType = record.productType,
                    caption = record.caption,
                    displayUrl = record.displayUrl,
                    videoUrl = record.videoUrl,
                    likeCount = record.likeCount,
                    commentCount = record.commentCount,
                    takenAt = record.takenAt,
                    fetchedAt = existing?.fetchedAt ?: System.currentTimeMillis(),
                    carouselCount = existing?.carouselCount ?: 0,
                    childrenJson = record.childrenJson,
                    locationName = record.locationName,
                    audioTitle = record.audioTitle,
                    audioArtist = record.audioArtist,
                    isLiked = existing?.isLiked ?: false,
                    isSaved = existing?.isSaved ?: false,
                    isDownloaded = true,
                    downloadedAt = existing?.downloadedAt ?: System.currentTimeMillis(),
                    localImagePathsJson = imageNames
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(prefix = "[", postfix = "]") { "\"${File(downloadDir, it).absolutePath}\"" },
                    localVideoPath = videoName?.let { File(downloadDir, it).absolutePath },
                ),
            )
        }
        data.media.size
    }
}
