package com.instaos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.instaos.app.model.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun CreateScreen(state: AppState, onAction: (Action) -> Unit) {
    var draft by remember { mutableStateOf(state.draft) }
    var front by rememberSaveable { mutableStateOf(false) }
    var flash by rememberSaveable { mutableStateOf(false) }
    var capture by remember { mutableIntStateOf(0) }
    var textEditor by rememberSaveable { mutableStateOf(false) }
    var stickers by rememberSaveable { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordProgress by remember { mutableFloatStateOf(0f) }
    var closeFriends by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val shareProgress = remember { Animatable(0f) }
    val story = draft.mode == 1
    val reel = draft.mode == 3
    val selectedKind = if (reel) MediaKind.REEL else MediaKind.IMAGE
    fun update(next: CreationDraft) {
        draft = next; onAction(Action.SaveDraft(next))
    }

    fun select(media: Media) {
        update(draft.copy(media = media, stage = "editor"))
    }

    fun back() {
        if (draft.stage == "editor") update(draft.copy(stage = "camera")) else onAction(Action.Navigate())
    }
    BackHandler { if (!publishing) back() }
    LaunchedEffect(publishing) {
        if (!publishing) return@LaunchedEffect
        shareProgress.animateTo(1f, tween(850, easing = FastOutSlowInEasing))

        if (story) onAction(
            Action.CreateStory(
                draft.media,
                draft.caption,
                "",
                draft.overlay.copy(closeFriends = closeFriends)
            )
        )
        else onAction(
            Action.CreatePost(
                draft.media.copy(kind = if (reel) MediaKind.REEL else draft.media.kind),
                draft.caption,
                draft.location
            )
        )
    }
    InstaTheme(true) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("creator")) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                    AnimatedContent(
                        draft.stage,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "capture-preview"
                    ) { stage ->
                        if (stage == "editor") {
                            Box(
                                Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = 1 - shareProgress.value * .07f; scaleY = scaleX; alpha =
                                    1 - shareProgress.value * .2f
                                }) {
                                MediaContent(
                                    draft.media,
                                    Modifier.fillMaxSize(),
                                    playVideo = true,
                                    paused = publishing || textEditor || stickers,
                                    controls = false
                                )
                                if (story) StoryDecoration(
                                    draft.overlay,
                                    Modifier.fillMaxSize(),
                                    onChange = { update(draft.copy(overlay = it)) })
                            }
                        } else CameraCapture(
                            front,
                            flash,
                            capture,
                            ::select,
                            Modifier.fillMaxSize(),
                            recording = recording,
                            onRecordingFinished = { media -> recording = false; select(media) },
                            onRecordingProgress = { recordProgress = it }
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CreatorIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", ::back)
                        Spacer(Modifier.weight(1f))
                        if (draft.stage == "editor" && story) {
                            TextButton(onClick = { textEditor = true }) {
                                Text(
                                    "Aa",
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            CreatorIcon(Icons.Default.EmojiEmotions, "Add sticker", { stickers = true })
                            CreatorIcon(Icons.Default.Palette, "Change text color", {
                                val colors = listOf(0xFFFFFFFF, 0xFFFFD166, 0xFFEF476F, 0xFF06D6A0, 0xFF101010)
                                update(
                                    draft.copy(
                                        overlay = draft.overlay.copy(
                                            textColor = colors[(colors.indexOf(draft.overlay.textColor) + 1).mod(
                                                colors.size
                                            )]
                                        )
                                    )
                                )
                            })
                        } else CreatorIcon(
                            if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            "Toggle flash",
                            { flash = !flash })
                        CreatorIcon(Icons.Default.Close, "Close creation", { onAction(Action.Navigate()) })
                    }
                    if (draft.stage == "camera") Text(
                        if (story) "Tap for a photo, hold to record"
                        else if (reel) "Choose a video from your gallery"
                        else "Capture or choose your own photo",
                        color = Color.White.copy(alpha = .65f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    )
                    if (publishing) Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                progress = { shareProgress.value },
                                color = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                "Sharing…",
                                color = Color.White,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
                if (draft.stage == "editor" && !story) {
                    OutlinedTextField(
                        draft.caption,
                        { update(draft.copy(caption = it.take(2200))) },
                        Modifier.fillMaxWidth().padding(12.dp).testTag("captionInput"),
                        placeholder = { Text("Write a caption…") },
                        maxLines = 3
                    )
                }
                if (draft.stage == "editor") {
                    Row(
                        Modifier.fillMaxWidth().imePadding().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (story) {
                            FilledTonalButton(
                                enabled = !publishing,
                                onClick = {
                                    closeFriends = false; publishing = true; haptic.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                                },
                                modifier = Modifier.weight(1f).testTag("publishButton"),
                                shape = RoundedCornerShape(50)
                            ) {
                                AvatarFromProfile(state.profile, Modifier.size(24.dp)); Text("  Your story")
                            }
                            FilledTonalButton(
                                enabled = !publishing,
                                onClick = { closeFriends = true; publishing = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.Stars, null, tint = Color(0xFF68C871)); Text("  Close friends")
                            }
                        } else Button(
                            enabled = !publishing && (!reel || draft.media.kind != MediaKind.IMAGE),
                            onClick = { publishing = true },
                            modifier = Modifier.fillMaxWidth().testTag("publishButton")
                        ) { Text("Share") }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GalleryButton(selectedKind, ::select, mixed = true)
                        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                            if (story && recording) CircularProgressIndicator(
                                progress = { recordProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFFFF3040),
                                strokeWidth = 4.dp,
                                trackColor = Color.Transparent
                            )
                            Box(
                                Modifier.fillMaxSize().border(
                                    3.dp,
                                    Brush.linearGradient(listOf(Color.White, Color.White)),
                                    CircleShape
                                )
                                    .padding(7.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = .96f))
                                    .testTag("shutter")
                                    .then(
                                        if (story && !publishing) Modifier.pointerInput(Unit) {
                                            detectTapGestures(onPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val start = System.currentTimeMillis()
                                                recording = true
                                                tryAwaitRelease()

                                                if (System.currentTimeMillis() - start < 350) {
                                                    recording = false; capture++
                                                } else recording = false
                                            })
                                        } else Modifier.clickable(enabled = !publishing) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!reel) capture++
                                        }
                                    ), contentAlignment = Alignment.Center
                            ) {
                                if (reel) Icon(Icons.Default.VideoLibrary, "Import a reel", tint = Color.Black)
                            }
                        }
                        CreatorIcon(Icons.Default.Cameraswitch, "Switch camera", { front = !front })
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
                        listOf(0 to "POST", 1 to "STORY", 3 to "REEL").forEach { (mode, title) ->
                            TextButton(onClick = {
                                update(
                                    draft.copy(
                                        mode = mode,
                                        stage = "camera",
                                        media = Media(kind = if (mode == 3) MediaKind.REEL else MediaKind.IMAGE)
                                    )
                                )
                            }) {
                                Text(
                                    title,
                                    color = if (draft.mode == mode) Color.White else Color(0xFF777777),
                                    fontWeight = if (draft.mode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (textEditor) {
        var text by remember { mutableStateOf(draft.overlay.text) }
        AlertDialog(
            onDismissRequest = { textEditor = false },
            title = { Text("Story text") },
            text = {
                OutlinedTextField(
                    text,
                    { text = it.take(300) },
                    Modifier.testTag("storyTextInput"),
                    placeholder = { Text("Say something…") })
            },
            confirmButton = {
                TextButton(onClick = {
                    update(draft.copy(overlay = draft.overlay.copy(text = text))); textEditor = false
                }) { Text("Done") }
            })
    }
    if (stickers) AlertDialog(
        onDismissRequest = { stickers = false },
        title = { Text("Add a little something") },
        text = {
            Column {
                listOf(listOf("🤍", "✨", "🔥", "🫶"), listOf("GOOD DAY", "WEEKEND", "MOOD", "📍 HERE")).forEach { row ->
                    Row {
                        row.forEach { sticker ->
                            TextButton(onClick = {
                                update(
                                    draft.copy(
                                        overlay = draft.overlay.copy(
                                            sticker = sticker
                                        )
                                    )
                                ); stickers = false
                            }) { Text(sticker) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                update(draft.copy(overlay = draft.overlay.copy(sticker = ""))); stickers = false
            }) { Text("Remove sticker") }
        })
}

@Composable
private fun CreatorIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = .18f), CircleShape)
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(25.dp)) }
}

@Composable
fun StoryDecoration(overlay: StoryOverlay, modifier: Modifier = Modifier, onChange: ((StoryOverlay) -> Unit)? = null) {
    val latest by rememberUpdatedState(overlay)
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Column(Modifier.offset {
            IntOffset(
                (width * latest.x - width * .35f).roundToInt(),
                (height * latest.y).roundToInt()
            )
        }
            .width(maxWidth * .7f).then(if (onChange != null) Modifier.pointerInput(width, height) {
                detectDragGestures { change, drag ->
                    change.consume(); onChange(
                    latest.copy(
                        x = (latest.x + drag.x / width).coerceIn(
                            .25f,
                            .75f
                        ), y = (latest.y + drag.y / height).coerceIn(.12f, .78f)
                    )
                )
                }
            } else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
            if (overlay.text.isNotBlank()) Text(
                overlay.text,
                color = Color(overlay.textColor),
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (overlay.sticker.isNotBlank()) Text(
                overlay.sticker,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp)
                    .background(Color.Black.copy(alpha = .2f), RoundedCornerShape(12.dp)).padding(10.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(profile: Profile, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var username by rememberSaveable { mutableStateOf(profile.username) }
    var bio by rememberSaveable { mutableStateOf(profile.bio) }
    var avatar by rememberSaveable { mutableStateOf(profile.avatar) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.85f).imePadding().verticalScroll(rememberScrollState())
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AvatarFromProfile(profile.copy(avatar = avatar), Modifier.size(80.dp).align(Alignment.CenterHorizontally))
            PrivateMediaPicker(MediaKind.IMAGE, { avatar = it.path }, "Change profile photo")
            OutlinedTextField(
                username,
                { username = it.take(30) },
                Modifier.fillMaxWidth().testTag("usernameInput"),
                label = { Text("Username") },
                singleLine = true
            )
            OutlinedTextField(
                name,
                { name = it.take(80) },
                Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true
            )
            OutlinedTextField(
                bio,
                { bio = it.take(300) },
                Modifier.fillMaxWidth(),
                label = { Text("Bio") },
                minLines = 2
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = username.isNotBlank(),
                onClick = {
                    onAction(
                        Action.EditProfile(
                            profile.copy(
                                username = username,
                                displayName = name,
                                bio = bio,
                                avatar = avatar
                            )
                        )
                    ); onDismiss()
                }) { Text("Save profile") }
        }
    }
}
