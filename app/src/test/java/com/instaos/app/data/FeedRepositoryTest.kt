package com.instaos.app.data

import com.instaos.app.model.FeedPage
import com.instaos.app.model.Media
import com.instaos.app.model.Post
import com.instaos.app.model.StoryTrayItem
import com.instaos.app.model.UserRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/**
 * Phase 2: feed repository — cache TTL, forced refresh, paging merge,
 * and honest behaviour when there is no session or the server says slow down.
 */
class FeedRepositoryTest {
    private var now = 1_730_000_000_000L
    private val session = WebSession("sess", "csrf", "42")

    private fun post(id: String) = Post(
        id = id,
        media = Media(path = "https://cdn.example.com/$id.jpg"),
        caption = "post $id",
        createdAt = now,
        author = UserRef("9", "someone"),
    )

    private class CountingApi(
        var feedResult: ApiResult<FeedPage> = ApiResult.Success(FeedPage()),
        var trayResult: ApiResult<List<StoryTrayItem>> = ApiResult.Success(emptyList()),
    ) : InstagramWebApi {
        var feedCalls = 0
        override suspend fun getHomeFeed(session: WebSession, cursor: String?): ApiResult<FeedPage> {
            feedCalls++
            return feedResult
        }

        override suspend fun getStoriesTray(session: WebSession): ApiResult<List<StoryTrayItem>> = trayResult
        override suspend fun getCurrentUser(session: WebSession): ApiResult<UserRef> =
            ApiResult.Success(UserRef("42", "someone"))

        override suspend fun likePost(session: WebSession, mediaId: String, like: Boolean): ApiResult<Unit> =
            ApiResult.Success(Unit)

        override suspend fun savePost(session: WebSession, mediaId: String, save: Boolean): ApiResult<Unit> =
            ApiResult.Success(Unit)

        override suspend fun followUser(session: WebSession, userId: String, follow: Boolean): ApiResult<Unit> =
            ApiResult.Success(Unit)

        override suspend fun addComment(
            session: WebSession,
            mediaId: String,
            text: String,
            parentId: String?,
        ): ApiResult<Unit> = ApiResult.Success(Unit)

        // Phase 3 members — the feed tests never call them; empty successes.
        override suspend fun getDmInbox(session: WebSession): ApiResult<DmInboxPage> =
            ApiResult.Success(DmInboxPage(emptyList()))

        override suspend fun getDmThread(
            session: WebSession,
            threadId: String,
            cursor: String?,
        ): ApiResult<DmThreadPage> =
            ApiResult.Success(DmThreadPage(com.instaos.app.model.DmThread(threadId), emptyList()))

        override suspend fun sendDm(session: WebSession, threadId: String, text: String): ApiResult<com.instaos.app.model.DmMessage> =
            ApiResult.Success(
                com.instaos.app.model.DmMessage("m1", threadId, UserRef("42", "someone"), text, 1_730_000_000_000L)
            )

        override suspend fun markDmThreadSeen(session: WebSession, threadId: String, itemId: String): ApiResult<Unit> =
            ApiResult.Success(Unit)

        override suspend fun getStoryMedia(session: WebSession, reelIds: List<String>): ApiResult<List<com.instaos.app.model.Story>> =
            ApiResult.Success(emptyList())

        override suspend fun searchUsers(session: WebSession, query: String): ApiResult<List<UserRef>> =
            ApiResult.Success(emptyList())
    }

    private fun repository(api: CountingApi): Pair<FeedRepository, FeedCache> {
        val dir = Files.createTempDirectory("feed-cache-test").toFile()
        return FeedRepository(api, FeedCache(dir), nowMs = { now }) to FeedCache(dir)
    }

    @Test
    fun noSessionMeansEmptyFeedWithSignInError() = runBlocking {
        val api = CountingApi()
        val (repo, _) = repository(api)
        val result = repo.refresh(null)
        assertTrue(result.posts.isEmpty())
        assertEquals(FeedRepository.NOT_SIGNED_IN, result.error)
        assertEquals(0, api.feedCalls)
    }

    @Test
    fun freshCacheIsServedWithoutANetworkCall() = runBlocking {
        val api = CountingApi(
            feedResult = ApiResult.Success(FeedPage(listOf(post("1"), post("2")), "C2", true)),
        )
        val (repo, _) = repository(api)
        val first = repo.refresh(session)
        assertEquals(2, first.posts.size)
        assertEquals(1, api.feedCalls)
        val second = repo.refresh(session)
        assertEquals(2, second.posts.size)
        assertTrue(second.fromCache)
        assertEquals(1, api.feedCalls)
        val forced = repo.refresh(session, force = true)
        assertEquals(2, forced.posts.size)
        assertEquals(2, api.feedCalls)
    }

    @Test
    fun staleCacheTriggersARefresh() = runBlocking {
        val api = CountingApi(
            feedResult = ApiResult.Success(FeedPage(listOf(post("1")))),
        )
        val (repo, _) = repository(api)
        repo.refresh(session)
        assertEquals(1, api.feedCalls)
        now += FeedCache.CACHE_TTL_MS + 1
        repo.refresh(session)
        assertEquals(2, api.feedCalls)
    }

    @Test
    fun loadMoreMergesAndDeduplicates() = runBlocking {
        val api = CountingApi(
            feedResult = ApiResult.Success(FeedPage(listOf(post("1"), post("2")), "C2", true)),
        )
        val (repo, _) = repository(api)
        repo.refresh(session)
        api.feedResult = ApiResult.Success(FeedPage(listOf(post("2"), post("3")), null, false))
        val more = repo.loadMore(session, "C2")
        assertEquals(listOf("1", "2", "3"), more.posts.map { it.id })
        assertFalse(more.hasMore)
    }

    @Test
    fun rateLimitedFallsBackToCacheWithAnHonestError() = runBlocking {
        val api = CountingApi(
            feedResult = ApiResult.Success(FeedPage(listOf(post("1")))),
        )
        val (repo, _) = repository(api)
        repo.refresh(session)
        api.feedResult = ApiResult.RateLimited(60)
        val result = repo.refresh(session, force = true)
        assertEquals(1, result.posts.size)
        assertTrue(result.fromCache)
        assertEquals(FeedRepository.RATE_LIMITED, result.error)
    }

    @Test
    fun networkFailureWithNoCacheIsEmptyWithAnError() = runBlocking {
        val api = CountingApi(feedResult = ApiResult.NetworkError("down"))
        val (repo, _) = repository(api)
        val result = repo.refresh(session)
        assertTrue(result.posts.isEmpty())
        assertNotNull(result.error)
    }
}
