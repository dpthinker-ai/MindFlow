package com.mindflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.mindflow.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun observeFeed(): Flow<List<NoteEntity>>

    @RawQuery(observedEntities = [NoteEntity::class])
    fun observeSearch(query: SupportSQLiteQuery): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: Long): NoteEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT * FROM notes ORDER BY createdAt ASC")
    suspend fun getAllNotesForExport(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND folderKey IS NULL AND folderSource != 'MANUAL' ORDER BY updatedAt DESC")
    suspend fun getPendingFolderClassificationNotes(): List<NoteEntity>
}
