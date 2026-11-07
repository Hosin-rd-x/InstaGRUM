package com.instaos.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.instaos.app.model.*
import kotlinx.coroutines.delay

/**
 * Post detail (phase 3): a REAL post permalink — one post and its
 * comments — not the old LazyColumn of every own post that pretended to
 * be a feed. The author comes from `post.author` (feed) or the local
 * profile (own post). No fabricated counts anywhere: shares are the real
 * server count (0 until the API returns one), the "Liked by" row is just
 * the real like count (the simulation's `sampledLikers` faces are gone),
 * and the Send icon shares the real instagram.com link when the post has
 * a shortcode — it is hidden for local-only posts that have no link.
 */
@Composable
fun PostDetail(
    state: AppState,
    postId: String,
    onAction: (Action) -> Unit,
    feedPosts: List<Post> = emptyList(),
) {
    val post = state.posts.find { it.id == postId }
        ?: feedPosts.find { it.id == postId }
    if (post == null) {
        LaunchedEffect(postId) { onAction(Action.OpenPost(null)) }; return
    }
    Scaffold(topBar = {
        ScreenHeader(
            "Posts",
            { onAction(Action.OpenPost(null)) }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                PostItem(state, post, true, onAction)
            }
        }
    }
}

@Composable
fun PostItem(
    state: AppState,
    post: Post,
    active: Boolean,
    onAction: (Action) -> Unit,
    /** True for home-feed posts (multi-author, from the web session). */
    isFeedPost: Boolean = false,
    /** When set, like/save/comment act on the feed list instead of the local posts. */
    onFeedLike: ((Post) -> Unit)? = null,
    onFeedSave: ((Post) -> Unit)? = null,
    onFeedComment: ((String, String?) -> Unit)? = null,
) {
    var comments by rememberSaveable(post.id) { mutableStateOf(false) }
    var controls by rememberSaveable(post.id) { mutableStateOf(false) }
    var fullScreen by rememberSaveable(post.id) { mutableStateOf(false) }
    var insights by rememberSaveable(post.id) { mutableStateOf(false) }
    var heart by remember(post.id) { mutableIntStateOf(0) }
    var showHeart by remember(post.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    LaunchedEffect(heart) {
        if (heart > 0) {
            showHeart = true; delay(650); showHeart = false
        }
    }
    // The author is the post's own author when present; only the signed-in
    // user's own posts (author == null) fall back to the local profile.
    val author = post.author
    val authorName = author?.username ?: state.profile.username
    val like: () -> Unit = {
        onFeedLike?.invoke(post) ?: onAction(Action.LikePost(post.id))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val likeOnly: () -> Unit = {
        if (!post.liked) {
            onFeedLike?.invoke(post) ?: onAction(Action.LikePost(post.id, onlyLike = true))
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val shareLink: () -> Unit = {
        if (post.shortcode.isNotBlank()) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://www.instagram.com/p/${post.shortcode}/")
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Share post"))
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (author != null) AvatarFromUserRef(author, Modifier.size(36.dp))
            else AvatarFromProfile(state.profile, Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(authorName, fontWeight = FontWeight.SemiBold)
                    if (author?.verified == true) Icon(
                        Icons.Default.Verified,
                        "Verified account",
                        Modifier.size(14.dp).padding(start = 3.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (post.location.isNotBlank()) Text(post.location, style = MaterialTheme.typography.labelSmall)
            }
            if (!isFeedPost) IconButton(onClick = { controls = true }) {
                Icon(Icons.Default.MoreHoriz, "Post controls")
            }
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (post.media.kind == MediaKind.REEL) .7f else 1f)
                .then(if (active) Modifier.testTag("postMedia") else Modifier)
                .pointerInput(post.id) {
                    detectTapGestures(onDoubleTap = {
                        likeOnly(); heart++
                    })
                }, contentAlignment = Alignment.Center
        ) {
            MediaContent(
                post.media,
                Modifier.fillMaxSize(),
                playVideo = true,
                paused = !active || comments || controls || fullScreen
            )
            androidx.compose.animation.AnimatedVisibility(
                showHeart,
                enter = scaleIn(spring(dampingRatio = .55f), initialScale = .4f) + fadeIn(),
                exit = scaleOut(tween(160), targetScale = 1.2f) + fadeOut()
            ) {
                Icon(Icons.Default.Favorite, "Liked", tint = Color.White, modifier = Modifier.size(96.dp))
            }
            IconButton(onClick = { fullScreen = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    Icons.Default.Fullscreen,
                    "Open full-screen media",
                    tint = Color.White
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isFeedPost) Text(
                "View insights",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { insights = true }
            )
            if (!isFeedPost) HorizontalDivider(thickness = .5.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountAction(
                    if (post.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Like post",
                    post.likes,
                    if (post.liked) HeartColor else LocalContentColor.current,
                    like
                )
                Spacer(Modifier.width(18.dp))
                CountAction(
                    Icons.Default.ChatBubbleOutline,
                    "Open comments",
                    post.commentCount,
                    LocalContentColor.current
                ) { comments = true }
                Spacer(Modifier.width(18.dp))
                CountAction(
                    Icons.Default.Repeat,
                    "Shares",
                    post.shares,
                    LocalContentColor.current
                ) { if (!isFeedPost) insights = true }
                Spacer(Modifier.width(18.dp))
                if (post.shortcode.isNotBlank()) Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "Share post",
                    Modifier.size(25.dp).clickable(onClick = shareLink))
                Spacer(Modifier.weight(1f))
                Icon(
                    if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    "Save post",
                    Modifier.size(25.dp).clickable {
                        onFeedSave?.invoke(post) ?: onAction(Action.SavePost(post.id))
                    })
            }
            if (post.likes > 0) Text(
                "${formatCount(post.likes)} likes",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (post.caption.isNotBlank()) Text("$authorName  ${post.caption}")
            Text(
                formatDate(post.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (comments) CommentsSheet(state, post, onAction, onFeedComment) { comments = false }
    if (controls) PostControls(post, onAction) { controls = false }
    if (fullScreen) FullScreenMedia(post.media) { fullScreen = false }
    if (insights) PostInsightsSheet(post) { insights = false }
}

@Composable
private fun CountAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    count: Long,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, description, Modifier.size(25.dp), tint = tint)
        if (count > 0) Text(
            "  ${formatCount(count)}",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostInsightsSheet(post: Post, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Post insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("views", post.views)
                Stat("likes", post.likes)
                Stat("comments", post.commentCount)
            }
            HorizontalDivider()
            val rate = if (post.views > 0) post.likes.toDouble() / post.views * 100 else 0.0
            Text("Engagement rate: ${String.format(java.util.Locale.US, "%.1f", rate)}%")
            Text(
                "Counts from this device only — this post was created here, not on Instagram.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    state: AppState,
    post: Post,
    onAction: (Action) -> Unit,
    onFeedComment: ((String, String?) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var replyId by rememberSaveable { mutableStateOf<String?>(null) }
    val roots =
        post.comments.filter { it.parentId == null || post.comments.none { parent -> parent.id == it.parentId } }
    val ordered = remember(post.comments) {
        buildList<Pair<Comment, Int>> {
            val visited = mutableSetOf<String>()
            fun append(comment: Comment, depth: Int) {
                if (!visited.add(comment.id)) return
                add(comment to depth)
                post.comments.filter { it.parentId == comment.id }.forEach { append(it, depth + 1) }
            }
            roots.forEach { append(it, 0) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).imePadding()) {
            Text(
                "Comments",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            // Honest count: the feed only carries the comments Instagram
            // sent with the post, so say how many are loaded when the
            // server total is bigger. No "local sample" fiction.
            Text(
                if (ordered.size < post.commentCount) {
                    "${formatCount(post.commentCount)} total · ${ordered.size} loaded"
                } else {
                    "${formatCount(post.commentCount)} total"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
            LazyColumn(Modifier.weight(1f).testTag("commentsList"), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(ordered, key = { it.first.id }) { (comment, depth) ->
                    Row(
                        Modifier.animateItem().padding(
                            start = (16 + depth.coerceAtMost(2) * 20).dp,
                            end = 8.dp,
                            top = 10.dp,
                            bottom = 10.dp
                        )
                    ) {
                        AvatarFromUserRef(
                            comment.person,
                            Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    comment.person.username,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (comment.person.verified) Icon(
                                    Icons.Default.Verified,
                                    "Verified account",
                                    Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    " · ${formatTime(comment.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { replyId = comment.id },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) { Text("Reply", style = MaterialTheme.typography.labelSmall) }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { onAction(Action.LikeComment(post.id, comment.id)) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (comment.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    "Like comment",
                                    Modifier.size(17.dp),
                                    tint = if (comment.liked) HeartColor else LocalContentColor.current
                                )
                            }
                            Text(formatCount(comment.likes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (ordered.isEmpty()) item { EmptyGrid("Start the conversation") }
            }
            if (replyId != null) Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Replying to ${post.comments.find { it.id == replyId }?.person?.username.orEmpty()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = { replyId = null }) {
                    Icon(
                        Icons.Default.Close,
                        "Cancel reply",
                        Modifier.size(16.dp)
                    )
                }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarFromProfile(state.profile, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    input,
                    { input = it.take(2000) },
                    Modifier.weight(1f).testTag("commentInput"),
                    placeholder = { Text("Add a comment…") },
                    maxLines = 3
                )
                IconButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        onFeedComment?.invoke(input, replyId)
                            ?: onAction(Action.AddComment(post.id, input, replyId))
                        input = ""; replyId = null
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Post comment") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostControls(post: Post, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
    var delete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Post options",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text(if (post.saved) "Remove from saved" else "Save post") },
                leadingContent = {
                    Icon(
                        if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        null
                    )
                },
                modifier = Modifier.clickable { onAction(Action.SavePost(post.id)); onDismiss() }
            )
            ListItem(
                headlineContent = { Text("Delete post", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { delete = true }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    if (delete) ConfirmAction(
        "Delete post?",
        "It will be removed from your profile along with its comments.",
        { onAction(Action.DeletePost(post.id)); onDismiss() },
        { delete = false })
}

@Composable
fun FullScreenMedia(media: Media, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Box(
                Modifier.fillMaxSize().pointerInput(media) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f); x =
                        (x + pan.x).coerceIn(-size.width.toFloat(), size.width.toFloat()); y =
                        (y + pan.y).coerceIn(-size.height.toFloat(), size.height.toFloat())
                    }
                }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = if (scale > 1f) x else 0f; translationY =
                        if (scale > 1f) y else 0f
                    }) {
                MediaContent(media, Modifier.fillMaxSize(), playVideo = true)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    Icons.Default.Close,
                    "Close media",
                    tint = Color.White
                )
            }
        }
    }
}
