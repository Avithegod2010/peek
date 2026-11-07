package com.avi.peek.data.repo

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.avi.peek.data.local.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Copies posts that are already saved offline into the device gallery.
 *
 * Android 10+ uses scoped MediaStore collections and needs no permission.
 * Android 8–9 writes to the public Pictures/Movies folder and asks for the
 * legacy write permission first.
 */
class GallerySaver(private val context: Context) {

    data class Summary(val images: Int, val videos: Int) {
        val total: Int get() = images + videos
    }

    class PermissionRequired :
        Exception("Allow storage access to save pictures to your gallery")

    fun needsLegacyPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

    suspend fun save(media: MediaEntity, writeSidecar: Boolean = false): Summary = withContext(Dispatchers.IO) {
        if (needsLegacyPermission()) throw PermissionRequired()
        var images = 0
        var videos = 0
        media.localImages().forEachIndexed { index, path ->
            val file = File(path)
            if (file.exists()) {
                val name = "peek_${safe(media.username)}_${safe(media.code)}_${index + 1}.jpg"
                if (copyToGallery(file, name, "image/jpeg", video = false, username = media.username)) {
                    images++
                }
            }
        }
        media.localVideoPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val name = "peek_${safe(media.username)}_${safe(media.code)}.mp4"
                if (copyToGallery(file, name, "video/mp4", video = true, username = media.username)) {
                    videos++
                }
            }
        }
        if (images == 0 && videos == 0) {
            throw IllegalStateException("No downloaded media was available to copy")
        }
        if (writeSidecar) {
            runCatching { writeSidecar(media) }
        }
        Summary(images, videos)
    }

    private fun writeSidecar(media: MediaEntity) {
        val metadata = buildJsonObject {
            put("app", "Peek")
            put("username", media.username)
            put("code", media.code)
            put("caption", media.caption ?: "")
            put("takenAt", media.takenAt ?: 0L)
            put("mediaType", media.mediaType)
            put("productType", media.productType ?: "")
            put("source", "https://www.instagram.com/p/${media.code}/")
            put("savedAt", System.currentTimeMillis())
        }.toString()
        val name = "peek_${safe(media.code)}.json"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Peek/${safe(media.username)}")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(metadata) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val directory = File(File(base, "Peek"), safe(media.username))
            if (directory.exists() || directory.mkdirs()) {
                File(directory, name).writeText(metadata)
            }
        }
    }

    private fun copyToGallery(
        source: File,
        displayName: String,
        mimeType: String,
        video: Boolean,
        username: String,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyScoped(source, displayName, mimeType, video, username)
        } else {
            copyLegacy(source, displayName, mimeType, video, username)
        }
    }

    private fun copyScoped(
        source: File,
        displayName: String,
        mimeType: String,
        video: Boolean,
        username: String,
    ): Boolean {
        val resolver = context.contentResolver
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val relative = buildString {
            append(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
            append("/Peek/")
            append(safe(username))
        }
        val alreadyThere = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, "$relative/"),
            null,
        )?.use { it.moveToFirst() } == true
        if (alreadyThere) return true

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        }
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            val output = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open gallery destination")
            output.use { sink ->
                source.inputStream().use { input -> input.copyTo(sink) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun copyLegacy(
        source: File,
        displayName: String,
        mimeType: String,
        video: Boolean,
        username: String,
    ): Boolean {
        if (needsLegacyPermission()) throw PermissionRequired()
        val base = Environment.getExternalStoragePublicDirectory(
            if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
        )
        val directory = File(File(base, "Peek"), safe(username))
        if (!directory.exists() && !directory.mkdirs()) return false
        val first = File(directory, displayName)
        if (first.exists()) return true
        val target = uniqueTarget(directory, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(mimeType),
            null,
        )
        return true
    }

    private fun uniqueTarget(directory: File, displayName: String): File {
        val first = File(directory, displayName)
        if (!first.exists()) return first
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(directory, "${base}_$index$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun safe(value: String): String =
        value.replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(60)
}
