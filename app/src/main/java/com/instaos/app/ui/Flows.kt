package com.instaos.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instaos.app.model.*

/**
 * Reels (phase 3): the real reels feed — videos from the home feed
 * (mixed authors, from the web session) plus the user's own local videos.
 * Full-screen vertical pager, right action rail (like / comments / save /
 * share), double-tap to like, sound toggle. No repost button: there is no
 * web endpoint for it in v1, and a dead button is worse than a missing one.
 */
@Composable
fun ReelsScreen(
    state: AppState,
    feed: FeedUiState,
    onAction: (Action) -> Unit,
    onRefreshFeed: () -> Unit = {},
    onFeedLike: (Post) -> Unit = {},
    onFeedSave: (Post) -> Unit = {},
) {
    val feedReels = feed.posts.filter { it.media.kind != MediaKind.IMAGE }
    val ownReels = state.posts.filter { it.media.kind != MediaKind.IMAGE }
    val reels = (feedReels + ownReels.filterNot { own -> feedReels.any { it.id == own.id } })
    val pager = rememberPagerState { reels.size }
    var muted by rememberSaveable { mutableStateOf(true) }
    AppScaffold(state, onAction) { padding ->
        if (reels.isEmpty()) Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyGrid(
                if (feed.loading) "Loading reels…" else "No reels in your feed yet"
            )
            OutlinedButton(onClick = onRefreshFeed) { Text("Refresh") }
        } else VerticalPager(pager, Modifier.fillMaxSize().padding(padding), key = { reels[it].id }) { page ->
            val post = reels[page]
            val isFeedPost = feedReels.any { it.id == post.id }
            val haptic = LocalHapticFeedback.current
            val context = LocalContext.current
            var heart by remember(post.id) { mutableIntStateOf(0) }
            var showHeart by remember(post.id) { mutableStateOf(false) }
            LaunchedEffect(heart) {
                if (heart > 0) {
                    showHeart = true
                    kotlinx.coroutines.delay(650)
                    showHeart = false
                }
            }
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .pointerInput(post.id) {
                        detectTapGestures(onDoubleTap = {
                            if (!post.liked) {
                                if (isFeedPost) onFeedLike(post) else onAction(Action.LikePost(post.id, onlyLike = true))
                            }
                            heart++
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        })
                    }
            ) {
                MediaContent(
                    post.media,
                    Modifier.fillMaxSize(),
                    playVideo = page == pager.settledPage,
                    controls = false,
                    muted = muted,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = .8f)
                            )
                        )
                    )
                )
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Reels",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { muted = !muted }) {
                        Icon(
                            if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            if (muted) "Unmute reel" else "Mute reel",
                            tint = Color.White,
                        )
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 70.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val reelAuthor = post.author
                        if (reelAuthor != null) AvatarFromUserRef(
                            reelAuthor,
                            Modifier.size(32.dp)
                        ) else AvatarFromProfile(
                            state.profile,
                            Modifier.size(32.dp)
                        )
                        Text(
                            "  ${reelAuthor?.username ?: state.profile.username}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(post.caption, color = Color.White, maxLines = 3)
                    Text(
                        "${formatCount(post.views)} views",
                        color = Color.White.copy(alpha = .7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp, 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = {
                        if (isFeedPost) onFeedLike(post) else onAction(Action.LikePost(post.id))
                    }) {
                        Icon(
                            if (post.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Like reel",
                            tint = if (post.liked) HeartColor else Color.White
                        )
                    }
                    Text(formatCount(post.likes), color = Color.White)
                    IconButton(onClick = { onAction(Action.OpenPost(post.id)) }) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            "Open reel comments",
                            tint = Color.White
                        )
                    }
                    Text(formatCount(post.commentCount), color = Color.White)
                    IconButton(onClick = {
                        if (isFeedPost) onFeedSave(post) else onAction(Action.SavePost(post.id))
                    }) {
                        Icon(
                            if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            "Save reel",
                            tint = Color.White
                        )
                    }
                    if (post.shortcode.isNotBlank()) IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://www.instagram.com/p/${post.shortcode}/")
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Share reel"))
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Share reel",
                            tint = Color.White
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    showHeart,
                    modifier = Modifier.align(Alignment.Center),
                    enter = androidx.compose.animation.scaleIn(
                        androidx.compose.animation.core.spring(dampingRatio = .55f),
                        initialScale = .4f
                    ) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut(
                        androidx.compose.animation.core.tween(160),
                        targetScale = 1.2f
                    ) + androidx.compose.animation.fadeOut()
                ) {
                    Icon(Icons.Default.Favorite, "Liked", tint = Color.White, modifier = Modifier.size(96.dp))
                }
            }
        }
    }
}
