package com.instaos.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.instaos.app.model.*

/**
 * Search + Explore (phase 3, audit §8 step 4).
 *
 * Users come from the `web/search/topsearch` endpoint (debounced in the
 * ViewModel, in-memory only — nothing is saved). Tapping a person shows
 * their card; there is no in-app profile page for other people in v1, so
 * the card offers the honest next step: opening their page in Instagram.
 *
 * Explore is the same real feed, shown as a 3:4 grid — no fake tiles.
 * If the feed has not loaded yet, it says so plainly instead of
 * inventing popular posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: AppState,
    search: SearchUiState,
    feed: FeedUiState,
    onAction: (Action) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefreshFeed: () -> Unit,
) {
    val context = LocalContext.current
    var selectedUser by remember { mutableStateOf<UserRef?>(null) }
    AppScaffold(state, onAction) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("searchField"),
                placeholder = { Text("Search people") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (search.query.isNotBlank()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                },
                singleLine = true,
            )
            if (search.error != null) {
                Text(
                    search.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (search.query.isNotBlank()) {
                if (search.loading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().testTag("searchResults"),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(search.results, key = { it.id.ifBlank { it.username } }) { user ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedUser = user }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarFromUserRef(user, Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.username, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    if (user.verified) Icon(
                                        Icons.Default.Verified,
                                        "Verified account",
                                        Modifier.size(14.dp).padding(start = 3.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (user.displayName.isNotBlank()) Text(
                                    user.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (search.results.isEmpty() && !search.loading) {
                        item {
                            Text(
                                "No people found for \"${search.query}\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp)
                            )
                        }
                    }
                }
            } else {
                // Explore: the real feed as a 3:4 grid.
                if (feed.posts.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Explore is empty",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Explore shows posts from your feed. Load the feed on Home first — nothing here is made up.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedButton(onClick = onRefreshFeed, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Load my feed")
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().testTag("exploreGrid"),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(feed.posts, key = { it.id }) { post ->
                            Box(
                                Modifier.aspectRatio(0.75f).clickable {
                                    onAction(Action.NavigateRoute(Route.PostDetail(post.id)))
                                }
                            ) {
                                MediaContent(post.media, Modifier.fillMaxSize())
                                if (post.media.kind == MediaKind.REEL) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        "Reel",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedUser?.let { user ->
        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = { Text(user.username) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarFromUserRef(user, Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            if (user.displayName.isNotBlank()) Text(user.displayName, fontWeight = FontWeight.SemiBold)
                            if (user.verified) Text("Verified", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "InstaOS doesn't have a profile page for other people yet. You can open them in Instagram.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedUser = null
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://www.instagram.com/${user.username}/")
                            )
                        )
                    }
                }) { Text("Open on Instagram") }
            },
            dismissButton = {
                TextButton(onClick = { selectedUser = null }) { Text("Close") }
            }
        )
    }
}
