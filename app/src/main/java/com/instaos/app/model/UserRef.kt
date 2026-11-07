package com.instaos.app.model

import kotlinx.serialization.Serializable

/**
 * A real person (or account) on Instagram, as returned by the web session.
 *
 * This replaces [FakePerson] as the author type for real data. [FakePerson]
 * still exists for the simulation-era Comment/person plumbing that phase 3
 * deletes; new code should use [UserRef].
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

fun UserRef.toFakePerson(): FakePerson = FakePerson(
    id = id,
    username = username,
    displayName = displayName,
    avatar = avatarUrl,
    verified = verified,
    followed = followed,
    followsYou = followsYou,
)
