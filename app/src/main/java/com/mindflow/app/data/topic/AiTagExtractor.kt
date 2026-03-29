package com.mindflow.app.data.topic

import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.settings.AiSettingsRepository
import java.security.MessageDigest
import java.time.LocalDate
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiTagExtractor(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    suspend fun extract(content: String): AiTagResult = withContext(Dispatchers.IO) {
        val settings = aiSettingsRepository.getCurrent()
        if (!settings.aiEnabled || !settings.isConfigured) {
            return@withContext AiTagResult()
        }

        val cacheKey = buildCacheKey(settings.configFingerprint, content)
        getCached(cacheKey)?.let { cachedTags ->
            return@withContext AiTagResult(tags = cachedTags)
        }

        aiSettingsRepository.recordUsage(
            requestIncrement = 1,
            dayKey = LocalDate.now().toString(),
        )

        when (val result = aiServiceClient.extractTags(settings, content)) {
            is AiChatResult.Success -> {
                val parsed = NoteTagCodec.parseAiOutput(result.content)
                if (parsed.isNotEmpty()) {
                    putCached(cacheKey, parsed)
                }
                aiSettingsRepository.recordUsage(
                    successIncrement = if (parsed.isNotEmpty()) 1 else 0,
                    tokenIncrement = result.totalTokens ?: 0,
                    dayKey = LocalDate.now().toString(),
                )
                AiTagResult(tags = parsed)
            }
            is AiChatResult.Failure -> AiTagResult(
                notice = when (result.reason) {
                    AiFailureReason.RATE_LIMIT -> "AI 暂时限流，标签已改用本地规则"
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
    private fun getCached(cacheKey: String): List<String>? {
        val item = tagCache[cacheKey] ?: return null
        return if (item.expiresAt > System.currentTimeMillis()) {
            item.tags
        } else {
            tagCache.remove(cacheKey)
            null
        }
    }

    @Synchronized
    private fun putCached(cacheKey: String, tags: List<String>) {
        tagCache[cacheKey] = CachedTags(
            tags = tags,
            expiresAt = System.currentTimeMillis() + CACHE_TTL_MS,
        )
    }

    private fun buildCacheKey(fingerprint: String, content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$fingerprint|${content.trim()}".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class CachedTags(
        val tags: List<String>,
        val expiresAt: Long,
    )

    private companion object {
        val tagCache = object : LinkedHashMap<String, CachedTags>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTags>?): Boolean =
                size > 64
        }
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

data class AiTagResult(
    val tags: List<String> = emptyList(),
    val notice: String? = null,
)
