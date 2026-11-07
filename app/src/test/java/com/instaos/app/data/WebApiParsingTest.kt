package com.instaos.app.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 2: the web-feed parsers, against canned payloads. No login, no
 * network — these pin the exact JSON shapes the client expects from
 * Instagram's web GraphQL responses and the logged-in homepage.
 */
class WebApiParsingTest {
    private val feedJson = """
        {"data":{"user":{"edge_web_feed_timeline":{
          "edges":[{"node":{
            "id":"123","display_url":"https://cdn.example.com/p.jpg","is_video":false,
            "taken_at_timestamp":1730000000,
            "owner":{"id":"9","username":"someone","profile_pic_url":"https://cdn.example.com/a.jpg","is_verified":true},
            "edge_media_to_caption":{"edges":[{"node":{"text":"hello"}}]},
            "edge_liked_by":{"count":5},"edge_media_to_comment":{"count":2}
          }}],
          "page_info":{"has_next_page":true,"end_cursor":"CURSOR"}
        }}}}
    """.trimIndent()

    private val videoFeedJson = """
        {"data":{"user":{"edge_web_feed_timeline":{
          "edges":[{"node":{
            "id":"124","display_url":"https://cdn.example.com/thumb.jpg",
            "video_url":"https://cdn.example.com/v.mp4","is_video":true,
            "taken_at_timestamp":1730000001,
            "owner":{"id":"10","username":"reeler","profile_pic_url":"","is_verified":false},
            "edge_media_to_caption":{"edges":[]},
            "edge_liked_by":{"count":0},"edge_media_to_comment":{"count":0}
          }}],
          "page_info":{"has_next_page":false,"end_cursor":null}
        }}}}
    """.trimIndent()

    private val trayJson = """
        {"data":{"user":{"feed_reels_tray":{"edges":[
          {"node":{"id":"r1","seen":false,
            "user":{"id":"9","username":"someone","profile_pic_url":"https://cdn.example.com/a.jpg"}}},
          {"node":{"id":"r2","seen":true,
            "user":{"id":"10","username":"reeler","profile_pic_url":""}}}
        ]}}}}
    """.trimIndent()

    @Test
    fun feedResponseParsesAuthorsCountsAndCaption() {
        val page = WebInstagramWebApi.parseFeedResponse(feedJson)
        assertNotNull(page)
        assertEquals(1, page!!.items.size)
        val post = page.items.single()
        assertEquals("123", post.id)
        assertEquals("https://cdn.example.com/p.jpg", post.media.path)
        assertEquals(com.instaos.app.model.MediaKind.IMAGE, post.media.kind)
        assertEquals("hello", post.caption)
        assertEquals(5, post.likes)
        assertEquals(2, post.commentCount)
        assertEquals(1_730_000_000_000L, post.createdAt)
        assertEquals("someone", post.author?.username)
        assertEquals("9", post.author?.id)
        assertTrue(post.author?.verified == true)
        assertTrue(page.hasMore)
        assertEquals("CURSOR", page.nextCursor)
    }

    @Test
    fun videoNodesUseTheVideoUrl() {
        val page = WebInstagramWebApi.parseFeedResponse(videoFeedJson)
        val post = page!!.items.single()
        assertEquals(com.instaos.app.model.MediaKind.VIDEO, post.media.kind)
        assertEquals("https://cdn.example.com/v.mp4", post.media.path)
        assertFalse(page.hasMore)
    }

    @Test
    fun malformedFeedJsonReturnsNull() {
        assertNull(WebInstagramWebApi.parseFeedResponse("not json"))
        assertNull(WebInstagramWebApi.parseFeedResponse("""{"data":{}}"""))
    }

    @Test
    fun trayResponseParsesSeenFlags() {
        val tray = WebInstagramWebApi.parseTrayResponse(trayJson)
        assertNotNull(tray)
        assertEquals(2, tray!!.size)
        assertEquals("someone", tray[0].user.username)
        assertFalse(tray[0].seen)
        assertTrue(tray[1].seen)
    }

    @Test
    fun homePageHtmlYieldsTheEmbeddedFeed() {
        val html = "<html><head></head><body><script>window.data = " +
            feedJson + ";</script></body></html>"
        val page = WebInstagramWebApi.parseFeedFromHomePageHtml(html)
        assertNotNull(page)
        assertEquals("123", page!!.items.single().id)
    }

    @Test
    fun homePageWithoutFeedDataYieldsNull() {
        assertNull(WebInstagramWebApi.parseFeedFromHomePageHtml("<html>login page</html>"))
    }

    // ---- Phase 3: DM / story / search parsers, canned payloads ----

    private val dmInboxJson = """
        {"inbox":{"unseen_count":2,"threads":[
          {"thread_id":"t1","thread_title":"","is_group":false,"is_e2ee":false,
           "users":[{"pk":10,"username":"friend","full_name":"Friend","profile_pic_url":"https://cdn.example.com/a.jpg","is_verified":false}],
           "last_permanent_item":{"item_id":"m9","user_id":"10","timestamp":1730000000000000,"item_type":"text","text":"hey there"},
           "last_seen_at":{"9":{"timestamp":1729999990000000,"item_id":"m8"}}},
          {"thread_id":"t2","thread_title":"Group","is_group":true,"is_e2ee":true,
           "users":[{"pk":11,"username":"pal","full_name":"Pal","profile_pic_url":"","is_verified":false}],
           "last_seen_at":{}}
        ],"has_older":false},"status":"ok"}
    """.trimIndent()

    private val dmThreadJson = """
        {"thread":{"thread_id":"t1","thread_title":"","is_group":false,"is_e2ee":false,
          "users":[{"pk":10,"username":"friend","full_name":"Friend","profile_pic_url":"","is_verified":false}],
          "last_seen_at":{"10":{"timestamp":1730000002000000,"item_id":"m2"}},
          "items":[
            {"item_id":"m1","user_id":"10","timestamp":1730000001000000,"item_type":"text","text":"hi"},
            {"item_id":"m2","user_id":"9","timestamp":1730000002000000,"item_type":"text","text":"hello"},
            {"item_id":"m3","user_id":"10","timestamp":1730000003000000,"item_type":"like"},
            {"item_id":"m4","user_id":"10","timestamp":1730000004000000,"item_type":"reel_share"}
          ],
          "has_older":false},"status":"ok"}
    """.trimIndent()

    @Test
    fun dmInboxParsesThreadsUnreadAndE2ee() {
        val page = WebInstagramWebApi.parseDmInboxResponse(dmInboxJson, viewerId = "9")
        assertNotNull(page)
        assertEquals(2, page!!.unseenCount)
        assertEquals(2, page.threads.size)
        val oneToOne = page.threads[0]
        assertEquals("t1", oneToOne.threadId)
        assertEquals("friend", oneToOne.title)
        assertEquals("hey there", oneToOne.lastMessage)
        assertEquals(1_730_000_000_000L, oneToOne.lastMessageAt)
        // The last message is from someone else and newer than my last-seen.
        assertEquals(1, oneToOne.unreadCount)
        assertFalse(oneToOne.e2ee)
        val encrypted = page.threads[1]
        assertTrue(encrypted.e2ee)
        assertTrue(encrypted.isGroup)
        assertEquals(0, encrypted.unreadCount)
    }

    @Test
    fun dmThreadParsesMessagesNewestFirstWithOwnFlag() {
        val page = WebInstagramWebApi.parseDmThreadResponse(dmThreadJson, viewerId = "9")
        assertNotNull(page)
        assertEquals(4, page!!.messages.size)
        // Newest first: m4 (reel share), m3 (like), m2 (own), m1.
        assertEquals("m4", page.messages[0].id)
        assertEquals("m3", page.messages[1].id)
        assertEquals("m2", page.messages[2].id)
        assertEquals("m1", page.messages[3].id)
        assertTrue(page.messages[2].own)
        assertFalse(page.messages[0].own)
        assertFalse(page.messages[3].own)
        assertEquals("hello", page.messages[2].text)
        assertEquals("hi", page.messages[3].text)
        // Previews never leak the raw item_type.
        assertEquals("Shared a reel", page.messages[0].text)
        assertEquals("Liked a message", page.messages[1].text)
        // The other side saw my newest message m2.
        assertEquals("m2", page.thread.seenItemId)
        assertEquals("Shared a reel", page.thread.lastMessage)
    }

    @Test
    fun dmSendParsesPayload() {
        val body = """{"payload":{"item_id":"m100","timestamp":1730000005000000,"text":"hello","item_type":"text"},"status":"ok"}"""
        val message = WebInstagramWebApi.parseDmSendResponse(body, "t1", "9", "hello")
        assertNotNull(message)
        assertEquals("m100", message!!.id)
        assertEquals("t1", message.threadId)
        assertEquals("hello", message.text)
        assertTrue(message.own)
        assertEquals(com.instaos.app.model.DmMessageStatus.SENT, message.status)
        assertNull(WebInstagramWebApi.parseDmSendResponse("""{"status":"ok"}""", "t1", "9", "hello"))
    }

    @Test
    fun searchParsesUsersOnly() {
        val body = """
            {"users":[
              {"user":{"pk":11,"username":"found","full_name":"Found One","profile_pic_url":"","is_verified":true}},
              {"user":{"pk":12,"username":"","full_name":"No name"}}
            ],"status":"ok"}
        """.trimIndent()
        val users = WebInstagramWebApi.parseSearchResponse(body)
        assertNotNull(users)
        assertEquals(1, users!!.size)
        assertEquals("found", users[0].username)
        assertEquals("Found One", users[0].displayName)
        assertTrue(users[0].verified)
        assertEquals(emptyList<com.instaos.app.model.UserRef>(), WebInstagramWebApi.parseSearchResponse("""{"users":[]}"""))
    }

    @Test
    fun storyMediaPicksBestImageAndVideoUrl() {
        val body = """
            {"reels":{"9":{"user":{"pk":10,"username":"friend","full_name":"Friend","profile_pic_url":"","is_verified":false},
              "items":[
                {"id":"s1","taken_at":1730000000,"expiring_at":1730086400,"media_type":1,
                 "image_versions2":{"candidates":[
                   {"width":320,"height":568,"url":"https://cdn.example.com/small.jpg"},
                   {"width":1080,"height":1920,"url":"https://cdn.example.com/big.jpg"}]}},
                {"id":"s2","taken_at":1730000001,"media_type":2,
                 "video_versions":[{"url":"https://cdn.example.com/v.mp4","width":720,"height":1280}]}
              ]}},"status":"ok"}
        """.trimIndent()
        val stories = WebInstagramWebApi.parseStoryMediaResponse(body)
        assertNotNull(stories)
        assertEquals(2, stories!!.size)
        assertEquals("https://cdn.example.com/big.jpg", stories[0].media.path)
        assertEquals(com.instaos.app.model.MediaKind.IMAGE, stories[0].media.kind)
        assertEquals("friend", stories[0].author?.username)
        assertEquals("https://cdn.example.com/v.mp4", stories[1].media.path)
        assertEquals(com.instaos.app.model.MediaKind.VIDEO, stories[1].media.kind)
        assertNull(WebInstagramWebApi.parseStoryMediaResponse("not json"))
    }
}
