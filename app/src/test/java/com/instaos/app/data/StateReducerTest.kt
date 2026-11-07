package com.instaos.app.data

import com.instaos.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/**
 * Phase 3: the simulation is gone, so the growth-preset / Live / activity
 * tests are gone with it. What is tested here is the real reducer:
 * creating posts and stories (starting at zero, no seeded comments),
 * comments, settings (dark mode + accent), the DM actions, and the
 * snapshot fallback. No fake people, no fake engagement anywhere.
 */
class StateReducerTest {
    private val now = 1_730_000_000_000L

    private fun ownedState() = InitialState.create(now).copy(
        profile = Profile("me", "Me"), profileCreated = true
    )

    @Test
    fun freshStateIsSchemaV4() {
        val initial = InitialState.create(now)
        assertEquals(4, initial.schemaVersion)
        assertFalse(initial.profileCreated)
        assertEquals(0, initial.profile.followers)
    }

    @Test
    fun creatingAPostAndStoryStartsAtZeroAndOpensThem() {
        val state = ownedState()
        val posted = StateReducer.reduce(
            state,
            Action.CreatePost(Media(), "first", ""),
            now,
            "post-1"
        )
        assertEquals("post-1", posted.session.openPostId)
        assertEquals(0, posted.posts.single().likes)
        assertEquals(0, posted.posts.single().views)
        assertTrue(posted.posts.single().comments.isEmpty())

        val story = StateReducer.reduce(
            posted,
            Action.CreateStory(Media(), "hey", "", StoryOverlay(text = "hi")),
            now,
            "story-1"
        )
        assertEquals("story-1", story.session.openStoryId)
        assertEquals(0, story.stories.single().views)
        assertEquals("hi", story.stories.single().overlay.text)
    }

    @Test
    fun ownCommentsNestAndIncrement() {
        var state = ownedState()
        state = StateReducer.reduce(state, Action.CreatePost(Media(), "x", ""), now, "p")
        val withComment = StateReducer.reduce(state, Action.AddComment("p", "first comment"), now, "c1")
        assertEquals(1, withComment.posts.single().commentCount)
        val parent = withComment.posts.single().comments.single().id
        val nested = StateReducer.reduce(withComment, Action.AddComment("p", "reply", parent), now, "c2")
        assertEquals(2, nested.posts.single().commentCount)
        assertEquals(parent, nested.posts.single().comments.last().parentId)
    }

    @Test
    fun settingsKeepDarkModeAndAccent() {
        val state = InitialState.create(now)
        val updated = StateReducer.reduce(
            state,
            Action.SaveSettings(state.settings.copy(darkMode = false, accent = AccentColor.LIME)),
            now,
            "settings"
        )
        assertFalse(updated.settings.darkMode)
        assertEquals(AccentColor.LIME, updated.settings.accent)
    }

    @Test
    fun dmInboxLoadSortsNewestFirstAndTotalsUnread() {
        val state = ownedState()
        val older = DmThread("t1", title = "old", lastMessageAt = now - 1000, unreadCount = 1)
        val newer = DmThread("t2", title = "new", lastMessageAt = now, unreadCount = 2)
        val loaded = StateReducer.reduce(state, Action.DmInboxLoaded(listOf(older, newer)), now, "inbox")
        assertEquals(listOf("t2", "t1"), loaded.dm.threads.map { it.threadId })
        assertEquals(3, loaded.dm.unreadTotal)
    }

    @Test
    fun dmThreadLoadCapsAt100AndMarksReadClearsUnread() {
        val state = ownedState()
        val other = UserRef("u1", "friend")
        val messages = (1..105).map { i ->
            DmMessage("m$i", "t1", other, "hi $i", now + i)
        }
        val thread = DmThread("t1", participants = listOf(other), title = "friend", unreadCount = 5)
        var loaded = StateReducer.reduce(
            state, Action.DmThreadLoaded(thread, messages), now, "thread"
        )
        assertEquals(100, loaded.dm.messages["t1"]!!.size)
        assertEquals(5, loaded.dm.unreadTotal)
        loaded = StateReducer.reduce(loaded, Action.DmThreadRead("t1"), now, "read")
        assertEquals(0, loaded.dm.threads.single().unreadCount)
        assertEquals(0, loaded.dm.unreadTotal)
        // The newest message is first.
        assertEquals("m105", loaded.dm.messages["t1"]!!.first().id)
    }

    @Test
    fun dmOptimisticAppendThenConfirmUpdatesThreadPreview() {
        var state = ownedState()
        val me = UserRef("me", "me")
        val other = UserRef("u1", "friend")
        state = StateReducer.reduce(
            state,
            Action.DmThreadLoaded(
                DmThread("t1", participants = listOf(other), title = "friend"),
                emptyList()
            ),
            now, "thread"
        )
        val sending = DmMessage("tmp-1", "t1", me, "hello", now, own = true, status = DmMessageStatus.SENDING)
        state = StateReducer.reduce(state, Action.DmMessageAppended(sending), now, "send")
        assertEquals(DmMessageStatus.SENDING, state.dm.messages["t1"]!!.single().status)
        val confirmed = sending.copy(id = "real-1", status = DmMessageStatus.SENT)
        state = StateReducer.reduce(state, Action.DmMessageUpdated("t1", "tmp-1", confirmed), now, "confirm")
        assertEquals("real-1", state.dm.messages["t1"]!!.single().id)
        assertEquals("hello", state.dm.threads.single().lastMessage)
    }

    @Test
    fun addStoriesSkipsDuplicates() {
        val state = ownedState()
        val story = Story("s1", Media(), "hi", now, now + 86400000)
        val once = StateReducer.reduce(state, Action.AddStories(listOf(story)), now, "add")
        assertEquals(1, once.stories.size)
        val twice = StateReducer.reduce(once, Action.AddStories(listOf(story)), now, "add2")
        assertEquals(1, twice.stories.size)
    }

    @Test
    fun snapshotRecoversFromDamagedPrimary() {
        val directory = Files.createTempDirectory("instaos-test").toFile()
        val store = SnapshotStore(directory)
        val state = InitialState.create(now)
        store.save(state)
        store.save(state.copy(profile = state.profile.copy(username = "second")))
        java.io.File(directory, "instaos-state.json").writeText("damaged")
        assertEquals("", store.load()?.profile?.username)
        directory.deleteRecursively()
    }
}
