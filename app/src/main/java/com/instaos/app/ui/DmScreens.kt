package com.instaos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.instaos.app.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val OwnBubbleGradient = Brush.horizontalGradient(
    listOf(Color(0xFF8A5CF6), Color(0xFF0095F6))
)

/**
 * DM inbox (phase 3, audit §8 step 5).
 *
 * Threads come from the `direct_v2` inbox endpoint, polled while this
 * screen is visible (no push, no realtime). A row is an avatar, a name,
 * a one-line preview, a timestamp and a blue unread dot. End-to-end
 * encrypted threads are listed honestly as "End-to-end encrypted" — their
 * content is never faked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmInboxScreen(
    state: AppState,
    loading: Boolean,
    error: String?,
    loggedIn: Boolean,
    onAction: (Action) -> Unit,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(loading) { if (!loading) refreshing = false }
    AppScaffold(state, onAction) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Messages",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (state.dm.unreadTotal > 0) Text(
                    "${state.dm.unreadTotal} unread",
                    style = MaterialTheme.typography.labelMedium,
                    color = ActionBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!loggedIn) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Sign in to see your messages", fontWeight = FontWeight.Bold)
                    Text(
                        "Your DMs load from Instagram once you sign in with the test account.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onOpenLogin) { Text("Sign in") }
                }
                return@Column
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; onRefresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.dm.threads.isEmpty() && !loading) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "No messages yet",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            error ?: "Pull down to try loading your inbox again.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Try again")
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("dmInbox"),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (error != null) item {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp, 8.dp)
                            )
                        }
                        items(state.dm.threads, key = { it.threadId }) { thread ->
                            DmThreadRow(thread) { onAction(Action.NavigateRoute(Route.DmThread(thread.threadId))) }
                        }
                        if (loading) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DmThreadRow(thread: DmThread, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thread.isGroup) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Group, "Group chat", Modifier.size(26.dp))
            }
        } else {
            val person = thread.participants.firstOrNull()
            if (person != null) AvatarFromUserRef(person, Modifier.size(52.dp))
            else Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Group, "Chat", Modifier.size(26.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thread.title.ifBlank { "Chat" },
                    fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (thread.e2ee) Icon(
                    Icons.Default.Lock,
                    "End-to-end encrypted",
                    Modifier.size(13.dp).padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (thread.e2ee) "End-to-end encrypted"
                else thread.lastMessage.ifBlank { "No messages yet" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (thread.lastMessageAt > 0) Text(
                formatTime(thread.lastMessageAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (thread.unreadCount > 0) Box(
                Modifier.size(9.dp).clip(CircleShape).background(ActionBlue)
                    .padding(top = 4.dp)
            )
        }
    }
}

private sealed interface ThreadDisplayItem {
    data class MessageItem(val message: DmMessage) : ThreadDisplayItem
    data class DateItem(val label: String) : ThreadDisplayItem
}

private fun dayLabel(timeMs: Long): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    val today = now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
    if (today) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
    if (yesterday) return "Yesterday"
    return SimpleDateFormat("MMMM d, yyyy", Locale.US).format(cal.time)
}

private fun timeLabel(timeMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(timeMs))

/**
 * DM thread (phase 3).
 *
 * Own messages are RIGHT-aligned in a purple→blue gradient bubble,
 * received messages LEFT-aligned in grey — the opposite of the
 * XPhotogram reference, which had the alignment backwards. Date
 * separators sit between days; the newest own message shows "Seen" when
 * the other side's last-seen item matches it. Sending is optimistic
 * (SENDING → SENT / FAILED with tap-to-retry). No reactions in v1.
 * E2E threads show an honest note and no input bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmThreadScreen(
    state: AppState,
    threadId: String,
    loading: Boolean,
    error: String?,
    onAction: (Action) -> Unit,
    onLoad: () -> Unit,
    onSend: (String) -> Unit,
    onRetry: (DmMessage) -> Unit,
) {
    val thread = state.dm.threads.find { it.threadId == threadId }
    val messages = state.dm.messages[threadId].orEmpty()
    var input by rememberSaveable(threadId) { mutableStateOf("") }
    LaunchedEffect(threadId) { onLoad() }

    // Chronological display list with date separators, newest last;
    // the LazyColumn is reversed so the newest sits at the bottom.
    val display = remember(messages) {
        val chronological = messages.sortedBy { it.createdAt }
        val out = mutableListOf<ThreadDisplayItem>()
        var lastDay = ""
        for (message in chronological) {
            val day = dayLabel(message.createdAt)
            if (day != lastDay) {
                out += ThreadDisplayItem.DateItem(day)
                lastDay = day
            }
            out += ThreadDisplayItem.MessageItem(message)
        }
        out.reversed()
    }
    val newestOwn = messages.filter { it.own }.maxByOrNull { it.createdAt }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onAction(Action.NavigateRoute(Route.DmInbox)) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to messages")
            }
            if (thread?.isGroup == true) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Group, "Group chat", Modifier.size(20.dp)) }
            } else {
                val person = thread?.participants?.firstOrNull()
                if (person != null) AvatarFromUserRef(person, Modifier.size(36.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    thread?.title?.ifBlank { "Chat" } ?: "Chat",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (thread?.isGroup == true) Text(
                    "${thread.participants.size} people",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (thread?.e2ee == true) Icon(
                Icons.Default.Lock,
                "End-to-end encrypted",
                Modifier.size(18.dp).padding(end = 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(thickness = .5.dp)
        if (thread?.e2ee == true) {
            Text(
                "This chat is end-to-end encrypted. InstaOS can't read or send these messages in this version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp, 8.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !loading) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No messages yet", fontWeight = FontWeight.Bold)
                    Text(
                        "Say hello — your message goes to ${thread?.title?.ifBlank { "this chat" } ?: "this chat"}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("dmThread"),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(display, key = {
                        when (it) {
                            is ThreadDisplayItem.MessageItem -> "m-${it.message.id}"
                            is ThreadDisplayItem.DateItem -> "d-${it.label}"
                        }
                    }) { item ->
                        when (item) {
                            is ThreadDisplayItem.DateItem -> {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            is ThreadDisplayItem.MessageItem -> {
                                val message = item.message
                                DmBubble(
                                    message = message,
                                    showSeen = message.id == newestOwn?.id &&
                                        thread?.seenItemId?.isNotBlank() == true &&
                                        thread.seenItemId == message.id,
                                    onRetry = { onRetry(message) },
                                )
                            }
                        }
                    }
                }
            }
            if (loading && messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (thread?.e2ee != true) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    modifier = Modifier.weight(1f).testTag("dmInput"),
                    placeholder = { Text("Message…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) {
                            onSend(input); input = ""
                        }
                    }),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSend(input); input = ""
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(
                            if (input.isNotBlank()) LocalAccent.current else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "Send message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DmBubble(message: DmMessage, showSeen: Boolean, onRetry: () -> Unit) {
    val isOwn = message.own
    val failed = message.status == DmMessageStatus.FAILED
    Column(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (message.status == DmMessageStatus.SENDING) 0.6f else 1f),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isOwn) {
                AvatarFromUserRef(message.sender, Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier.widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (isOwn) 18.dp else 4.dp,
                            bottomEnd = if (isOwn) 4.dp else 18.dp,
                        )
                    )
                    .then(
                        if (isOwn) Modifier.background(OwnBubbleGradient)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    message.text,
                    color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 2.dp, end = 4.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                failed -> Text(
                    "Couldn't send — tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
                message.status == DmMessageStatus.SENDING -> Text(
                    "Sending…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    if (showSeen) Text(
                        "Seen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "  ${timeLabel(message.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
