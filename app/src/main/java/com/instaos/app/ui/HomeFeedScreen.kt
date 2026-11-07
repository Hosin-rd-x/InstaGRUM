package com.instaos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.instaos.app.model.*

/**
 * The Home feed (phase 2, audit §8 step 3) — new screen.
 *
 * Header per iOS Instagram 454.1 §1.4: Create "+" top-left, the Instagram
 * wordmark centred, Activity (heart) top-right. Below it the stories tray
 * (gradient rings for unviewed stories), then the multi-author feed.
 *
 * Posts come from [FeedUiState] (web session + disk cache), never from the
 * snapshot. An empty feed is an honest empty state, not fake posts.
 * The floating capsule tab bar is phase 3; the bottom bar here is the
 * existing strip with the new tab order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    state: AppState,
    feed: FeedUiState,
    loggedIn: Boolean,
    onAction: (Action) -> Unit,
    onRefreshFeed: () -> Unit,
    onLoadMoreFeed: () -> Unit,
    onFeedLike: (Post) -> Unit,
    onFeedSave: (Post) -> Unit,
    onFeedComment: (Post, String, String?) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenTrayStory: (StoryTrayItem) -> Unit = {},
    storyError: String? = null,
    onClearStoryError: () -> Unit = {},
) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(feed.loading) { if (!feed.loading) refreshing = false }
    val listState = rememberLazyListState()
    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
    LaunchedEffect(lastVisible, feed.posts.size, feed.hasMore, feed.loading) {
        if (lastVisible != null && feed.hasMore && !feed.loading &&
            lastVisible >= feed.posts.size - 2 && feed.posts.isNotEmpty()
        ) {
            onLoadMoreFeed()
        }
    }
    AppScaffold(state, onAction) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) {
                    Icon(
                        Icons.Default.Add,
                        "Create content",
                        Modifier.size(26.dp).clickable { onAction(Action.NavigateRoute(Route.Create)) },
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Instagram", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        "Activity",
                        Modifier.size(25.dp).clickable { onAction(Action.NavigateRoute(Route.Notifications)) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; onRefreshFeed() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("homeFeed"),
                    contentPadding = PaddingValues(bottom = 36.dp),
                ) {
                    item {
                        StoriesTray(state, feed, onAction, onOpenTrayStory)
                        if (storyError != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    storyError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onClearStoryError) { Text("Dismiss") }
                            }
                        }
                    }
                    if (feed.posts.isEmpty() && !feed.loading) {
                        item {
                            FeedEmptyState(loggedIn, feed.error, onOpenLogin, onRefreshFeed)
                        }
                    }
                    itemsIndexed(feed.posts, key = { _, post -> post.id }) { index, post ->
                        val active = index == listState.firstVisibleItemIndex
                        PostItem(
                            state = state,
                            post = post,
                            active = active,
                            onAction = onAction,
                            isFeedPost = true,
                            onFeedLike = onFeedLike,
                            onFeedSave = onFeedSave,
                            onFeedComment = { text, parentId -> onFeedComment(post, text, parentId) },
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.8.dp)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (feed.loading && feed.posts.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                    if (feed.error != null && feed.posts.isNotEmpty()) {
                        item {
                            Text(
                                feed.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                if (feed.loading && feed.posts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun StoriesTray(state: AppState, feed: FeedUiState, onAction: (Action) -> Unit, onOpenTrayStory: (StoryTrayItem) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                Modifier.width(64.dp).clickable { onAction(Action.NavigateRoute(Route.Create)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(62.dp)) {
                    AvatarFromProfile(state.profile, Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape)
                            .background(Color.White).padding(1.5.dp).clip(CircleShape)
                            .background(Color(0xFF0095F6)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, "Add to your story", Modifier.size(12.dp), tint = Color.White)
                    }
                }
                Text(
                    "Your story",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        items(feed.tray.size) { index ->
            val item = feed.tray[index]
            Column(
                Modifier.width(64.dp).clickable { onOpenTrayStory(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(62.dp).storyRing(!item.seen, 2.5.dp).padding(3.dp),
                ) {
                    AvatarFromUserRef(item.user, Modifier.fillMaxSize())
                }
                Text(
                    item.user.username,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedEmptyState(
    loggedIn: Boolean,
    error: String?,
    onOpenLogin: () -> Unit,
    onRefreshFeed: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!loggedIn) {
            Text(
                "Sign in to see your feed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Your home feed loads from Instagram once you sign in with the test account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenLogin) { Text("Sign in") }
        } else {
            Text(
                "Nothing in your feed yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                error ?: "Pull down to try loading your feed again.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRefreshFeed) { Text("Try again") }
        }
    }
}
