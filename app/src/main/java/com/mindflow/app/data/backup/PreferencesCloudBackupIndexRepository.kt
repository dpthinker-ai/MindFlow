package com.mindflow.app.data.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.cloudBackupIndexDataStore by preferencesDataStore(name = "mindflow_cloud_backup_index")

class PreferencesCloudBackupIndexRepository(
    private val context: Context,
) : CloudBackupIndexRepository {
    override suspend fun getCurrent(): CloudBackupIndex = context.cloudBackupIndexDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            CloudBackupIndex(
                targetKey = preferences[TARGET_KEY].orEmpty(),
                noteHashes = parseHashes(preferences[HASHES_JSON].orEmpty()),
            )
        }
        .first()

    override suspend fun save(index: CloudBackupIndex) {
        context.cloudBackupIndexDataStore.edit { preferences ->
            preferences[TARGET_KEY] = index.targetKey
            preferences[HASHES_JSON] = JSONObject(index.noteHashes).toString()
        }
    }

    override suspend fun clear() {
        context.cloudBackupIndexDataStore.edit { preferences ->
            preferences.remove(TARGET_KEY)
            preferences.remove(HASHES_JSON)
        }
    }

    private fun parseHashes(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { key -> json.optString(key) }
    }

    private companion object {
        val TARGET_KEY = stringPreferencesKey("target_key")
        val HASHES_JSON = stringPreferencesKey("hashes_json")
    }
}
