package com.mindflow.app.data.knowledgebrain

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MemoryLayerChatAssemblerTest {
    @Test
    fun assemble_questionAboutDay_prefersDayDigestThenRawNotes() = runTest {
        val assembler = MemoryLayerChatAssembler(
            memoryLayerRepository = FakeMemoryLayerRepository(
                digests = listOf(
                    MemoryDigest(
                        id = "day-2026-04-10",
                        scopeType = MemoryDigestScopeType.DAY,
                        scopeKey = "2026-04-10",
                        summary = "4 月 10 号主要在聊推荐链路和 leakspace 的关系。",
                        highlights = listOf("想确认这项技术有没有直接指导意义"),
                        sourceFragmentIds = listOf("fragment-1"),
                        updatedAt = 1L,
                    ),
                ),
                threads = listOf(
                    MemoryThread(
                        id = "thread-1",
                        title = "推荐链路优化",
                        type = MemoryThreadType.QUESTION,
                        fragmentIds = listOf("fragment-1"),
                        summary = "一条关注推荐链路优化空间的问题线。",
                        currentState = "刚开始形成判断",
                        openQuestions = emptyList(),
                        updatedAt = 2L,
                    ),
                ),
            ),
            loadNotes = {
                listOf(
                    sampleNote(
                        id = 42L,
                        topic = "4 月 10 号讨论",
                        content = "这是 4 月 10 号的完整原文，里面详细讨论了 leakspace 和推荐链路。",
                        updatedAt = dayTimestamp("2026-04-10"),
                    ),
                )
            },
        )

        val context = assembler.assemble(
            question = "把 4 月 10 号那天的完整内容发给我",
            priorMessages = emptyList(),
        )

        assertThat(context.memoryDigestSnippets.first()).contains("4 月 10")
        assertThat(context.rawNoteDetails.single().fullContent).contains("完整原文")
        assertThat(context.rawNoteDetails.single().noteId).isEqualTo(42L)
    }

    private fun sampleNote(id: Long, topic: String, content: String, updatedAt: Long): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
        folderSource = FolderSource.MANUAL,
        tags = listOf("推荐", "leakspace"),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun dayTimestamp(date: String): Long =
        LocalDate.parse(date)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private class FakeMemoryLayerRepository(
        private val digests: List<MemoryDigest> = emptyList(),
        private val threads: List<MemoryThread> = emptyList(),
    ) : MemoryLayerRepository {
        override suspend fun upsertFragment(fragment: MemoryFragment) = Unit
        override suspend fun upsertThread(thread: MemoryThread) = Unit
        override suspend fun upsertDigest(digest: MemoryDigest) = Unit
        override suspend fun loadDigest(scopeType: MemoryDigestScopeType, scopeKey: String): MemoryDigest? =
            digests.firstOrNull { it.scopeType == scopeType && it.scopeKey == scopeKey }

        override suspend fun loadThreadsForQuery(keywords: List<String>, limit: Int): List<MemoryThread> =
            threads.take(limit)

        override suspend fun loadFragmentsForNotes(noteIds: List<Long>): List<MemoryFragment> = emptyList()
        override suspend fun clearAll() = Unit
    }
}
