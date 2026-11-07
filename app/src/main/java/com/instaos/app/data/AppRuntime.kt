package com.instaos.app.data

import android.content.Context
import com.instaos.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The single-writer store for [AppState] (phase 3).
 *
 * Renamed from `SimulatorRuntime`: the simulation is gone, and with it
 * the 500 ms tick loop, the background catch-up (`advanceWhileClosed` /
 * `advanceBackground`), the WorkManager worker, the foreground service
 * and the notification diffing. What stays is the good skeleton — a
 * process-wide singleton, one mutex, a serial action channel, a
 * `StateFlow<AppState?>`, and atomic snapshot saves.
 *
 * Network work (feed, DM, search) lives in [com.instaos.app.AppViewModel],
 * which dispatches the `*Loaded` actions here; this class never touches
 * the network and never runs a timer.
 */
class AppRuntime private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: AppRuntime? = null
        fun get(context: Context): AppRuntime = instance ?: synchronized(this) {
            instance ?: AppRuntime(context.applicationContext).also { instance = it }
        }

        internal fun releaseForTests() {
            instance?.scope?.cancel(); instance = null
        }
    }

    private val appContext = context.applicationContext
    private val repository = AppRepository(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actions = Channel<Action>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<AppState?>(null)
    val state = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    @Volatile
    var foreground = false
        private set

    private var loaded = false

    init {
        scope.launch { for (action in actions) dispatch(action) }
    }

    fun enqueue(action: Action) {
        actions.trySend(action)
    }

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        try {
            val now = System.currentTimeMillis()
            val saved = repository.load()
            val value = InitialState.migrate(saved ?: InitialState.create(now))
            if (saved == null || value != saved) repository.save(value)
            mutableState.value = value
            loaded = true
            mutableError.value = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableError.value = failure.message ?: "Your local profile could not be opened."
        }
    }

    suspend fun dispatch(action: Action) {
        load()
        mutex.withLock {
            val before = mutableState.value ?: return@withLock
            try {
                val now = System.currentTimeMillis()
                if (action is Action.SwitchAccount) {
                    repository.save(before)
                    val switched = repository.switch(action.id) ?: return@withLock
                    mutableState.value = switched
                    return@withLock
                }
                if (action == Action.CreateAccount) {
                    repository.save(before)
                    val accountId = "account-${UUID.randomUUID()}"
                    val created =
                        repository.create(accountId, InitialState.create(now).copy(activeAccountId = accountId))
                    mutableState.value = created
                    return@withLock
                }
                val after = StateReducer.reduce(before, action, now, UUID.randomUUID().toString())
                if (action is Action.EditProfile) {
                    val withAccounts = after.copy(
                        accounts = after.accounts.filterNot { it.id == after.activeAccountId } + AccountSummary(
                            after.activeAccountId,
                            after.profile.username,
                            after.profile.displayName,
                            after.profile.avatar,
                        )
                    )
                    save(before, withAccounts)
                } else if (action is Action.GridPosition || action is Action.StoryProgress || action is Action.SaveDraft) {
                    mutableState.value = after
                } else {
                    save(before, after)
                }

                if (action is Action.DeletePost || action is Action.DeleteStory || action is Action.EditProfile) {
                    runCatching { repository.collectUnusedMedia(appContext) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableError.value = failure.message ?: "Could not save changes."
            }
        }
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) scope.launch { flush() }
    }

    suspend fun exportBackup(): ByteArray {
        load()
        return mutex.withLock {
            mutableState.value?.let { repository.save(it) }
            repository.exportBackup()
        }
    }

    suspend fun importBackup(bytes: ByteArray): Int {
        load()
        return mutex.withLock {
            val restored = repository.importBackup(bytes)
            mutableState.value = repository.load()
            mutableError.value = null
            restored
        }
    }

    suspend fun deleteActiveAccount() {
        load()
        mutex.withLock {
            val current = mutableState.value ?: return@withLock
            val next = repository.deleteAccount(current.activeAccountId) ?: return@withLock
            mutableState.value = next
        }
    }

    suspend fun retry() {
        if (!loaded) load() else mutex.withLock {
            mutableState.value?.let {
                try {
                    repository.save(it); mutableError.value = null
                } catch (failure: Exception) {
                    mutableError.value = failure.message
                }
            }
        }
    }

    suspend fun flush() = mutex.withLock {
        mutableState.value?.let {
            try {
                repository.save(it)
            } catch (failure: Exception) {
                mutableError.value = failure.message
            }
        }
    }

    private suspend fun save(before: AppState, after: AppState) {
        val saved = after.copy(lastSavedAt = System.currentTimeMillis())
        mutableState.value = saved
        repository.save(saved)
        mutableError.value = null
    }
}
