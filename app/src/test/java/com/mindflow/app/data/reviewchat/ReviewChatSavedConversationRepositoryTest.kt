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

    private class FakeReviewChatDao : ReviewChatDao {
        private var nextSessionId = 1L
        private var nextMessageId = 1L
        private val sessions = linkedMapOf<Long, ReviewChatSessionEntity>()
        private val messages = linkedMapOf<Long, MutableList<ReviewChatMessageEntity>>()
        private val latestSession = MutableStateFlow<ReviewChatSessionEntity?>(null)

        override suspend fun insertSession(entity: ReviewChatSessionEntity): Long {
            val id = nextSessionId++
            val stored = entity.copy(id = id)
            sessions[id] = stored
            latestSession.value = stored
            return id
        }

        override suspend fun insertMessages(entities: List<ReviewChatMessageEntity>) {
            entities.forEach { entity ->
                val stored = entity.copy(id = nextMessageId++)
                messages.getOrPut(stored.sessionId) { mutableListOf() }.add(stored)
            }
        }

        override fun observeLatestSession(): Flow<ReviewChatSessionEntity?> = latestSession

        override suspend fun getSession(sessionId: Long): ReviewChatSessionEntity? = sessions[sessionId]

        override suspend fun getMessages(sessionId: Long): List<ReviewChatMessageEntity> =
            messages[sessionId].orEmpty()
    }
}
