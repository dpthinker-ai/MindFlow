package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.model.CloudBackupSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudBackupSettingsDataStore by preferencesDataStore(name = "mindflow_cloud_backup_settings")

class PreferencesCloudBackupSettingsRepository(
    private val context: Context,
) : CloudBackupSettingsRepository {
    override val settings: Flow<CloudBackupSettings> = context.cloudBackupSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            CloudBackupSettings(
                baseUrl = preferences[BASE_URL] ?: CloudBackupSettings.DEFAULT_BASE_URL,
                username = preferences[USERNAME].orEmpty(),
                password = preferences[PASSWORD].orEmpty(),
                remoteDir = preferences[REMOTE_DIR] ?: CloudBackupSettings.DEFAULT_REMOTE_DIR,
                autoBackupEnabled = preferences[AUTO_BACKUP_ENABLED] ?: false,
                lastBackupAt = preferences[LAST_BACKUP_AT] ?: 0L,
                lastBackupError = preferences[LAST_BACKUP_ERROR].orEmpty(),
            )
        }

    override suspend fun getCurrent(): CloudBackupSettings = settings.first()

    override suspend fun save(settings: CloudBackupSettings) {
        context.cloudBackupSettingsDataStore.edit { preferences ->
            preferences[BASE_URL] = settings.normalizedBaseUrl
            preferences[USERNAME] = settings.username.trim()
            preferences[PASSWORD] = settings.password.trim()
            preferences[REMOTE_DIR] = settings.normalizedRemoteDir
            preferences[AUTO_BACKUP_ENABLED] = settings.autoBackupEnabled
            preferences[LAST_BACKUP_AT] = settings.lastBackupAt
            if (settings.lastBackupError.isBlank()) {
                preferences.remove(LAST_BACKUP_ERROR)
            } else {
                preferences[LAST_BACKUP_ERROR] = settings.lastBackupError.trim()
            }
        }
    }

    override suspend fun clear() {
        context.cloudBackupSettingsDataStore.edit { preferences ->
            preferences.remove(BASE_URL)
            preferences.remove(USERNAME)
            preferences.remove(PASSWORD)
            preferences.remove(REMOTE_DIR)
            preferences.remove(AUTO_BACKUP_ENABLED)
            preferences.remove(LAST_BACKUP_AT)
            preferences.remove(LAST_BACKUP_ERROR)
        }
    }

    override suspend fun updateBackupStatus(
        succeededAt: Long?,
        errorMessage: String,
    ) {
        context.cloudBackupSettingsDataStore.edit { preferences ->
            if (succeededAt != null) {
                preferences[LAST_BACKUP_AT] = succeededAt
            }
            if (errorMessage.isBlank()) {
                preferences.remove(LAST_BACKUP_ERROR)
            } else {
                preferences[LAST_BACKUP_ERROR] = errorMessage.trim()
            }
        }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val REMOTE_DIR = stringPreferencesKey("remote_dir")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_ERROR = stringPreferencesKey("last_backup_error")
    }
}
