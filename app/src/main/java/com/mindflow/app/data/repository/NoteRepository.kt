package com.mindflow.app.data.repository

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.CloudBackupSnapshot
import com.mindflow.app.data.model.ExportPayload
import com.mindflow.app.data.model.FolderRefreshResult
import com.mindflow.app.data.model.ImportResult
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.NoteStats
import com.mindflow.app.data.model.SearchFilters
import com.mindflow.app.data.model.TagRefreshResult
import com.mindflow.app.data.model.TopicRefreshResult
import com.mindflow.app.data.model.TopicSuggestion
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeFeed(): Flow<List<NoteEntity>>
    fun observeAllNotes(): Flow<List<NoteEntity>>
    fun observeSearchResults(filters: SearchFilters): Flow<List<NoteEntity>>
    fun observeStatusHistory(noteId: Long): Flow<List<NoteStatusHistoryEntity>>
    fun observeNoteStats(): Flow<NoteStats>
    fun observeSystemNotices(): Flow<String>

    suspend fun getNote(noteId: Long): NoteEntity?
    suspend fun createNote(
        content: String,
        topic: String = "",
        folderKey: String? = null,
        tags: List<String> = emptyList(),
        status: NoteStatus = NoteStatus.IDEA,
        horizon: NoteHorizon = NoteHorizon.MEDIUM,
        knowledgeTrust: KnowledgeTrust = KnowledgeTrust.NONE,
        isArchived: Boolean = false,
        folderManuallyEdited: Boolean = false,
        topicManuallyEdited: Boolean = false,
        tagsManuallyEdited: Boolean = false,
    ): Long
    suspend fun updateNote(
        noteId: Long,
        content: String,
        topic: String,
        folderKey: String?,
        tags: List<String>,
        status: NoteStatus,
        horizon: NoteHorizon,
        knowledgeTrust: KnowledgeTrust,
        isArchived: Boolean,
        folderManuallyEdited: Boolean,
        topicManuallyEdited: Boolean,
        tagsManuallyEdited: Boolean,
    )

    suspend fun setArchived(noteId: Long, archived: Boolean)
    suspend fun deleteNote(noteId: Long)
    suspend fun classifyPendingFolders(): Int
    suspend fun retriggerFolderClassification(noteId: Long): FolderRefreshResult
    suspend fun retriggerTopicExtraction(noteId: Long): TopicRefreshResult
    suspend fun retriggerTagExtraction(noteId: Long): TagRefreshResult
    suspend fun exportAllNotes(): ExportPayload
    suspend fun exportCloudBackupSnapshot(): CloudBackupSnapshot
    suspend fun importNotes(markdown: String): ImportResult
    suspend fun replaceAllNotes(markdown: String): ImportResult
}
