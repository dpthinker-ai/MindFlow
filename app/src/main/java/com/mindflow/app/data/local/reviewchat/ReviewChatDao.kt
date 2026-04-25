package com.mindflow.app.data.local.reviewchat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewChatDao {
    @Insert
    suspend fun insertSession(entity: ReviewChatSessionEntity): Long

    @Insert
    suspend fun insertMessages(entities: List<ReviewChatMessageEntity>)

    @Query("SELECT * FROM review_chat_sessions WHERE isArchived = 1 ORDER BY updatedAt DESC, id DESC LIMIT 1")
    fun observeLatestSession(): Flow<ReviewChatSessionEntity?>

    @Query("SELECT * FROM review_chat_sessions WHERE isArchived = 1 ORDER BY updatedAt DESC, id DESC")
    fun observeSavedSessions(): Flow<List<ReviewChatSessionEntity>>

    @Query("SELECT * FROM review_chat_sessions WHERE isArchived = 0 ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getLatestWorkingSession(): ReviewChatSessionEntity?

    @Query("SELECT * FROM review_chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: Long): ReviewChatSessionEntity?

    @Query("SELECT * FROM review_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(sessionId: Long): List<ReviewChatMessageEntity>

    @Query(
        """
        UPDATE review_chat_sessions
        SET title = :title,
            updatedAt = :updatedAt,
            messageCount = :messageCount,
            latestExcerpt = :latestExcerpt,
            isArchived = :isArchived,
            draftContent = :draftContent
        WHERE id = :sessionId
        """
    )
    suspend fun updateSession(
        sessionId: Long,
        title: String,
        updatedAt: Long,
        messageCount: Int,
        latestExcerpt: String,
        isArchived: Boolean,
        draftContent: String,
    )

    @Query("DELETE FROM review_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: Long)

    @Query("DELETE FROM review_chat_sessions WHERE id IN (:sessionIds)")
    suspend fun deleteSessions(sessionIds: List<Long>)
}
