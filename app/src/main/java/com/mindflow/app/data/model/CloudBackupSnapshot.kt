package com.mindflow.app.data.model

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity

data class CloudBackupSnapshot(
    val notes: List<CloudBackupNoteSnapshot>,
)

data class CloudBackupNoteSnapshot(
    val note: NoteEntity,
    val history: List<NoteStatusHistoryEntity>,
)
