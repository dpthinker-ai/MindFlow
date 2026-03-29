package com.mindflow.app.data.topic

import com.mindflow.app.data.settings.AiSettingsRepository
import java.security.MessageDigest
import java.time.LocalDate
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiTopicExtractor(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    suspend fun extract(content: String): AiTopicResult = withContext(Dispatchers.IO) {
        val settings = aiSettingsRepository.getCurrent()
        if (!settings.aiEnabled || !settings.isConfigured) {
            return@withContext AiTopicResult()
        }

        val cacheKey = buildCacheKey(settings, content)
        getCached(cacheKey)?.let { cachedTopic ->
            return@withContext AiTopicResult(topic = cachedTopic)
        }

        aiSettingsRepository.recordUsage(
            requestIncrement = 1,
            dayKey = LocalDate.now().toString(),
        )
        return@withContext when (val result = aiServiceClient.extractTopic(settings, content)) {
            is AiChatResult.Success -> {
                val normalized = normalize(result.content)
                if (!normalized.isNullOrBlank()) {
                    putCached(cacheKey, normalized)
                }
                aiSettingsRepository.recordUsage(
                    successIncrement = 1,
                    tokenIncrement = result.totalTokens ?: 0,
                    dayKey = LocalDate.now().toString(),
                )
                AiTopicResult(
                    topic = normalized,
                )
            }
            is AiChatResult.Failure -> AiTopicResult(
                notice = when (result.reason) {
                    AiFailureReason.RATE_LIMIT -> "AI 暂时限流，已改用本地规则"
                    AiFailureReason.AUTH,
                    AiFailureReason.MODEL,
                    AiFailureReason.CONFIG,
                    AiFailureReason.NETWORK,
                    AiFailureReason.SERVER,
                    AiFailureReason.OTHER,
                    -> null
                },
            )
        }
    }

    @Synchronized
    private fun getCached(cacheKey: String): String? {
        val item = topicCache[cacheKey] ?: return null
        return if (item.expiresAt > System.currentTimeMillis()) {
            item.topic
        } else {
            topicCache.remove(cacheKey)
            null
        }
    }

    @Synchronized
    private fun putCached(cacheKey: String, topic: String) {
        topicCache[cacheKey] = CachedTopic(
            topic = topic,
            expiresAt = System.currentTimeMillis() + CACHE_TTL_MS,
        )
    }

    private fun buildCacheKey(
        settings: com.mindflow.app.data.model.AiSettings,
        content: String,
    ): String {
        val raw = listOf(
            settings.configFingerprint,
            content.trim(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun normalize(raw: String?): String? {
        val cleaned = raw
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { it.isNotBlank() }
            ?.trim('"', '\'', '#', '-', '*', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.take(24)
            ?.trim()

        return cleaned?.takeIf { it.isNotBlank() }
    }

    private data class CachedTopic(
        val topic: String,
        val expiresAt: Long,
    )

    private companion object {
        val topicCache = object : LinkedHashMap<String, CachedTopic>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTopic>?): Boolean =
                size > 64
        }
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

data class AiTopicResult(
    val topic: String? = null,
    val notice: String? = null,
)
