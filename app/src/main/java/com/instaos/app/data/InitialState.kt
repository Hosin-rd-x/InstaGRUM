package com.instaos.app.data

import com.instaos.app.model.*
import com.instaos.app.simulation.GrowthPresets

object InitialState {
    fun create(now: Long) = AppState(
        schemaVersion = 3,
        profile = Profile(), settings = GrowthPresets.settings(GrowthPreset.NORMAL),
        lastSavedAt = now, lastSimulationAt = now, profileCreated = false
    )

    fun migrate(state: AppState): AppState {
        var base = if (state.schemaVersion < 2) state.copy(
            schemaVersion = 2,
            settings = state.settings.copy(backgroundActivity = true)
        ) else state
        if (base.schemaVersion < 3) {
            // Schema v3 (phase 2): drop the accumulated simulation *data* from
            // the persisted snapshot, while keeping the user's own settings
            // (dark mode + notifications) and their profile/posts/stories.
            // The simulation *fields* (EngineState, GrowthProfile,
            // EngagementProfile, the Post sim counters) stay in the model for
            // now: the simulation engine and the UI that reads those fields
            // are explicitly kept in phase 2 (audit §8 step 6 deletes them
            // in phase 3, once nothing reads them any more). Clearing the
            // data here means a v3 snapshot never carries stale sim state
            // forward, and new snapshots start from a clean engine.
            base = base.copy(
                schemaVersion = 3,
                engine = EngineState(),
                events = emptyList(),
                people = emptyList(),
                activity = emptyList(),
                settings = SimulationSettings(
                    darkMode = base.settings.darkMode,
                    notificationsEnabled = base.settings.notificationsEnabled,
                ),
                draft = CreationDraft(),
                session = UiSession(),
                lastSimulationAt = System.currentTimeMillis(),
            )
        }
        return if (base.profileCreated) base else base.copy(
            settings = GrowthPresets.settings(GrowthPreset.NORMAL, base.settings),
            session = UiSession(), activeLive = null,
        )
    }
}
