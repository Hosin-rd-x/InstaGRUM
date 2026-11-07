package com.instaos.app.data

import com.instaos.app.model.*
import com.instaos.app.simulation.GrowthPresets

object InitialState {
    fun create(now: Long) = AppState(
        schemaVersion = 2,
        profile = Profile(), settings = GrowthPresets.settings(GrowthPreset.NORMAL),
        lastSavedAt = now, lastSimulationAt = now, profileCreated = false
    )

    fun migrate(state: AppState): AppState {

        val base = if (state.schemaVersion < 2) state.copy(
            schemaVersion = 2,
            settings = state.settings.copy(backgroundActivity = true)
        ) else state
        return if (base.profileCreated) base else base.copy(
            settings = GrowthPresets.settings(GrowthPreset.NORMAL, base.settings),
            session = UiSession(), activeLive = null,
        )
    }
}
