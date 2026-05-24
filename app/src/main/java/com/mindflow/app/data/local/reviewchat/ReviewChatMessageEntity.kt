package com.mindflow.app.data.local.reviewchat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ReviewChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ReviewChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val provider: String?,
    val createdAt: Long,
    val skillWebViewUrl: String? = null,
    val skillWebViewIframe: Boolean = false,
    val skillWebViewAspectRatio: Float? = null,
    val answerTraceDisplayLine: String? = null,
    val answerTraceEmptyReason: String? = null,
)
