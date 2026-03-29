package com.mindflow.app.data.backup

import com.mindflow.app.data.model.CloudBackupSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class WebDavBackupClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun uploadText(
        settings: CloudBackupSettings,
        relativePath: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        val authHeader = settings.authorizationHeader()
        val fileUrl = ensurePathAndBuildFileUrl(settings, authHeader, relativePath)
        val request = Request.Builder()
            .url(fileUrl)
            .header("Authorization", authHeader)
            .put(content.toRequestBody("text/markdown; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("上传失败：${response.code} ${response.safeMessage()}")
            }
        }
    }

    suspend fun downloadText(
        settings: CloudBackupSettings,
        relativePath: String,
    ): String = withContext(Dispatchers.IO) {
        val authHeader = settings.authorizationHeader()
        val fileUrl = buildPathUrl(settings, relativePath)
        val request = Request.Builder()
            .url(fileUrl)
            .header("Authorization", authHeader)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string().orEmpty()
                response.code == 404 -> throw IllegalStateException("云端还没有可恢复的备份")
                else -> throw IllegalStateException("下载失败：${response.code} ${response.safeMessage()}")
            }
        }
    }

    suspend fun listMarkdownFiles(
        settings: CloudBackupSettings,
        relativeDir: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val authHeader = settings.authorizationHeader()
        val dirUrl = buildPathUrl(settings, relativeDir)
        val request = Request.Builder()
            .url(dirUrl)
            .header("Authorization", authHeader)
            .header("Depth", "1")
            .method(
                "PROPFIND",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <propfind xmlns="DAV:">
                  <prop><displayname/></prop>
                </propfind>
                """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
            )
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> emptyList()
                response.code !in 200..299 && response.code != 207 -> {
                    throw IllegalStateException("读取云端目录失败：${response.code} ${response.safeMessage()}")
                }
                else -> {
                    val body = response.body?.string().orEmpty()
                    Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.IGNORE_CASE)
                        .findAll(body)
                        .mapNotNull { match ->
                            val href = URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8.name())
                            href.substringAfterLast('/').takeIf { it.endsWith(".md") }
                        }
                        .distinct()
                        .sorted()
                        .toList()
                }
            }
        }
    }

    suspend fun moveFile(
        settings: CloudBackupSettings,
        fromRelativePath: String,
        toRelativePath: String,
    ) = withContext(Dispatchers.IO) {
        val authHeader = settings.authorizationHeader()
        ensureDirectoryPath(settings, authHeader, toRelativePath.substringBeforeLast('/', ""))
        val sourceUrl = buildPathUrl(settings, fromRelativePath)
        val destinationUrl = buildPathUrl(settings, toRelativePath)
        val request = Request.Builder()
            .url(sourceUrl)
            .header("Authorization", authHeader)
            .header("Destination", destinationUrl.toString())
            .header("Overwrite", "T")
            .method("MOVE", ByteArray(0).toRequestBody(null))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext
            if (response.code !in setOf(200, 201, 204)) {
                throw IllegalStateException("移动云端文件失败：${response.code} ${response.safeMessage()}")
            }
        }
    }

    suspend fun deleteFile(
        settings: CloudBackupSettings,
        relativePath: String,
    ) = withContext(Dispatchers.IO) {
        val authHeader = settings.authorizationHeader()
        val fileUrl = buildPathUrl(settings, relativePath)
        val request = Request.Builder()
            .url(fileUrl)
            .header("Authorization", authHeader)
            .delete()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext
            if (response.code !in setOf(200, 202, 204)) {
                throw IllegalStateException("删除云端文件失败：${response.code} ${response.safeMessage()}")
            }
        }
    }

    private fun ensurePathAndBuildFileUrl(
        settings: CloudBackupSettings,
        authHeader: String,
        relativePath: String,
    ): HttpUrl {
        val pathSegments = relativePath
            .split('/')
            .filter { it.isNotBlank() }
        pathSegments.lastOrNull()
            ?: throw IllegalArgumentException("云端文件路径不能为空")
        val directoryPath = pathSegments.dropLast(1).joinToString("/")

        ensureDirectoryPath(settings, authHeader, directoryPath)
        return buildPathUrl(settings, relativePath)
    }

    private fun ensureDirectoryPath(
        settings: CloudBackupSettings,
        authHeader: String,
        relativeDir: String,
    ) {
        val rootUrl = settings.normalizedBaseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("WebDAV 地址格式不正确")

        var currentUrl = rootUrl
        val allSegments = buildFullSegments(settings, relativeDir)
        allSegments
            .split('/')
            .filter { it.isNotBlank() }
            .forEach { segment ->
                currentUrl = currentUrl.newBuilder()
                    .addPathSegment(segment)
                    .build()
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("Authorization", authHeader)
                    .method("MKCOL", ByteArray(0).toRequestBody(null))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code !in setOf(200, 201, 204, 301, 302, 405)) {
                        throw IllegalStateException("创建远端目录失败：${response.code} ${response.safeMessage()}")
                    }
                }
            }
    }

    private fun buildPathUrl(
        settings: CloudBackupSettings,
        relativePath: String,
    ): HttpUrl {
        val rootUrl = settings.normalizedBaseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("WebDAV 地址格式不正确")

        val builder = rootUrl.newBuilder()
        buildFullSegments(settings, relativePath)
            .split('/')
            .filter { it.isNotBlank() }
            .forEach(builder::addPathSegment)
        return builder.build()
    }

    private fun buildFullSegments(
        settings: CloudBackupSettings,
        relativePath: String,
    ): String = listOf(settings.normalizedRemoteDir, relativePath.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

    private fun CloudBackupSettings.authorizationHeader(): String {
        if (!isConfigured) {
            throw IllegalStateException("请先填好 WebDAV 地址、用户名和应用密码")
        }
        return Credentials.basic(username.trim(), password)
    }

    private fun okhttp3.Response.safeMessage(): String {
        val bodyText = body?.string()?.trim().orEmpty()
        return bodyText.ifBlank { message }.take(80).ifBlank { "请求未成功" }
    }
}
