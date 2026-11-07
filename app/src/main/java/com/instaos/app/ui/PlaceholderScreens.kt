package com.instaos.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instaos.app.model.Action
import com.instaos.app.model.AppState
import com.instaos.app.model.Route

/**
 * Honest placeholders for tabs whose screens arrive in phase 3.
 *
 * A tab must never be a dead button: tapping it opens a screen that says
 * plainly what is coming, instead of doing nothing or crashing.
 */
@Composable
fun DmPlaceholderScreen(state: AppState, onAction: (Action) -> Unit) {
    PlaceholderScreen(
        state = state,
        onAction = onAction,
        title = "Direct messages",
        subtitleFa = "دایرکت در فاز ۳ می‌آید.",
        subtitleEn = "Direct messages arrive in phase 3 — inbox, threads and sending.",
    )
}

@Composable
fun SearchPlaceholderScreen(state: AppState, onAction: (Action) -> Unit) {
    PlaceholderScreen(
        state = state,
        onAction = onAction,
        title = "Search",
        subtitleFa = "جستجو و اکسپلور در فاز ۳ می‌آید.",
        subtitleEn = "Search and Explore arrive in phase 3 — accounts, tags and the 3:4 grid.",
    )
}

@Composable
private fun PlaceholderScreen(
    state: AppState,
    onAction: (Action) -> Unit,
    title: String,
    subtitleFa: String,
    subtitleEn: String,
) {
    AppScaffold(state, onAction) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(subtitleFa, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitleEn, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { onAction(Action.NavigateRoute(Route.Home)) }) {
                Text("Back to Home")
            }
        }
    }
}
