package com.instaos.app.data

import com.instaos.app.model.*

object InitialState {
    fun create(now: Long) = AppState(
        schemaVersion = 4,
        profile = Profile(),
        lastSavedAt = now, profileCreated = false
    )

    /**
     * Schema v4 (phase 3): the simulation is deleted from the model, so
     * old snapshots simply stop decoding its fields (`ignoreUnknownKeys`
     * drops `engine`, `events`, `people`, `activity`, the sim settings and
     * the per-post/per-story sim counters). What survives the migration:
     * the user's profile, their own local posts/stories, and the dark-mode
     * + notifications choices (decoded into [UiSettings] from the old
     * settings object). The navigation is reset to Home (old sessions may
     * point at deleted routes) and any stale creation draft is cleared.
     */
    fun migrate(state: AppState): AppState {
        var base = state
        if (base.schemaVersion < 2) base = base.copy(schemaVersion = 2)
        if (base.schemaVersion < 3) base = base.copy(schemaVersion = 3)
        if (base.schemaVersion < 4) {
            base = base.copy(
                schemaVersion = 4,
                draft = CreationDraft(),
                session = UiSession(),
            )
        }
        return if (base.profileCreated) base else base.copy(session = UiSession())
    }
}
