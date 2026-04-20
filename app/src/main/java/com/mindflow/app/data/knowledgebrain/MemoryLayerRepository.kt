package com.mindflow.app.data.knowledgebrain

import com.mindflow.app.data.local.dao.MemoryLayerDao

interface MemoryLayerRepository {
    suspend fun upsertFragment(fragment: MemoryFragment)
    suspend fun upsertThread(thread: MemoryThread)
    suspend fun upsertDigest(digest: MemoryDigest)
    suspend fun loadDigest(scopeType: MemoryDigestScopeType, scopeKey: String): MemoryDigest?
    suspend fun loadThreadsForQuery(keywords: List<String>, limit: Int): List<MemoryThread>
    suspend fun loadFragmentsForNotes(noteIds: List<Long>): List<MemoryFragment>
    suspend fun clearAll()
}

class RoomMemoryLayerRepository(
    private val dao: MemoryLayerDao,
) : MemoryLayerRepository {
    override suspend fun upsertFragment(fragment: MemoryFragment) {
        dao.upsertFragment(fragment.toEntity())
    }

    override suspend fun upsertThread(thread: MemoryThread) {
        dao.upsertThread(thread.toEntity())
    }

    override suspend fun upsertDigest(digest: MemoryDigest) {
        dao.upsertDigest(digest.toEntity())
    }

    override suspend fun loadDigest(
        scopeType: MemoryDigestScopeType,
        scopeKey: String,
    ): MemoryDigest? = dao.loadDigest(scopeType.name, scopeKey)?.toModel()

    override suspend fun loadThreadsForQuery(
        keywords: List<String>,
        limit: Int,
    ): List<MemoryThread> {
        val normalizedKeywords = keywords.map(String::lowercase).filter(String::isNotBlank)
        val candidates = dao.loadLatestThreads(limit = limit.coerceAtLeast(1) * 4)
            .map { it.toModel() }
        if (normalizedKeywords.isEmpty()) return candidates.take(limit)
        return candidates
            .filter { thread ->
                val haystack = listOf(thread.title, thread.summary, thread.currentState)
                    .joinToString(" ")
                    .lowercase()
                normalizedKeywords.any(haystack::contains)
            }
            .take(limit)
    }

    override suspend fun loadFragmentsForNotes(noteIds: List<Long>): List<MemoryFragment> =
        dao.loadFragmentsByNoteIds(noteIds).map { it.toModel() }

    override suspend fun clearAll() {
        dao.clearDigests()
        dao.clearThreads()
        dao.clearFragments()
    }
}
