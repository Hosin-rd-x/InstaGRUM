package com.instaos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instaos.app.ui.*

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            // Phase 3: the theme comes from the (small) UI settings —
            // dark mode + the user's accent colour. No notification
            // permission prompt, no background service any more.
            InstaTheme(state?.settings?.darkMode ?: true, state?.settings?.accent) {
                MediaProvider {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val value = state
                        val webSession by viewModel.session.collectAsStateWithLifecycle()
                        val offline by viewModel.offlineMode.collectAsStateWithLifecycle()
                        val feed by viewModel.feedState.collectAsStateWithLifecycle()
                        val dmLoading by viewModel.dmLoading.collectAsStateWithLifecycle()
                        val dmError by viewModel.dmError.collectAsStateWithLifecycle()
                        val search by viewModel.searchState.collectAsStateWithLifecycle()
                        val storyError by viewModel.storyError.collectAsStateWithLifecycle()
                        if (value != null) {
                            val context = this@MainActivity
                            // Login gate: no web session and not "continue
                            // offline" → the login screen. A fresh install
                            // signs in first and the profile is adopted
                            // from Instagram; "continue offline" shows the
                            // app with local content only.
                            if (webSession?.isLoggedIn != true && !offline) {
                                LoginScreen(
                                    onLoginSuccess = viewModel::completeLogin,
                                    onContinueOffline = viewModel::continueOffline,
                                )
                            } else {
                                InstaApp(
                                    value, viewModel::dispatch, BackupActions(
                                        export = { uri -> viewModel.exportBackup(uri, context) { toast(it) } },
                                        import = { uri -> viewModel.importBackup(uri, context) { toast(it) } },
                                        deleteAccount = viewModel::deleteAccount
                                    ),
                                    feed = feed,
                                    loggedIn = webSession?.isLoggedIn == true,
                                    onRefreshFeed = { viewModel.refreshFeed(force = true) },
                                    onLoadMoreFeed = viewModel::loadMoreFeed,
                                    onFeedLike = viewModel::feedLike,
                                    onFeedSave = viewModel::feedSave,
                                    onFeedComment = { post, text, parentId ->
                                        viewModel.feedAddComment(post, text, parentId)
                                    },
                                    onOpenLogin = viewModel::showLogin,
                                    onLogout = viewModel::logout,
                                    dmLoading = dmLoading,
                                    dmError = dmError,
                                    onRefreshDmInbox = { viewModel.refreshDmInbox() },
                                    onLoadDmThread = { viewModel.loadDmThread(it) },
                                    onSendDm = { threadId, text -> viewModel.sendDmMessage(threadId, text) },
                                    onRetryDm = { threadId, message -> viewModel.retryDmMessage(threadId, message) },
                                    onDmVisible = { viewModel.startDmPolling(it) },
                                    onDmHidden = viewModel::stopDmPolling,
                                    search = search,
                                    onSearch = viewModel::searchUsers,
                                    onClearSearch = viewModel::clearSearch,
                                    storyError = storyError,
                                    onOpenTrayStory = viewModel::openTrayStory,
                                    onClearStoryError = viewModel::clearStoryError,
                                )
                            }
                            if (error != null) AlertDialog(
                                onDismissRequest = {},
                                title = { Text("Couldn't save changes") },
                                text = { Text("$error\n\nActivity is paused until saving succeeds. Your last saved profile is preserved.") },
                                confirmButton = { TextButton(onClick = viewModel::retry) { Text("Retry") } })
                        } else Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                            if (error == null) CircularProgressIndicator() else Column(
                                Modifier.padding(28.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("Couldn't open your local profile", style = MaterialTheme.typography.titleLarge)
                                Text(error.orEmpty()); Button(onClick = viewModel::retry) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()

    override fun onStart() {
        super.onStart(); viewModel.setForeground(true)
    }

    override fun onStop() {
        viewModel.setForeground(false); super.onStop()
    }
}
