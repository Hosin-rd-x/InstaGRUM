package com.instaos.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.instaos.app.model.*

/**
 * Activity (phase 3, audit §8 step 4 — replaces the old placeholder).
 *
 * The old NotificationsScreen invented follower/like events ("growth
 * events" from the simulation engine). This screen shows only what is
 * real on this device: unread DMs, and comments other people left on
 * your posts (local + the ones the web session loaded). Totals are
 * labelled as "on this device" — Instagram's full activity feed is not
 * available to a web-session client.
 */
@Composable
fun ActivityScreen(state: AppState, onAction: (Action) -> Unit) {
    val myUsername = state.profile.username
    val othersComments = state.posts.flatMap { post ->
        post.comments.filter { !it.own && it.person.username != myUsername }.map { post to it }
    }.sortedByDescending { it.second.createdAt }.take(30)
    val totalLikes = state.posts.sumOf { it.likes }
    AppScaffold(state, onAction) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("activityList"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (state.dm.unreadTotal > 0) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onAction(Action.NavigateRoute(Route.DmInbox))
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "You have ${state.dm.unreadTotal} unread message${if (state.dm.unreadTotal == 1) "" else "s"}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Open your inbox to read them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("Open", color = ActionBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (state.posts.isNotEmpty()) {
                item {
                    Text(
                        "Your posts have ${formatCount(totalLikes)} likes in total (on this device).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (othersComments.isNotEmpty()) {
                item {
                    Text("Comments on your posts", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                items(othersComments, key = { it.second.id }) { (post, comment) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onAction(Action.NavigateRoute(Route.PostDetail(post.id)))
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarFromUserRef(comment.person, Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                comment.person.username,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                comment.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                            Text(
                                formatTime(comment.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nothing here yet",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Likes and comments on your posts will show up here. " +
                                "Instagram's full activity feed isn't available in this version.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
