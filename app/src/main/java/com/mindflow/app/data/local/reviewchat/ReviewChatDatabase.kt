package com.mindflow.app.data.local.reviewchat

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReviewChatSessionEntity::class, ReviewChatMessageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ReviewChatDatabase : RoomDatabase() {
    abstract fun reviewChatDao(): ReviewChatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE review_chat_sessions ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE review_chat_sessions ADD COLUMN draftContent TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
