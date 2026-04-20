package com.mindflow.app.data.knowledgebrain

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.topic.AiChatResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalKnowledgeBrainPlannerTest {
    @Test
    fun ingestNote_generatesFragmentThreadAndDayDigest() = runTest {
        val repository = FakeMemoryLayerRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val planner = LocalKnowledgeBrainPlanner(
            memoryLayerRepository = repository,
            loadNoteById = { sampleNote(id = 7L, topic = "leakspace", content = "讨论 leakspace 对推荐链路是否有帮助") },
            loadAllNotes = {
                listOf(sampleNote(id = 7L, topic = "leakspace", content = "讨论 leakspace 对推荐链路是否有帮助"))
            },
            runOnDevice = {
                AiChatResult.Success(
                    "fragmentSummary=这条记录在追问 leakspace 是否能优化推荐\n" +
                        "topicKey=topic/leakspace\n" +
                        "questionKey=question/recommendation\n" +
                        "salience=0.86",
                )
            },
            applicationScope = scope,
            now = { 1710000000000L },
        )

        planner.ingestNote(noteId = 7L)

        assertThat(repository.fragments).hasSize(1)
        assertThat(repository.fragments.single().topicKey).isEqualTo("topic/leakspace")
        assertThat(repository.threads.single().title).contains("leakspace")
        assertThat(repository.digests.single().scopeType).isEqualTo(MemoryDigestScopeType.DAY)
        scope.cancel()
    }

    @Test
    fun rebuildAll_clearsExistingMemoryLayerThenReingestsNotes() = runTest {
        val repository = FakeMemoryLayerRepository().apply {
            fragments += MemoryFragment(
                id = "stale",
                sourceNoteIds = listOf(1L),
                topicKey = "topic/stale",
                questionKey = "",
                summary = "旧数据",
                salience = 0.3,
                timeSpanStart = 1L,
                timeSpanEnd = 1L,
                createdAt = 1L,
                updatedAt = 1L,
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val older = sampleNote(id = 11L, topic = "更早记录", content = "第一条")
        val newer = sampleNote(id = 12L, topic = "更新记录", content = "第二条").copy(updatedAt = older.updatedAt + 1000L)
        val planner = LocalKnowledgeBrainPlanner(
            memoryLayerRepository = repository,
            loadNoteById = { noteId -> listOf(older, newer).firstOrNull { it.id == noteId } },
            loadAllNotes = { listOf(newer, older) },
            runOnDevice = { prompt ->
                val summary = if (prompt.contains("更新记录")) "第二条摘要" else "第一条摘要"
                AiChatResult.Success(
                    "fragmentSummary=$summary\n" +
                        "topicKey=topic/rebuild\n" +
                        "questionKey=\n" +
                        "salience=0.7",
                )
            },
            applicationScope = scope,
            now = { 1710000000000L },
        )

        planner.rebuildAll()

        assertThat(repository.clearAllCalled).isTrue()
        assertThat(repository.fragments).hasSize(2)
        assertThat(repository.fragments.map { it.sourceNoteIds.single() }).containsExactly(11L, 12L).inOrder()
        scope.cancel()
    }

    private fun sampleNote(id: Long, topic: String, content: String): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
        folderSource = FolderSource.MANUAL,
        tags = listOf("推荐"),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L + id,
        updatedAt = 1_710_000_000_000L,
    )

    private class FakeMemoryLayerRepository : MemoryLayerRepository {
        val fragments = mutableListOf<MemoryFragment>()
        val threads = mutableListOf<MemoryThread>()
        val digests = mutableListOf<MemoryDigest>()
        var clearAllCalled: Boolean = false

        override suspend fun upsertFragment(fragment: MemoryFragment) {
            fragments.removeAll { it.id == fragment.id }
            fragments += fragment
        }

        override suspend fun upsertThread(thread: MemoryThread) {
            threads.removeAll { it.id == thread.id }
            threads += thread
        }

        override suspend fun upsertDigest(digest: MemoryDigest) {
            digests.removeAll { it.id == digest.id }
            digests += digest
        }

        override suspend fun loadDigest(scopeType: MemoryDigestScopeType, scopeKey: String): MemoryDigest? =
            digests.firstOrNull { it.scopeType == scopeType && it.scopeKey == scopeKey }

        override suspend fun loadThreadsForQuery(keywords: List<String>, limit: Int): List<MemoryThread> =
            threads.take(limit)

        override suspend fun loadFragmentsForNotes(noteIds: List<Long>): List<MemoryFragment> =
            fragments.filter { fragment -> fragment.sourceNoteIds.any(noteIds::contains) }

        override suspend fun clearAll() {
            clearAllCalled = true
            fragments.clear()
            threads.clear()
            digests.clear()
        }
    }
}
