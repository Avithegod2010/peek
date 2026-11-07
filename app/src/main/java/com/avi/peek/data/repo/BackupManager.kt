package com.avi.peek.data.repo

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.avi.peek.data.local.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BackupData(
    val app: String = "Peek",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val follows: List<String> = emptyList(),
    val saved: List<String> = emptyList(),
    val liked: List<String> = emptyList(),
)

@Serializable
data class ArchiveData(
    val app: String = "Peek",
    val kind: String = "archive",
    val createdAt: Long = System.currentTimeMillis(),
    val following: List<String> = emptyList(),
    val posts: List<ArchivePost> = emptyList(),
)

@Serializable
data class ArchivePost(
    val code: String,
    val username: String,
    val caption: String? = null,
    val takenAt: Long? = null,
    val mediaType: Int = 1,
    val saved: Boolean = false,
    val liked: Boolean = false,
    val downloaded: Boolean = false,
    val localFiles: List<String> = emptyList(),
    val source: String? = null,
)

class BackupManager(
    private val context: Context,
    private val repository: PeekRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri, passphrase: String? = null): Int {
        val data = repository.exportBackup()
        val text = json.encodeToString(BackupData.serializer(), data)
        val plain = text.toByteArray(Charsets.UTF_8)
        val bytes = if (passphrase.isNullOrBlank()) {
            plain
        } else {
            BackupCrypto.encrypt(plain, passphrase.toCharArray())
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Could not open that file for writing")
        return data.follows.size
    }

    suspend fun importFrom(uri: Uri, passphrase: String? = null): ImportSummary {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read that file")
        val text = if (BackupCrypto.isEncrypted(raw)) {
            if (passphrase.isNullOrBlank()) {
                throw IllegalArgumentException("This backup is encrypted. Enter its passphrase.")
            }
            val decrypted = try {
                BackupCrypto.decrypt(raw, passphrase.toCharArray())
            } catch (e: Exception) {
                throw IllegalArgumentException("Wrong passphrase, or the backup is damaged.")
            }
            decrypted.toString(Charsets.UTF_8)
        } else {
            raw.toString(Charsets.UTF_8)
        }
        val data = runCatching { json.decodeFromString(BackupData.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("That doesn't look like a Peek backup") }
        if (data.app != "Peek") throw IllegalArgumentException("That backup belongs to another app")
        return repository.importBackup(data)
    }

    /** A metadata-only archive that can be re-imported or read by other tools. */
    suspend fun exportJsonArchive(uri: Uri) = writeJsonArchive(uri, buildArchive())

    suspend fun exportProfileArchive(
        uri: Uri,
        username: String,
        html: Boolean,
        since: Long = 0L,
    ) {
        val media = repository.profileMedia(username)
            .filter { since <= 0L || (it.downloadedAt ?: it.fetchedAt) > since }
        val following = repository.following.first().map { it.username }
        val archive = archiveFrom(media, following)
        if (html) writeHtmlArchive(uri, archive) else writeJsonArchive(uri, archive)
    }

    suspend fun exportMediaArchive(uri: Uri, codes: List<String>, html: Boolean) {
        val media = repository.mediaByCodesOnce(codes)
        val ordered = codes.mapNotNull { code -> media.firstOrNull { it.code == code } }
        val following = repository.following.first().map { it.username }
        val archive = archiveFrom(ordered, following)
        if (html) writeHtmlArchive(uri, archive) else writeJsonArchive(uri, archive)
    }

    private fun writeJsonArchive(uri: Uri, archive: ArchiveData) {
        val text = json.encodeToString(ArchiveData.serializer(), archive)
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open that file for writing")
    }

    /** A readable, self-contained page. Cached images are embedded when present. */
    suspend fun exportHtmlArchive(uri: Uri) = writeHtmlArchive(uri, buildArchive())

    private suspend fun writeHtmlArchive(uri: Uri, archive: ArchiveData) = withContext(Dispatchers.IO) {
        val following = archive.following.joinToString("") { handle ->
            "<li><a href=\"https://www.instagram.com/${escape(handle)}/\">@${escape(handle)}</a></li>"
        }
        val cards = archive.posts.joinToString("\n") { post ->
            val images = post.localFiles.filter { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".webp", true) }
                .mapNotNull { filePath -> embedImage(filePath) }
                .joinToString("") { "<img alt=\"Cached image\" src=\"$it\" />" }
            val imageBlock = images.ifBlank {
                "<div class=\"placeholder\">${if (post.downloaded) "Cached image unavailable" else "Not downloaded"}</div>"
            }
            val badges = buildString {
                if (post.saved) append("<span>saved</span>")
                if (post.liked) append("<span>liked</span>")
                if (post.downloaded) append("<span>offline</span>")
            }
            """
            <article>
              <div class="media">$imageBlock</div>
              <div class="body">
                <h2>@${escape(post.username)}</h2>
                <p class="caption">${escape(post.caption ?: "No caption cached.")}</p>
                <div class="badges">$badges</div>
                <a class="source" href="https://www.instagram.com/p/${escape(post.code)}/">Open on Instagram ↗</a>
              </div>
            </article>
            """.trimIndent()
        }
        val styles = """
            :root { color-scheme: light dark; }
            * { box-sizing: border-box; }
            body { margin: 0; padding: 28px; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; background: #f7f4fb; color: #1c1b1f; }
            header { max-width: 1050px; margin: 0 auto 24px; }
            h1 { margin: 0; font-size: 30px; letter-spacing: -0.02em; }
            .lede { color: #625b71; margin-top: 8px; }
            .circle { margin-top: 16px; columns: 3; font-size: 14px; }
            .grid { max-width: 1050px; margin: 0 auto; display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
            article { background: #fff; border-radius: 24px; overflow: hidden; box-shadow: 0 8px 30px rgba(50, 30, 80, .08); }
            .media img { width: 100%; height: 260px; object-fit: cover; display: block; }
            .placeholder { height: 160px; display: grid; place-items: center; background: #ede7f6; color: #7b7288; font-size: 13px; }
            .body { padding: 16px; }
            h2 { font-size: 16px; margin: 0 0 6px; }
            .caption { font-size: 14px; line-height: 1.45; color: #49454f; margin: 0 0 12px; white-space: pre-wrap; }
            .badges span { display: inline-block; margin: 0 6px 6px 0; padding: 3px 8px; border-radius: 999px; background: #e8def8; color: #4a4458; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; }
            .source { font-size: 13px; color: #6750a4; text-decoration: none; font-weight: 600; }
            footer { max-width: 1050px; margin: 28px auto 0; color: #625b71; font-size: 12px; }
            @media (prefers-color-scheme: dark) {
              body { background: #141218; color: #e6e0e9; }
              article { background: #1d1b20; box-shadow: none; }
              .placeholder { background: #2b2831; color: #cac4d0; }
              .caption { color: #cac4d0; }
              .badges span { background: #4a4458; color: #eaddff; }
              .source { color: #d0bcff; }
              .lede, footer { color: #cac4d0; }
            }
        """.trimIndent()
        val html = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Peek archive</title>
              <style>$styles</style>
            </head>
            <body>
              <header>
                <h1>Peek archive</h1>
                <p class="lede">${archive.posts.size} cached posts · exported ${archive.createdAt}</p>
                <ul class="circle">${following.ifBlank { "<li>No follows yet</li>" }}</ul>
              </header>
              <main class="grid">$cards</main>
              <footer>Made with Peek. Read-only, account-free, and stored on your device. Post media belongs to its creators.</footer>
            </body>
            </html>
        """.trimIndent()
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(html.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open that file for writing")
    }

    private suspend fun buildArchive(): ArchiveData {
        val follows = repository.following.first().map { it.username }
        val saved = repository.saved.first()
        val liked = repository.liked.first()
        val downloaded = repository.downloaded.first()
        val byCode = LinkedHashMap<String, MediaEntity>()
        (saved + liked + downloaded).forEach { entity -> byCode.putIfAbsent(entity.code, entity) }
        return archiveFrom(byCode.values.toList(), follows)
    }

    private fun archiveFrom(media: List<MediaEntity>, following: List<String>): ArchiveData {
        val posts = media.map { item ->
            ArchivePost(
                code = item.code,
                username = item.username,
                caption = item.caption,
                takenAt = item.takenAt,
                mediaType = item.mediaType,
                saved = item.isSaved,
                liked = item.isLiked,
                downloaded = item.isDownloaded,
                localFiles = buildList {
                    addAll(item.localImages())
                    item.localVideoPath?.let { add(it) }
                },
                source = item.displayUrl,
            )
        }
        return ArchiveData(following = following, posts = posts)
    }

    private fun embedImage(path: String): String? = runCatching {
        val file = File(path)
        if (!file.exists() || file.length() > 1_500_000L) return null
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        "data:$mime;base64,$encoded"
    }.getOrNull()

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private val jsonList = Json { ignoreUnknownKeys = true }

@Suppress("unused")
private fun parseArchive(text: String): List<ArchivePost> = runCatching {
    jsonList.decodeFromString(ListSerializer(ArchivePost.serializer()), text)
}.getOrDefault(emptyList())
