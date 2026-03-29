package com.mindflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mindflow.app.data.model.NoteStatus

@Entity(
    tableName = "note_status_history",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class NoteStatusHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val fromStatus: NoteStatus?,
    val toStatus: NoteStatus,
    val changedAt: Long,
)
