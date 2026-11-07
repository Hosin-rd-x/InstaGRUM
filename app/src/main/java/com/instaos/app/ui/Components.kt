package com.instaos.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.instaos.app.model.*
import java.text.NumberFormat
import java.util.Locale

val StoryGradient = Brush.linearGradient(
    listOf(
        Color(0xFFFEDA75),
        Color(0xFFFA7E1E),
        Color(0xFFD62976),
        Color(0xFF962FBF),
        Color(0xFF4F5BD5)
    )
)
private val RingStops = listOf(
    Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976),
    Color(0xFF962FBF), Color(0xFF4F5BD5), Color(0xFFFEDA75)
)
val HeartColor = Color(0xFFFF3040)

fun Modifier.storyRing(unseen: Boolean, stroke: Dp = 2.5.dp): Modifier = drawBehind {
    val width = stroke.toPx()
    val radius = size.minDimension / 2 - width / 2
    if (unseen) drawCircle(Brush.sweepGradient(RingStops), radius, style = Stroke(width))
    else drawCircle(IgSeenRing, radius, style = Stroke(width))
}

@Composable
fun AvatarFromProfile(profile: Profile, modifier: Modifier = Modifier.size(86.dp)) {
    Box(modifier.clip(CircleShape)) {
        if (profile.avatar.isNotBlank()) MediaContent(Media(path = profile.avatar), Modifier.fillMaxSize())
        else Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                "Add a profile photo",
                Modifier.fillMaxSize(.7f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AvatarFromUserRef(user: UserRef, modifier: Modifier = Modifier.size(36.dp)) {
    Box(modifier.clip(CircleShape)) {
        if (user.avatarUrl.isNotBlank()) MediaContent(Media(path = user.avatarUrl), Modifier.fillMaxSize())
        else Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                "Profile photo",
                Modifier.fillMaxSize(.7f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnimatedNumber(value: Long, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 16.sp, uppercase: Boolean = false) {
    AnimatedContent(
        formatCount(value, uppercase),
        transitionSpec = {
            (slideInVertically(Motion.enter()) { it / 2 } + fadeIn(Motion.quick()))
                .togetherWith(slideOutVertically(Motion.quick()) { -it / 2 } + fadeOut(Motion.quick()))
        },
        label = "stat",
        modifier = modifier
    ) {
        Text(it, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Stat(label: String, value: Long, modifier: Modifier = Modifier, uppercase: Boolean = true) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(value, uppercase = uppercase)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun SmallPill(text: String, icon: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/**
 * Bottom navigation, iOS Instagram 454.1 order: Home → Reels → DMs →
 * Search → Profile. Create is out of the bar (the "+" at the top-left of
 * the Home/Profile headers).
 *
 * Phase 3 restyle (§1.11): a FLOATING translucent capsule instead of the
 * pinned strip — a wide, strongly rounded pill hovering above the bottom
 * edge, content visible through it, the selected tab behind a smaller
 * translucent highlight. No divider, no labels, no heavy shadow. The
 * Profile tab shows the signed-in user's avatar, like the real app.
 */
@Composable
fun BottomNav(state: AppState, onAction: (Action) -> Unit) {
    val route = Route.fromUiSession(state.session)
    val selected = route.bottomTab()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val capsule = if (isDark) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.85f)
    val highlight = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = capsule,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().height(60.dp)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(50)),
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = listOf(
                    Triple(Route.Home, "Home", Icons.Outlined.Home to Icons.Filled.Home),
                    Triple(Route.Reels, "Reels", Icons.Outlined.Movie to Icons.Filled.Movie),
                    Triple(Route.DmInbox, "Direct messages", Icons.Outlined.Send to Icons.Filled.Send),
                    Triple(Route.Search, "Search", Icons.Outlined.Search to Icons.Filled.Search),
                )
                tabs.forEach { (tab, label, icons) ->
                    val active = selected == tab
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(50))
                                .background(if (active) highlight else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = { onAction(Action.NavigateRoute(tab)) }) {
                                Icon(
                                    if (active) icons.second else icons.first,
                                    label,
                                    Modifier.size(25.dp),
                                    tint = if (active) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Unread DM badge (from real DM state; 0 until the inbox loads).
                        if (tab == Route.DmInbox && state.dm.unreadTotal > 0) {
                            Box(
                                Modifier.align(Alignment.Center).offset(x = 15.dp, y = (-11).dp)
                                    .background(HeartColor, RoundedCornerShape(50))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    formatCount(state.dm.unreadTotal.toLong()),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                // Profile tab: the user's avatar (real Instagram shows it
                // instead of a person glyph); a ring marks it selected.
                val profileActive = selected == Route.Profile
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(50))
                            .background(if (profileActive) highlight else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(30.dp)
                                .then(
                                    if (profileActive) Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape,
                                    ) else Modifier
                                )
                                .padding(if (profileActive) 1.5.dp else 0.dp)
                                .semantics { contentDescription = "Profile"; role = Role.Button }
                                .clickable { onAction(Action.NavigateRoute(Route.Profile)) },
                        ) {
                            AvatarFromProfile(state.profile, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 10.dp else 0.dp)
        )
        actions()
    }
}

@Composable
fun StatInput(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false
) {
    OutlinedTextField(
        value,
        { raw -> onValue(raw.filter { it.isDigit() || (decimal && it == '.') }.take(16)) },
        modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    )
}

@Composable
fun EmptyGrid(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmAction(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun formatCount(number: Long, uppercase: Boolean = false): String {
    val (divisor, suffix) = when {
        number >= 1_000_000_000 -> 1_000_000_000.0 to "B"
        number >= 1_000_000 -> 1_000_000.0 to "M"
        number >= 1_000 -> 1_000.0 to if (uppercase) "K" else "k"
        else -> return NumberFormat.getIntegerInstance(Locale.US).format(number)
    }
    return String.format(Locale.US, "%.1f", number / divisor).removeSuffix(".0") + suffix
}

fun formatDuration(seconds: Double): String =
    (seconds.toInt().coerceAtLeast(0)).let { "%02d:%02d".format(Locale.US, it / 60, it % 60) }

fun formatTime(timestamp: Long): String = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0)).let {
    when {
        it < 60_000 -> "now"; it < 3_600_000 -> "${it / 60_000}m"; it < 86_400_000 -> "${it / 3_600_000}h"; else -> "${it / 86_400_000}d"
    }
}

fun formatDate(timestamp: Long): String =
    java.text.SimpleDateFormat("MMMM d, yyyy", Locale.US).format(java.util.Date(timestamp))
