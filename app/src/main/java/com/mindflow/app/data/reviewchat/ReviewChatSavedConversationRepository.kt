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
    val draft: String = "",
    val isArchived: Boolean = true,
)

data class SavedReviewChatSessionSummary(
    val sessionId: Long,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val latestExcerpt: String,
    val isArchived: Boolean = true,
)

interface ReviewChatSavedConversationRepository {
    suspend fun saveSession(
        sessionId: Long? = null,
        title: String,
        messages: List<ReviewChatMessage>,
    ): Long

    suspend fun cacheWorkingSession(
        sessionId: Long?,
        title: String,
        messages: List<ReviewChatMessage>,
        draft: String,
    ): Long

    suspend fun getLatestWorkingSession(): SavedReviewChatSession?

    suspend fun getSession(sessionId: Long): SavedReviewChatSession?

    fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?>

    fun observeSavedSessionSummaries(): Flow<List<SavedReviewChatSessionSummary>>

    suspend fun deleteSessions(sessionIds: List<Long>)
}

class RoomReviewChatSavedConversationRepository(
    private val dao: ReviewChatDao,
) : ReviewChatSavedConversationRepository {
    override suspend fun saveSession(
        sessionId: Long?,
        title: String,
        messages: List<ReviewChatMessage>,
    ): Long = upsertSession(
        sessionId = sessionId,
        title = title,
        messages = messages,
        draft = "",
        isArchived = true,
    )

    override suspend fun cacheWorkingSession(
        sessionId: Long?,
        title: String,
        messages: List<ReviewChatMessage>,
        draft: String,
    ): Long = upsertSession(
        sessionId = sessionId,
        title = title,
        messages = messages,
        draft = draft,
        isArchived = false,
    )

    override suspend fun getLatestWorkingSession(): SavedReviewChatSession? {
        val session = dao.getLatestWorkingSession() ?: return null
        return session.toSavedSession()
    }

    private suspend fun upsertSession(
        sessionId: Long?,
        title: String,
        messages: List<ReviewChatMessage>,
        draft: String,
        isArchived: Boolean,
    ): Long {
        val now = System.currentTimeMillis()
        val createdAt = messages.firstOrNull()?.createdAt ?: System.currentTimeMillis()
        val updatedAt = listOfNotNull(messages.lastOrNull()?.createdAt, now).max()
        val latestExcerpt = messages.lastOrNull()?.content?.take(120)
            ?: draft.take(120)
        val resolvedSessionId = sessionId?.takeIf { dao.getSession(it) != null }
        if (resolvedSessionId != null) {
            dao.updateSession(
                sessionId = resolvedSessionId,
                title = title,
                updatedAt = updatedAt,
                messageCount = messages.size,
                latestExcerpt = latestExcerpt,
                isArchived = isArchived,
                draftContent = draft,
            )
            dao.deleteMessages(resolvedSessionId)
            insertMessages(resolvedSessionId, messages)
            return resolvedSessionId
        }
        val insertedSessionId = dao.insertSession(
            ReviewChatSessionEntity(
                title = title,
                createdAt = createdAt,
                updatedAt = updatedAt,
                messageCount = messages.size,
                latestExcerpt = latestExcerpt,
                isArchived = isArchived,
                draftContent = draft,
            ),
        )
        insertMessages(insertedSessionId, messages)
        return insertedSessionId
    }

    override suspend fun getSession(sessionId: Long): SavedReviewChatSession? {
        val session = dao.getSession(sessionId) ?: return null
        return session.toSavedSession()
    }

    private suspend fun ReviewChatSessionEntity.toSavedSession(): SavedReviewChatSession {
        val messages = dao.getMessages(id).map { entity ->
            val role = ReviewChatMessageRole.valueOf(entity.role)
            ReviewChatMessage(
                role = role,
                content = entity.content,
                structuredAnswer = if (role == ReviewChatMessageRole.ASSISTANT) {
                    parseReviewChatStructuredAnswer(entity.content)
                } else {
                    null
                },
                provider = entity.provider?.let(ReviewChatProvider::valueOf),
                createdAt = entity.createdAt,
            )
        }
        return SavedReviewChatSession(
            sessionId = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            messages = messages,
            draft = draftContent,
            isArchived = isArchived,
        )
    }

    override fun observeLatestSavedSessionSummary(): Flow<SavedReviewChatSessionSummary?> =
        dao.observeLatestSession().map { entity ->
            entity?.toSummary()
        }

    override fun observeSavedSessionSummaries(): Flow<List<SavedReviewChatSessionSummary>> =
        dao.observeSavedSessions().map { sessions ->
            sessions.map { it.toSummary() }
        }

    override suspend fun deleteSessions(sessionIds: List<Long>) {
        val resolvedIds = sessionIds.distinct()
        if (resolvedIds.isEmpty()) return
        dao.deleteSessions(resolvedIds)
    }

    private fun ReviewChatSessionEntity.toSummary(): SavedReviewChatSessionSummary =
        SavedReviewChatSessionSummary(
            sessionId = id,
            title = title,
            updatedAt = updatedAt,
            messageCount = messageCount,
            latestExcerpt = latestExcerpt,
            isArchived = isArchived,
        )

    private suspend fun insertMessages(
        sessionId: Long,
        messages: List<ReviewChatMessage>,
    ) {
        if (messages.isEmpty()) return
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
    }
}
