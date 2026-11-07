package com.instaos.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.instaos.app.data.ApiResult
import com.instaos.app.data.FeedCache
import com.instaos.app.data.FeedRepository
import com.instaos.app.data.SessionStore
import com.instaos.app.data.SimulatorRuntime
import com.instaos.app.data.WebInstagramWebApi
import com.instaos.app.data.WebSession
import com.instaos.app.model.Action
import com.instaos.app.model.Comment
import com.instaos.app.model.FeedUiState
import com.instaos.app.model.Post
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = SimulatorRuntime.get(application)
    val state = runtime.state
    val error = runtime.error

    // ---- Phase 2: web session + home feed (outside the snapshot) ----
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

    init {
        viewModelScope.launch { runtime.load() }
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (runtime.foreground) runtime.tick()
            }
        }
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
        sessionStore.clear()
        mutableSession.value = null
        mutableOfflineMode.value = false
        mutableFeedState.value = FeedUiState(error = FeedRepository.NOT_SIGNED_IN)
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
            person = com.instaos.app.model.FakePerson(
                "self",
                me?.username.orEmpty(),
                me?.displayName.orEmpty(),
                me?.avatar.orEmpty(),
                verified = me?.verified == true,
            ),
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
    fun openNotification(id: String) = dispatch(Action.OpenActivity(id))
}
