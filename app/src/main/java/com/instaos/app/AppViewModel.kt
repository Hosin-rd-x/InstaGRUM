package com.instaos.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.instaos.app.data.ApiResult
import com.instaos.app.data.DmInboxPage
import com.instaos.app.data.FeedCache
import com.instaos.app.data.FeedRepository
import com.instaos.app.data.SessionStore
import com.instaos.app.data.AppRuntime
import com.instaos.app.data.WebInstagramWebApi
import com.instaos.app.data.WebSession
import com.instaos.app.model.Action
import com.instaos.app.model.Comment
import com.instaos.app.model.DmMessage
import com.instaos.app.model.DmMessageStatus
import com.instaos.app.model.FeedUiState
import com.instaos.app.model.Post
import com.instaos.app.model.SearchUiState
import com.instaos.app.model.StoryTrayItem
import com.instaos.app.model.toUserRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = AppRuntime.get(application)
    val state = runtime.state
    val error = runtime.error

    // ---- Web session + home feed (outside the snapshot) ----
    private val sessionStore = SessionStore.create(application)
    private val api = WebInstagramWebApi()
    private val feedRepository = FeedRepository(
        api,
        FeedCache(File(application.filesDir, "feed-cache")),
    )
    private val mutableSession = MutableStateFlow(sessionStore.load())
    val session = mutableSession.asStateFlow()
    private val mutableOfflineMode = MutableStateFlow(false)
    val offlineMode = mutableOfflineMode.asStateFlow()
    private val mutableFeedState = MutableStateFlow(FeedUiState(loading = true))
    val feedState = mutableFeedState.asStateFlow()

    // ---- Direct messages (phase 3): threads live in AppState.dm; the
    // loading/error flags live here so polling never clobbers the list. ----
    private val mutableDmLoading = MutableStateFlow(false)
    val dmLoading = mutableDmLoading.asStateFlow()
    private val mutableDmError = MutableStateFlow<String?>(null)
    val dmError = mutableDmError.asStateFlow()
    private var dmPollJob: Job? = null

    // ---- Search (phase 3): results are in-memory only. ----
    private val mutableSearchState = MutableStateFlow(SearchUiState())
    val searchState = mutableSearchState.asStateFlow()
    private var searchJob: Job? = null

    // ---- Tray-story loading errors (shown once, then cleared). ----
    private val mutableStoryError = MutableStateFlow<String?>(null)
    val storyError = mutableStoryError.asStateFlow()

    init {
        viewModelScope.launch { runtime.load() }
        // If a session survived, warm the feed from the cache immediately
        // (no network call when the cache is fresh) and refresh in the
        // background when it is stale.
        if (mutableSession.value?.isLoggedIn == true) refreshFeed()
        else mutableFeedState.value = FeedUiState(loading = false, error = FeedRepository.NOT_SIGNED_IN)
    }

    fun dispatch(action: Action) = runtime.enqueue(action)

    /** A WebView login finished. Persist the session, then adopt the Instagram identity. */
    fun completeLogin(session: WebSession) {
        sessionStore.save(session)
        mutableSession.value = session
        mutableOfflineMode.value = false
        viewModelScope.launch {
            val current = state.value
            val baseProfile = current?.profile
            val identified = when (val result = api.getCurrentUser(session)) {
                is ApiResult.Success -> {
                    val user = result.value
                    sessionStore.save(session.copy(username = user.username))
                    mutableSession.value = session.copy(username = user.username)
                    (baseProfile ?: com.instaos.app.model.Profile()).copy(
                        username = user.username,
                        displayName = user.displayName.ifBlank { user.username },
                        avatar = user.avatarUrl,
                        verified = user.verified,
                    )
                }
                // Who-is-signed-in could not be confirmed: keep the local
                // profile, but the login itself still counts — the feed
                // will load with the session either way.
                else -> baseProfile ?: com.instaos.app.model.Profile(
                    username = session.username.ifBlank { session.dsUserId },
                )
            }
            dispatch(Action.LoginCompleted(identified))
            refreshFeed(force = true)
        }
    }

    /** Sign out: forget the session cookie; the local profile is kept. */
    fun logout() {
        stopDmPolling()
        sessionStore.clear()
        mutableSession.value = null
        mutableOfflineMode.value = false
        mutableFeedState.value = FeedUiState(error = FeedRepository.NOT_SIGNED_IN)
        mutableDmError.value = null
        dispatch(Action.Logout)
    }

    /** Keep using the app without signing in (local content only). */
    fun continueOffline() {
        mutableOfflineMode.value = true
    }

    /** Show the login screen again after "continue offline". */
    fun showLogin() {
        mutableOfflineMode.value = false
    }

    fun refreshFeed(force: Boolean = false) {
        viewModelScope.launch {
            val current = mutableFeedState.value
            mutableFeedState.value = current.copy(loading = true, error = null)
            val result = feedRepository.refresh(mutableSession.value, force)
            mutableFeedState.value = FeedUiState(
                posts = result.posts,
                tray = result.tray,
                nextCursor = result.nextCursor,
                hasMore = result.hasMore,
                loading = false,
                error = result.error,
                lastUpdatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun loadMoreFeed() {
        val current = mutableFeedState.value
        if (current.loading || !current.hasMore) return
        viewModelScope.launch {
            mutableFeedState.value = current.copy(loading = true)
            val result = feedRepository.loadMore(mutableSession.value, current.nextCursor)
            mutableFeedState.value = current.copy(
                posts = result.posts,
                tray = result.tray,
                nextCursor = result.nextCursor,
                hasMore = result.hasMore,
                loading = false,
                error = result.error,
            )
        }
    }

    /** Optimistic feed like; the web request runs in the background. */
    fun feedLike(post: Post) {
        val session = mutableSession.value ?: return
        val current = mutableFeedState.value
        val liked = !post.liked
        mutableFeedState.value = current.copy(
            posts = current.posts.map {
                if (it.id == post.id) it.copy(liked = liked, likes = (it.likes + if (liked) 1 else -1).coerceAtLeast(0))
                else it
            },
        )
        viewModelScope.launch {
            val result = api.likePost(session, post.id, liked)
            if (result !is ApiResult.Success) {
                mutableFeedState.value = current.copy(error = "Like did not reach Instagram.")
            }
        }
    }

    fun feedSave(post: Post) {
        val session = mutableSession.value ?: return
        val current = mutableFeedState.value
        val saved = !post.saved
        mutableFeedState.value = current.copy(
            posts = current.posts.map { if (it.id == post.id) it.copy(saved = saved) else it },
        )
        viewModelScope.launch {
            val result = api.savePost(session, post.id, saved)
            if (result !is ApiResult.Success) {
                mutableFeedState.value = current.copy(error = "Save did not reach Instagram.")
            }
        }
    }

    /** Optimistic feed comment; the web request runs in the background. */
    fun feedAddComment(post: Post, text: String, parentId: String?) {
        val session = mutableSession.value ?: return
        if (text.isBlank()) return
        val current = mutableFeedState.value
        val me = state.value?.profile
        val comment = Comment(
            id = "local-${UUID.randomUUID()}",
            person = me?.toUserRef() ?: com.instaos.app.model.UserRef("self", ""),
            text = text.trim().take(2000),
            createdAt = System.currentTimeMillis(),
            parentId = parentId,
            own = true,
        )
        mutableFeedState.value = current.copy(
            posts = current.posts.map {
                if (it.id == post.id) it.copy(
                    commentCount = it.commentCount + 1,
                    comments = it.comments + comment,
                ) else it
            },
        )
        viewModelScope.launch {
            val result = api.addComment(session, post.id, text, parentId)
            if (result !is ApiResult.Success) {
                mutableFeedState.value = mutableFeedState.value.copy(
                    error = "Comment was kept locally — it did not reach Instagram.",
                )
            }
        }
    }

    // ---- Direct messages (phase 3) ----

    fun refreshDmInbox(quiet: Boolean = false) {
        val session = mutableSession.value
        if (session == null || !session.isLoggedIn) {
            if (!quiet) mutableDmError.value = "Sign in to see your messages."
            return
        }
        viewModelScope.launch {
            if (!quiet) {
                mutableDmLoading.value = true
                mutableDmError.value = null
            }
            when (val result = api.getDmInbox(session)) {
                is ApiResult.Success -> {
                    val page: DmInboxPage = result.value
                    dispatch(Action.DmInboxLoaded(page.threads))
                    if (!quiet) mutableDmError.value = null
                }
                is ApiResult.RateLimited -> {
                    if (!quiet) mutableDmError.value = "Instagram asked us to slow down. Try again in a bit."
                }
                is ApiResult.HttpError -> {
                    if (!quiet) mutableDmError.value =
                        if (result.code == 0) result.message
                        else "Instagram answered ${result.code}. Your saved messages are shown, if any."
                }
                is ApiResult.NetworkError -> {
                    if (!quiet) mutableDmError.value = "No connection. Your saved messages are shown, if any."
                }
            }
            if (!quiet) mutableDmLoading.value = false
        }
    }

    fun loadDmThread(threadId: String, quiet: Boolean = false) {
        val session = mutableSession.value
        if (session == null || !session.isLoggedIn) {
            if (!quiet) mutableDmError.value = "Sign in to see your messages."
            return
        }
        viewModelScope.launch {
            if (!quiet) {
                mutableDmLoading.value = true
                mutableDmError.value = null
            }
            when (val result = api.getDmThread(session, threadId)) {
                is ApiResult.Success -> {
                    val page = result.value
                    dispatch(Action.DmThreadLoaded(page.thread, page.messages))
                    dispatch(Action.DmThreadRead(threadId))
                    if (!quiet) mutableDmError.value = null
                    // Best-effort "seen": mark the newest message from the
                    // other side as seen. A failure here is silent — the
                    // thread still reads fine.
                    val newestOther = page.messages.firstOrNull { !it.own }
                    if (newestOther != null) {
                        runCatching { api.markDmThreadSeen(session, threadId, newestOther.id) }
                    }
                }
                is ApiResult.RateLimited -> {
                    if (!quiet) mutableDmError.value = "Instagram asked us to slow down. Try again in a bit."
                }
                is ApiResult.HttpError -> {
                    if (!quiet) mutableDmError.value =
                        if (result.code == 0) result.message
                        else "Instagram answered ${result.code}."
                }
                is ApiResult.NetworkError -> {
                    if (!quiet) mutableDmError.value = "No connection."
                }
            }
            if (!quiet) mutableDmLoading.value = false
        }
    }

    /** Send a text message, optimistically appended, then confirmed or failed. */
    fun sendDmMessage(threadId: String, text: String) {
        val session = mutableSession.value ?: return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val me = state.value?.profile?.toUserRef() ?: com.instaos.app.model.UserRef("self", "")
        val tempId = "local-${UUID.randomUUID()}"
        val optimistic = DmMessage(
            id = tempId,
            threadId = threadId,
            sender = me,
            text = trimmed.take(2000),
            createdAt = System.currentTimeMillis(),
            own = true,
            status = DmMessageStatus.SENDING,
        )
        dispatch(Action.DmMessageAppended(optimistic))
        viewModelScope.launch {
            when (val result = api.sendDm(session, threadId, trimmed)) {
                is ApiResult.Success -> {
                    dispatch(Action.DmMessageUpdated(threadId, tempId, result.value))
                }
                else -> {
                    dispatch(
                        Action.DmMessageUpdated(
                            threadId,
                            tempId,
                            optimistic.copy(status = DmMessageStatus.FAILED),
                        )
                    )
                    mutableDmError.value = "Message did not send. Tap it to try again."
                }
            }
        }
    }

    /** Retry a failed message (same text, new attempt). */
    fun retryDmMessage(threadId: String, message: DmMessage) {
        dispatch(
            Action.DmMessageUpdated(
                threadId,
                message.id,
                message.copy(status = DmMessageStatus.SENDING),
            )
        )
        sendDmMessage(threadId, message.text)
    }

    /**
     * Gentle polling while a DM screen is visible: inbox every 60 s, an
     * open thread every 30 s. No push, no realtime — this is the whole
     * freshness story for v1, and the throttler still paces every call.
     */
    fun startDmPolling(threadId: String?) {
        stopDmPolling()
        if (mutableSession.value?.isLoggedIn != true) return
        dmPollJob = viewModelScope.launch {
            while (isActive) {
                delay(if (threadId == null) 60_000 else 30_000)
                if (threadId == null) refreshDmInbox(quiet = true)
                else loadDmThread(threadId, quiet = true)
            }
        }
    }

    fun stopDmPolling() {
        dmPollJob?.cancel()
        dmPollJob = null
    }

    // ---- Stories tray (phase 3) ----

    /** Load one tray user's stories and open the viewer on the first one. */
    fun openTrayStory(item: StoryTrayItem) {
        val session = mutableSession.value
        if (session == null || !session.isLoggedIn) {
            mutableStoryError.value = "Sign in to watch stories."
            return
        }
        mutableStoryError.value = null
        viewModelScope.launch {
            when (val result = api.getStoryMedia(session, listOf(item.user.id))) {
                is ApiResult.Success -> {
                    val stories = result.value
                    if (stories.isEmpty()) {
                        mutableStoryError.value = "No story to show for ${item.user.username} right now."
                    } else {
                        dispatch(Action.AddStories(stories))
                        dispatch(Action.OpenStory(stories.first().id))
                    }
                }
                is ApiResult.RateLimited -> {
                    mutableStoryError.value = "Instagram asked us to slow down. Try again in a bit."
                }
                is ApiResult.HttpError -> {
                    mutableStoryError.value =
                        if (result.code == 0) result.message
                        else "Instagram answered ${result.code}."
                }
                is ApiResult.NetworkError -> {
                    mutableStoryError.value = "No connection."
                }
            }
        }
    }

    fun clearStoryError() {
        mutableStoryError.value = null
    }

    // ---- Search (phase 3) ----

    /** User search with a short debounce; results are in-memory only. */
    fun searchUsers(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            mutableSearchState.value = SearchUiState()
            return
        }
        mutableSearchState.value = mutableSearchState.value.copy(query = trimmed, loading = true, error = null)
        searchJob = viewModelScope.launch {
            delay(500)
            val session = mutableSession.value
            if (session == null || !session.isLoggedIn) {
                mutableSearchState.value = mutableSearchState.value.copy(
                    loading = false,
                    error = "Sign in to search.",
                )
                return@launch
            }
            when (val result = api.searchUsers(session, trimmed)) {
                is ApiResult.Success -> {
                    mutableSearchState.value = mutableSearchState.value.copy(
                        results = result.value,
                        loading = false,
                        error = null,
                    )
                }
                is ApiResult.RateLimited -> {
                    mutableSearchState.value = mutableSearchState.value.copy(
                        loading = false,
                        error = "Instagram asked us to slow down. Try again in a bit.",
                    )
                }
                is ApiResult.HttpError -> {
                    mutableSearchState.value = mutableSearchState.value.copy(
                        loading = false,
                        error = if (result.code == 0) result.message else "Instagram answered ${result.code}.",
                    )
                }
                is ApiResult.NetworkError -> {
                    mutableSearchState.value = mutableSearchState.value.copy(
                        loading = false,
                        error = "No connection.",
                    )
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        mutableSearchState.value = SearchUiState()
    }

    fun exportBackup(target: android.net.Uri, context: android.content.Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val bytes = runtime.exportBackup()
                context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                    ?: error("Could not open that location.")
                "Backup saved. Keep the file to restore your accounts later."
            }.getOrElse { "Backup failed: ${it.message}" }
            onResult(message)
        }
    }

    fun importBackup(source: android.net.Uri, context: android.content.Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                    ?: error("Could not read that file.")
                "Restored ${runtime.importBackup(bytes)} account(s)."
            }.getOrElse { "Restore failed: ${it.message}" }
            onResult(message)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch { runtime.deleteActiveAccount() }
    }

    fun retry() {
        viewModelScope.launch { runtime.retry() }
    }

    fun setForeground(active: Boolean) = runtime.setForeground(active)
}
