package com.instaos.app.model

import kotlinx.serialization.Serializable

/**
 * Feed models (phase 2, audit §8 steps 1–3).
 *
 * The home feed is deliberately NOT part of [AppState]: the whole-state
 * JSON snapshot is written on nearly every action, and feed pages would
 * balloon it (audit §9.3). Feed data lives in [com.instaos.app.data.FeedCache]
 * (its own small file, 5-minute TTL) plus in-memory [FeedUiState] only.
 */
@Serializable
data class FeedPage(
    val items: List<Post> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class StoryTrayItem(
    val user: UserRef,
    val latestStoryId: String = "",
    val seen: Boolean = false,
    /** Cover image URL for the tray circle (the user's avatar is the fallback). */
    val mediaUrl: String = "",
)

/** In-memory UI state for the Home feed screen (never persisted). */
data class FeedUiState(
    val posts: List<Post> = emptyList(),
    val tray: List<StoryTrayItem> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val lastUpdatedAt: Long = 0,
)
