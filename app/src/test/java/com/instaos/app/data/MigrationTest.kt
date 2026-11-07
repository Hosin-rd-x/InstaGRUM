package com.instaos.app.data

import com.instaos.app.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 2: schema v3 migration. A v2 snapshot's accumulated simulation
 * data (engine clock, events, fake people, activity, draft) is dropped;
 * the user's own profile and their dark-mode choice survive.
 */
class MigrationTest {
    private val now = 1_730_000_000_000L

    private fun v2State(): AppState = InitialState.create(now).copy(
        schemaVersion = 2,
        profile = Profile("kept.user", "Kept", followers = 12),
        profileCreated = true,
        engine = EngineState(seed = 123, elapsedSeconds = 999.0),
        events = listOf(GrowthEvent("e1", EventKind.VIRAL, "t", "d", now)),
        people = listOf(FakePerson("p1", "fake.person", "Fake")),
        activity = listOf(
            SocialActivity("a1", InteractionKind.LIKE, FakePerson("p1", "fake.person", "Fake"), now),
        ),
        settings = SimulationSettings(darkMode = false, notificationsEnabled = false),
        draft = CreationDraft(caption = "stale draft"),
        session = UiSession(tab = "notifications", screen = "notifications"),
    )

    @Test
    fun v3MigrationDropsSimulationDataButKeepsProfileAndSettings() {
        val migrated = InitialState.migrate(v2State())
        assertEquals(3, migrated.schemaVersion)
        assertEquals(EngineState(), migrated.engine)
        assertTrue(migrated.events.isEmpty())
        assertTrue(migrated.people.isEmpty())
        assertTrue(migrated.activity.isEmpty())
        assertEquals(CreationDraft(), migrated.draft)
        assertEquals(UiSession(), migrated.session)
        assertEquals("kept.user", migrated.profile.username)
        assertEquals(12, migrated.profile.followers)
        assertFalse(migrated.settings.darkMode)
        assertFalse(migrated.settings.notificationsEnabled)
    }

    @Test
    fun v3MigrationIsIdempotent() {
        val v3 = InitialState.migrate(v2State())
        assertEquals(v3, InitialState.migrate(v3))
    }

    @Test
    fun freshStateIsAlreadyV3() {
        assertEquals(3, InitialState.create(now).schemaVersion)
    }
}
