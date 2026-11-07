package com.instaos.app.data

import com.instaos.app.model.*

/**
 * Pure `Action → AppState` transitions (phase 3).
 *
 * The simulation half is gone: no engine stepping, no generated comments,
 * no fake followers/activity. What stays is the honest half — user-intent
 * transitions (navigation, local likes/saves/comments, local posts and
 * stories the user creates on this device) plus the loaders for real data
 * that arrives from the web session (DM inbox/threads, tray stories).
 */
object StateReducer {
    private const val LIMIT = 9_000_000_000_000L
    private fun Long.stat() = coerceIn(0, LIMIT)
    fun reduce(s: AppState, action: Action, now: Long, id: String): AppState {
        fun posts(transform: (Post) -> Post) = s.copy(posts = s.posts.map(transform))
        fun ownComment(text: String, parent: String? = null) = Comment(
            id,
            s.profile.toUserRef(),
            text.trim().take(2000),
            now,
            parentId = parent,
            own = true
        )
        return when (action) {
            is Action.SwitchAccount, Action.CreateAccount -> s
            is Action.NavigateRoute -> s.copy(
                session = action.route.toUiSession(s.session),
                dm = when (val route = action.route) {
                    is Route.DmThread -> s.dm.copy(openThreadId = route.threadId)
                    Route.DmInbox -> s.dm.copy(openThreadId = null)
                    else -> s.dm
                },
            )
            is Action.OpenDm -> s.copy(
                session = (if (action.threadId == null) Route.DmInbox else Route.DmThread(action.threadId))
                    .toUiSession(s.session),
                dm = s.dm.copy(openThreadId = action.threadId),
            )
            is Action.LoginCompleted -> s.copy(
                profile = action.profile,
                profileCreated = true,
                session = UiSession(),
                accounts = s.accounts.filterNot { it.id == s.activeAccountId } + AccountSummary(
                    s.activeAccountId,
                    action.profile.username,
                    action.profile.displayName,
                    action.profile.avatar,
                ),
            )
            Action.Logout -> s.copy(session = UiSession())

            is Action.Navigate -> s.copy(
                session = s.session.copy(
                    screen = action.screen,
                    tab = action.tab,
                    openPostId = null,
                    openStoryId = null
                )
            )

            is Action.OpenPost -> s.copy(session = s.session.copy(openPostId = action.id))
            is Action.OpenStory -> s.copy(
                session = s.session.copy(
                    openStoryId = action.id,
                    storyProgress = action.progress,
                    storyHighlight = action.highlight.orEmpty(),
                ), stories = s.stories.map { if (it.id == action.id) it.copy(seen = true) else it })

            is Action.ViewStory -> s.copy(stories = s.stories.map { story ->
                if (story.id == action.id) story.copy(
                    seen = true,
                    views = if (story.expiresAt > now) story.views + 1 else story.views
                ) else story
            })

            is Action.StoryProgress -> s.copy(
                session = s.session.copy(
                    storyProgress = action.progress.coerceIn(
                        0f,
                        1f
                    )
                )
            )

            is Action.GridPosition -> s.copy(
                session = s.session.copy(
                    gridIndex = action.index,
                    gridOffset = action.offset,
                    gridTab = action.tab
                )
            )

            is Action.EditProfile -> s.copy(
                profile = action.profile.copy(
                    username = action.profile.username.trim().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
                        .take(30).ifBlank { s.profile.username },
                    displayName = action.profile.displayName.take(80),
                    bio = action.profile.bio.take(300),
                    followers = s.profile.followers, following = s.profile.following, visits = s.profile.visits,
                ), session = s.session.copy(screen = "main")
            )

            is Action.SaveSettings -> s.copy(settings = action.settings)

            is Action.SaveDraft -> s.copy(draft = action.draft)

            is Action.CreatePost -> {
                val post = Post(
                    id,
                    action.media,
                    action.caption.take(2200),
                    action.location.take(100),
                    now,
                )
                s.copy(posts = listOf(post) + s.posts, session = UiSession(openPostId = id), draft = CreationDraft())
            }

            is Action.CreateStory -> s.copy(
                stories = s.stories + Story(
                    id,
                    action.media,
                    action.caption.take(500),
                    now,
                    now + 86400000,
                    highlight = action.highlight.trim().take(30),
                    overlay = action.overlay,
                ), session = UiSession(openStoryId = id), draft = CreationDraft(mode = 1)
            )

            is Action.LikePost -> posts { p ->
                if (p.id != action.id || (action.onlyLike && p.liked)) p else p.copy(
                    liked = !p.liked,
                    likes = (p.likes + if (p.liked) -1 else 1).stat()
                )
            }

            is Action.SavePost -> posts { if (it.id == action.id) it.copy(saved = !it.saved) else it }
            is Action.DeletePost -> s.copy(
                posts = s.posts.filterNot { it.id == action.id },
                session = s.session.copy(openPostId = null)
            )

            is Action.AddComment -> if (action.text.isBlank()) s else posts {
                if (it.id == action.postId) it.copy(
                    commentCount = (it.commentCount + 1).stat(),
                    comments = (it.comments + ownComment(
                        action.text,
                        action.parentId?.takeIf { parent -> it.comments.any { comment -> comment.id == parent } })).threadWindow()
                ) else it
            }

            is Action.LikeComment -> posts { p ->
                if (p.id == action.postId) p.copy(comments = p.comments.map {
                    if (it.id == action.commentId) it.copy(
                        liked = !it.liked,
                        likes = (it.likes + if (it.liked) -1 else 1).stat()
                    ) else it
                }) else p
            }

            is Action.StoryReply -> s.copy(stories = s.stories.map {
                if (it.id == action.id && action.text.isNotBlank()) it.copy(
                    replies = (it.replies + action.text.trim().take(500)).takeLast(100)
                ) else it
            })

            is Action.Highlight -> s.copy(stories = s.stories.map {
                if (it.id == action.id) it.copy(
                    highlight = action.name.trim().take(30)
                ) else it
            })

            is Action.DeleteStory -> s.copy(
                stories = s.stories.filterNot { it.id == action.id },
                session = s.session.copy(openStoryId = null)
            )

            is Action.AddStories -> {
                val known = s.stories.map { it.id }.toSet()
                val fresh = action.stories.filterNot { it.id in known }
                if (fresh.isEmpty()) s else s.copy(stories = s.stories + fresh)
            }

            is Action.DmInboxLoaded -> {
                val threads = action.threads.sortedByDescending { it.lastMessageAt }
                s.copy(
                    dm = s.dm.copy(
                        threads = threads,
                        unreadTotal = threads.sumOf { it.unreadCount },
                    ),
                )
            }

            is Action.DmThreadLoaded -> {
                val thread = action.thread
                val capped = action.messages.sortedByDescending { it.createdAt }.take(100)
                val threads = s.dm.threads.map { if (it.threadId == thread.threadId) thread else it }
                    .let { if (it.any { t -> t.threadId == thread.threadId }) it else it + thread }
                s.copy(
                    dm = s.dm.copy(
                        threads = threads,
                        messages = s.dm.messages + (thread.threadId to capped),
                        unreadTotal = threads.sumOf { it.unreadCount },
                    ),
                )
            }

            is Action.DmMessageAppended -> {
                val list = s.dm.messages[action.message.threadId].orEmpty()
                s.copy(
                    dm = s.dm.copy(
                        messages = s.dm.messages + (action.message.threadId to (listOf(action.message) + list).take(100)),
                    ),
                )
            }

            is Action.DmMessageUpdated -> {
                val list = s.dm.messages[action.threadId].orEmpty()
                    .map { if (it.id == action.tempId) action.message else it }
                val threads = s.dm.threads.map {
                    if (it.threadId == action.threadId && action.message.status == DmMessageStatus.SENT) {
                        it.copy(lastMessage = action.message.text, lastMessageAt = action.message.createdAt)
                    } else it
                }
                s.copy(
                    dm = s.dm.copy(
                        messages = s.dm.messages + (action.threadId to list),
                        threads = threads,
                    ),
                )
            }

            is Action.DmThreadRead -> {
                val threads = s.dm.threads.map {
                    if (it.threadId == action.threadId) it.copy(unreadCount = 0) else it
                }
                s.copy(
                    dm = s.dm.copy(
                        threads = threads,
                        unreadTotal = threads.sumOf { it.unreadCount },
                    ),
                )
            }
        }
    }
}
