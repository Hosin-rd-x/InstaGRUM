package com.instaos.app.data

import com.instaos.app.model.DmMessage
import com.instaos.app.model.DmMessageStatus
import com.instaos.app.model.DmThread
import com.instaos.app.model.FeedPage
import com.instaos.app.model.Post
import com.instaos.app.model.Media
import com.instaos.app.model.MediaKind
import com.instaos.app.model.Story
import com.instaos.app.model.StoryTrayItem
import com.instaos.app.model.UserRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Results from the Instagram web session layer.
 *
 * [RateLimited] is returned instead of hitting the network again while a
 * back-off is active — the UI should show "try again in a bit", not retry
 * in a loop (that is exactly how accounts get `spam:true` locked).
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class HttpError(val code: Int, val message: String) : ApiResult<Nothing>
    data class NetworkError(val message: String) : ApiResult<Nothing>
    data class RateLimited(val retryAfterSeconds: Long?) : ApiResult<Nothing>
}

/** One page of a DM thread: the thread itself plus its messages. */
data class DmThreadPage(
    val thread: DmThread,
    val messages: List<DmMessage>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

/** The DM inbox: threads plus Instagram's own total unread count. */
data class DmInboxPage(
    val threads: List<DmThread>,
    val unseenCount: Int = 0,
)

/**
 * The Instagram web-session client (phase 2 feed, phase 3 DM/search/stories).
 *
 * This is the ONLY layer that knows about Instagram endpoints. UI and the
 * reducer depend on this interface, never on [WebInstagramWebApi], so unit
 * tests run against [FakeInstagramWebApi] with no login and no network.
 *
 * Rules, all from web-method-report.md:
 * - Real browser session only: `sessionid` + `csrftoken` cookies from the
 *   login WebView, browser headers (`x-ig-app-id`, `x-csrftoken`,
 *   `x-requested-with: XMLHttpRequest`, a standard browser UA).
 * - NO `/api/v1/feed/timeline/` (private app API with signed-request and
 *   device fingerprinting). The feed comes from the web GraphQL query the
 *   browser session itself uses — or, when no GraphQL `doc_id` is
 *   configured, from the feed JSON embedded in the logged-in homepage.
 *   GraphQL `doc_id`s rotate with Instagram's deploys; [GraphQlDocIds]
 *   must be captured from a live logged-in browser session (phase 3, on
 *   the phone). Until then the homepage path is the default.
 * - DM uses the `direct_v2` endpoints the web session already carries
 *   cookies for, with polling only — no push, no realtime MQTT. A thread
 *   Instagram marks end-to-end encrypted (`is_e2ee`) is reported as such
 *   and never faked: the UI says so plainly.
 * - No login happens here: there is no password anywhere in this app.
 * - Every call is throttled ([Throttler]) and 429s back off.
 */
interface InstagramWebApi {
    suspend fun getHomeFeed(session: WebSession, cursor: String?): ApiResult<FeedPage>
    suspend fun getStoriesTray(session: WebSession): ApiResult<List<StoryTrayItem>>
    suspend fun getCurrentUser(session: WebSession): ApiResult<UserRef>
    suspend fun likePost(session: WebSession, mediaId: String, like: Boolean): ApiResult<Unit>
    suspend fun savePost(session: WebSession, mediaId: String, save: Boolean): ApiResult<Unit>
    suspend fun followUser(session: WebSession, userId: String, follow: Boolean): ApiResult<Unit>
    suspend fun addComment(session: WebSession, mediaId: String, text: String, parentId: String?): ApiResult<Unit>
    /** DM inbox (polling only). */
    suspend fun getDmInbox(session: WebSession): ApiResult<DmInboxPage>
    /** One DM thread, newest messages first. */
    suspend fun getDmThread(session: WebSession, threadId: String, cursor: String? = null): ApiResult<DmThreadPage>
    /** Send a text message; returns the sent message as Instagram echoed it. */
    suspend fun sendDm(session: WebSession, threadId: String, text: String): ApiResult<DmMessage>
    /** Best-effort "seen" marker; failures are swallowed by the caller. */
    suspend fun markDmThreadSeen(session: WebSession, threadId: String, itemId: String): ApiResult<Unit>
    /** Story media for tray reels (`reels_media`), as [Story] objects. */
    suspend fun getStoryMedia(session: WebSession, reelIds: List<String>): ApiResult<List<Story>>
    /** User search (`topsearch`), users only. */
    suspend fun searchUsers(session: WebSession, query: String): ApiResult<List<UserRef>>
}

/** Fake implementation for unit tests and offline previews. No network, no login. */
class FakeInstagramWebApi(
    private val feed: FeedPage = FeedPage(),
    private val tray: List<StoryTrayItem> = emptyList(),
    private val currentUser: UserRef = UserRef("0", "test.user"),
    private val inbox: DmInboxPage = DmInboxPage(emptyList()),
    private val thread: DmThreadPage? = null,
    private val searchResults: List<UserRef> = emptyList(),
    private val storyMedia: List<Story> = emptyList(),
) : InstagramWebApi {
    override suspend fun getHomeFeed(session: WebSession, cursor: String?): ApiResult<FeedPage> =
        ApiResult.Success(feed)

    override suspend fun getStoriesTray(session: WebSession): ApiResult<List<StoryTrayItem>> =
        ApiResult.Success(tray)

    override suspend fun getCurrentUser(session: WebSession): ApiResult<UserRef> =
        ApiResult.Success(currentUser)

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

    override suspend fun getDmInbox(session: WebSession): ApiResult<DmInboxPage> =
        ApiResult.Success(inbox)

    override suspend fun getDmThread(
        session: WebSession,
        threadId: String,
        cursor: String?,
    ): ApiResult<DmThreadPage> =
        ApiResult.Success(thread ?: DmThreadPage(DmThread(threadId), emptyList()))

    override suspend fun sendDm(session: WebSession, threadId: String, text: String): ApiResult<DmMessage> =
        ApiResult.Success(
            DmMessage(
                id = "fake-${UUID.randomUUID()}",
                threadId = threadId,
                sender = currentUser,
                text = text,
                createdAt = System.currentTimeMillis(),
                own = true,
                status = DmMessageStatus.SENT,
            )
        )

    override suspend fun markDmThreadSeen(session: WebSession, threadId: String, itemId: String): ApiResult<Unit> =
        ApiResult.Success(Unit)

    override suspend fun getStoryMedia(session: WebSession, reelIds: List<String>): ApiResult<List<Story>> =
        ApiResult.Success(storyMedia)

    override suspend fun searchUsers(session: WebSession, query: String): ApiResult<List<UserRef>> =
        ApiResult.Success(searchResults)
}

/**
 * GraphQL `doc_id`s for the web queries. Instagram rotates these with
 * every deploy — capture the current values from a logged-in browser
 * session (DevTools → Network → graphql/query → doc_id) and fill them in
 * here. Null means "that query is unavailable; use the fallback or an
 * empty result, never a guessed id".
 *
 * Phase-3 finding (2026-11-07, from the PUBLIC logged-out JS bundles on
 * static.cdninstagram.com — no login): the current web feed query is
 * `PolarisFeedRootPaginationCachedQuery_subscribe`, doc_id
 * `29356681030682853`, and its root field is
 * `xdt_api__v1__feed__timeline__connection` — NOT the
 * `data.user.edge_web_feed_timeline` shape the phase-2 parser expects.
 * The plain (non-subscribe) feed query artifact and its variables could
 * NOT be captured from the logged-out bundles (they load only after
 * sign-in), so the doc_id is deliberately NOT wired in here: posting a
 * guessed query + variables would break the feed outright, while the
 * homepage-HTML fallback still has a chance. Capture the live values on
 * the phone and fill them in then — and re-check the response shape first.
 */
data class GraphQlDocIds(
    /** `PolarisFeedRootPaginationCachedQuery` — the home-feed query. */
    val feedDocId: String? = null,
    /** Stories-tray query. */
    val storiesTrayDocId: String? = null,
)

class WebInstagramWebApi(
    private val docIds: GraphQlDocIds = GraphQlDocIds(),
    private val throttler: Throttler = Throttler(),
) : InstagramWebApi {

    companion object {
        const val IG_APP_ID = "936619743392459"
        const val BASE = "https://www.instagram.com"
        const val API_BASE = "https://i.instagram.com"
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Parse a web GraphQL feed response into a [FeedPage]. Pure, unit-tested. */
        fun parseFeedResponse(body: String): FeedPage? {
            val response = runCatching { json.decodeFromString<GraphQlResponse>(body) }.getOrNull()
                ?: return null
            val timeline = response.data?.user?.edge_web_feed_timeline ?: return null
            val items = timeline.edges.mapNotNull { it.node?.toPost() }
            return FeedPage(
                items = items,
                nextCursor = timeline.page_info?.end_cursor,
                hasMore = timeline.page_info?.has_next_page == true,
            )
        }

        /** Parse a stories-tray GraphQL response. Pure, unit-tested. */
        fun parseTrayResponse(body: String): List<StoryTrayItem>? {
            val response = runCatching { json.decodeFromString<GraphQlResponse>(body) }.getOrNull()
                ?: return null
            val tray = response.data?.user?.feed_reels_tray ?: return null
            return tray.edges.mapNotNull { edge ->
                val node = edge.node ?: return@mapNotNull null
                val user = node.user ?: return@mapNotNull null
                StoryTrayItem(
                    user = UserRef(
                        id = user.id,
                        username = user.username,
                        avatarUrl = user.profile_pic_url,
                    ),
                    latestStoryId = node.id,
                    seen = node.seen,
                    mediaUrl = user.profile_pic_url,
                )
            }
        }

        /**
         * Extract the `{"data":{"user":{"edge_web_feed_timeline":…}}}` blob
         * embedded in the logged-in homepage HTML, and parse it. Returns
         * null when the page carries no feed data (logged out, or Instagram
         * changed the page shape) — the caller must surface that honestly.
         */
        fun parseFeedFromHomePageHtml(html: String): FeedPage? {
            val marker = "\"edge_web_feed_timeline\""
            val markerAt = html.indexOf(marker)
            if (markerAt < 0) return null
            val start = html.lastIndexOf("{\"data\"", markerAt)
            if (start < 0) return null
            val blob = extractJsonObject(html, start) ?: return null
            return parseFeedResponse(blob)
        }

        /** Brace-match one JSON object starting at [start] (string-aware). */
        internal fun extractJsonObject(text: String, start: Int): String? {
            var depth = 0
            var inString = false
            var escaped = false
            var i = start
            while (i < text.length) {
                val c = text[i]
                if (inString) {
                    if (escaped) escaped = false
                    else when (c) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) return text.substring(start, i + 1)
                        }
                    }
                }
                i++
            }
            return null
        }

        // ---- direct_v2 parsing (pure, unit-tested) ----

        /** direct_v2 timestamps are microseconds since epoch. */
        private fun microsToMs(value: JsonElement?): Long =
            (value?.jsonPrimitive?.longOrNull ?: 0L) / 1_000

        private fun JsonObject.string(key: String): String =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

        private fun JsonObject.bool(key: String): Boolean =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true

        private fun JsonObject.long(key: String): Long =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: 0L

        private fun parseDirectUser(obj: JsonObject): UserRef = UserRef(
            id = obj.string("pk").ifBlank { obj.string("id") },
            username = obj.string("username"),
            displayName = obj.string("full_name"),
            avatarUrl = obj.string("profile_pic_url"),
            verified = obj.bool("is_verified"),
        )

        /** Human preview for a non-text DM item — never the raw `item_type`. */
        fun dmItemPreview(itemType: String, item: JsonObject): String = when (itemType) {
            "text" -> item.string("text")
            "media" -> if (item.long("media_type") == 2L) "Video" else "Photo"
            "like" -> "Liked a message"
            "reel_share" -> "Shared a reel"
            "media_share" -> "Shared a post"
            "link" -> "Shared a link"
            "voice_media" -> "Voice message"
            "action_log" -> item.get("action_log")?.jsonObject?.string("description")?.ifBlank { "…" } ?: "…"
            "story_share" -> "Shared a story"
            "felix_share" -> "Shared a video"
            "placeholder" -> "Message unavailable"
            else -> "Message"
        }

        /**
         * Parse a `direct_v2/inbox` response. Per-thread unread is derived
         * honestly: 1 when the last message is from someone else and newer
         * than what the viewer last saw, else 0 (Instagram gives no
         * per-thread unread count in this payload; the totals come from
         * `unseen_count`).
         */
        fun parseDmInboxResponse(body: String, viewerId: String): DmInboxPage? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val inbox = root["inbox"]?.jsonObject ?: return null
            val unseen = inbox.long("unseen_count").toInt()
            val threads = inbox["threads"]?.jsonArray?.mapNotNull { element ->
                val t = element.jsonObject
                val threadId = t.string("thread_id")
                if (threadId.isBlank()) return@mapNotNull null
                val users = t["users"]?.jsonArray?.mapNotNull {
                    runCatching { parseDirectUser(it.jsonObject) }.getOrNull()
                }.orEmpty()
                val last = t["last_permanent_item"]?.jsonObject
                val lastType = last?.string("item_type").orEmpty()
                val lastText = if (last != null) dmItemPreview(lastType, last) else ""
                val lastAt = microsToMs(last?.get("timestamp"))
                val lastFrom = last?.string("user_id").orEmpty()
                val seenAt = t["last_seen_at"]?.jsonObject
                val mySeenAt = seenAt?.get(viewerId)?.jsonObject?.let { microsToMs(it["timestamp"]) } ?: 0L
                val otherSeenItem = seenAt?.entries
                    ?.firstOrNull { it.key != viewerId && it.key.isNotBlank() }
                    ?.value?.jsonObject?.string("item_id").orEmpty()
                val unread = if (lastAt > 0 && lastFrom.isNotBlank() && lastFrom != viewerId && lastAt > mySeenAt) 1 else 0
                val title = t.string("thread_title").ifBlank {
                    users.joinToString(", ") { it.username.ifBlank { it.displayName } }
                }
                DmThread(
                    threadId = threadId,
                    participants = users,
                    title = title,
                    lastMessage = lastText,
                    lastMessageAt = lastAt,
                    unreadCount = unread,
                    isGroup = t.bool("is_group"),
                    e2ee = t.bool("is_e2ee"),
                    seenItemId = otherSeenItem,
                )
            }.orEmpty()
            return DmInboxPage(threads, unseen)
        }

        /** Parse a `direct_v2/threads/{id}` response. */
        fun parseDmThreadResponse(body: String, viewerId: String): DmThreadPage? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val t = root["thread"]?.jsonObject ?: return null
            val threadId = t.string("thread_id")
            if (threadId.isBlank()) return null
            val users = t["users"]?.jsonArray?.mapNotNull {
                runCatching { parseDirectUser(it.jsonObject) }.getOrNull()
            }.orEmpty()
            val seenAt = t["last_seen_at"]?.jsonObject
            val otherSeenItem = seenAt?.entries
                ?.firstOrNull { it.key != viewerId && it.key.isNotBlank() }
                ?.value?.jsonObject?.string("item_id").orEmpty()
            val last = t["last_permanent_item"]?.jsonObject
            val lastType = last?.string("item_type").orEmpty()
            val messages = t["items"]?.jsonArray?.mapNotNull { element ->
                val item = element.jsonObject
                val itemId = item.string("item_id")
                if (itemId.isBlank()) return@mapNotNull null
                val itemType = item.string("item_type").ifBlank { "text" }
                val senderId = item.string("user_id")
                val sender = users.find { it.id == senderId }
                    ?: UserRef(id = senderId, username = if (senderId == viewerId) "you" else "")
                DmMessage(
                    id = itemId,
                    threadId = threadId,
                    sender = sender,
                    text = dmItemPreview(itemType, item),
                    createdAt = microsToMs(item["timestamp"]),
                    own = senderId == viewerId,
                    status = DmMessageStatus.SENT,
                    itemType = itemType,
                )
            }.orEmpty().sortedByDescending { it.createdAt }
            val thread = DmThread(
                threadId = threadId,
                participants = users,
                title = t.string("thread_title").ifBlank {
                    users.joinToString(", ") { it.username.ifBlank { it.displayName } }
                },
                lastMessage = messages.firstOrNull()?.text
                    ?: if (last != null) dmItemPreview(lastType, last) else "",
                lastMessageAt = messages.firstOrNull()?.createdAt ?: microsToMs(last?.get("timestamp")),
                unreadCount = 0,
                isGroup = t.bool("is_group"),
                e2ee = t.bool("is_e2ee"),
                seenItemId = otherSeenItem,
            )
            return DmThreadPage(
                thread = thread,
                messages = messages,
                nextCursor = t.string("next_cursor").ifBlank { null },
                hasMore = t.bool("has_older"),
            )
        }

        /** Parse the `payload` of a successful `broadcast/text` send. */
        fun parseDmSendResponse(body: String, threadId: String, viewerId: String, text: String): DmMessage? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val payload = root["payload"]?.jsonObject ?: return null
            val itemId = payload.string("item_id")
            if (itemId.isBlank()) return null
            val timestamp = microsToMs(payload["timestamp"])
            return DmMessage(
                id = itemId,
                threadId = threadId,
                sender = UserRef(id = viewerId, username = ""),
                text = payload.string("text").ifBlank { text },
                createdAt = if (timestamp > 0) timestamp else System.currentTimeMillis(),
                own = true,
                status = DmMessageStatus.SENT,
                itemType = payload.string("item_type").ifBlank { "text" },
            )
        }

        /** Parse a `feed/reels_media` response into [Story] objects. */
        fun parseStoryMediaResponse(body: String): List<Story>? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val reels = root["reels"]?.jsonObject ?: return null
            val stories = mutableListOf<Story>()
            for ((_, reelElement) in reels) {
                val reel = reelElement.jsonObject
                val user = reel["user"]?.jsonObject?.let { parseDirectUser(it) } ?: continue
                val items = reel["items"]?.jsonArray.orEmpty()
                for (itemElement in items) {
                    val item = itemElement.jsonObject
                    val id = item.string("id")
                    if (id.isBlank()) continue
                    val mediaType = item.long("media_type")
                    val isVideo = mediaType == 2L
                    val url = if (isVideo) {
                        item["video_versions"]?.jsonArray?.firstOrNull()?.jsonObject?.string("url").orEmpty()
                    } else {
                        item["image_versions2"]?.jsonObject?.get("candidates")?.jsonArray
                            ?.mapNotNull { it.jsonObject }
                            ?.maxByOrNull { it.long("width") }
                            ?.string("url").orEmpty()
                    }
                    if (url.isBlank()) continue
                    val caption = item["caption"]?.jsonObject?.string("text").orEmpty()
                    val takenAt = item.long("taken_at")
                    val expiringAt = item.long("expiring_at")
                    stories += Story(
                        id = id,
                        media = Media(path = url, kind = if (isVideo) MediaKind.VIDEO else MediaKind.IMAGE),
                        caption = caption,
                        createdAt = takenAt * 1_000,
                        expiresAt = if (expiringAt > 0) expiringAt * 1_000 else takenAt * 1_000 + 86_400_000,
                        author = user,
                    )
                }
            }
            return stories
        }

        /** Parse a `web/search/topsearch` response (users only). */
        fun parseSearchResponse(body: String): List<UserRef>? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val users = root["users"]?.jsonArray ?: return emptyList()
            return users.mapNotNull { element ->
                val user = element.jsonObject["user"]?.jsonObject ?: return@mapNotNull null
                val ref = parseDirectUser(user)
                if (ref.username.isBlank()) null else ref
            }
        }
    }

    // ---- Web GraphQL response DTOs (only the fields we read) ----

    @Serializable
    private data class GraphQlResponse(val data: GraphQlData? = null)

    @Serializable
    private data class GraphQlData(val user: GraphQlUser? = null)

    @Serializable
    private data class GraphQlUser(
        val edge_web_feed_timeline: FeedTimeline? = null,
        val feed_reels_tray: ReelsTray? = null,
    )

    @Serializable
    private data class FeedTimeline(
        val edges: List<FeedEdge> = emptyList(),
        val page_info: PageInfo? = null,
    )

    @Serializable
    private data class ReelsTray(val edges: List<TrayEdge> = emptyList())

    @Serializable
    private data class FeedEdge(val node: FeedNode? = null)

    @Serializable
    private data class TrayEdge(val node: TrayNode? = null)

    @Serializable
    private data class TrayNode(
        val id: String = "",
        val user: TrayUser? = null,
        val seen: Boolean = false,
    )

    @Serializable
    private data class TrayUser(
        val id: String = "",
        val username: String = "",
        val profile_pic_url: String = "",
    )

    @Serializable
    private data class PageInfo(val has_next_page: Boolean = false, val end_cursor: String? = null)

    @Serializable
    private data class FeedNode(
        val id: String = "",
        val shortcode: String = "",
        val display_url: String = "",
        val video_url: String? = null,
        val is_video: Boolean = false,
        val taken_at_timestamp: Long = 0,
        val owner: FeedOwner? = null,
        val edge_media_to_caption: CaptionEdges? = null,
        val edge_liked_by: CountOnly? = null,
        val edge_media_to_comment: CountOnly? = null,
    ) {
        fun toPost(): Post? {
            if (id.isBlank()) return null
            val ownerRef = owner?.let {
                UserRef(
                    id = it.id,
                    username = it.username,
                    avatarUrl = it.profile_pic_url,
                    verified = it.is_verified,
                )
            }
            val caption = edge_media_to_caption?.edges?.firstOrNull()?.node?.text.orEmpty()
            val mediaUrl = video_url?.takeIf { it.isNotBlank() } ?: display_url
            if (mediaUrl.isBlank()) return null
            return Post(
                id = id,
                media = Media(path = mediaUrl, kind = if (is_video) MediaKind.VIDEO else MediaKind.IMAGE),
                caption = caption,
                createdAt = taken_at_timestamp * 1_000,
                likes = edge_liked_by?.count ?: 0,
                commentCount = edge_media_to_comment?.count ?: 0,
                author = ownerRef,
                shortcode = shortcode,
            )
        }
    }

    @Serializable
    private data class FeedOwner(
        val id: String = "",
        val username: String = "",
        val profile_pic_url: String = "",
        val is_verified: Boolean = false,
    )

    @Serializable
    private data class CaptionEdges(val edges: List<CaptionEdge> = emptyList())

    @Serializable
    private data class CaptionEdge(val node: CaptionNode? = null)

    @Serializable
    private data class CaptionNode(val text: String = "")

    @Serializable
    private data class CountOnly(val count: Long = 0)

    @Serializable
    private data class CurrentUserResponse(val user: CurrentUser? = null)

    @Serializable
    private data class CurrentUser(
        val pk: Long = 0,
        val id: String = "",
        val username: String = "",
        val full_name: String = "",
        val profile_pic_url: String = "",
        val is_verified: Boolean = false,
    )

    // ---- HTTP plumbing ----

    private data class RawResponse(val code: Int, val body: String, val retryAfterSeconds: Long?)

    private suspend fun request(
        url: String,
        session: WebSession,
        method: String = "GET",
        formParams: Map<String, String>? = null,
    ): ApiResult<RawResponse> = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", BROWSER_UA)
                setRequestProperty("Accept", "application/json, text/html, */*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("X-IG-App-ID", IG_APP_ID)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Referer", "$BASE/")
                setRequestProperty("Cookie", session.cookieHeader())
                if (session.csrftoken.isNotBlank()) {
                    setRequestProperty("X-CSRFToken", session.csrftoken)
                }
                if (formParams != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    val body = formParams.entries.joinToString("&") { (key, value) ->
                        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                    }
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = connection.responseCode
            val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            when (code) {
                in 200..299 -> ApiResult.Success(RawResponse(code, body, retryAfter))
                429 -> ApiResult.RateLimited(retryAfter)
                else -> ApiResult.HttpError(code, "Instagram answered $code")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e.message ?: "Network failed")
        }
    }

    /** Pace the call, run it, and feed 429s back into the throttler. */
    private suspend fun paced(block: suspend () -> ApiResult<RawResponse>): ApiResult<RawResponse> {
        val wait = throttler.waitMs()
        if (wait > 0) delay(wait)
        throttler.recordCall()
        return when (val result = block()) {
            is ApiResult.RateLimited -> {
                throttler.recordRateLimited(result.retryAfterSeconds)
                result
            }
            is ApiResult.Success -> {
                throttler.recordSuccess()
                result
            }
            else -> result
        }
    }

    private fun <T> ApiResult<RawResponse>.mapError(): ApiResult<T> = when (this) {
        is ApiResult.Success -> error("mapError called on Success")
        is ApiResult.HttpError -> ApiResult.HttpError(code, message)
        is ApiResult.NetworkError -> ApiResult.NetworkError(message)
        is ApiResult.RateLimited -> ApiResult.RateLimited(retryAfterSeconds)
    }

    // ---- InstagramWebApi ----

    override suspend fun getHomeFeed(session: WebSession, cursor: String?): ApiResult<FeedPage> {
        val docId = docIds.feedDocId
        if (docId != null) {
            val variables = buildString {
                append("{\"fetch_media_item_count\":12")
                append(",\"fetch_comment_count\":4")
                append(",\"fetch_like_count\":10")
                append(",\"has_threaded_comments\":true")
                if (cursor != null) {
                    append(",\"fetch_media_item_cursor\":\"")
                    append(cursor.replace("\\", "\\\\").replace("\"", "\\\""))
                    append("\"")
                }
                append("}")
            }
            val result = paced {
                request(
                    url = "$BASE/graphql/query/",
                    session = session,
                    method = "POST",
                    formParams = mapOf(
                        "fb_api_req_friendly_name" to "PolarisFeedRootPaginationCachedQuery",
                        "variables" to variables,
                        "doc_id" to docId,
                    ),
                )
            }
            return when (result) {
                is ApiResult.Success -> {
                    parseFeedResponse(result.value.body)
                        ?.let { ApiResult.Success(it) }
                        ?: homePageFeedFallback(session)
                }
                else -> result.mapError()
            }
        }
        // No GraphQL doc_id configured: read the feed JSON embedded in the
        // logged-in homepage instead (best effort; see class doc).
        return homePageFeedFallback(session)
    }

    /** Homepage-HTML feed path, used when no GraphQL doc_id is configured
     *  or when the GraphQL answer could not be read (Instagram changed
     *  shape — fall back instead of failing outright). */
    private suspend fun homePageFeedFallback(session: WebSession): ApiResult<FeedPage> {
        val result = paced { request(url = "$BASE/", session = session) }
        return when (result) {
            is ApiResult.Success -> {
                parseFeedFromHomePageHtml(result.value.body)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.HttpError(0, "Signed in, but the home page carried no feed data.")
            }
            else -> result.mapError()
        }
    }

    override suspend fun getStoriesTray(session: WebSession): ApiResult<List<StoryTrayItem>> {
        val docId = docIds.storiesTrayDocId ?: return ApiResult.Success(emptyList())
        val result = paced {
            request(
                url = "$BASE/graphql/query/",
                session = session,
                method = "POST",
                formParams = mapOf(
                    "fb_api_req_friendly_name" to "PolarisStoriesTrayQuery",
                    "variables" to "{}",
                    "doc_id" to docId,
                ),
            )
        }
        return when (result) {
            is ApiResult.Success -> {
                parseTrayResponse(result.value.body)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.Success(emptyList())
            }
            else -> result.mapError()
        }
    }

    override suspend fun getCurrentUser(session: WebSession): ApiResult<UserRef> {
        val result = paced { request(url = "$API_BASE/api/v1/accounts/current_user/", session = session) }
        when (result) {
            is ApiResult.Success -> {
                val parsed = runCatching { json.decodeFromString<CurrentUserResponse>(result.value.body) }.getOrNull()
                val user = parsed?.user
                if (user != null && user.username.isNotBlank()) {
                    return ApiResult.Success(
                        UserRef(
                            id = user.id.ifBlank { user.pk.toString() },
                            username = user.username,
                            displayName = user.full_name,
                            avatarUrl = user.profile_pic_url,
                            verified = user.is_verified,
                        )
                    )
                }
            }
            else -> return result.mapError()
        }
        // Fallback: the logged-in homepage embeds the viewer username.
        val home = paced { request(url = "$BASE/", session = session) }
        if (home is ApiResult.Success) {
            val html = home.value.body
            val viewerAt = html.indexOf("\"viewer\"")
            val searchFrom = if (viewerAt >= 0) viewerAt else 0
            val match = Regex("\"username\"\\s*:\\s*\"([A-Za-z0-9_.]{1,30})\"")
                .find(html, searchFrom)
            val username = match?.groupValues?.getOrNull(1).orEmpty()
            if (username.isNotBlank()) {
                return ApiResult.Success(
                    UserRef(
                        id = session.dsUserId,
                        username = username,
                    )
                )
            }
        } else if (home !is ApiResult.Success) {
            return home.mapError()
        }
        return ApiResult.HttpError(0, "Could not read who is signed in.")
    }

    private suspend fun webPost(session: WebSession, path: String, formParams: Map<String, String>? = null): ApiResult<Unit> {
        val result = paced {
            request(url = "$BASE$path", session = session, method = "POST", formParams = formParams ?: emptyMap())
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            else -> result.mapError()
        }
    }

    override suspend fun likePost(session: WebSession, mediaId: String, like: Boolean): ApiResult<Unit> =
        webPost(session, "/web/likes/$mediaId/${if (like) "like" else "unlike"}/")

    override suspend fun savePost(session: WebSession, mediaId: String, save: Boolean): ApiResult<Unit> =
        webPost(session, "/web/save/$mediaId/${if (save) "save" else "unsave"}/")

    override suspend fun followUser(session: WebSession, userId: String, follow: Boolean): ApiResult<Unit> =
        webPost(session, "/web/friendships/$userId/${if (follow) "follow" else "unfollow"}/")

    override suspend fun addComment(
        session: WebSession,
        mediaId: String,
        text: String,
        parentId: String?,
    ): ApiResult<Unit> {
        val params = mutableMapOf("comment_text" to text)
        if (!parentId.isNullOrBlank()) params["replied_to_comment_id"] = parentId
        return webPost(session, "/web/comments/$mediaId/add/", params)
    }

    // ---- Direct messages (phase 3) ----

    override suspend fun getDmInbox(session: WebSession): ApiResult<DmInboxPage> {
        val result = paced {
            request(
                url = "$API_BASE/api/v1/direct_v2/inbox/?persistentBadging=true&folder=&limit=20&thread_message_limit=5",
                session = session,
            )
        }
        return when (result) {
            is ApiResult.Success -> {
                parseDmInboxResponse(result.value.body, session.dsUserId)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.HttpError(0, "Instagram's inbox answer could not be read (it may have changed shape).")
            }
            else -> result.mapError()
        }
    }

    override suspend fun getDmThread(
        session: WebSession,
        threadId: String,
        cursor: String?,
    ): ApiResult<DmThreadPage> {
        val cursorParam = if (cursor.isNullOrBlank()) "" else "&cursor=${URLEncoder.encode(cursor, "UTF-8")}"
        val result = paced {
            request(
                url = "$API_BASE/api/v1/direct_v2/threads/$threadId/?limit=20$cursorParam",
                session = session,
            )
        }
        return when (result) {
            is ApiResult.Success -> {
                parseDmThreadResponse(result.value.body, session.dsUserId)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.HttpError(0, "Instagram's thread answer could not be read (it may have changed shape).")
            }
            else -> result.mapError()
        }
    }

    override suspend fun sendDm(session: WebSession, threadId: String, text: String): ApiResult<DmMessage> {
        val token = UUID.randomUUID().toString()
        val result = paced {
            request(
                url = "$API_BASE/api/v1/direct_v2/threads/$threadId/broadcast/text/",
                session = session,
                method = "POST",
                formParams = mapOf(
                    "text" to text,
                    "mutation_token" to token,
                    "client_context" to token,
                ),
            )
        }
        return when (result) {
            is ApiResult.Success -> {
                parseDmSendResponse(result.value.body, threadId, session.dsUserId, text)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.HttpError(0, "Instagram accepted the message but its answer could not be read.")
            }
            else -> result.mapError()
        }
    }

    override suspend fun markDmThreadSeen(session: WebSession, threadId: String, itemId: String): ApiResult<Unit> {
        val result = paced {
            request(
                url = "$API_BASE/api/v1/direct_v2/threads/$threadId/items/$itemId/seen/",
                session = session,
                method = "POST",
                formParams = emptyMap(),
            )
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            else -> result.mapError()
        }
    }

    override suspend fun getStoryMedia(session: WebSession, reelIds: List<String>): ApiResult<List<Story>> {
        if (reelIds.isEmpty()) return ApiResult.Success(emptyList())
        val query = reelIds.joinToString("&") { "reel_ids=${URLEncoder.encode(it, "UTF-8")}" }
        val result = paced {
            request(url = "$API_BASE/api/v1/feed/reels_media/?$query", session = session)
        }
        return when (result) {
            is ApiResult.Success -> {
                parseStoryMediaResponse(result.value.body)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.HttpError(0, "Instagram's story answer could not be read (it may have changed shape).")
            }
            else -> result.mapError()
        }
    }

    override suspend fun searchUsers(session: WebSession, query: String): ApiResult<List<UserRef>> {
        if (query.isBlank()) return ApiResult.Success(emptyList())
        val result = paced {
            request(
                url = "$BASE/web/search/topsearch/?query=${URLEncoder.encode(query.trim(), "UTF-8")}",
                session = session,
            )
        }
        return when (result) {
            is ApiResult.Success -> {
                parseSearchResponse(result.value.body)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.HttpError(0, "Instagram's search answer could not be read (it may have changed shape).")
            }
            else -> result.mapError()
        }
    }
}
