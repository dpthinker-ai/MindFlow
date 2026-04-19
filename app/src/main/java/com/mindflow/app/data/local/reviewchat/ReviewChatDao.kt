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

    @Query("SELECT * FROM review_chat_sessions ORDER BY updatedAt DESC, id DESC LIMIT 1")
    fun observeLatestSession(): Flow<ReviewChatSessionEntity?>

    @Query("SELECT * FROM review_chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: Long): ReviewChatSessionEntity?

    @Query("SELECT * FROM review_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(sessionId: Long): List<ReviewChatMessageEntity>
}
