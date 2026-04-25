package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.reviewchat.ReviewChatDao
import com.mindflow.app.data.local.reviewchat.ReviewChatMessageEntity
import com.mindflow.app.data.local.reviewchat.ReviewChatSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReviewChatSavedConversationRepositoryTest {
    @Test
    fun saveSession_persistsMessagesAndLatestSummary() = runTest {
        val repository = RoomReviewChatSavedConversationRepository(FakeReviewChatDao())

        val sessionId = repository.saveSession(
            title = "产品方向的矛盾",
            messages = listOf(
                ReviewChatMessage(
                    role = ReviewChatMessageRole.USER,
                    content = "最近最大的矛盾是什么",
                    createdAt = 1_000L,
                ),
                ReviewChatMessage(
                    role = ReviewChatMessageRole.ASSISTANT,
                    content = "你在增长和定位之间反复摇摆",
                    provider = ReviewChatProvider.CLOUD,
                    createdAt = 1_100L,
                ),
            ),
        )

        val saved = repository.getSession(sessionId)
        val latest = repository.observeLatestSavedSessionSummary().first()

        assertThat(saved?.messages).hasSize(2)
        assertThat(saved?.messages?.last()?.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(latest?.sessionId).isEqualTo(sessionId)
        assertThat(latest?.title).isEqualTo("产品方向的矛盾")
    }

    @Test
    fun cacheWorkingSession_restoresEditableDraftWithoutBecomingLatestSaved() = runTest {
        val repository = RoomReviewChatSavedConversationRepository(FakeReviewChatDao())

        val sessionId = repository.cacheWorkingSession(
            sessionId = null,
            title = "临时聊天",
            messages = emptyList(),
            draft = "还没发出去",
        )

        val working = repository.getLatestWorkingSession()
        val latestSaved = repository.observeLatestSavedSessionSummary().first()

        assertThat(working?.sessionId).isEqualTo(sessionId)
        assertThat(working?.draft).isEqualTo("还没发出去")
        assertThat(working?.isArchived).isFalse()
        assertThat(latestSaved).isNull()
    }

    @Test
    fun deleteSessions_removesSingleAndBatchSavedSessions() = runTest {
        val repository = RoomReviewChatSavedConversationRepository(FakeReviewChatDao())
        val firstId = repository.saveSession(
            title = "第一段",
            messages = listOf(
                ReviewChatMessage(
                    role = ReviewChatMessageRole.USER,
                    content = "第一段问题",
                    createdAt = 1_000L,
                )
            ),
        )
        val secondId = repository.saveSession(
            title = "第二段",
            messages = listOf(
                ReviewChatMessage(
                    role = ReviewChatMessageRole.USER,
                    content = "第二段问题",
                    createdAt = 2_000L,
                )
            ),
        )

        assertThat(repository.observeSavedSessionSummaries().first().map { it.sessionId })
            .containsExactly(secondId, firstId)

        repository.deleteSessions(listOf(firstId))
        assertThat(repository.observeSavedSessionSummaries().first().map { it.sessionId })
            .containsExactly(secondId)

        repository.deleteSessions(listOf(secondId))
        assertThat(repository.observeSavedSessionSummaries().first()).isEmpty()
        assertThat(repository.getSession(firstId)).isNull()
        assertThat(repository.getSession(secondId)).isNull()
    }

    private class FakeReviewChatDao : ReviewChatDao {
        private var nextSessionId = 1L
        private var nextMessageId = 1L
        private val sessions = linkedMapOf<Long, ReviewChatSessionEntity>()
        private val messages = linkedMapOf<Long, MutableList<ReviewChatMessageEntity>>()
        private val latestSession = MutableStateFlow<ReviewChatSessionEntity?>(null)
        private val savedSessions = MutableStateFlow<List<ReviewChatSessionEntity>>(emptyList())

        override suspend fun insertSession(entity: ReviewChatSessionEntity): Long {
            val id = nextSessionId++
            val stored = entity.copy(id = id)
            sessions[id] = stored
            updateLatestArchivedSession()
            return id
        }

        override suspend fun insertMessages(entities: List<ReviewChatMessageEntity>) {
            entities.forEach { entity ->
                val stored = entity.copy(id = nextMessageId++)
                messages.getOrPut(stored.sessionId) { mutableListOf() }.add(stored)
            }
        }

        override fun observeLatestSession(): Flow<ReviewChatSessionEntity?> = latestSession

        override fun observeSavedSessions(): Flow<List<ReviewChatSessionEntity>> = savedSessions

        override suspend fun getLatestWorkingSession(): ReviewChatSessionEntity? =
            sessions.values
                .filter { !it.isArchived }
                .maxWithOrNull(compareBy<ReviewChatSessionEntity> { it.updatedAt }.thenBy { it.id })

        override suspend fun getSession(sessionId: Long): ReviewChatSessionEntity? = sessions[sessionId]

        override suspend fun getMessages(sessionId: Long): List<ReviewChatMessageEntity> =
            messages[sessionId].orEmpty()

        override suspend fun updateSession(
            sessionId: Long,
            title: String,
            updatedAt: Long,
            messageCount: Int,
            latestExcerpt: String,
            isArchived: Boolean,
            draftContent: String,
        ) {
            val existing = sessions.getValue(sessionId)
            sessions[sessionId] = existing.copy(
                title = title,
                updatedAt = updatedAt,
                messageCount = messageCount,
                latestExcerpt = latestExcerpt,
                isArchived = isArchived,
                draftContent = draftContent,
            )
            updateLatestArchivedSession()
        }

        override suspend fun deleteMessages(sessionId: Long) {
            messages.remove(sessionId)
        }

        override suspend fun deleteSessions(sessionIds: List<Long>) {
            sessionIds.forEach { sessionId ->
                sessions.remove(sessionId)
                messages.remove(sessionId)
            }
            updateLatestArchivedSession()
        }

        private fun updateLatestArchivedSession() {
            val archivedSessions = sessions.values
                .filter { it.isArchived }
                .sortedWith(compareByDescending<ReviewChatSessionEntity> { it.updatedAt }.thenByDescending { it.id })
            savedSessions.value = archivedSessions
            latestSession.value = archivedSessions
                .maxWithOrNull(compareBy<ReviewChatSessionEntity> { it.updatedAt }.thenBy { it.id })
        }
    }
}
