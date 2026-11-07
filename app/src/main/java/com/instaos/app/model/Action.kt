package com.instaos.app.model

/**
 * User intents (phase 3).
 *
 * The simulation-era actions are gone with the engine: ChooseGrowth,
 * SetSeed, Viral/EndViral, PostStats, AccountStats, AddFollowers, AddViews,
 * StoryStats, ReadEvents, ResetStatistics, OpenActivity, StartLive /
 * StopLive / LiveLike / LiveHype / LiveDehype / LiveComment, CompleteProfile
 * (onboarding is replaced by the WebView login), Follow (no real user lists
 * in v1). Network results (feed, DM, search) arrive through the ViewModel,
 * which dispatches the `Dm*Loaded` / `AddStories` actions below.
 */
sealed interface Action {
    data class Navigate(val screen: String = "main", val tab: String = "home") : Action
    /** Typed navigation. Prefer this over the string [Navigate]. */
    data class NavigateRoute(val route: Route) : Action
    /** Open the DM inbox (`threadId == null`) or one thread by `thread_id`. */
    data class OpenDm(val threadId: String?) : Action
    /** A web login finished: adopt the Instagram profile, mark signed in. */
    data class LoginCompleted(val profile: Profile) : Action
    /** Sign out of the web session (local profile is kept). */
    data object Logout : Action
    data class SwitchAccount(val id: String) : Action
    data object CreateAccount : Action
    data class OpenPost(val id: String?) : Action
    data class OpenStory(val id: String?, val progress: Float = 0f, val highlight: String? = null) : Action
    data class ViewStory(val id: String) : Action
    data class StoryProgress(val progress: Float) : Action
    data class GridPosition(val index: Int, val offset: Int, val tab: Int) : Action
    data class EditProfile(val profile: Profile) : Action
    data class SaveSettings(val settings: UiSettings) : Action
    data class SaveDraft(val draft: CreationDraft) : Action
    /** Create a local post (stays on this device; posting to Instagram is not in v1). */
    data class CreatePost(val media: Media, val caption: String, val location: String) : Action
    /** Create a local story (stays on this device). */
    data class CreateStory(
        val media: Media,
        val caption: String,
        val highlight: String,
        val overlay: StoryOverlay = StoryOverlay()
    ) : Action

    data class LikePost(val id: String, val onlyLike: Boolean = false) : Action
    data class SavePost(val id: String) : Action
    data class DeletePost(val id: String) : Action
    data class AddComment(val postId: String, val text: String, val parentId: String? = null) : Action
    data class LikeComment(val postId: String, val commentId: String) : Action
    data class StoryReply(val id: String, val text: String) : Action
    data class Highlight(val id: String, val name: String) : Action
    data class DeleteStory(val id: String) : Action
    /** Stories loaded from the stories tray (other people's, real data). */
    data class AddStories(val stories: List<Story>) : Action
    /** The DM inbox was loaded from the web session. */
    data class DmInboxLoaded(val threads: List<DmThread>) : Action
    /** One DM thread was loaded (messages newest-first, capped by the loader). */
    data class DmThreadLoaded(val thread: DmThread, val messages: List<DmMessage>) : Action
    /** Optimistic local append while a message is sending. */
    data class DmMessageAppended(val message: DmMessage) : Action
    /** Replace an optimistic message (by its temp id) with the sent/failed one. */
    data class DmMessageUpdated(val threadId: String, val tempId: String, val message: DmMessage) : Action
    /** The user opened a thread: clear its unread count. */
    data class DmThreadRead(val threadId: String) : Action
}
