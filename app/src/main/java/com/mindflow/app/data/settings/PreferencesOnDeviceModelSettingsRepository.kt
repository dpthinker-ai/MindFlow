package com.mindflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.model.OnDeviceModelStatus
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onDeviceModelDataStore by preferencesDataStore(name = "mindflow_on_device_model")

class PreferencesOnDeviceModelSettingsRepository(
    private val context: Context,
) : OnDeviceModelSettingsRepository {
    override val settings: Flow<OnDeviceModelSettings> = context.onDeviceModelDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            OnDeviceModelSettings(
                modelLabel = preferences[MODEL_LABEL] ?: OnDeviceModelSettings.DEFAULT_MODEL_LABEL,
                modelDownloadUrl = OnDeviceModelSettings.normalizeDownloadUrl(
                    preferences[MODEL_DOWNLOAD_URL]
                        ?.takeIf { it.isNotBlank() }
                        ?: OnDeviceModelSettings.DEFAULT_MODEL_DOWNLOAD_URL,
                ),
                preferOnDevice = preferences[PREFER_ON_DEVICE] ?: false,
                localModelPath = preferences[LOCAL_MODEL_PATH].orEmpty(),
                downloadedBytes = preferences[DOWNLOADED_BYTES] ?: 0L,
                downloadTargetBytes = preferences[DOWNLOAD_TARGET_BYTES] ?: OnDeviceModelSettings.DEFAULT_MODEL_SIZE_BYTES,
                lastDownloadedAt = preferences[LAST_DOWNLOADED_AT] ?: 0L,
                lastMessage = preferences[LAST_MESSAGE].orEmpty(),
                status = preferences[STATUS]
                    ?.let { raw -> runCatching { OnDeviceModelStatus.valueOf(raw) }.getOrNull() }
                    ?: OnDeviceModelStatus.NOT_DOWNLOADED,
            )
        }

    override suspend fun getCurrent(): OnDeviceModelSettings = settings.first()

    override suspend fun save(settings: OnDeviceModelSettings) {
        context.onDeviceModelDataStore.edit { preferences ->
            preferences[MODEL_LABEL] = settings.modelLabel.trim().ifBlank { OnDeviceModelSettings.DEFAULT_MODEL_LABEL }
            preferences[MODEL_DOWNLOAD_URL] = OnDeviceModelSettings.normalizeDownloadUrl(settings.modelDownloadUrl)
            preferences[PREFER_ON_DEVICE] = settings.preferOnDevice
            preferences[DOWNLOAD_TARGET_BYTES] = settings.downloadTargetBytes
        }
    }

    override suspend fun markDownloading(
        downloadUrl: String,
        downloadedBytes: Long,
        targetBytes: Long,
        message: String,
    ) {
        context.onDeviceModelDataStore.edit { preferences ->
            preferences[MODEL_DOWNLOAD_URL] = OnDeviceModelSettings.normalizeDownloadUrl(downloadUrl)
            preferences[DOWNLOADED_BYTES] = downloadedBytes
            preferences[DOWNLOAD_TARGET_BYTES] = targetBytes
            preferences[STATUS] = OnDeviceModelStatus.DOWNLOADING.name
            preferences[LAST_MESSAGE] = message
        }
    }

    override suspend fun markDownloadProgress(
        downloadedBytes: Long,
        targetBytes: Long,
        message: String,
    ) {
        context.onDeviceModelDataStore.edit { preferences ->
            preferences[DOWNLOADED_BYTES] = downloadedBytes
            preferences[DOWNLOAD_TARGET_BYTES] = targetBytes
            preferences[STATUS] = OnDeviceModelStatus.DOWNLOADING.name
            preferences[LAST_MESSAGE] = message
        }
    }

    override suspend fun markReady(
        localModelPath: String,
        downloadedBytes: Long,
        targetBytes: Long,
        message: String,
        downloadedAt: Long,
    ) {
        context.onDeviceModelDataStore.edit { preferences ->
            preferences[LOCAL_MODEL_PATH] = localModelPath
            preferences[DOWNLOADED_BYTES] = downloadedBytes
            preferences[DOWNLOAD_TARGET_BYTES] = targetBytes
            preferences[LAST_DOWNLOADED_AT] = downloadedAt
            preferences[STATUS] = OnDeviceModelStatus.READY.name
            preferences[LAST_MESSAGE] = message
        }
    }

    override suspend fun markError(
        message: String,
        downloadedBytes: Long?,
        targetBytes: Long?,
    ) {
        context.onDeviceModelDataStore.edit { preferences ->
            downloadedBytes?.let { preferences[DOWNLOADED_BYTES] = it }
            targetBytes?.let { preferences[DOWNLOAD_TARGET_BYTES] = it }
            preferences[STATUS] = OnDeviceModelStatus.ERROR.name
            preferences[LAST_MESSAGE] = message.trim()
        }
    }

    override suspend fun clearDownloadState() {
        context.onDeviceModelDataStore.edit { preferences ->
            preferences.remove(LOCAL_MODEL_PATH)
            preferences.remove(DOWNLOADED_BYTES)
            preferences[DOWNLOAD_TARGET_BYTES] = OnDeviceModelSettings.DEFAULT_MODEL_SIZE_BYTES
            preferences.remove(LAST_DOWNLOADED_AT)
            preferences[STATUS] = OnDeviceModelStatus.NOT_DOWNLOADED.name
            preferences[LAST_MESSAGE] = ""
            preferences[MODEL_DOWNLOAD_URL] = OnDeviceModelSettings.DEFAULT_MODEL_DOWNLOAD_URL
        }
    }

    private companion object {
        val MODEL_LABEL = stringPreferencesKey("model_label")
        val MODEL_DOWNLOAD_URL = stringPreferencesKey("model_download_url")
        val PREFER_ON_DEVICE = booleanPreferencesKey("prefer_on_device")
        val LOCAL_MODEL_PATH = stringPreferencesKey("local_model_path")
        val DOWNLOADED_BYTES = longPreferencesKey("downloaded_bytes")
        val DOWNLOAD_TARGET_BYTES = longPreferencesKey("download_target_bytes")
        val LAST_DOWNLOADED_AT = longPreferencesKey("last_downloaded_at")
        val LAST_MESSAGE = stringPreferencesKey("last_message")
        val STATUS = stringPreferencesKey("status")
    }
}
