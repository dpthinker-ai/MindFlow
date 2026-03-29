package com.mindflow.app.data.topic

import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.settings.AiSettingsRepository
import java.security.MessageDigest
import java.time.LocalDate
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiFolderClassifier(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    suspend fun classify(content: String): AiFolderResult = withContext(Dispatchers.IO) {
        val settings = aiSettingsRepository.getCurrent()
        if (!settings.aiEnabled || !settings.isConfigured) {
            return@withContext AiFolderResult()
        }

        val cacheKey = buildCacheKey(settings.configFingerprint, content)
        getCached(cacheKey)?.let { cachedFolder ->
            return@withContext AiFolderResult(folderKey = cachedFolder)
        }

        val dayKey = LocalDate.now().toString()
        aiSettingsRepository.recordUsage(
            requestIncrement = 1,
            dayKey = dayKey,
        )

        return@withContext when (val result = aiServiceClient.classifyFolder(settings, content)) {
            is AiChatResult.Success -> {
                val normalized = normalize(result.content)
                if (normalized != null) {
                    putCached(cacheKey, normalized)
                }
                aiSettingsRepository.recordUsage(
                    successIncrement = if (normalized != null) 1 else 0,
                    tokenIncrement = result.totalTokens ?: 0,
                    dayKey = dayKey,
                )
                AiFolderResult(folderKey = normalized)
            }

            is AiChatResult.Failure -> AiFolderResult(
                notice = when (result.reason) {
                    AiFailureReason.RATE_LIMIT -> "AI 暂时限流，文件夹已改用本地规则"
                    else -> null
                },
            )
        }
    }

    private fun normalize(raw: String?): String? {
        val normalized = raw
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z]"), "")

        return MindFolderCatalog.normalizedKey(normalized)
    }

    @Synchronized
    private fun getCached(cacheKey: String): String? {
        val item = folderCache[cacheKey] ?: return null
        return if (item.expiresAt > System.currentTimeMillis()) {
            item.folderKey
        } else {
            folderCache.remove(cacheKey)
            null
        }
    }

    @Synchronized
    private fun putCached(cacheKey: String, folderKey: String) {
        folderCache[cacheKey] = CachedFolder(
            folderKey = folderKey,
            expiresAt = System.currentTimeMillis() + CACHE_TTL_MS,
        )
    }

    private fun buildCacheKey(fingerprint: String, content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$fingerprint|${content.trim()}".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class CachedFolder(
        val folderKey: String,
        val expiresAt: Long,
    )

    private companion object {
        val folderCache = object : LinkedHashMap<String, CachedFolder>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedFolder>?): Boolean =
                size > 64
        }
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

data class AiFolderResult(
    val folderKey: String? = null,
    val notice: String? = null,
)
