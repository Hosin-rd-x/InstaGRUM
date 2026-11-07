package com.instaos.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    /** Avatar URL (from Instagram) or a local file path for a picked photo. */
    val avatar: String = "",
    val followers: Long = 0,
    val following: Long = 0,
    val visits: Long = 0,
    val verified: Boolean = false,
)

@Serializable
enum class MediaKind { IMAGE, VIDEO, REEL }

@Serializable
data class Media(val path: String = "", val kind: MediaKind = MediaKind.IMAGE, val artwork: Int = 0)

@Serializable
data class Comment(
    val id: String,
    val person: UserRef,
    val text: String,
    val createdAt: Long,
    val likes: Long = 0,
    val liked: Boolean = false,
    val parentId: String? = null,
    val own: Boolean = false,
)

@Serializable
data class Post(
    val id: String,
    val media: Media,
    val caption: String,
    val location: String = "",
    val createdAt: Long,
    val likes: Long = 0,
    val commentCount: Long = 0,
    val views: Long = 0,
    val comments: List<Comment> = emptyList(),
    val liked: Boolean = false,
    val saved: Boolean = false,
    /** Real share count from the server (0 until the API returns one). */
    val shares: Long = 0,
    /**
     * The post's author. Real feed posts carry their author here.
     * Null means "the signed-in user's own post" (the old implicit author);
     * UI must render `author ?: state.profile` and never assume otherwise.
     */
    val author: UserRef? = null,
    /** Instagram shortcode, e.g. `DfajF1zvsbl` — used for the share link. */
    val shortcode: String = "",
)

@Serializable
data class Story(
    val id: String,
    val media: Media,
    val caption: String = "",
    val createdAt: Long,
    val expiresAt: Long,
    val views: Long = 0,
    val highlight: String = "",
    val replies: List<String> = emptyList(),
    val seen: Boolean = false,
    val overlay: StoryOverlay = StoryOverlay(),
    /**
     * The story's owner. Null means the signed-in user's own story.
     * Stories loaded from the tray (other people's) carry their author.
     */
    val author: UserRef? = null,
)

/** UI-only settings. The simulation settings are gone with the engine (phase 3). */
@Serializable
data class UiSettings(
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val accent: AccentColor = AccentColor.VIOLET,
)

/** Accent colours offered in Settings (from the HTML preview spec). */
@Serializable
enum class AccentColor { VIOLET, LIME, PINK, TEAL }

@Serializable
data class UiSession(
    val tab: String = "home",
    val openPostId: String? = null,
    val openStoryId: String? = null,
    val storyProgress: Float = 0f,
    val screen: String = "main",
    val gridTab: Int = 0,
    val gridIndex: Int = 0,
    val gridOffset: Int = 0,
    val storyHighlight: String = "",
    /** Open DM thread id for [Route.DmThread]; null means the inbox. */
    val openDmThreadId: String? = null,
)

@Serializable
data class CreationDraft(
    val mode: Int = 0,
    val media: Media = Media(),
    val caption: String = "",
    val location: String = "",
    val highlight: String = "",
    val stage: String = "camera",
    val overlay: StoryOverlay = StoryOverlay(),
)

@Serializable
data class AccountSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String = "",
)

@Serializable
data class AppState(
    val schemaVersion: Int = 1,
    val activeAccountId: String = "default",
    val accounts: List<AccountSummary> = emptyList(),
    val profile: Profile = Profile(),
    val posts: List<Post> = emptyList(),
    val stories: List<Story> = emptyList(),
    val settings: UiSettings = UiSettings(),
    val session: UiSession = UiSession(),
    val lastSavedAt: Long = 0L,
    val draft: CreationDraft = CreationDraft(),
    val profileCreated: Boolean = false,
    /** Direct-message state (phase 2 models; screens in phase 3). */
    val dm: DmState = DmState(),
)
