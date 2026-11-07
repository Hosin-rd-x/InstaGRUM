package com.instaos.app.model

import kotlinx.serialization.Serializable

/**
 * Typed navigation destinations (phase 2, audit §8 step 1).
 *
 * The old navigation was two raw strings in [UiSession] (`tab` / `screen`).
 * Any typo — or any route the router did not know — silently fell through
 * to `ProfileScreen` via the router's `else` branch. [Route] is the single
 * source of truth for where the app is; [UiSession] stays the *persisted*
 * form (its strings are still what the snapshot stores, so old snapshots
 * keep loading), and [fromUiSession]/[toUiSession] translate between them.
 *
 * Unknown legacy strings map explicitly to [Home] — never silently to
 * Profile — and [Route.fromUiSession] is the only place that decision lives.
 */
sealed interface Route {
    data object Home : Route
    data object Reels : Route
    data object DmInbox : Route
    data class DmThread(val threadId: String) : Route
    data object Search : Route
    data object Profile : Route
    data object Settings : Route
    data object Create : Route
    data object Live : Route
    data object LiveHistory : Route
    data object Archive : Route
    data object Notifications : Route
    data class PostDetail(val postId: String) : Route
    data class StoryViewer(val storyId: String, val highlight: String = "") : Route

    companion object {
        /** The five bottom-bar tabs, left to right (iOS Instagram 454.1 order). */
        val bottomTabs: List<Route> = listOf(Home, Reels, DmInbox, Search, Profile)

        fun fromUiSession(session: UiSession): Route {
            session.openStoryId?.let { return StoryViewer(it, session.storyHighlight) }
            session.openPostId?.let { return PostDetail(it) }
            return when (session.screen) {
                "settings" -> Settings
                "create" -> Create
                "live" -> Live
                "history" -> LiveHistory
                "archive" -> Archive
                "reels", "explore" -> Reels
                "notifications" -> Notifications
                "dm" -> session.openDmThreadId?.let { DmThread(it) } ?: DmInbox
                "search" -> Search
                "main" -> fromTab(session.tab)
                // Unknown screen string: explicit Home, never a silent Profile.
                else -> Home
            }
        }

        /** The tab a legacy `tab` string means; unknown tabs are Home. */
        fun fromTab(tab: String): Route = when (tab) {
            "home" -> Home
            "reels", "explore" -> Reels
            "dm" -> DmInbox
            "search" -> Search
            "profile" -> Profile
            "notifications" -> Notifications
            else -> Home
        }
    }
}

/** The bottom-bar tab a route belongs to, or null for full-screen routes. */
fun Route.bottomTab(): Route? = when (this) {
    Route.Home -> Route.Home
    Route.Reels -> Route.Reels
    Route.DmInbox, is Route.DmThread -> Route.DmInbox
    Route.Search -> Route.Search
    Route.Profile -> Route.Profile
    else -> null
}

/**
 * Persist this route back into a [UiSession]. Overlay routes keep the
 * current tab/screen underneath so closing them returns where the user was.
 */
fun Route.toUiSession(previous: UiSession = UiSession()): UiSession = when (this) {
    Route.Home -> UiSession(tab = "home", screen = "main")
    Route.Profile -> UiSession(tab = "profile", screen = "main")
    Route.Reels -> UiSession(tab = "reels", screen = "reels")
    Route.DmInbox -> UiSession(tab = "dm", screen = "dm")
    is Route.DmThread -> UiSession(tab = "dm", screen = "dm", openDmThreadId = threadId)
    Route.Search -> UiSession(tab = "search", screen = "search")
    Route.Settings -> UiSession(tab = previous.tab, screen = "settings")
    Route.Create -> UiSession(tab = previous.tab, screen = "create")
    Route.Live -> UiSession(tab = previous.tab, screen = "live")
    Route.LiveHistory -> UiSession(tab = previous.tab, screen = "history")
    Route.Archive -> UiSession(tab = previous.tab, screen = "archive")
    Route.Notifications -> UiSession(tab = "notifications", screen = "notifications")
    is Route.PostDetail -> previous.copy(openPostId = postId)
    is Route.StoryViewer -> previous.copy(openStoryId = storyId, storyHighlight = highlight)
}
