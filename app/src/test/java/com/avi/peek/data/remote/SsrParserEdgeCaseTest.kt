package com.avi.peek.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unhappy-path coverage for [SsrParser]: the payloads Instagram renders are
 * arbitrary JSON, so every accessor must degrade to a null/empty result instead
 * of throwing. The happy paths already live in `LogicTest`; these fixtures are
 * the same shapes with the fields that usually carry data removed.
 */
class SsrParserEdgeCaseTest {

    private val parser = SsrParser()

    private fun script(payload: String) = """<script type="application/json" data-sjs>$payload</script>"""

    @Test
    fun profileWithoutOptionalFieldsUsesSafeDefaults() {
        val payload = """
            {"data":{"user":{"xig_user_by_username":{
              "pk":"7","id":"7","username":"quiet","biography":"","full_name":""
            }}}}
        """.trimIndent()
        val profile = parser.parseProfile(script(payload), "@Quiet")
        assertNotNull(profile)
        assertEquals("quiet", profile!!.username)
        assertEquals("7", profile.pk)
        assertEquals("", profile.biography)
        assertEquals("", profile.fullName)
        assertNull(profile.profilePicUrl)
        assertNull(profile.latestReelMedia)
        assertFalse(profile.isPrivate)
        assertFalse(profile.isVerified)
        assertFalse(profile.hasNextPage)
        assertNull(profile.endCursor)
        assertEquals(0L, profile.followerCount)
        assertEquals(0L, profile.followingCount)
        assertEquals(0L, profile.postCount)
        assertTrue(profile.bioLinks.isEmpty())
        assertTrue(profile.highlights.isEmpty())
        assertTrue(profile.media.isEmpty())
        assertTrue(profile.relatedProfiles.isEmpty())
    }

    @Test
    fun profileWithEmptyTimelineKeepsMediaEmptyAndCursorAbsent() {
        val payload = """
            {"data":{"user":{"xig_user_by_username":{
              "pk":"8","username":"nasa","biography":"Space",
              "polaris_ordered_timeline_connection":{"edges":[],"page_info":{"has_next_page":true}}
            }}}}
        """.trimIndent()
        val profile = parser.parseProfile(script(payload), "nasa")
        assertNotNull(profile)
        assertTrue("an empty timeline must not invent posts", profile!!.media.isEmpty())
        // Instagram said there is another page but shipped no cursor: the caller
        // cannot page, and nothing is fabricated to pretend otherwise.
        assertTrue(profile.hasNextPage)
        assertNull(profile.endCursor)

        val withoutPageInfo = payload.replace(""","page_info":{"has_next_page":true}""", "")
        val noInfo = parser.parseProfile(script(withoutPageInfo), "nasa")
        assertNotNull(noInfo)
        assertFalse(noInfo!!.hasNextPage)
        assertNull(noInfo.endCursor)
        assertTrue(noInfo.media.isEmpty())
    }

    @Test
    fun userFeedWithoutCursorNeverClaimsAnotherPage() {
        val body = """
            {"items":[{"code":"a1","pk":"1","media_type":1,
              "image_versions2":{"candidates":[{"url":"https://cdn/a.jpg","width":100,"height":100}]}}],"more_available":true}
        """.trimIndent()

        val page = parser.parseUserFeed(body)
        assertNotNull(page)
        assertEquals(1, page!!.nodes.size)
        assertEquals("https://cdn/a.jpg", page.nodes[0].displayUrl)
        assertFalse("no cursor means no pageable next page", page.hasNextPage)
        assertNull(page.endCursor)

        val blankCursor = parser.parseUserFeed(body.replace(""""more_available":true""", """"more_available":true,"next_max_id":"   """"))
        assertNotNull(blankCursor)
        assertFalse(blankCursor!!.hasNextPage)
        assertNull(blankCursor.endCursor)

        // Cursor present but no more_available flag: the cursor wins.
        val cursorOnly = parser.parseUserFeed(body.replace(""""more_available":true""", """"next_max_id":"c2""""))
        assertNotNull(cursorOnly)
        assertTrue(cursorOnly!!.hasNextPage)
        assertEquals("c2", cursorOnly.endCursor)
    }

    @Test
    fun unexpectedEnvelopeShapesDegradeInsteadOfThrowing() {
        // Missing or wrongly typed envelope keys resolve to null, never an exception.
        assertNull(parser.parseUserFeed("{}"))
        assertNull(parser.parseUserFeed("""{"items":{"code":"x"}}"""))
        assertNull(parser.parseUserFeed("[1,2,3]"))
        assertNull(parser.parseUserFeed("null"))
        assertNull(parser.parseProfilePostsPage("""{"data":{"node":{}}}"""))
        assertNull(parser.parseApiPost("""{"items":[]}"""))
        assertNull(parser.parseProfile("<html><body>no payload</body></html>", "nasa"))
        assertNull(parser.parsePost("<html></html>", "abc"))

        // The envelope is right but the entries are junk: an empty page, not a crash.
        val junkItems = parser.parseUserFeed("""{"items":[42,"str",{},null]}""")
        assertNotNull(junkItems)
        assertTrue(junkItems!!.nodes.isEmpty())
        assertFalse(junkItems.hasNextPage)

        val nullEdges = parser.parseProfilePostsPage(
            """{"data":{"node":{"polaris_ordered_timeline_connection":{"edges":null}}}}""",
        )
        assertNotNull(nullEdges)
        assertTrue(nullEdges!!.nodes.isEmpty())
        assertFalse(nullEdges.hasNextPage)

        val wrongComments = parser.parseComments("""{"comments":{}}""")
        assertNotNull(wrongComments)
        assertTrue(wrongComments!!.comments.isEmpty())
        assertNull(wrongComments.nextMaxId)

        // Reels/highlight helpers return empty lists rather than null.
        assertTrue(parser.parseReelsMedia("{}").isEmpty())
        assertTrue(parser.parseReelsMedia("""{"reels_media":[]}""").isEmpty())
        assertTrue(parser.parseReelsMedia("""{"reels_media":[{"items":"nope"}]}""").isEmpty())
        assertTrue(parser.parseHighlightMedia("").isEmpty())
        assertTrue(parser.parseHighlightMedia("<html></html>").isEmpty())
    }

    @Test
    fun commentsDropReplySubtreesWhenTheParentIsDeleted() {
        val json = """
            {"comments":[
              {"pk":"gone","text":"  ","user":{"username":"x"},
               "child_comments":[{"pk":"c1","text":"orphan","user":{"username":"b"}}]},
              {"pk":"kept","text":"alive","user":{"username":"a","is_verified":true},
               "child_comments":[{"pk":"c2","text":"r2","user":{"username":"c"}}]}
            ],"next_max_id":""}
        """.trimIndent()
        val page = parser.parseComments(json)
        assertNotNull(page)
        assertEquals(1, page!!.comments.size)
        val kept = page.comments[0]
        assertEquals("kept", kept.pk)
        assertTrue(kept.verified)
        assertEquals(1, kept.replies.size)
        assertEquals("kept", kept.replies[0].parentPk)
        // The deleted parent takes its replies with it: no orphan node survives
        // pointing at a parent that is not in the page.
        assertEquals(0, page.comments.count { it.pk == "gone" })
        assertTrue(page.comments.none { it.replies.any { reply -> reply.parentPk == "gone" } })
    }

    @Test
    fun nestedRepliesChainTheirParentKeys() {
        val json = """
            {"comments":[
              {"pk":"p","text":"top","user":{"username":"a"},
               "child_comments":[
                 {"pk":"c","text":"mid","user":{"username":"b"},
                  "child_comments":[{"pk":"g","text":"deep","user":{"username":"c"}}]}
               ]}
            ]}
        """.trimIndent()
        val page = parser.parseComments(json)
        assertNotNull(page)
        val top = page!!.comments.single()
        assertEquals("p", top.pk)
        assertNull(top.parentPk)
        val mid = top.replies.single()
        assertEquals("p", mid.parentPk)
        val deep = mid.replies.single()
        assertEquals("deep", deep.text)
        assertEquals("c", deep.parentPk)
        assertTrue("the deepest reply has no children of its own", deep.replies.isEmpty())
    }

    @Test
    fun commentAuthorFallsBackWhenTheUserObjectIsMissing() {
        val page = parser.parseComments(
            """{"comments":[{"pk":"k1","text":"hi","like_count":3},
               {"pk":"k2","text":"yo","user":{"username":"  "},"created_at":1700000000}]}""",
        )
        assertNotNull(page)
        assertEquals(2, page!!.comments.size)
        val anonymous = page.comments[0]
        assertEquals("instagram", anonymous.username)
        assertNull(anonymous.avatarUrl)
        assertNull(anonymous.createdAt)
        assertFalse(anonymous.verified)
        assertEquals(3L, anonymous.likeCount)
        val blank = page.comments[1]
        assertEquals("instagram", blank.username)
        assertEquals(1700000000L, blank.createdAt)
    }

    @Test
    fun commentPageWithoutCursorOrCommentsIsStillUsable() {
        val empty = parser.parseComments("{}")
        assertNotNull(empty)
        assertTrue(empty!!.comments.isEmpty())
        assertNull(empty.nextMaxId)

        val blankCursor = parser.parseComments("""{"comments":[],"next_max_id":""}""")
        assertNotNull(blankCursor)
        assertNull(blankCursor!!.nextMaxId)

        val paged = parser.parseComments("""{"comments":[],"next_max_id":"next"}""")
        assertNotNull(paged)
        assertEquals("next", paged!!.nextMaxId)
    }
}
