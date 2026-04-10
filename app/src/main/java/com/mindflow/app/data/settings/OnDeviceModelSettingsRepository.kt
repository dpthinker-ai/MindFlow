package com.mindflow.app.data.settings

import com.mindflow.app.data.model.OnDeviceModelSettings
import kotlinx.coroutines.flow.Flow

interface OnDeviceModelSettingsRepository {
    val settings: Flow<OnDeviceModelSettings>

    suspend fun getCurrent(): OnDeviceModelSettings
    suspend fun save(settings: OnDeviceModelSettings)
    suspend fun markDownloading(
        downloadUrl: String,
        downloadedBytes: Long,
        targetBytes: Long,
        message: String = "正在下载本地模型",
    )
    suspend fun markDownloadProgress(
        downloadedBytes: Long,
        targetBytes: Long,
        message: String,
    )
    suspend fun markReady(
        localModelPath: String,
        downloadedBytes: Long,
        targetBytes: Long,
        message: String = "",
        downloadedAt: Long = System.currentTimeMillis(),
    )
    suspend fun markError(
        message: String,
        downloadedBytes: Long? = null,
        targetBytes: Long? = null,
    )
    suspend fun clearDownloadState()
}
