package com.instaos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.instaos.app.model.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun InstaApp(
    state: AppState,
    onAction: (Action) -> Unit,
    backup: BackupActions? = null,
    feed: FeedUiState = FeedUiState(),
    loggedIn: Boolean = false,
    onRefreshFeed: () -> Unit = {},
    onLoadMoreFeed: () -> Unit = {},
    onFeedLike: (Post) -> Unit = {},
    onFeedSave: (Post) -> Unit = {},
    onFeedComment: (Post, String, String?) -> Unit = { _, _, _ -> },
    onOpenLogin: () -> Unit = {},
    dmLoading: Boolean = false,
    dmError: String? = null,
    search: SearchUiState = SearchUiState(),
    storyError: String? = null,
    onRefreshDmInbox: () -> Unit = {},
    onLoadDmThread: (String) -> Unit = {},
    onSendDm: (String, String) -> Unit = { _, _ -> },
    onRetryDm: (String, DmMessage) -> Unit = { _, _ -> },
    onDmVisible: (String?) -> Unit = {},
    onDmHidden: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onOpenTrayStory: (StoryTrayItem) -> Unit = {},
    onClearStoryError: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    // No onboarding gate: the login gate lives in MainActivity (WebView
    // sign-in); the local profile is created from the web session.
    // Typed routing (phase 2): the router switches on Route, never on raw
    // strings, and there is no silent `else -> ProfileScreen` fallback —
    // unknown legacy routes resolve explicitly to Home inside Route.
    val route = Route.fromUiSession(state.session)
    // Poll DMs only while an inbox/thread screen is visible.
    LaunchedEffect(route) {
        when (route) {
            is Route.DmThread -> onDmVisible(route.threadId)
            Route.DmInbox -> onDmVisible(null)
            else -> onDmHidden()
        }
    }
    BackHandler(route != Route.Home && route != Route.Profile) {
        when (route) {
            is Route.StoryViewer -> onAction(Action.OpenStory(null))
            is Route.PostDetail -> onAction(Action.OpenPost(null))
            is Route.DmThread -> onAction(Action.NavigateRoute(Route.DmInbox))
            else -> onAction(Action.NavigateRoute(Route.Home))
        }
    }
    AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "navigation") { destination ->
        when (destination) {
            is Route.StoryViewer -> StoryViewer(state, destination.storyId, onAction)
            is Route.PostDetail -> PostDetail(state, destination.postId, onAction, feedPosts = feed.posts)
            Route.Settings -> SettingsScreen(state, onAction, backup, onLogout)
            Route.Create -> CreateScreen(state, onAction)
            Route.Archive -> StoryArchive(state, onAction)
            Route.Reels -> ReelsScreen(state, feed, onAction, onRefreshFeed, onFeedLike, onFeedSave)
            Route.Notifications -> ActivityScreen(state, onAction)
            Route.Home -> HomeFeedScreen(
                state, feed, loggedIn, onAction,
                onRefreshFeed, onLoadMoreFeed, onFeedLike, onFeedSave, onFeedComment, onOpenLogin,
                onOpenTrayStory, storyError, onClearStoryError,
            )
            Route.DmInbox -> DmInboxScreen(
                state, dmLoading, dmError, loggedIn, onAction, onRefreshDmInbox, onOpenLogin
            )
            is Route.DmThread -> DmThreadScreen(
                state, destination.threadId, dmLoading, dmError, onAction,
                onLoad = { onLoadDmThread(destination.threadId) },
                onSend = { text -> onSendDm(destination.threadId, text) },
                onRetry = { message -> onRetryDm(destination.threadId, message) },
            )
            Route.Search -> SearchScreen(
                state, search, feed, onAction, onSearch, onRefreshFeed
            )
            Route.Profile -> ProfileScreen(state, onAction)
        }
    }
}

@Composable
private fun AccountSwitcher(state: AppState, onAction: (Action) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Accounts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.accounts.forEach { account ->
                    Surface(
                        onClick = {
                            if (account.id != state.activeAccountId) onAction(Action.SwitchAccount(account.id))
                            dismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (account.id == state.activeAccountId) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarFromProfile(
                                Profile(
                                    username = account.username,
                                    displayName = account.displayName,
                                    avatar = account.avatar,
                                ),
                                Modifier.size(40.dp)
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(account.username.ifBlank { "New account" }, fontWeight = FontWeight.SemiBold)
                                Text(account.displayName, style = MaterialTheme.typography.bodySmall)
                            }
                            if (account.id == state.activeAccountId) Icon(Icons.Default.CheckCircle, "Current account")
                        }
                    }
                }
                TextButton(onClick = { onAction(Action.CreateAccount); dismiss() }) {
                    Icon(Icons.Default.AddCircleOutline, null)
                    Text("  Add account")
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } }
    )
}

@Composable
private fun ProfileStat(value: Long, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        AnimatedNumber(value, size = 16.sp, uppercase = true)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AppScaffold(state: AppState, onAction: (Action) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNav(state, onAction) },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(state: AppState, onAction: (Action) -> Unit) {
    var editOpen by rememberSaveable { mutableStateOf(false) }
    var accountsOpen by rememberSaveable { mutableStateOf(false) }
    val profile = state.profile
    val activeStories = state.stories.filter { it.expiresAt > System.currentTimeMillis() }
    val unseenStories = activeStories.any { !it.seen }
    val highlights = state.stories.filter { it.highlight.isNotBlank() }.distinctBy { it.highlight }
    val grid = rememberLazyGridState(state.session.gridIndex, state.session.gridOffset)
    val currentTab by rememberUpdatedState(state.session.gridTab)
    LaunchedEffect(grid) {
        snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }.distinctUntilChanged()
            .collect { (index, offset) ->
                onAction(Action.GridPosition(index, offset, currentTab))
            }
    }
    val posts = when (state.session.gridTab) {
        1 -> state.posts.filter { it.media.kind != MediaKind.IMAGE }
        2 -> state.posts.filter { it.saved }
        else -> state.posts
    }
    AppScaffold(state, onAction) { padding ->
        // No pull-to-refresh here: the profile is local data, and a
        // refresh gesture that reloaded nothing would be a dead control.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("profileGrid"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().height(44.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) {
                                Icon(
                                    Icons.Default.Add,
                                    "Create content",
                                    Modifier.size(26.dp).clickable { onAction(Action.Navigate("create")) })
                            }
                            Row(
                                Modifier.weight(1f).clickable { accountsOpen = true },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    profile.username,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    "Switch account",
                                    Modifier.size(19.dp).padding(start = 3.dp)
                                )
                            }
                            Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                                Icon(
                                    Icons.Default.Menu,
                                    "Settings",
                                    Modifier.size(25.dp).clickable { onAction(Action.Navigate("settings")) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(80.dp)) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .storyRing(unseenStories, 2.5.dp)
                                        .padding(3.dp)
                                        .clickable {
                                            activeStories.firstOrNull { !it.seen }
                                                ?.let { onAction(Action.OpenStory(it.id)) }
                                                ?: activeStories.firstOrNull()
                                                    ?.let { onAction(Action.OpenStory(it.id)) }
                                                ?: run { editOpen = true }
                                        }
                                ) { AvatarFromProfile(profile, Modifier.fillMaxSize()) }
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(1.5.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { onAction(Action.Navigate("create")) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Add, "Add", Modifier.size(13.dp), tint = Color.Black) }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 24.dp, end = 6.dp)
                            ) {
                                Text(
                                    profile.displayName.ifBlank { profile.username },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    ProfileStat(state.posts.size.toLong(), "posts")
                                    ProfileStat(profile.followers, "followers")
                                    ProfileStat(profile.following, "following")
                                }
                            }
                        }
                        if (profile.bio.isNotBlank()) Text(
                            profile.bio,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProfileButton("Edit profile", Modifier.weight(1f)) { editOpen = true }
                            ProfileButton("Share profile", Modifier.weight(1f)) { editOpen = true }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(Modifier.padding(horizontal = 18.dp)) {
                            SectionTitle(
                                "Highlights",
                                "Archive"
                            ) { onAction(Action.Navigate("archive")) }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(highlights, key = { it.id }) { story ->
                                Column(
                                    Modifier.width(66.dp)
                                        .clickable {
                                            onAction(
                                                Action.OpenStory(
                                                    story.id,
                                                    highlight = story.highlight
                                                )
                                            )
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(62.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            .padding(4.dp).clip(CircleShape)
                                    ) { MediaContent(story.media, Modifier.fillMaxSize()) }
                                    Text(
                                        story.highlight,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                            item {
                                Column(
                                    Modifier.width(66.dp).clickable { onAction(Action.Navigate("archive")) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(62.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Add, "Add highlight") }
                                    Text(
                                        "New",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            listOf(
                                Icons.Default.GridOn to "Posts",
                                Icons.Default.PlayCircleOutline to "Reels",
                                Icons.Default.BookmarkBorder to "Saved"
                            ).forEachIndexed { index, (icon, label) ->
                                val active = currentTab == index
                                Column(
                                    Modifier.weight(1f).clickable {
                                        onAction(
                                            Action.GridPosition(
                                                grid.firstVisibleItemIndex,
                                                grid.firstVisibleItemScrollOffset,
                                                index
                                            )
                                        )
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        icon,
                                        label,
                                        Modifier.padding(vertical = 10.dp).size(24.dp),
                                        tint = if (active) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Box(
                                        Modifier.fillMaxWidth().height(1.5.dp).background(
                                            if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyGrid("No ${if (currentTab == 1) "videos" else if (currentTab == 2) "saved posts" else "posts"} yet. Create your next moment.") }
                items(posts, key = { it.id }) { post ->
                    Box(
                        Modifier.aspectRatio(.75f).testTag("grid-${post.id}")
                            .clickable { onAction(Action.OpenPost(post.id)) }) {
                        MediaContent(post.media, Modifier.fillMaxSize(), description = post.caption)
                        if (post.media.kind != MediaKind.IMAGE) Icon(
                            Icons.Default.PlayArrow,
                            "Video",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        )
                    }
                }
            }
    }
    if (editOpen) EditProfileDialog(profile, onAction) { editOpen = false }
    if (accountsOpen) AccountSwitcher(state, onAction) { accountsOpen = false }
}
