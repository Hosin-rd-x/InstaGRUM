package com.instaos.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instaos.app.model.*

/**
 * Settings (phase 3, rebuilt).
 *
 * The old screen was the simulation's control panel ("Your pace", nine
 * growth presets, a foreground service, fake notifications). This one is
 * the real thing: account (sign out), appearance (dark mode + accent
 * colour), your data (backup/restore), and an honest About section. No
 * control here does nothing — push notifications are not in this version,
 * so there is no notifications toggle pretending otherwise.
 */
@Composable
fun SettingsScreen(
    state: AppState,
    onAction: (Action) -> Unit,
    backup: BackupActions? = null,
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    Scaffold(topBar = { ScreenHeader("Settings", { onAction(Action.Navigate()) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("settingsList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarFromProfile(state.profile, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.profile.username.ifBlank { "Not signed in" }, fontWeight = FontWeight.SemiBold)
                        Text(
                            state.profile.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Sign out") }
                Text(
                    "Signing out forgets the login session on this device. Your local posts and stories stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Appearance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                ToggleSetting(
                    "Dark appearance",
                    state.settings.darkMode
                ) { onAction(Action.SaveSettings(state.settings.copy(darkMode = it))) }
                Text(
                    "Accent colour",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(
                        Triple(AccentColor.VIOLET, AccentViolet, "Violet"),
                        Triple(AccentColor.LIME, AccentLime, "Lime"),
                        Triple(AccentColor.PINK, AccentPink, "Pink"),
                        Triple(AccentColor.TEAL, AccentTeal, "Teal"),
                    ).forEach { (accent, color, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(color)
                                    .testTag("accent-$accent")
                                    .then(
                                        if (state.settings.accent == accent) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { onAction(Action.SaveSettings(state.settings.copy(accent = accent))) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.settings.accent == accent) Icon(
                                    Icons.Default.Check,
                                    "Selected",
                                    tint = if (accent == AccentColor.LIME) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Push notifications aren't in this version — DMs refresh when you open them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (backup != null) item { BackupControls(state, backup) }
            item {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("About", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "InstaOS 1.1.0 — a web-session Instagram client for the test account. " +
                        "It signs in inside Instagram's own page and reads the feed, stories, " +
                        "profile and messages through that session. Posting, Live and push " +
                        "notifications are not in this version.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "InstaOS",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "UI base: InstaGRUM by zyrexdz (MIT)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/zyrexdz/InstaGRUM"))
                            )
                        }
                    }) { Text("github.com/zyrexdz/InstaGRUM") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (confirmSignOut) ConfirmAction(
        "Sign out?",
        "Your Instagram session on this device will be forgotten. You will need to sign in again to see your feed and messages.",
        { onLogout() },
        { confirmSignOut = false })
}

class BackupActions(
    val export: (android.net.Uri) -> Unit,
    val import: (android.net.Uri) -> Unit,
    val deleteAccount: () -> Unit,
)

@Composable
private fun BackupControls(state: AppState, backup: BackupActions) {
    var confirmDelete by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(backup.export)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backup.import)
    }
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Text("Your data", fontWeight = FontWeight.SemiBold)
    Text(
        "Your accounts stay on this device. Save a file as well if you want a copy you control. The login session itself is never included in a backup.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = { save.launch("instaos-backup.json") },
            modifier = Modifier.weight(1f)
        ) { Text("Save backup") }
        FilledTonalButton(
            onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.weight(1f)
        ) { Text("Restore") }
    }
    if (state.accounts.size > 1) TextButton(onClick = { confirmDelete = true }) {
        Text("Delete this account", color = MaterialTheme.colorScheme.error)
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this account?") },
        text = { Text("${state.profile.username} and its posts and stories are removed from this device. Your other accounts are untouched. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = { backup.deleteAccount(); confirmDelete = false }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
    )
}

@Composable
fun ToggleSetting(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onChange)
    }
}
