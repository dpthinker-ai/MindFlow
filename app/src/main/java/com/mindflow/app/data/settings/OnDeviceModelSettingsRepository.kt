package com.mindflow.app.data.settings

import com.mindflow.app.data.model.OnDeviceModelSettings
import kotlinx.coroutines.flow.Flow

interface OnDeviceModelSettingsRepository {
    val settings: Flow<OnDeviceModelSettings>

    suspend fun getCurrent(): OnDeviceModelSettings
    suspend fun save(settings: OnDeviceModelSettings)
    suspend fun markDownloading(downloadUrl: String)
    suspend fun markReady(
        localModelPath: String,
        downloadedBytes: Long,
        message: String = "",
        downloadedAt: Long = System.currentTimeMillis(),
    )
    suspend fun markError(message: String)
    suspend fun clearDownloadState()
}
