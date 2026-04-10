package com.mindflow.app.data.localmodel

import android.content.Context
import android.os.NetworkOnMainThreadException
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OnDeviceModelDownloadResult(
    val path: String,
    val bytes: Long,
)

class OnDeviceModelManager(
    private val context: Context,
    private val repository: OnDeviceModelSettingsRepository,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private companion object {
        const val PROGRESS_PUBLISH_BYTES = 4L * 1024L * 1024L
    }

    suspend fun downloadModel(settings: OnDeviceModelSettings): Result<OnDeviceModelDownloadResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val downloadUrl = OnDeviceModelSettings.normalizeDownloadUrl(settings.modelDownloadUrl)
                validateDownloadUrl(downloadUrl)
                val outputFile = resolveModelFile(downloadUrl)
                outputFile.parentFile?.mkdirs()
                val tempFile = File(outputFile.absolutePath + ".part")
                var resumedBytes = tempFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
                var targetBytes = settings.downloadTargetBytes.takeIf { it > 0L }
                    ?: OnDeviceModelSettings.DEFAULT_MODEL_SIZE_BYTES
                repository.markDownloading(
                    downloadUrl = downloadUrl,
                    downloadedBytes = resumedBytes,
                    targetBytes = targetBytes,
                    message = if (resumedBytes > 0L) {
                        "检测到未完成下载，准备继续下载"
                    } else {
                        "正在下载本地模型"
                    },
                )

                val requestBuilder = Request.Builder().url(downloadUrl)
                if (resumedBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$resumedBytes-")
                }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 416 && tempFile.exists()) {
                            tempFile.delete()
                            throw IllegalStateException("已清理失效断点，请再次点击继续下载")
                        }
                        throw IllegalStateException(mapHttpError(response.code))
                    }
                    val body = response.body ?: throw IllegalStateException("下载失败：空响应")
                    val supportsResume = response.code == 206
                    if (resumedBytes > 0L && !supportsResume) {
                        tempFile.delete()
                        resumedBytes = 0L
                    }

                    targetBytes = resolveTargetBytes(
                        response = response,
                        resumedBytes = resumedBytes,
                        fallback = settings.downloadTargetBytes.takeIf { it > 0L }
                            ?: OnDeviceModelSettings.DEFAULT_MODEL_SIZE_BYTES,
                    )
                    val remainingBytes = (targetBytes - resumedBytes).coerceAtLeast(0L)
                    ensureEnoughStorage(remainingBytes, outputFile)
                    repository.markDownloading(
                        downloadUrl = downloadUrl,
                        downloadedBytes = resumedBytes,
                        targetBytes = targetBytes,
                        message = if (resumedBytes > 0L) {
                            "正在继续下载本地模型"
                        } else {
                            "正在下载本地模型"
                        },
                    )
                    writeResponseBody(
                        body = body,
                        tempFile = tempFile,
                        resumedBytes = resumedBytes,
                        targetBytes = targetBytes,
                    )
                    if (!tempFile.renameTo(outputFile)) {
                        tempFile.copyTo(outputFile, overwrite = true)
                        tempFile.delete()
                    }
                    val bytes = outputFile.length().coerceAtLeast(0L)
                    repository.markReady(
                        localModelPath = outputFile.absolutePath,
                        downloadedBytes = bytes,
                        targetBytes = targetBytes.coerceAtLeast(bytes),
                        message = "本地模型已就绪",
                    )
                    OnDeviceModelDownloadResult(
                        path = outputFile.absolutePath,
                        bytes = bytes,
                    )
                }
            }.onFailure {
                val downloadUrl = OnDeviceModelSettings.normalizeDownloadUrl(settings.modelDownloadUrl)
                val partialBytes = File(resolveModelFile(downloadUrl).absolutePath + ".part")
                    .takeIf { file -> file.exists() }
                    ?.length()
                    ?.coerceAtLeast(0L)
                val message = toUserFacingMessage(it).let { base ->
                    if ((partialBytes ?: 0L) > 0L && !base.contains("可继续下载")) {
                        "$base，已保留下载进度，可继续下载"
                    } else {
                        base
                    }
                }
                repository.markError(
                    message = message,
                    downloadedBytes = partialBytes,
                    targetBytes = settings.downloadTargetBytes.takeIf { it > 0L },
                )
            }
        }

    suspend fun deleteModel(settings: OnDeviceModelSettings) {
        settings.localModelPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).delete() }
        }
        val downloadUrl = OnDeviceModelSettings.normalizeDownloadUrl(settings.modelDownloadUrl)
        runCatching { File(resolveModelFile(downloadUrl).absolutePath + ".part").delete() }
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

    private suspend fun writeResponseBody(
        body: okhttp3.ResponseBody,
        tempFile: File,
        resumedBytes: Long,
        targetBytes: Long,
    ) {
        body.byteStream().use { input ->
            FileOutputStream(tempFile, resumedBytes > 0L).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = resumedBytes
                var lastReportedBytes = resumedBytes
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    if (downloadedBytes - lastReportedBytes >= PROGRESS_PUBLISH_BYTES) {
                        output.flush()
                        lastReportedBytes = downloadedBytes
                        repository.markDownloadProgress(
                            downloadedBytes = downloadedBytes,
                            targetBytes = targetBytes,
                            message = buildProgressMessage(downloadedBytes, targetBytes),
                        )
                    }
                }
                output.flush()
                repository.markDownloadProgress(
                    downloadedBytes = downloadedBytes,
                    targetBytes = targetBytes,
                    message = buildProgressMessage(downloadedBytes, targetBytes),
                )
            }
        }
    }

    private fun resolveTargetBytes(
        response: Response,
        resumedBytes: Long,
        fallback: Long,
    ): Long {
        response.header("Content-Range")
            ?.substringAfter('/')
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let { return it }
        response.body?.contentLength()
            ?.takeIf { it > 0L }
            ?.let { contentLength ->
                return if (response.code == 206) resumedBytes + contentLength else contentLength
            }
        return fallback
    }

    private fun buildProgressMessage(downloadedBytes: Long, targetBytes: Long): String {
        val safeTarget = targetBytes.coerceAtLeast(downloadedBytes).coerceAtLeast(1L)
        val percentage = (downloadedBytes.toDouble() / safeTarget.toDouble() * 100.0)
            .coerceIn(0.0, 100.0)
        return "正在下载 ${String.format(Locale.US, "%.0f", percentage)}%"
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
        is NetworkOnMainThreadException -> "下载失败：本地模型下载线程异常，请更新到最新版本后重试"
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
