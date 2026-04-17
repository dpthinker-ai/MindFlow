package com.mindflow.app.data.topic

import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskType
import com.mindflow.app.data.model.MindFolderCatalog
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiFolderClassifier(
    private val aiTaskRouter: AiTaskRouter,
) {
    suspend fun classify(content: String): AiFolderResult = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(content)
        getCached(cacheKey)?.let { cachedFolder ->
            return@withContext AiFolderResult(folderKey = cachedFolder)
        }

        val result = runCatching {
            aiTaskRouter.run(
                AiTaskRequest(
                    type = AiTaskType.CLASSIFY_CATEGORY,
                    input = AiTaskInput.NoteText(content),
                    validate = { payload ->
                        val folder = payload as AiTaskPayload.Folder
                        normalize(folder.folderKey) != null
                    },
                ),
            )
        }.getOrNull() ?: return@withContext AiFolderResult()

        val normalized = normalize((result.payload as AiTaskPayload.Folder).folderKey)
        if (normalized != null) {
            putCached(cacheKey, normalized)
        }
        AiFolderResult(folderKey = normalized)
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
        folderCache[cacheKey] = CachedFolder(folderKey = folderKey, expiresAt = System.currentTimeMillis() + CACHE_TTL_MS)
    }

    private fun buildCacheKey(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.trim().toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class CachedFolder(val folderKey: String, val expiresAt: Long)

    private companion object {
        val folderCache = object : LinkedHashMap<String, CachedFolder>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedFolder>?): Boolean = size > 64
        }
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

data class AiFolderResult(
    val folderKey: String? = null,
    val notice: String? = null,
)
