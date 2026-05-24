package com.mindflow.app.data.local.reviewchat

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReviewChatSessionEntity::class, ReviewChatMessageEntity::class],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE review_chat_messages ADD COLUMN skillWebViewUrl TEXT")
                db.execSQL("ALTER TABLE review_chat_messages ADD COLUMN skillWebViewIframe INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE review_chat_messages ADD COLUMN skillWebViewAspectRatio REAL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE review_chat_messages ADD COLUMN answerTraceDisplayLine TEXT")
                db.execSQL("ALTER TABLE review_chat_messages ADD COLUMN answerTraceEmptyReason TEXT")
            }
        }
    }
}
