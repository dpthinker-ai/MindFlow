package com.mindflow.app.data.organize

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.AiSettingsRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.folderOrganizerDataStore by preferencesDataStore(name = "mindflow_folder_organizer")

data class FolderOrganizerStatus(
    val lastOrganizedAt: Long = 0L,
    val lastOrganizedCount: Int = 0,
)

class BackgroundFolderOrganizer(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val applicationScope: CoroutineScope,
) {
    val status = context.folderOrganizerDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            FolderOrganizerStatus(
                lastOrganizedAt = preferences[LAST_AUTO_ORGANIZED_AT] ?: 0L,
                lastOrganizedCount = preferences[LAST_AUTO_ORGANIZED_COUNT] ?: 0,
            )
        }

    fun organizeInBackgroundIfNeeded() {
        applicationScope.launch {
            runOrganization(force = false)
        }
    }

    suspend fun organizeNow(): Int = runOrganization(force = true) ?: 0

    private suspend fun runOrganization(force: Boolean): Int? {
        val aiSettings = aiSettingsRepository.getCurrent()
        if (!aiSettings.aiEnabled || !aiSettings.isConfigured) return null

        val preferences = context.folderOrganizerDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        val now = System.currentTimeMillis()
        val lastOrganizedAt = preferences[LAST_AUTO_ORGANIZED_AT] ?: 0L
        if (!force && lastOrganizedAt > 0L && now - lastOrganizedAt < AUTO_ORGANIZE_INTERVAL_MS) {
            return null
        }

        val organizedCount = runCatching {
            noteRepository.classifyPendingFolders()
        }.getOrElse {
            return null
        }

        context.folderOrganizerDataStore.edit { mutablePreferences ->
            mutablePreferences[LAST_AUTO_ORGANIZED_AT] = now
            mutablePreferences[LAST_AUTO_ORGANIZED_COUNT] = organizedCount
        }
        return organizedCount
    }

    private companion object {
        val LAST_AUTO_ORGANIZED_AT = longPreferencesKey("last_auto_organized_at")
        val LAST_AUTO_ORGANIZED_COUNT = intPreferencesKey("last_auto_organized_count")
        val AUTO_ORGANIZE_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(1)
    }
}
