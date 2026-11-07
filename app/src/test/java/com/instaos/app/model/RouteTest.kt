package com.instaos.app.model

import com.instaos.app.data.InitialState
import com.instaos.app.data.StateReducer
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 2: typed navigation. The old router silently rendered Profile for
 * any route string it did not know — including typos. These tests pin the
 * new behaviour: unknown routes are Home, explicitly.
 */
class RouteTest {
    private val now = 1_730_000_000_000L

    @Test
    fun bottomTabsAreInIosOrder() {
        assertEquals(
            listOf(Route.Home, Route.Reels, Route.DmInbox, Route.Search, Route.Profile),
            Route.bottomTabs,
        )
    }

    @Test
    fun legacyTabStringsMapToRoutes() {
        assertEquals(Route.Home, Route.fromUiSession(UiSession(tab = "home")))
        assertEquals(Route.Profile, Route.fromUiSession(UiSession(tab = "profile")))
        assertEquals(Route.Reels, Route.fromUiSession(UiSession(screen = "reels")))
        assertEquals(Route.Reels, Route.fromUiSession(UiSession(screen = "explore")))
        assertEquals(Route.Notifications, Route.fromUiSession(UiSession(screen = "notifications")))
        assertEquals(Route.DmInbox, Route.fromUiSession(UiSession(screen = "dm")))
        assertEquals(Route.Search, Route.fromUiSession(UiSession(screen = "search")))
        assertEquals(Route.Settings, Route.fromUiSession(UiSession(screen = "settings")))
        assertEquals(Route.Create, Route.fromUiSession(UiSession(screen = "create")))
    }

    @Test
    fun overlaysWinOverTabs() {
        assertEquals(Route.PostDetail("p1"), Route.fromUiSession(UiSession(openPostId = "p1")))
        assertEquals(Route.StoryViewer("s1"), Route.fromUiSession(UiSession(openStoryId = "s1")))
    }

    @Test
    fun dmThreadIsKeyedByThreadId() {
        val route = Route.fromUiSession(UiSession(screen = "dm", openDmThreadId = "thread-7"))
        assertEquals(Route.DmThread("thread-7"), route)
        assertEquals(Route.DmInbox, Route.DmThread("thread-7").bottomTab())
    }

    @Test
    fun unknownRoutesAreHomeNeverSilentProfile() {
        assertEquals(Route.Home, Route.fromUiSession(UiSession(screen = "nonsense")))
        assertEquals(Route.Home, Route.fromUiSession(UiSession(tab = "bogus")))
        assertEquals(Route.Home, Route.fromUiSession(UiSession(screen = "main", tab = "typo")))
    }

    @Test
    fun routeRoundTripsThroughThePersistedSession() {
        val routes = listOf(
            Route.Home, Route.Reels, Route.DmInbox, Route.DmThread("t-1"),
            Route.Search, Route.Profile, Route.Settings, Route.Create,
        )
        for (route in routes) {
            assertEquals(route, Route.fromUiSession(route.toUiSession()))
        }
    }

    @Test
    fun navigateRouteActionMovesTheSession() {
        val state = InitialState.create(now).copy(profile = Profile("me", "Me"), profileCreated = true)
        val searched = StateReducer.reduce(state, Action.NavigateRoute(Route.Search), now, "nav")
        assertEquals("search", searched.session.screen)
        val thread = StateReducer.reduce(state, Action.NavigateRoute(Route.DmThread("t-9")), now, "nav2")
        assertEquals("t-9", thread.session.openDmThreadId)
        assertEquals("t-9", thread.dm.openThreadId)
    }

    @Test
    fun loginCompletedAdoptsTheInstagramProfile() {
        val state = InitialState.create(now)
        val loggedIn = StateReducer.reduce(
            state,
            Action.LoginCompleted(Profile("someone", "Someone", verified = true)),
            now,
            "login",
        )
        assertTrue(loggedIn.profileCreated)
        assertEquals("someone", loggedIn.profile.username)
        assertEquals(UiSession(), loggedIn.session)
    }
}
