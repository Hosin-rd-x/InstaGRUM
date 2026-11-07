package com.instaos.app.data

import com.instaos.app.model.FeedPage
import com.instaos.app.model.Post
import com.instaos.app.model.StoryTrayItem

/**
 * Home-feed loading: web API + disk cache + honest errors (phase 2).
 *
 * - No session → the disk cache if there is one, otherwise an empty feed
 *   with a "sign in" error. Never a crash, never fake posts.
 * - Fresh cache (< 5 min) → served without a network call, unless forced.
 * - Network failure → stale cache + the error, so the app still opens.
 * - Tray failure alone never fails the feed (the tray just stays as cached).
 */
data class FeedLoadResult(
    val posts: List<Post>,
    val tray: List<StoryTrayItem>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val error: String? = null,
    val fromCache: Boolean = false,
)

class FeedRepository(
    private val api: InstagramWebApi,
    private val cache: FeedCache,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun refresh(session: WebSession?, force: Boolean = false): FeedLoadResult {
        val cached = cache.load()
        if (session == null || !session.isLoggedIn) {
            return if (cached != null) {
                FeedLoadResult(cached.posts, cached.tray, cached.nextCursor, cached.hasMore, NOT_SIGNED_IN, fromCache = true)
            } else {
                FeedLoadResult(emptyList(), emptyList(), null, false, NOT_SIGNED_IN)
            }
        }
        if (!force && cached != null && nowMs() - cached.savedAt in 0 until FeedCache.CACHE_TTL_MS) {
            return FeedLoadResult(cached.posts, cached.tray, cached.nextCursor, cached.hasMore, fromCache = true)
        }
        val tray = when (val trayResult = api.getStoriesTray(session)) {
            is ApiResult.Success -> trayResult.value
            else -> cached?.tray.orEmpty()
        }
        return when (val result = api.getHomeFeed(session, null)) {
            is ApiResult.Success -> {
                val page = result.value
                cache.save(CachedFeed(page.items, tray, page.nextCursor, page.hasMore, nowMs()))
                FeedLoadResult(page.items, tray, page.nextCursor, page.hasMore)
            }
            is ApiResult.RateLimited -> FeedLoadResult(
                cached?.posts.orEmpty(), cached?.tray.orEmpty(), cached?.nextCursor, cached?.hasMore == true,
                RATE_LIMITED, fromCache = cached != null,
            )
            is ApiResult.HttpError -> FeedLoadResult(
                cached?.posts.orEmpty(), cached?.tray.orEmpty(), cached?.nextCursor, cached?.hasMore == true,
                "Instagram answered ${result.code}. Showing what was saved, if anything.",
                fromCache = cached != null,
            )
            is ApiResult.NetworkError -> FeedLoadResult(
                cached?.posts.orEmpty(), cached?.tray.orEmpty(), cached?.nextCursor, cached?.hasMore == true,
                "No connection. Showing what was saved, if anything.",
                fromCache = cached != null,
            )
        }
    }

    suspend fun loadMore(session: WebSession?, cursor: String?): FeedLoadResult {
        val cached = cache.load()
        if (session == null || !session.isLoggedIn || cursor == null) {
            return FeedLoadResult(
                cached?.posts.orEmpty(), cached?.tray.orEmpty(), cached?.nextCursor,
                cached?.hasMore == true, NOT_SIGNED_IN, fromCache = true,
            )
        }
        return when (val result = api.getHomeFeed(session, cursor)) {
            is ApiResult.Success -> {
                val page = result.value
                val merged = (cached?.posts.orEmpty() + page.items)
                    .distinctBy { it.id }
                    .take(FeedCache.MAX_POSTS)
                val tray = cached?.tray.orEmpty()
                cache.save(CachedFeed(merged, tray, page.nextCursor, page.hasMore, nowMs()))
                FeedLoadResult(merged, tray, page.nextCursor, page.hasMore)
            }
            is ApiResult.RateLimited -> FeedLoadResult(
                cached?.posts.orEmpty(), cached?.tray.orEmpty(), cursor, true, RATE_LIMITED, fromCache = true,
            )
            is ApiResult.HttpError -> FeedLoadResult(
                cached?.posts.orEmpty(), cached?.tray.orEmpty(), cursor, true,
                "Instagram answered ${result.code}.", fromCache = true,
            )
            is ApiResult.NetworkError -> FeedLoadResult(
                cached?.posts.orEmpty(), cached?.tray.orEmpty(), cursor, true,
                "No connection.", fromCache = true,
            )
        }
    }

    companion object {
        const val NOT_SIGNED_IN = "Sign in to load your feed."
        const val RATE_LIMITED = "Instagram asked us to slow down. Try again in a bit."
    }
}
