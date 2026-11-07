package com.instaos.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.instaos.app.data.InitialState
import com.instaos.app.data.StateReducer
import com.instaos.app.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Phase 3: onboarding, the growth-pace settings and Live are gone, so
 * those tests are gone too. What is tested here is the real flow:
 * Home → Profile → Create → like + comment; the accent picker in
 * Settings; the story viewer with its honest insights note; and the
 * DM inbox / Search screens opening without placeholders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class AppFlowsTest {
    @get:Rule
    val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val state = mutableStateOf(InitialState.create(now))
    private var sequence = 0

    private fun dispatch(action: Action) {
        state.value = StateReducer.reduce(state.value, action, now, "ui-${sequence++}")
    }

    private fun launch() {
        compose.setContent {
            InstaTheme(state.value.settings.darkMode) {
                MediaProvider { InstaApp(state.value, ::dispatch) }
            }
        }
    }

    private fun withProfile() {
        state.value = state.value.copy(
            profile = Profile("my.world", "My Name"),
            profileCreated = true
        )
    }

    @Test
    fun createPostThenLikeAndComment() {
        launch()
        withProfile()
        // Phase 2 nav: Home is the default tab now; the profile lives
        // under the Profile tab, and Create is the "+" top-left.
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("my.world").assertIsDisplayed()
        compose.onNodeWithContentDescription("Create content").performClick()
        compose.onNodeWithText("POST").performClick()

        compose.runOnIdle {
            dispatch(
                Action.CreatePost(
                    Media(), "A quiet morning", ""
                )
            )
        }
        compose.onNodeWithTag("postMedia").assertIsDisplayed()
        compose.onNodeWithContentDescription("Like post").performClick()
        compose.runOnIdle { assertTrue(state.value.posts.first().liked) }
        compose.onNodeWithContentDescription("Open comments").performClick()
        compose.onNodeWithTag("commentInput").performTextInput("From my phone")
        compose.onNodeWithContentDescription("Post comment").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(1, state.value.posts.first().commentCount)
            assertEquals("From my phone", state.value.posts.first().comments.single().text)
        }
    }

    @Test
    fun chooseAccentColourFromSettings() {
        launch()
        withProfile()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("accent-LIME"))
        compose.onNodeWithTag("accent-LIME").performClick()
        compose.runOnIdle { assertEquals(AccentColor.LIME, state.value.settings.accent) }
        compose.onNodeWithTag("accent-PINK").performClick()
        compose.runOnIdle { assertEquals(AccentColor.PINK, state.value.settings.accent) }
    }

    @Test
    fun storyOpensViewerWithHonestInsightsNote() {
        launch()
        withProfile()

        compose.mainClock.autoAdvance = false

        state.value = state.value.copy(profile = state.value.profile.copy(followers = 1_000))
        state.value = StateReducer.reduce(
            state.value,
            Action.CreateStory(Media(), "", "", StoryOverlay(text = "hey")),
            now,
            "ui-${sequence++}"
        )
        compose.waitForIdle()

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("storyViewer").assertExists()
        compose.onNodeWithContentDescription("Story insights").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("storyInsights").assertIsDisplayed()
        compose.onNodeWithText("Viewer list isn't available", substring = true).assertExists()
    }

    @Test
    fun dmInboxAndSearchOpenWithoutPlaceholders() {
        launch()
        withProfile()
        compose.runOnIdle { dispatch(Action.NavigateRoute(Route.DmInbox)) }
        compose.onNodeWithText("Messages").assertIsDisplayed()
        // Not signed in in this test, so the inbox says so honestly.
        compose.onNodeWithText("Sign in to see your messages").assertIsDisplayed()
        compose.runOnIdle { dispatch(Action.NavigateRoute(Route.Search)) }
        compose.onNodeWithTag("searchField").assertIsDisplayed()
        compose.onNodeWithText("Explore is empty").assertIsDisplayed()
    }
}
