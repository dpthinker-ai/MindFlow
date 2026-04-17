package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskType
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiTopicExtractor(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun extract(content: String): AiTopicResult = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(content)
        getCached(cacheKey)?.let { cachedTopic ->
            return@withContext AiTopicResult(topic = cachedTopic)
        }

        val result = runCatching {
            aiTaskRouter.run(
                AiTaskRequest(
                    type = AiTaskType.EXTRACT_TOPIC,
                    input = AiTaskInput.NoteText(content),
                    validate = { payload ->
                        val topic = payload as AiTaskPayload.Topic
                        topic.topic.isNotBlank() && topic.topic !in setOf("记录", "想法", "学习", "随想")
                    },
                ),
            )
        }.getOrNull() ?: return@withContext AiTopicResult()

        val normalized = normalize((result.payload as AiTaskPayload.Topic).topic)
        if (!normalized.isNullOrBlank()) {
            putCached(cacheKey, normalized)
        }
        AiTopicResult(topic = normalized)
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
        topicCache[cacheKey] = CachedTopic(topic = topic, expiresAt = System.currentTimeMillis() + CACHE_TTL_MS)
    }

    private fun buildCacheKey(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.trim().toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

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

    private data class CachedTopic(val topic: String, val expiresAt: Long)

    private companion object {
        val topicCache = object : LinkedHashMap<String, CachedTopic>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTopic>?): Boolean = size > 64
        }
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

data class AiTopicResult(
    val topic: String? = null,
    val notice: String? = null,
)
