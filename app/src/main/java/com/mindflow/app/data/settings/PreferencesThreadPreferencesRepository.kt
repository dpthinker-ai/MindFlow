package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.model.ThreadPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.threadPreferencesDataStore by preferencesDataStore(name = "mindflow_thread_preferences")

class PreferencesThreadPreferencesRepository(
    private val context: Context,
) : ThreadPreferencesRepository {
    override val settings: Flow<ThreadPreferences> = context.threadPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ThreadPreferences(
                followedThreadKeys = preferences[FOLLOWED_THREAD_KEYS] ?: emptySet(),
            )
        }

    override suspend fun getCurrent(): ThreadPreferences = settings.first()

    override suspend fun toggleFollow(threadKey: String): Boolean {
        val normalized = threadKey.trim()
        if (normalized.isBlank()) return false
        var nowFollowed = false
        context.threadPreferencesDataStore.edit { preferences ->
            val current = preferences[FOLLOWED_THREAD_KEYS] ?: emptySet()
            val next = if (normalized in current) {
                current - normalized
            } else {
                nowFollowed = true
                current + normalized
            }
            preferences[FOLLOWED_THREAD_KEYS] = next
        }
        return nowFollowed
    }

    override suspend fun clear() {
        context.threadPreferencesDataStore.edit { preferences ->
            preferences.remove(FOLLOWED_THREAD_KEYS)
        }
    }

    private companion object {
        val FOLLOWED_THREAD_KEYS = stringSetPreferencesKey("followed_thread_keys")
    }
}
