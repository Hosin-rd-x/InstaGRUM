package com.instaos.app.model

import kotlinx.serialization.Serializable

/**
 * Direct-message models (phase 2 models; screens in phase 3).
 *
 * Threads are keyed by Instagram's `thread_id` — never by a user id: a 1:1
 * DM has a stable thread id, and a group thread has no single user id at
 * all (see xphotogram-dm-notes.md §7).
 *
 * Bubble styling target (iOS 454.1, ios-ui-research.md §1.7): sent bubbles
 * purple gradient, received bubbles gray, own messages right-aligned.
 *
 * End-to-end encrypted threads ([DmThread.e2ee]) cannot be read or sent
 * from a web-session client — the app shows an honest "not supported in
 * this version" note for them instead of faking content.
 */
@Serializable
enum class DmMessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

@Serializable
data class DmMessage(
    val id: String,
    val threadId: String,
    val sender: UserRef,
    val text: String,
    val createdAt: Long,
    val own: Boolean = false,
    val status: DmMessageStatus = DmMessageStatus.SENT,
    /** Instagram `item_type` ("text", "media", "like", "reel_share", …). */
    val itemType: String = "text",
)

@Serializable
data class DmThread(
    /** Instagram `thread_id`. */
    val threadId: String,
    val participants: List<UserRef> = emptyList(),
    val title: String = "",
    val lastMessage: String = "",
    val lastMessageAt: Long = 0,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    /** True when the thread is end-to-end encrypted (unreadable here). */
    val e2ee: Boolean = false,
    /**
     * The `item_id` the other side last saw — when it matches our newest
     * own message, the thread shows a "Seen" receipt under it.
     */
    val seenItemId: String = "",
)

@Serializable
data class DmState(
    val threads: List<DmThread> = emptyList(),
    val openThreadId: String? = null,
    /** Messages per thread, keyed by `thread_id`. Kept small: newest first, capped by the loader. */
    val messages: Map<String, List<DmMessage>> = emptyMap(),
    val unreadTotal: Int = 0,
)
