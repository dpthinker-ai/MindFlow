package com.mindflow.app.data.knowledgebrain

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.dao.MemoryLayerDao
import com.mindflow.app.data.local.entity.MemoryDigestEntity
import com.mindflow.app.data.local.entity.MemoryFragmentEntity
import com.mindflow.app.data.local.entity.MemoryThreadEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MemoryLayerRepositoryTest {
    @Test
    fun upsertFragmentAndThread_roundTripsThroughDaoContract() = runTest {
        val dao = FakeMemoryLayerDao()

        dao.upsertFragment(
            MemoryFragmentEntity(
                id = "fragment-1",
                sourceNoteIds = listOf(11L),
                topicKey = "topic/leakspace",
                questionKey = "question/optimization",
                summary = "记录提出 leakspace 对抖音推荐链路是否有指导意义。",
                salience = 0.82,
                timeSpanStart = 1710000000000,
                timeSpanEnd = 1710000000000,
                createdAt = 1710000000000,
                updatedAt = 1710000000000,
            ),
        )

        dao.upsertThread(
            MemoryThreadEntity(
                id = "thread-1",
                title = "leakspace 与推荐优化",
                type = "QUESTION",
                fragmentIds = listOf("fragment-1"),
                summary = "一条持续问题线，关注 leakspace 对推荐优化是否有实际指导。",
                currentState = "刚形成问题线",
                openQuestions = listOf("是否已在真实分发链里验证"),
                updatedAt = 1710000000100,
            ),
        )

        assertThat(dao.loadThread("thread-1")).isNotNull()
        assertThat(dao.loadThread("thread-1")!!.fragmentIds).containsExactly("fragment-1")
        assertThat(dao.loadFragment("fragment-1")).isNotNull()
        assertThat(dao.loadFragment("fragment-1")!!.sourceNoteIds).containsExactly(11L)
    }

    @Test
    fun repository_loadsDayDigestAndLatestThreads() = runTest {
        val repository = RoomMemoryLayerRepository(FakeMemoryLayerDao())

        repository.upsertFragment(
            MemoryFragment(
                id = "fragment-2",
                sourceNoteIds = listOf(21L, 22L),
                topicKey = "topic/douyin",
                questionKey = "question/recommendation",
                summary = "最近几条记录都在收敛到推荐链路的优化判断。",
                salience = 0.76,
                timeSpanStart = 1710100000000,
                timeSpanEnd = 1710186400000,
                createdAt = 1710186400000,
                updatedAt = 1710186400000,
            ),
        )
        repository.upsertThread(
            MemoryThread(
                id = "thread-2",
                title = "抖音推荐优化",
                type = MemoryThreadType.QUESTION,
                fragmentIds = listOf("fragment-2"),
                summary = "一条关注推荐链路优化空间的问题线。",
                currentState = "正在比对不同技术方案",
                openQuestions = listOf("是否值得进入实验"),
                updatedAt = 1710186401000,
            ),
        )
        repository.upsertDigest(
            MemoryDigest(
                id = "day-2026-04-19",
                scopeType = MemoryDigestScopeType.DAY,
                scopeKey = "2026-04-19",
                summary = "这一天主要在聊 leakspace 与推荐链路的关系。",
                highlights = listOf("讨论是否存在直接优化指导意义"),
                sourceFragmentIds = listOf("fragment-2"),
                updatedAt = 1710186402000,
            ),
        )

        assertThat(repository.loadDigest(MemoryDigestScopeType.DAY, "2026-04-19")?.summary)
            .contains("leakspace")
        assertThat(repository.loadThreadsForQuery(listOf("推荐"), limit = 3).size)
            .isEqualTo(1)
        assertThat(repository.loadFragmentsForNotes(listOf(22L)).size).isEqualTo(1)
    }

    private class FakeMemoryLayerDao : MemoryLayerDao {
        private val fragments = linkedMapOf<String, MemoryFragmentEntity>()
        private val threads = linkedMapOf<String, MemoryThreadEntity>()
        private val digests = linkedMapOf<String, MemoryDigestEntity>()

        override suspend fun upsertFragment(entity: MemoryFragmentEntity) {
            fragments[entity.id] = entity
        }

        override suspend fun upsertFragments(entities: List<MemoryFragmentEntity>) {
            entities.forEach { entity ->
                upsertFragment(entity)
            }
        }

        override suspend fun upsertThread(entity: MemoryThreadEntity) {
            threads[entity.id] = entity
        }

        override suspend fun upsertDigest(entity: MemoryDigestEntity) {
            digests[entity.id] = entity
        }

        override suspend fun loadFragment(id: String): MemoryFragmentEntity? = fragments[id]

        override suspend fun loadThread(id: String): MemoryThreadEntity? = threads[id]

        override suspend fun loadDigest(scopeType: String, scopeKey: String): MemoryDigestEntity? =
            digests.values.firstOrNull { it.scopeType == scopeType && it.scopeKey == scopeKey }

        override suspend fun loadLatestThreads(limit: Int): List<MemoryThreadEntity> =
            threads.values.sortedByDescending(MemoryThreadEntity::updatedAt).take(limit)

        override suspend fun loadAllFragments(): List<MemoryFragmentEntity> = fragments.values.toList()

        override suspend fun loadFragmentsByNoteIds(noteIds: List<Long>): List<MemoryFragmentEntity> =
            fragments.values.filter { entity -> entity.sourceNoteIds.any(noteIds::contains) }

        override suspend fun clearFragments() {
            fragments.clear()
        }

        override suspend fun clearThreads() {
            threads.clear()
        }

        override suspend fun clearDigests() {
            digests.clear()
        }
    }
}
