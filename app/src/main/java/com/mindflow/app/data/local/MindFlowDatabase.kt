package com.mindflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mindflow.app.data.local.dao.MemoryLayerDao
import com.mindflow.app.data.local.dao.NoteDao
import com.mindflow.app.data.local.dao.NoteStatusHistoryDao
import com.mindflow.app.data.local.entity.MemoryDigestEntity
import com.mindflow.app.data.local.entity.MemoryFragmentEntity
import com.mindflow.app.data.local.entity.MemoryThreadEntity
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity

@Database(
    entities = [
        NoteEntity::class,
        NoteStatusHistoryEntity::class,
        MemoryFragmentEntity::class,
        MemoryThreadEntity::class,
        MemoryDigestEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(MindFlowConverters::class)
abstract class MindFlowDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteStatusHistoryDao(): NoteStatusHistoryDao
    abstract fun memoryLayerDao(): MemoryLayerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN tagSource TEXT NOT NULL DEFAULT 'RULE'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN folderKey TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN folderSource TEXT NOT NULL DEFAULT 'RULE'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE notes SET folderKey = 'health' WHERE folderKey = 'fitness'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN horizon TEXT NOT NULL DEFAULT 'MEDIUM'")
                db.execSQL(
                    """
                    UPDATE notes
                    SET horizon = CASE
                        WHEN status = 'IN_PROGRESS' THEN 'SHORT'
                        WHEN status = 'DONE' OR isArchived = 1 THEN 'LONG'
                        ELSE 'MEDIUM'
                    END
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN knowledgeTrust TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_fragments (
                        id TEXT NOT NULL PRIMARY KEY,
                        sourceNoteIds TEXT NOT NULL,
                        topicKey TEXT NOT NULL,
                        questionKey TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        salience REAL NOT NULL,
                        timeSpanStart INTEGER NOT NULL,
                        timeSpanEnd INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_threads (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        fragmentIds TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        currentState TEXT NOT NULL,
                        openQuestions TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_digests (
                        id TEXT NOT NULL PRIMARY KEY,
                        scopeType TEXT NOT NULL,
                        scopeKey TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        highlights TEXT NOT NULL,
                        sourceFragmentIds TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN aiSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN aiKeyPoints TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN aiInsightContentHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN aiInsightUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
