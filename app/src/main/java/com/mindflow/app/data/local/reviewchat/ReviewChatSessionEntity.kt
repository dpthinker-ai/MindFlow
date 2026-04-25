package com.mindflow.app.data.local.reviewchat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_chat_sessions")
data class ReviewChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val latestExcerpt: String,
    val isArchived: Boolean = true,
    val draftContent: String = "",
)
