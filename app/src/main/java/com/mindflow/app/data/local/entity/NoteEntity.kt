package com.mindflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val topic: String,
    val topicSource: TopicSource,
    val folderKey: String? = null,
    val folderSource: FolderSource = FolderSource.RULE,
    val tags: List<String> = emptyList(),
    val tagSource: TagSource = TagSource.RULE,
    val status: NoteStatus,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
