package com.instaos.app.data

import com.instaos.app.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 3: schema v4 migration. The simulation is deleted from the model,
 * so a v3 snapshot simply decodes without its old sim fields
 * (`ignoreUnknownKeys` drops them). What survives: the user's profile,
 * their own local posts/stories, and their dark-mode choice. The
 * navigation resets to Home (old sessions may point at deleted routes)
 * and any stale creation draft is cleared.
 */
class MigrationTest {
    private val now = 1_730_000_000_000L

    private fun v3State(): AppState = InitialState.create(now).copy(
        schemaVersion = 3,
        profile = Profile("kept.user", "Kept", followers = 12),
        profileCreated = true,
        settings = UiSettings(darkMode = false, notificationsEnabled = false, accent = AccentColor.LIME),
        draft = CreationDraft(caption = "stale draft"),
        session = UiSession(tab = "notifications", screen = "notifications"),
        posts = listOf(Post("p1", Media(), "kept post", "", now)),
    )

    @Test
    fun v4MigrationKeepsProfilePostsAndSettingsButResetsSessionAndDraft() {
        val migrated = InitialState.migrate(v3State())
        assertEquals(4, migrated.schemaVersion)
        assertEquals("kept.user", migrated.profile.username)
        assertEquals(12, migrated.profile.followers)
        assertEquals(1, migrated.posts.size)
        assertEquals("kept post", migrated.posts.single().caption)
        assertFalse(migrated.settings.darkMode)
        assertFalse(migrated.settings.notificationsEnabled)
        assertEquals(AccentColor.LIME, migrated.settings.accent)
        assertEquals(CreationDraft(), migrated.draft)
        assertEquals(UiSession(), migrated.session)
    }

    @Test
    fun v4MigrationIsIdempotent() {
        val v4 = InitialState.migrate(v3State())
        assertEquals(v4, InitialState.migrate(v4))
    }

    @Test
    fun freshStateIsAlreadyV4() {
        assertEquals(4, InitialState.create(now).schemaVersion)
    }

    @Test
    fun v2StateMigratesAllTheWayToV4() {
        val v2 = v3State().copy(schemaVersion = 2)
        val migrated = InitialState.migrate(v2)
        assertEquals(4, migrated.schemaVersion)
        assertEquals("kept.user", migrated.profile.username)
    }
}
