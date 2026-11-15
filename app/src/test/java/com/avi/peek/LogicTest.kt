package com.avi.peek

import com.avi.peek.data.local.MediaEntity
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.remote.NetworkAllowlist
import com.avi.peek.data.remote.SsrParser
import com.avi.peek.data.repo.BackupCrypto
import com.avi.peek.data.repo.VisibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test
    fun backupCryptoRoundTrip() {
        val plain = "Peek private metadata".toByteArray()
        val encrypted = BackupCrypto.encrypt(plain, "correct horse battery staple".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(encrypted.contentEquals(plain))
        val restored = BackupCrypto.decrypt(encrypted, "correct horse battery staple".toCharArray())
        assertTrue(plain.contentEquals(restored))
    }

    @Test(expected = Exception::class)
    fun backupCryptoRejectsWrongPassphrase() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "right".toCharArray())
        BackupCrypto.decrypt(encrypted, "wrong".toCharArray())
    }

    @Test
    fun backupCryptoRejectsTruncatedAndTamperedData() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "passphrase".toCharArray())
        assertTrue(runCatching { BackupCrypto.decrypt(encrypted.copyOfRange(0, 20), "passphrase".toCharArray()) }.isFailure)

        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        assertTrue(runCatching { BackupCrypto.decrypt(tampered, "passphrase".toCharArray()) }.isFailure)
        assertFalse(BackupCrypto.isEncrypted("not a backup".toByteArray()))
    }

    @Test
    fun backupCryptoHandlesEmptyPayload() {
        val encrypted = BackupCrypto.encrypt(ByteArray(0), "passphrase".toCharArray())
        assertTrue(encrypted.size > 33)
        assertTrue(BackupCrypto.decrypt(encrypted, "passphrase".toCharArray()).isEmpty())
    }

    @Test
    fun allowlistAcceptsOnlyPeekNetworkHosts() {
        assertTrue(NetworkAllowlist.isAllowedUrl("https://www.instagram.com/p/abc/"))
        assertTrue(NetworkAllowlist.isAllowedUrl("https://i.instagram.com/api/v1/media/1/info/"))
        assertTrue(NetworkAllowlist.isAllowedUrl("https://scontent-arn2-1.cdninstagram.com/v/t51/x.jpg"))
        assertTrue(NetworkAllowlist.isAllowedUrl("https://instagram.fbcdn.net/v/t51/x.jpg"))
        assertTrue(NetworkAllowlist.isAllowedUrl("https://html.duckduckgo.com/html/"))
    }

    @Test
    fun allowlistRejectsLookalikeAndCleartextHosts() {
        assertFalse(NetworkAllowlist.isAllowedUrl("https://instagram.com.evil.example/p/abc/"))
        assertFalse(NetworkAllowlist.isAllowedUrl("https://evilinstagram.com/p/abc/"))
        assertFalse(NetworkAllowlist.isAllowedUrl("https://cdninstagram.com.evil.example/x.jpg"))
        assertFalse(NetworkAllowlist.isAllowedUrl("http://www.instagram.com/p/abc/"))
        assertFalse(NetworkAllowlist.isAllowedUrl("https://example.com/"))
    }

    @Test
    fun privateVisibilityPolicyFailsClosed() {
        assertTrue(VisibilityPolicy.canShow(isPrivate = false, privateContentRevealed = false))
        assertFalse(VisibilityPolicy.canShow(isPrivate = true, privateContentRevealed = false))
        assertTrue(VisibilityPolicy.canShow(isPrivate = true, privateContentRevealed = true))
        assertFalse(VisibilityPolicy.privateFeedVisible(false))
        assertTrue(VisibilityPolicy.privateFeedVisible(true))
    }

    @Test
    fun normalizesInstagramHandlesAndLinks() {
        assertEquals("nasa", GuestInstagramClient.normalizeUsername("NASA"))
        assertEquals("nasa", GuestInstagramClient.normalizeUsername("@nasa"))
        assertEquals("nasa", GuestInstagramClient.normalizeUsername("https://instagram.com/nasa/"))
        assertEquals("nasa", GuestInstagramClient.normalizeUsername("instagram.com/nasa?hl=en"))
        assertEquals(null, GuestInstagramClient.normalizeUsername("https://instagram.com/p/abc/"))
        assertEquals(null, GuestInstagramClient.normalizeUsername("   "))
    }

    @Test
    fun parsesAuthenticatedUserFeedPage() {
        val json = """
            {"items":[
              {"code":"abc","pk":"1","media_type":1,"caption":{"text":"hi"},
               "image_versions2":{"candidates":[{"url":"https://cdn/x.jpg","width":100,"height":100}]}}
            ],"more_available":true,"next_max_id":"next"}
        """.trimIndent()
        val page = SsrParser().parseUserFeed(json)
        assertNotNull(page)
        assertEquals(1, page!!.nodes.size)
        assertEquals("abc", page.nodes[0].code)
        assertTrue(page.hasNextPage)
        assertEquals("next", page.endCursor)
    }

    @Test
    fun parsesProfilePayloadWithMediaLinksAndHighlights() {
        val payload = """
            {"data":{"user":{
              "xig_user_by_username":{
                "pk":"42","id":"42","username":"NASA","full_name":"NASA",
                "biography":"Space","profile_pic_url":"https://cdn/avatar.jpg",
                "is_private":false,"is_verified":true,"follower_count":1000,
                "following_count":12,"media_count":7,"latest_reel_media":1700000000,
                "has_any_clips":true,
                "bio_links":[{"title":"Site","url":"https://nasa.gov"}],
                "lox_highlights_connection":{"edges":[{"node":{"id":"h1","title":"Launch","cover_media_cropped_thumbnail_url":"https://cdn/h.jpg"}}]}
              },
              "polaris_ordered_timeline_connection":{
                "edges":[{"node":{"code":"abc","pk":"9","media_type":1,"display_uri":"https://cdn/p.jpg","caption":{"text":"hello"},"carousel_media_count":2}}],
                "page_info":{"has_next_page":true,"end_cursor":"next-page"}
              }
            }}}
        """.trimIndent()
        val html = """<script type="application/json" data-sjs>$payload</script>"""
        val profile = SsrParser().parseProfile(html, "nasa")
        assertNotNull(profile)
        assertEquals("NASA", profile!!.username)
        assertEquals("NASA", profile.fullName)
        assertTrue(profile.isVerified)
        assertEquals(1000L, profile.followerCount)
        assertEquals(1, profile.bioLinks.size)
        assertEquals("https://nasa.gov", profile.bioLinks[0].url)
        assertEquals(1, profile.highlights.size)
        assertEquals("Launch", profile.highlights[0].title)
        assertEquals(1, profile.media.size)
        assertEquals("hello", profile.media[0].caption)
        assertEquals(2, profile.media[0].carouselCount)
        assertTrue(profile.hasNextPage)
        assertEquals("next-page", profile.endCursor)
    }

    @Test
    fun parsesRelayPostsPageAndApiPost() {
        val pageJson = """
            {"data":{"node":{"polaris_ordered_timeline_connection":{
              "edges":[{"node":{"code":"p1","pk":"11","media_type":8,"display_uri":"https://cdn/a.jpg","carousel_media_count":3}}],
              "page_info":{"has_next_page":true,"end_cursor":"cursor-2"}
            }}}}
        """.trimIndent()
        val page = SsrParser().parseProfilePostsPage(pageJson)
        assertNotNull(page)
        assertEquals(1, page!!.nodes.size)
        assertEquals("p1", page.nodes[0].code)
        assertEquals(3, page.nodes[0].carouselCount)
        assertTrue(page.hasNextPage)

        val postJson = """
            {"items":[{"code":"p2","pk":"22","media_type":2,"taken_at":1700000000,
              "caption":{"text":"clip"},"like_count":5,"comment_count":2,
              "user":{"username":"nasa","is_verified":true},
              "video_versions":[{"url":"https://cdn/v.mp4","width":720,"height":1280}],
              "image_versions2":{"candidates":[{"url":"https://cdn/c.jpg","width":720,"height":1280}]}}]}
        """.trimIndent()
        val post = SsrParser().parseApiPost(postJson)
        assertNotNull(post)
        assertEquals("p2", post!!.code)
        assertEquals("nasa", post.username)
        assertEquals("clip", post.caption)
        assertEquals(5L, post.likeCount)
        assertTrue(post.children.any { it.videoUrl == "https://cdn/v.mp4" })
    }

    @Test
    fun malformedPayloadsFailClosed() {
        val parser = SsrParser()
        assertNull(parser.parseProfile("<html>not json</html>", "nasa"))
        assertNull(parser.parseProfilePostsPage("{broken"))
        assertNull(parser.parseUserFeed("[]"))
        assertNull(parser.parseApiPost("{}"))
        assertTrue(parser.parseComments("{\"comments\":[]}")!!.comments.isEmpty())
    }

    @Test
    fun mediaHelpersTolerateMissingOrMalformedJson() {
        val empty = MediaEntity(code = "m1", username = "nasa")
        assertTrue(empty.children().isEmpty())
        assertTrue(empty.localImages().isEmpty())

        val malformed = empty.copy(childrenJson = "{not json", localImagePathsJson = "nope")
        assertTrue(malformed.children().isEmpty())
        assertTrue(malformed.localImages().isEmpty())

        val valid = empty.copy(
            childrenJson = """[{"index":1,"mediaType":2,"videoUrl":"https://cdn/v.mp4"}]""",
            localImagePathsJson = """["/data/user/0/peek/files/downloads/m1/image_0.jpg"]""",
        )
        assertEquals(1, valid.children().size)
        assertEquals("https://cdn/v.mp4", valid.children()[0].videoUrl)
        assertEquals(1, valid.localImages().size)
    }

    @Test
    fun parsesNestedCommentReplies() {
        val json = """
            {"comments":[
              {"pk":"p1","text":"top","user":{"username":"a"},
               "child_comments":[{"pk":"c1","text":"reply","user":{"username":"b"}}]}
            ]}
        """.trimIndent()
        val page = SsrParser().parseComments(json)
        assertNotNull(page)
        assertEquals(1, page!!.comments.size)
        assertEquals(1, page.comments[0].replies.size)
        assertEquals("p1", page.comments[0].replies[0].parentPk)
    }
}
