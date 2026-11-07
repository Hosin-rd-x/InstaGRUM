package com.instaos.app.data

import com.instaos.app.model.AppState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UnsupportedSnapshotVersion(version: Int) :
    IllegalStateException("Snapshot version $version requires a newer InstaOS. Your data has not been overwritten.")

class SnapshotStore(private val directory: File) {
    private companion object {
        const val CURRENT = 4
    }

    private val file = File(directory, "instaos-state.json")
    private val backup = File(directory, "instaos-state.backup.json")
    // Phase 1–2 snapshots used the upstream name; read them once so an
    // upgrade keeps the profile, then save under the new name only.
    private val legacyFile = File(directory, "instagrum-local-state.json")
    private val legacyBackup = File(directory, "instagrum-local-state.backup.json")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Synchronized
    fun load(): AppState? {
        val candidates = listOf(file, backup, legacyFile, legacyBackup)
        if (candidates.none { it.exists() }) return null
        var failure: Exception? = null
        for (candidate in candidates) {
            if (!candidate.exists()) continue
            try {
                val state = json.decodeFromString<AppState>(candidate.readText())
                if (state.schemaVersion > CURRENT) throw UnsupportedSnapshotVersion(state.schemaVersion)
                return state
            } catch (error: UnsupportedSnapshotVersion) {
                throw error
            } catch (error: Exception) {
                failure = error
            }
        }
        throw IllegalStateException("Your saved data could not be read. It has not been overwritten.", failure)
    }

    @Synchronized
    fun save(state: AppState) {
        check(state.schemaVersion <= CURRENT)
        directory.mkdirs()
        val temporary = File(directory, "instaos-state.tmp")
        val bytes = json.encodeToString(state).toByteArray(Charsets.UTF_8)
        FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }

        if (file.exists() && runCatching { json.decodeFromString<AppState>(file.readText()) }.isSuccess) {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
