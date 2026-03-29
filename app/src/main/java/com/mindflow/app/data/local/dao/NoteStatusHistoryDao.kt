package com.mindflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteStatusHistoryDao {
    @Insert
    suspend fun insertEntry(entry: NoteStatusHistoryEntity)

    @Insert
    suspend fun insertEntries(entries: List<NoteStatusHistoryEntity>)

    @Query("SELECT * FROM note_status_history WHERE noteId = :noteId ORDER BY changedAt DESC")
    fun observeHistory(noteId: Long): Flow<List<NoteStatusHistoryEntity>>

    @Query("SELECT * FROM note_status_history")
    suspend fun getAllHistory(): List<NoteStatusHistoryEntity>

    @Query("SELECT * FROM note_status_history")
    fun observeAllHistory(): Flow<List<NoteStatusHistoryEntity>>

    @Query("DELETE FROM note_status_history")
    suspend fun deleteAll()
}
