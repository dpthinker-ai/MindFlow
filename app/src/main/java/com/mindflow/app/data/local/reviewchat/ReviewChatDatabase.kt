package com.mindflow.app.data.local.reviewchat

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReviewChatSessionEntity::class, ReviewChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ReviewChatDatabase : RoomDatabase() {
    abstract fun reviewChatDao(): ReviewChatDao
}
