package com.instaos.app.model

import kotlinx.serialization.Serializable

/**
 * Direct-message models (phase 2, audit §8 step 1).
 *
 * Threads are keyed by Instagram's `thread_id` — never by a user id: a 1:1
 * DM has a stable thread id, and a group thread has no single user id at
 * all (see xphotogram-dm-notes.md §7). The inbox/thread *screens* are
 * phase 3; these types exist now so the session/feed work can already
 * carry DM state without a later model migration.
 *
 * Bubble styling target (iOS 454.1, ios-ui-research.md §1.7): sent bubbles
 * purple gradient, received bubbles gray, own messages right-aligned.
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
)

@Serializable
data class DmState(
    val threads: List<DmThread> = emptyList(),
    val openThreadId: String? = null,
    /** Messages per thread, keyed by `thread_id`. Kept small: newest first, capped by the loader. */
    val messages: Map<String, List<DmMessage>> = emptyMap(),
    val unreadTotal: Int = 0,
)
