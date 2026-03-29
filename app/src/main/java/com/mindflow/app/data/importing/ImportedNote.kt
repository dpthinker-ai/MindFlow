package com.mindflow.app.data.importing

import com.mindflow.app.data.model.NoteStatus

data class ImportedNote(
    val topic: String,
    val folderKey: String? = null,
    val tags: List<String>,
    val content: String,
    val status: NoteStatus,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val history: List<ImportedStatusHistory>,
)

data class ImportedStatusHistory(
    val fromStatus: NoteStatus?,
    val toStatus: NoteStatus,
    val changedAt: Long,
)
