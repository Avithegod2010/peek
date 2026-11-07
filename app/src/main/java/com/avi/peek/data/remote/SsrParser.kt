package com.avi.peek.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses the JSON payloads that Instagram server-renders inside
 * `<script type="application/json" data-sjs>` tags.
 *
 * Instagram ships a small "Polaris" payload for logged-out visitors. It is the
 * only reliable, login-free source of profile and post data, so the whole app
 * is built around it. The shape can change without notice; every accessor is
 * defensive and returns null instead of throwing.
 */
class SsrParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    private val scriptRegex = Regex(
        pattern = """<script[^>]*type="application/json"[^>]*data-sjs[^>]*>(.*?)</script>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val captionDateRegex = Regex("""\bon ([A-Z][a-z]+ \d{1,2}, \d{4})\b""")
    private val captionDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

    private fun parseScripts(html: String): List<JsonElement> {
        val roots = ArrayList<JsonElement>(48)
        for (match in scriptRegex.findAll(html)) {
            val text = match.groupValues[1]
            if (text.length < 80) continue
            if (
                !text.contains("xig_user_by_username") &&
                !text.contains("video_versions") &&
                !text.contains("carousel_media") &&
                !text.contains("image_versions2") &&
                !text.contains("related_profiles")
            ) {
                continue
            }
            runCatching { json.parseToJsonElement(text) }.getOrNull()?.let(roots::add)
        }
        return roots
    }

    private inline fun forEachObject(
        roots: List<JsonElement>,
        crossinline action: (JsonObject) -> Boolean,
    ) {
        val stack = ArrayDeque<JsonElement>()
        roots.forEach(stack::addLast)
        var visited = 0
        while (stack.isNotEmpty() && visited < 250_000) {
            visited++
            val element = stack.removeLast()
            when (element) {
                is JsonObject -> {
                    if (action(element)) return
                    for (value in element.values) stack.addLast(value)
                }
                is JsonArray -> for (value in element) stack.addLast(value)
                else -> Unit
            }
        }
    }

    fun parseProfile(html: String, fallbackUsername: String): ScrapedProfile? {
        val roots = parseScripts(html)
        if (roots.isEmpty()) return null

        val candidates = ArrayList<JsonObject>()
        var timeline: JsonObject? = null
        var related: JsonObject? = null

        forEachObject(roots) { obj ->
            obj["xig_user_by_username"]?.let { node ->
                if (node is JsonObject) {
                    if (node["biography"] != null || node["username"] != null) {
                        candidates += node
                    }
                    if (timeline == null && node["polaris_ordered_timeline_connection"] != null) {
                        timeline = node
                    }
                }
            }
            obj["related_profiles"]?.let { if (related == null && it is JsonObject) related = it }
            false
        }

        val requested = fallbackUsername.trim().removePrefix("@").lowercase()
        // Prefer the canonical user node for the requested handle. Instagram can
        // include secondary/placeholder user nodes; picking one blindly used to
        // surface a stale `is_private` flag for public accounts.
        val p = candidates.firstOrNull {
            it.string("username")?.equals(requested, ignoreCase = true) == true && it["biography"] != null
        } ?: candidates.firstOrNull {
            it.string("username")?.equals(requested, ignoreCase = true) == true
        } ?: candidates.firstOrNull { it["biography"] != null }
            ?: return null
        val username = p.string("username") ?: fallbackUsername
        val timelineConnection = timeline?.obj("polaris_ordered_timeline_connection")
        val media = ArrayList<ProfileNode>()
        timelineConnection?.array("edges")?.forEach { edge ->
            val node = (edge as? JsonObject)?.obj("node") ?: return@forEach
            val code = node.string("code") ?: return@forEach
            media += ProfileNode(
                code = code,
                pk = node.string("pk") ?: "",
                mediaType = node.int("media_type") ?: 1,
                productType = node.string("product_type"),
                caption = node.obj("caption")?.string("text") ?: node.string("caption"),
                displayUrl = node.string("display_uri"),
                accessibilityCaption = node.string("accessibility_caption"),
                carouselCount = node.int("carousel_media_count") ?: 0,
            )
        }

        val highlights = p.obj("lox_highlights_connection")?.array("edges")?.mapNotNull { edge ->
            val node = (edge as? JsonObject)?.obj("node") ?: return@mapNotNull null
            val id = node.string("id") ?: return@mapNotNull null
            Highlight(
                id = id,
                title = node.string("title").orEmpty(),
                coverUrl = node.string("cover_media_cropped_thumbnail_url"),
            )
        }.orEmpty()

        val links = p.array("bio_links")?.mapNotNull { link ->
            val o = link as? JsonObject ?: return@mapNotNull null
            val url = o.string("url") ?: return@mapNotNull null
            BioLink(title = o.string("title").orEmpty(), url = url)
        }.orEmpty()

        val relatedProfiles = buildList {
            related?.array("edges")?.forEach { edge ->
                val node = (edge as? JsonObject)?.obj("node") ?: return@forEach
                val uname = node.string("username") ?: return@forEach
                add(
                    RelatedProfile(
                        username = uname,
                        fullName = node.string("full_name").orEmpty(),
                        profilePicUrl = node.string("profile_pic_url"),
                        isVerified = node.boolean("is_verified") ?: false,
                    ),
                )
            }
        }

        val pageInfo = timelineConnection?.obj("page_info")

        return ScrapedProfile(
            pk = p.string("pk") ?: p.string("id").orEmpty(),
            userId = p.string("id"),
            username = username,
            fullName = p.string("full_name").orEmpty(),
            biography = p.string("biography").orEmpty(),
            profilePicUrl = p.string("profile_pic_url"),
            // Public posts in the payload prove the account is readable; never
            // label it private when Instagram actually returned its media.
            isPrivate = (p.boolean("is_private") ?: false) && media.isEmpty(),
            isVerified = p.boolean("is_verified") ?: false,
            followerCount = p.long("follower_count") ?: 0L,
            followingCount = p.long("following_count") ?: 0L,
            bioLinks = links,
            highlights = highlights,
            latestReelMedia = p.long("latest_reel_media"),
            hasClips = p.boolean("has_any_clips") ?: false,
            media = media,
            hasNextPage = pageInfo?.boolean("has_next_page") ?: false,
            endCursor = pageInfo?.string("end_cursor"),
            relatedProfiles = relatedProfiles,
        )
    }

    fun parsePost(html: String, code: String): ScrapedPost? {
        val roots = parseScripts(html)
        if (roots.isEmpty()) return null

        val candidates = ArrayList<JsonObject>()
        forEachObject(roots) { obj ->
            if (
                obj.string("code") == code &&
                obj["media_type"] != null &&
                (obj["image_versions2"] != null || obj["video_versions"] != null || obj["carousel_media"] != null)
            ) {
                candidates += obj
            }
            false
        }
        val media = candidates.firstOrNull { it["taken_at"] != null }
            ?: candidates.firstOrNull()
            ?: return null

        val carousel = media.array("carousel_media")
        val children = if (carousel != null) {
            carousel.mapIndexedNotNull { index, element ->
                val node = element as? JsonObject ?: return@mapIndexedNotNull null
                parseChild(node, index)
            }
        } else {
            listOfNotNull(parseChild(media, 0))
        }

        val user = media.obj("user")
        val clips = media.obj("clips_metadata")
        val originalSound = clips?.obj("original_sound_info")
        val musicInfo = clips?.obj("music_info")?.obj("music_asset_info")

        val audioTitle = originalSound?.string("original_audio_title")
            ?: musicInfo?.string("title")
        val audioArtist = originalSound?.obj("ig_artist")?.string("username")
            ?: musicInfo?.string("display_artist")

        return ScrapedPost(
            code = media.string("code") ?: code,
            pk = media.string("pk"),
            username = user?.string("username").orEmpty(),
            userPk = user?.string("pk"),
            userAvatar = user?.string("profile_pic_url"),
            userVerified = user?.boolean("is_verified") ?: false,
            mediaType = media.int("media_type") ?: 1,
            productType = media.string("product_type"),
            caption = media.obj("caption")?.string("text") ?: media.string("caption"),
            takenAt = media.long("taken_at"),
            likeCount = media.long("like_count"),
            commentCount = media.long("comment_count"),
            locationName = media.obj("location")?.string("name"),
            audioTitle = audioTitle,
            audioArtist = audioArtist,
            children = children,
            accessibilityCaption = media.string("accessibility_caption"),
            width = media.int("original_width"),
            height = media.int("original_height"),
        )
    }

    private fun parseChild(node: JsonObject, index: Int): MediaChild? {
        val images = node.obj("image_versions2")?.array("candidates")
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { candidate ->
                val url = candidate.string("url") ?: return@mapNotNull null
                Triple(url, candidate.int("width") ?: 0, candidate.int("height") ?: 0)
            }
            ?.sortedByDescending { it.second }
            .orEmpty()

        val videos = node.array("video_versions")
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { candidate ->
                val url = candidate.string("url") ?: return@mapNotNull null
                Triple(url, candidate.int("width") ?: 0, candidate.int("height") ?: 0)
            }
            ?.sortedByDescending { it.second }
            .orEmpty()

        val bestImage = images.firstOrNull()
        val bestVideo = videos.firstOrNull()
        if (bestImage == null && bestVideo == null) return null

        return MediaChild(
            index = index,
            mediaType = node.int("media_type") ?: if (bestVideo != null) 2 else 1,
            imageUrl = bestImage?.first,
            imageUrlLow = images.lastOrNull()?.first,
            videoUrl = bestVideo?.first,
            videoUrlLow = videos.lastOrNull()?.first,
            width = bestImage?.second ?: bestVideo?.second ?: node.int("original_width") ?: 0,
            height = bestImage?.third ?: bestVideo?.third ?: node.int("original_height") ?: 0,
            accessibilityCaption = node.string("accessibility_caption"),
        )
    }

    /** Used when a profile payload has no timestamp; accessibility captions include a date. */
    fun dateFromAccessibilityCaption(caption: String?): Long? {
        if (caption == null) return null
        val raw = captionDateRegex.find(caption)?.groupValues?.getOrNull(1) ?: return null
        return runCatching {
            val date = LocalDate.parse(raw, captionDateFormatter)
            date.atTime(LocalTime.NOON).toEpochSecond(ZoneOffset.UTC)
        }.getOrNull()
    }

    /** Extracts the signed, short-lived cover URL from a share/embed page as a fallback. */
    fun parseOpenGraphImage(html: String): String? {
        val match = Regex("<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]+)\"")
            .find(html) ?: return null
        return match.groupValues.getOrNull(1)?.replace("&amp;", "&")
    }
}

internal fun JsonObject.string(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.contentOrNull
}

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
