package com.mindflow.app.data.repository

import androidx.room.withTransaction
import com.mindflow.app.data.export.MarkdownExporter
import com.mindflow.app.data.importing.MarkdownImportParser
import com.mindflow.app.data.local.MindFlowDatabase
import com.mindflow.app.data.local.dao.NoteDao
import com.mindflow.app.data.local.dao.NoteStatusHistoryDao
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.CloudBackupNoteSnapshot
import com.mindflow.app.data.model.CloudBackupSnapshot
import com.mindflow.app.data.model.ExportPayload
import com.mindflow.app.data.model.FolderRefreshResult
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.ImportResult
import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.NoteStats
import com.mindflow.app.data.model.SearchFilters
import com.mindflow.app.data.model.TagRefreshResult
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicRefreshResult
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.topic.FolderClassifier
import com.mindflow.app.data.topic.TagExtractor
import com.mindflow.app.data.topic.TopicExtractor
import com.mindflow.app.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class OfflineFirstNoteRepository(
    private val database: MindFlowDatabase,
    private val noteDao: NoteDao,
    private val historyDao: NoteStatusHistoryDao,
    private val topicExtractor: TopicExtractor,
    private val folderClassifier: FolderClassifier,
    private val tagExtractor: TagExtractor,
    private val markdownExporter: MarkdownExporter,
    private val markdownImportParser: MarkdownImportParser,
    private val applicationScope: CoroutineScope,
) : NoteRepository {
    private val systemNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var lastSystemNotice: String = ""
    private var lastSystemNoticeAt: Long = 0L

    override fun observeFeed(): Flow<List<NoteEntity>> = noteDao.observeFeed()

    override fun observeAllNotes(): Flow<List<NoteEntity>> = noteDao.observeAllNotes()

    override fun observeSearchResults(filters: SearchFilters): Flow<List<NoteEntity>> =
        noteDao.observeSearch(NoteSearchQueryBuilder.build(filters))

    override fun observeStatusHistory(noteId: Long): Flow<List<NoteStatusHistoryEntity>> =
        historyDao.observeHistory(noteId)

    override fun observeNoteStats(): Flow<NoteStats> =
        noteDao.observeAllNotes().combine(historyDao.observeAllHistory()) { notes, history ->
            NoteStatsCalculator.calculate(notes, history)
        }

    override fun observeSystemNotices(): Flow<String> = systemNotices.asSharedFlow()

    override suspend fun getNote(noteId: Long): NoteEntity? = noteDao.getNoteById(noteId)

    override suspend fun createNote(
        content: String,
        topic: String,
        folderKey: String?,
        tags: List<String>,
        status: NoteStatus,
        isArchived: Boolean,
        folderManuallyEdited: Boolean,
        topicManuallyEdited: Boolean,
        tagsManuallyEdited: Boolean,
    ): Long {
        val normalizedContent = content.trim()
        val now = System.currentTimeMillis()
        val fallbackTopic = topicExtractor.extractRule(normalizedContent)
        val manualTopic = topic.trim()
        val ruleFolder = folderClassifier.classifyRule(normalizedContent)
        val fallbackFolder = if (folderManuallyEdited) {
            com.mindflow.app.data.model.FolderSuggestion(
                folderKey = folderKey?.trim()?.ifBlank { null },
                source = FolderSource.MANUAL,
            )
        } else {
            ruleFolder
        }
        val fallbackTags = tagExtractor.extractRule(normalizedContent)
        val normalizedManualTags = NoteTagCodec.normalize(tags)

        val noteId = database.withTransaction {
            val id = noteDao.insertNote(
                NoteEntity(
                    content = normalizedContent,
                    topic = if (topicManuallyEdited && manualTopic.isNotBlank()) manualTopic else fallbackTopic.topic,
                    topicSource = if (topicManuallyEdited && manualTopic.isNotBlank()) TopicSource.MANUAL else fallbackTopic.source,
                    folderKey = fallbackFolder.folderKey,
                    folderSource = fallbackFolder.source,
                    tags = if (tagsManuallyEdited) normalizedManualTags else fallbackTags.tags,
                    tagSource = if (tagsManuallyEdited) TagSource.MANUAL else fallbackTags.source,
                    status = status,
                    isArchived = isArchived,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            historyDao.insertEntry(
                NoteStatusHistoryEntity(
                    noteId = id,
                    fromStatus = null,
                    toStatus = status,
                    changedAt = now,
                )
            )
            id
        }

        applicationScope.launch {
            val topicResult = if (topicManuallyEdited) {
                TopicRefreshResult()
            } else {
                refreshTopicIfPossible(noteId, updateTimestamp = false)
            }
            val folderResult = if (folderManuallyEdited) {
                FolderRefreshResult()
            } else {
                refreshFolderIfPossible(noteId, updateTimestamp = false)
            }
            val tagResult = if (tagsManuallyEdited) {
                TagRefreshResult()
            } else {
                refreshTagsIfPossible(noteId, updateTimestamp = false)
            }
            topicResult.notice?.let(::emitSystemNotice)
            folderResult.notice?.let(::emitSystemNotice)
            tagResult.notice?.let(::emitSystemNotice)
        }

        return noteId
    }

    override suspend fun updateNote(
        noteId: Long,
        content: String,
        topic: String,
        folderKey: String?,
        tags: List<String>,
        status: NoteStatus,
        isArchived: Boolean,
        folderManuallyEdited: Boolean,
        topicManuallyEdited: Boolean,
        tagsManuallyEdited: Boolean,
    ) {
        val existing = noteDao.getNoteById(noteId) ?: return
        val normalizedContent = content.trim()
        val normalizedTopic = topic.trim().ifBlank { topicExtractor.extractRule(normalizedContent).topic }
        val normalizedFolderKey = folderKey?.trim()?.ifBlank { null }
        val normalizedTags = NoteTagCodec.normalize(tags)
        val now = System.currentTimeMillis()
        val contentChanged = normalizedContent != existing.content
        val nextSource = when {
            topicManuallyEdited -> TopicSource.MANUAL
            normalizedTopic != existing.topic -> TopicSource.MANUAL
            else -> existing.topicSource
        }
        val nextFolderSource = when {
            folderManuallyEdited -> FolderSource.MANUAL
            normalizedFolderKey != existing.folderKey -> FolderSource.MANUAL
            else -> existing.folderSource
        }
        val nextTagSource = when {
            tagsManuallyEdited -> TagSource.MANUAL
            normalizedTags != existing.tags -> TagSource.MANUAL
            else -> existing.tagSource
        }

        database.withTransaction {
            noteDao.updateNote(
                existing.copy(
                    content = normalizedContent,
                    topic = normalizedTopic,
                    topicSource = nextSource,
                    folderKey = normalizedFolderKey,
                    folderSource = nextFolderSource,
                    tags = normalizedTags,
                    tagSource = nextTagSource,
                    status = status,
                    isArchived = isArchived,
                    updatedAt = now,
                )
            )

            if (existing.status != status) {
                historyDao.insertEntry(
                    NoteStatusHistoryEntity(
                        noteId = noteId,
                        fromStatus = existing.status,
                        toStatus = status,
                        changedAt = now,
                    )
                )
            }
        }

        if (contentChanged && nextFolderSource != FolderSource.MANUAL) {
            applicationScope.launch {
                val result = refreshFolderIfPossible(noteId, updateTimestamp = false)
                result.notice?.let(::emitSystemNotice)
            }
        }
    }

    override suspend fun setArchived(noteId: Long, archived: Boolean) {
        val existing = noteDao.getNoteById(noteId) ?: return
        if (existing.isArchived == archived) return

        noteDao.updateNote(
            existing.copy(
                isArchived = archived,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteNote(noteId: Long) {
        noteDao.deleteById(noteId)
    }

    override suspend fun classifyPendingFolders(): Int {
        val pendingNotes = noteDao.getPendingFolderClassificationNotes()
        var classifiedCount = 0
        pendingNotes.forEach { note ->
            val result = refreshFolderIfPossible(
                noteId = note.id,
                force = true,
                updateTimestamp = false,
            )
            if (result.suggestion?.folderKey != null) {
                classifiedCount += 1
            }
            result.notice?.let(::emitSystemNotice)
        }
        return classifiedCount
    }

    override suspend fun retriggerFolderClassification(noteId: Long): FolderRefreshResult =
        refreshFolderIfPossible(noteId, force = true, updateTimestamp = true)

    override suspend fun retriggerTopicExtraction(noteId: Long): TopicRefreshResult =
        refreshTopicIfPossible(noteId, force = true, updateTimestamp = true)

    override suspend fun retriggerTagExtraction(noteId: Long): TagRefreshResult =
        refreshTagsIfPossible(noteId, force = true, updateTimestamp = true)

    override suspend fun exportAllNotes(): ExportPayload {
        val generatedAt = System.currentTimeMillis()
        val notes = noteDao.getAllNotesForExport()
        val history = historyDao.getAllHistory()
        val fileStamp = TimeFormatter.fileStamp(generatedAt)
        return ExportPayload(
            fileName = "mindflow-$fileStamp.md",
            content = markdownExporter.export(notes, history, generatedAt),
        )
    }

    override suspend fun exportCloudBackupSnapshot(): CloudBackupSnapshot {
        val notes = noteDao.getAllNotesForExport()
        val historyByNoteId = historyDao.getAllHistory().groupBy { it.noteId }
        return CloudBackupSnapshot(
            notes = notes.map { note ->
                CloudBackupNoteSnapshot(
                    note = note,
                    history = historyByNoteId[note.id].orEmpty(),
                )
            },
        )
    }

    override suspend fun importNotes(markdown: String): ImportResult {
        val parsedNotes = markdownImportParser.parse(markdown)
        return storeImportedNotes(parsedNotes, replaceExisting = false)
    }

    override suspend fun replaceAllNotes(markdown: String): ImportResult {
        val parsedNotes = markdownImportParser.parse(markdown)
        return storeImportedNotes(parsedNotes, replaceExisting = true)
    }

    private suspend fun refreshTopicIfPossible(
        noteId: Long,
        force: Boolean = false,
        updateTimestamp: Boolean,
    ): TopicRefreshResult {
        val existing = noteDao.getNoteById(noteId) ?: return TopicRefreshResult()
        if (!force && existing.topicSource == TopicSource.MANUAL) {
            return TopicRefreshResult()
        }

        val extraction = topicExtractor.extract(existing.content)
        val suggestion = extraction.suggestion
        val topicChanged = suggestion.topic != existing.topic || suggestion.source != existing.topicSource
        if (!topicChanged && !force) {
            return TopicRefreshResult(suggestion = suggestion, notice = extraction.notice)
        }

        noteDao.updateNote(
            existing.copy(
                topic = suggestion.topic,
                topicSource = suggestion.source,
                updatedAt = if (updateTimestamp) System.currentTimeMillis() else existing.updatedAt,
            )
        )
        return TopicRefreshResult(suggestion = suggestion, notice = extraction.notice)
    }

    private suspend fun refreshFolderIfPossible(
        noteId: Long,
        force: Boolean = false,
        updateTimestamp: Boolean,
    ): FolderRefreshResult {
        val existing = noteDao.getNoteById(noteId) ?: return FolderRefreshResult()
        if (!force && existing.folderSource == FolderSource.MANUAL) {
            return FolderRefreshResult()
        }

        val extraction = folderClassifier.classify(existing.content)
        val suggestion = extraction.suggestion
        val folderChanged =
            suggestion.folderKey != existing.folderKey || suggestion.source != existing.folderSource
        if (!folderChanged && !force) {
            return FolderRefreshResult(suggestion = suggestion, notice = extraction.notice)
        }

        noteDao.updateNote(
            existing.copy(
                folderKey = suggestion.folderKey,
                folderSource = suggestion.source,
                updatedAt = if (updateTimestamp) System.currentTimeMillis() else existing.updatedAt,
            )
        )
        return FolderRefreshResult(suggestion = suggestion, notice = extraction.notice)
    }

    private suspend fun refreshTagsIfPossible(
        noteId: Long,
        force: Boolean = false,
        updateTimestamp: Boolean,
    ): TagRefreshResult {
        val existing = noteDao.getNoteById(noteId) ?: return TagRefreshResult()
        if (!force && existing.tagSource == TagSource.MANUAL) {
            return TagRefreshResult()
        }

        val extraction = tagExtractor.extract(existing.content)
        val suggestion = extraction.suggestion
        val tagsChanged = suggestion.tags != existing.tags || suggestion.source != existing.tagSource
        if (!tagsChanged && !force) {
            return TagRefreshResult(suggestion = suggestion, notice = extraction.notice)
        }

        noteDao.updateNote(
            existing.copy(
                tags = suggestion.tags,
                tagSource = suggestion.source,
                updatedAt = if (updateTimestamp) System.currentTimeMillis() else existing.updatedAt,
            )
        )
        return TagRefreshResult(suggestion = suggestion, notice = extraction.notice)
    }

    private suspend fun storeImportedNotes(
        parsedNotes: List<com.mindflow.app.data.importing.ImportedNote>,
        replaceExisting: Boolean,
    ): ImportResult {
        var importedHistoryCount = 0

        database.withTransaction {
            if (replaceExisting) {
                historyDao.deleteAll()
                noteDao.deleteAll()
            }

            parsedNotes.forEach { importedNote ->
                val newNoteId = noteDao.insertNote(
                    NoteEntity(
                        content = importedNote.content,
                        topic = importedNote.topic.ifBlank { "未命名想法" },
                        topicSource = TopicSource.MANUAL,
                        folderKey = importedNote.folderKey,
                        folderSource = FolderSource.MANUAL,
                        tags = importedNote.tags,
                        tagSource = TagSource.MANUAL,
                        status = importedNote.status,
                        isArchived = importedNote.isArchived,
                        createdAt = importedNote.createdAt,
                        updatedAt = importedNote.updatedAt,
                    )
                )

                val mappedHistory = importedNote.history.map { entry ->
                    NoteStatusHistoryEntity(
                        noteId = newNoteId,
                        fromStatus = entry.fromStatus,
                        toStatus = entry.toStatus,
                        changedAt = entry.changedAt,
                    )
                }
                historyDao.insertEntries(mappedHistory)
                importedHistoryCount += mappedHistory.size
            }
        }

        return ImportResult(
            noteCount = parsedNotes.size,
            historyCount = importedHistoryCount,
        )
    }

    private fun emitSystemNotice(message: String) {
        val now = System.currentTimeMillis()
        if (message == lastSystemNotice && now - lastSystemNoticeAt < 20_000L) {
            return
        }
        lastSystemNotice = message
        lastSystemNoticeAt = now
        systemNotices.tryEmit(message)
    }
}
