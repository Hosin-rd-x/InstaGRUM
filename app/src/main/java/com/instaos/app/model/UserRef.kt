package com.instaos.app.model

import kotlinx.serialization.Serializable

/**
 * A real person (or account) on Instagram, as returned by the web session.
 * This is the only person type left: the simulation-era [FakePerson] is
 * deleted in phase 3, and [Comment.person] is a [UserRef] now.
 */
@Serializable
data class UserRef(
    val id: String,
    val username: String,
    val displayName: String = "",
    val avatarUrl: String = "",
    val verified: Boolean = false,
    val followed: Boolean = false,
    val followsYou: Boolean = false,
)

/** The signed-in user, as a [UserRef] (id "self" until the server id is known). */
fun Profile.toUserRef(): UserRef = UserRef(
    id = "self",
    username = username,
    displayName = displayName,
    avatarUrl = avatar,
    verified = verified,
)
