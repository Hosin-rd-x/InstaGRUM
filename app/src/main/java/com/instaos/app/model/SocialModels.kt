package com.instaos.app.model

import kotlinx.serialization.Serializable

/**
 * Story editor overlay (text + sticker). The only survivor of the old
 * social-models file: everything else in it (fake activity, fake story
 * viewers/insights, live viewers, pending fake interactions) was
 * simulation data and is deleted with the engine in phase 3.
 */
@Serializable
data class StoryOverlay(
    val text: String = "", val sticker: String = "", val x: Float = .5f, val y: Float = .45f,
    val textColor: Long = 0xFFFFFFFF, val closeFriends: Boolean = false,
)
