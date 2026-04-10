package com.mindflow.app.data.localmodel

import android.content.Context
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
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
        val downloadUrl = OnDeviceModelSettings.normalizeDownloadUrl(settings.modelDownloadUrl)
        validateDownloadUrl(downloadUrl)
        repository.markDownloading(downloadUrl)

        val request = Request.Builder().url(downloadUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(mapHttpError(response.code))
            }
            val body = response.body ?: throw IllegalStateException("下载失败：空响应")
            val outputFile = resolveModelFile(downloadUrl)
            outputFile.parentFile?.mkdirs()
            val tempFile = File(outputFile.absolutePath + ".part")
            val requiredBytes = body.contentLength().takeIf { it > 0L }
            ensureEnoughStorage(requiredBytes, outputFile)
            try {
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (error: Throwable) {
                tempFile.delete()
                throw error
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
        repository.markError(toUserFacingMessage(it))
    }

    suspend fun deleteModel(settings: OnDeviceModelSettings) {
        settings.localModelPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).delete() }
        }
        repository.clearDownloadState()
    }

    fun resolveModelFile(downloadUrl: String): File {
        val cleanedPath = downloadUrl.substringBefore('?').substringAfterLast('/')
        val fileName = cleanedPath.takeIf { it.isNotBlank() && it.contains('.') } ?: when {
            downloadUrl.endsWith(".task", ignoreCase = true) -> "local-model.task"
            downloadUrl.endsWith(".litertlm", ignoreCase = true) -> "local-model.litertlm"
            else -> "local-model.task"
        }
        return File(modelDirectory(), fileName)
    }

    fun modelDirectory(): File = File(context.filesDir, "local-models")

    private fun validateDownloadUrl(downloadUrl: String) {
        val lower = downloadUrl.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw IllegalArgumentException("模型链接无效，请填写 Hugging Face 仓库页、repo id 或可下载直链")
        }
    }

    private fun ensureEnoughStorage(requiredBytes: Long?, outputFile: File) {
        val usableSpace = (outputFile.parentFile ?: modelDirectory()).usableSpace
        if (requiredBytes != null) {
            val safetyBuffer = 256L * 1024L * 1024L
            if (usableSpace in 1 until (requiredBytes + safetyBuffer)) {
                throw IllegalStateException(
                    "存储空间不足：模型约 ${formatBytes(requiredBytes)}，当前可用 ${formatBytes(usableSpace)}，建议至少预留 ${formatBytes(requiredBytes + safetyBuffer)}"
                )
            }
        } else if (usableSpace in 1 until 512L * 1024L * 1024L) {
            throw IllegalStateException(
                "存储空间偏低：当前可用 ${formatBytes(usableSpace)}，建议至少预留 512MB 以上再下载"
            )
        }
    }

    private fun mapHttpError(code: Int): String = when (code) {
        401 -> "下载失败：模型仓库需要登录或授权"
        403 -> "下载失败：模型仓库拒绝访问，可能需要登录或链接已失效"
        404 -> "下载失败：没有找到模型文件，请检查模型仓库或文件名"
        429 -> "下载失败：请求过于频繁，请稍后再试"
        in 500..599 -> "下载失败：模型源站暂时不可用（$code）"
        else -> "下载失败：服务器返回 $code"
    }

    private fun toUserFacingMessage(error: Throwable): String = when (error) {
        is IllegalStateException, is IllegalArgumentException -> error.message ?: "本地模型下载失败"
        is UnknownHostException -> "下载失败：无法连接网络，请检查 Wi‑Fi 或代理设置"
        is ConnectException -> "下载失败：无法连接到模型源站，请稍后再试"
        is SocketTimeoutException, is InterruptedIOException -> "下载失败：网络超时，请稍后重试"
        is SSLException -> "下载失败：安全连接异常，请检查网络环境或代理设置"
        is IOException -> {
            val message = error.message.orEmpty()
            when {
                message.contains("No space left", ignoreCase = true) ||
                    message.contains("ENOSPC", ignoreCase = true) -> "下载失败：存储空间不足，请先清理手机空间"
                else -> "下载失败：文件写入异常，请检查存储空间或权限"
            }
        }
        else -> error.message ?: "本地模型下载失败"
    }

    private fun formatBytes(bytes: Long): String {
        val mb = 1024L * 1024L
        val gb = mb * 1024L
        return when {
            bytes >= gb -> String.format("%.1fGB", bytes.toDouble() / gb.toDouble())
            bytes >= mb -> String.format("%.0fMB", bytes.toDouble() / mb.toDouble())
            else -> "${bytes / 1024L}KB"
        }
    }
}
