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

class AiTagExtractor(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun extract(content: String): AiTagResult = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(content)
        getCached(cacheKey)?.let { cachedTags ->
            return@withContext AiTagResult(tags = cachedTags)
        }

        val result = runCatching {
            aiTaskRouter.run(
                AiTaskRequest(
                    type = AiTaskType.EXTRACT_TAGS,
                    input = AiTaskInput.NoteText(content),
                    validate = { payload ->
                        val tags = payload as AiTaskPayload.Tags
                        tags.tags.isNotEmpty()
                    },
                ),
            )
        }.getOrNull() ?: return@withContext AiTagResult()

        val parsed = (result.payload as AiTaskPayload.Tags).tags.distinct()
        if (parsed.isNotEmpty()) {
            putCached(cacheKey, parsed)
        }
        AiTagResult(tags = parsed)
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
        tagCache[cacheKey] = CachedTags(tags = tags, expiresAt = System.currentTimeMillis() + CACHE_TTL_MS)
    }

    private fun buildCacheKey(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.trim().toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class CachedTags(val tags: List<String>, val expiresAt: Long)

    private companion object {
        val tagCache = object : LinkedHashMap<String, CachedTags>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTags>?): Boolean = size > 64
        }
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

data class AiTagResult(
    val tags: List<String> = emptyList(),
    val notice: String? = null,
)
