package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.local.reviewchat.ReviewChatDao
import com.mindflow.app.data.local.reviewchat.ReviewChatMessageEntity
import com.mindflow.app.data.local.reviewchat.ReviewChatSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SavedReviewChatSession(
    val sessionId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ReviewChatMessage>,
)

data class SavedReviewChatSessionSummary(
    val sessionId: Long,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val latestExcerpt: String,
)

interface ReviewChatSavedConversationRepository {
    suspend fun saveSession(
        title: String,
        messages: List<ReviewChatMessage>,
    ): Long

    suspend fun getSession(sessionId: Long): SavedReviewChatSession?

    fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?>
}

class RoomReviewChatSavedConversationRepository(
    private val dao: ReviewChatDao,
) : ReviewChatSavedConversationRepository {
    override suspend fun saveSession(
        title: String,
        messages: List<ReviewChatMessage>,
    ): Long {
        val createdAt = messages.firstOrNull()?.createdAt ?: System.currentTimeMillis()
        val updatedAt = messages.lastOrNull()?.createdAt ?: createdAt
        val latestExcerpt = messages.lastOrNull()?.content?.take(120).orEmpty()
        val sessionId = dao.insertSession(
            ReviewChatSessionEntity(
                title = title,
                createdAt = createdAt,
                updatedAt = updatedAt,
                messageCount = messages.size,
                latestExcerpt = latestExcerpt,
            ),
        )
        dao.insertMessages(
            messages.map { message ->
                ReviewChatMessageEntity(
                    sessionId = sessionId,
                    role = message.role.name,
                    content = message.content,
                    provider = message.provider?.name,
                    createdAt = message.createdAt,
                )
            },
        )
        return sessionId
    }

    override suspend fun getSession(sessionId: Long): SavedReviewChatSession? {
        val session = dao.getSession(sessionId) ?: return null
        val messages = dao.getMessages(sessionId).map { entity ->
            ReviewChatMessage(
                role = ReviewChatMessageRole.valueOf(entity.role),
                content = entity.content,
                provider = entity.provider?.let(ReviewChatProvider::valueOf),
                createdAt = entity.createdAt,
            )
        }
        return SavedReviewChatSession(
            sessionId = session.id,
            title = session.title,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            messages = messages,
        )
    }

    override fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?> =
        dao.observeLatestSession().map { entity ->
            entity?.let {
                SavedReviewChatSessionSummary(
                    sessionId = it.id,
                    title = it.title,
                    updatedAt = it.updatedAt,
                    messageCount = it.messageCount,
                    latestExcerpt = it.latestExcerpt,
                )
            }
        }
}
