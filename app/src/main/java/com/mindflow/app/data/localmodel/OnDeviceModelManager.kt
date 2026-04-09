package com.mindflow.app.data.localmodel

import android.content.Context
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

data class OnDeviceModelDownloadResult(
    val path: String,
    val bytes: Long,
)

class OnDeviceModelManager(
    private val context: Context,
    private val repository: OnDeviceModelSettingsRepository,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun downloadModel(settings: OnDeviceModelSettings): Result<OnDeviceModelDownloadResult> = runCatching {
        val downloadUrl = settings.modelDownloadUrl.trim()
        require(downloadUrl.isNotBlank()) { "请先填入模型下载链接" }
        repository.markDownloading(downloadUrl)

        val request = Request.Builder().url(downloadUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("下载失败：${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("下载失败：空响应")
            val outputFile = resolveModelFile(downloadUrl)
            outputFile.parentFile?.mkdirs()
            val tempFile = File(outputFile.absolutePath + ".part")
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            val bytes = outputFile.length().coerceAtLeast(0L)
            repository.markReady(
                localModelPath = outputFile.absolutePath,
                downloadedBytes = bytes,
                message = "本地模型已就绪",
            )
            OnDeviceModelDownloadResult(
                path = outputFile.absolutePath,
                bytes = bytes,
            )
        }
    }.onFailure {
        repository.markError(it.message ?: "本地模型下载失败")
    }

    suspend fun deleteModel(settings: OnDeviceModelSettings) {
        settings.localModelPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).delete() }
        }
        repository.clearDownloadState()
    }

    fun resolveModelFile(downloadUrl: String): File {
        val extension = when {
            downloadUrl.endsWith(".task", ignoreCase = true) -> ".task"
            downloadUrl.endsWith(".litertlm", ignoreCase = true) -> ".litertlm"
            else -> ".task"
        }
        return File(modelDirectory(), "gemma4-e4b$extension")
    }

    fun modelDirectory(): File = File(context.filesDir, "local-models")
}
