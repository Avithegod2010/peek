package com.avi.peek

import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.remote.SsrParser
import com.avi.peek.data.repo.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
