package com.instaos.app.data

import com.instaos.app.model.FeedPage
import com.instaos.app.model.Post
import com.instaos.app.model.StoryTrayItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The on-disk home-feed cache (phase 2, audit §8 step 3).
 *
 * Lives in its own small file — `feed-cache/feed.json` under filesDir —
 * never inside the whole-state JSON snapshot, so feed pages never bloat
 * the snapshot that is rewritten on every action. TTL is 5 minutes;
 * stale cache is still shown when the network fails, with the error
 * surfaced next to it. Capped at 50 posts.
 */
@Serializable
data class CachedFeed(
    val posts: List<Post> = emptyList(),
    val tray: List<StoryTrayItem> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val savedAt: Long = 0,
) {
    fun toFeedPage(): FeedPage = FeedPage(posts, nextCursor, hasMore)
}

class FeedCache(private val directory: File) {
    companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        const val MAX_POSTS = 50
    }

    private val file = File(directory, "feed.json")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun load(): CachedFeed? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<CachedFeed>(file.readText()) }.getOrNull()
    }

    fun isFresh(now: Long = System.currentTimeMillis()): Boolean {
        val cached = load() ?: return false
        return now - cached.savedAt in 0 until CACHE_TTL_MS
    }

    fun save(feed: CachedFeed) {
        directory.mkdirs()
        val capped = feed.copy(posts = feed.posts.take(MAX_POSTS))
        val temporary = File(directory, "feed.tmp")
        temporary.writeText(json.encodeToString(capped), Charsets.UTF_8)
        temporary.renameTo(file)
    }

    fun clear() {
        file.delete()
    }
}
