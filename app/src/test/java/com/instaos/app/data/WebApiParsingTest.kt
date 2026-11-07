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
}
