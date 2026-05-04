package com.mindflow.app.data.repository

import android.content.Context
import com.mindflow.app.QuickCaptureWidgetProvider
import com.mindflow.app.data.ai.AiAutomaticPreference
import com.mindflow.app.data.backup.CloudNoteDocumentCodec
import com.mindflow.app.data.importing.ImportedNote
import com.mindflow.app.data.importing.ImportedStatusHistory
import com.mindflow.app.data.importing.MarkdownImportParser
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.export.MarkdownExporter
import com.mindflow.app.data.model.CloudBackupNoteSnapshot
import com.mindflow.app.data.model.CloudBackupSnapshot
import com.mindflow.app.data.model.ExportPayload
import com.mindflow.app.data.model.FolderRefreshResult
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.ImportResult
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.NoteStats
import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.SearchFilters
import com.mindflow.app.data.model.TagRefreshResult
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicRefreshResult
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.topic.FolderClassifier
import com.mindflow.app.data.topic.NoteInsightPlanner
import com.mindflow.app.data.topic.NoteInsightResult
import com.mindflow.app.data.topic.noteInsightSourceContent
import com.mindflow.app.data.topic.shouldAutoGenerateVoiceInsight
import com.mindflow.app.data.topic.TagExtractor
import com.mindflow.app.data.topic.TopicExtractor
import com.mindflow.app.ui.navigation.MindFlowDestinations
import com.mindflow.app.util.TimeFormatter
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class StoredNoteDocument(
    val note: NoteEntity,
    val history: List<NoteStatusHistoryEntity>,
)

class MarkdownNoteRepository(
    private val appContext: Context,
    private val topicExtractor: TopicExtractor,
    private val folderClassifier: FolderClassifier,
    private val tagExtractor: TagExtractor,
    private val noteInsightPlanner: NoteInsightPlanner,
    private val markdownExporter: MarkdownExporter,
    private val markdownImportParser: MarkdownImportParser,
    private val cloudNoteDocumentCodec: CloudNoteDocumentCodec,
    private val applicationScope: CoroutineScope,
    private val onNoteMutated: (Long) -> Unit = {},
) : NoteRepository {
    private val storageMutex = Mutex()
    private val notesDir = File(appContext.filesDir, NOTES_DIR_NAME)
    private val stagingDir = File(appContext.filesDir, "$NOTES_DIR_NAME.staging")
    private val backupDir = File(appContext.filesDir, "$NOTES_DIR_NAME.backup")
    private val storeState = MutableStateFlow<Map<Long, StoredNoteDocument>>(emptyMap())
    private val systemNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var lastSystemNotice: String = ""
    private var lastSystemNoticeAt: Long = 0L

    init {
        notesDir.mkdirs()
        storeState.value = loadRecordsFromDisk().associateBy { it.note.id }
    }

    override fun observeFeed(): Flow<List<NoteEntity>> =
        storeState.map { records ->
            records.values
                .map { it.note }
                .filter { !it.isArchived }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeAllNotes(): Flow<List<NoteEntity>> =
        storeState.map { records ->
            records.values
                .map { it.note }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeSearchResults(filters: SearchFilters): Flow<List<NoteEntity>> =
        storeState.map { records ->
            records.values
                .map { it.note }
                .filter { note -> note.matches(filters) }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeStatusHistory(noteId: Long): Flow<List<NoteStatusHistoryEntity>> =
        storeState.map { records ->
            records[noteId]
                ?.history
                ?.sortedByDescending { it.changedAt }
                .orEmpty()
        }

    override fun observeNoteStats(): Flow<NoteStats> =
        storeState.map { records ->
            val documents = records.values.toList()
            NoteStatsCalculator.calculate(
                notes = documents.map { it.note },
                history = documents.flatMap { it.history },
            )
        }

    override fun observeSystemNotices(): Flow<String> = systemNotices.asSharedFlow()

    override suspend fun getNote(noteId: Long): NoteEntity? = storeState.value[noteId]?.note

    override suspend fun createNote(
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
        aiSummary: String,
        aiKeyPoints: List<String>,
        aiInsightContentHash: String,
        aiInsightUpdatedAt: Long,
    ): Long {
        val normalizedContent = content.trim()
        val topicContent = topicExtractionContent(normalizedContent)
        val now = System.currentTimeMillis()
        val fallbackTopic = topicExtractor.extractRule(topicContent)
        val manualTopic = topic.trim()
        val fallbackFolder = if (folderManuallyEdited) {
            com.mindflow.app.data.model.FolderSuggestion(
                folderKey = folderKey?.trim()?.ifBlank { null },
                source = FolderSource.MANUAL,
            )
        } else {
            folderClassifier.classifyRule(normalizedContent)
        }
        val fallbackTags = tagExtractor.extractRule(normalizedContent)
        val normalizedManualTags = NoteTagCodec.normalize(tags)

        val document = storageMutex.withLock {
            val noteId = nextNoteIdLocked()
            val note = NoteEntity(
                id = noteId,
                content = normalizedContent,
                topic = if (topicManuallyEdited && manualTopic.isNotBlank()) manualTopic else fallbackTopic.topic,
                topicSource = if (topicManuallyEdited && manualTopic.isNotBlank()) TopicSource.MANUAL else fallbackTopic.source,
                folderKey = fallbackFolder.folderKey,
                folderSource = fallbackFolder.source,
                tags = if (tagsManuallyEdited) normalizedManualTags else fallbackTags.tags,
                tagSource = if (tagsManuallyEdited) TagSource.MANUAL else fallbackTags.source,
                status = status,
                horizon = horizon,
                knowledgeTrust = knowledgeTrust,
                isArchived = isArchived,
                aiSummary = aiSummary.trim(),
                aiKeyPoints = aiKeyPoints.map { it.trim() }.filter { it.isNotBlank() }.take(4),
                aiInsightContentHash = aiInsightContentHash,
                aiInsightUpdatedAt = aiInsightUpdatedAt,
                createdAt = now,
                updatedAt = now,
            )
            val history = listOf(
                NoteStatusHistoryEntity(
                    id = 1L,
                    noteId = noteId,
                    fromStatus = null,
                    toStatus = status,
                    changedAt = now,
                )
            )
            val stored = StoredNoteDocument(note = note, history = history)
            upsertLocked(stored)
            stored
        }

        applicationScope.launch {
            val topicResult = if (topicManuallyEdited) {
                TopicRefreshResult()
            } else {
                refreshTopicIfPossible(document.note.id, updateTimestamp = false)
            }
            val folderResult = if (folderManuallyEdited) {
                FolderRefreshResult()
            } else {
                refreshFolderIfPossible(document.note.id, updateTimestamp = false)
            }
            val tagResult = if (tagsManuallyEdited) {
                TagRefreshResult()
            } else {
                refreshTagsIfPossible(document.note.id, updateTimestamp = false)
            }
            topicResult.notice?.let(::emitSystemNotice)
            folderResult.notice?.let(::emitSystemNotice)
            tagResult.notice?.let(::emitSystemNotice)
            if (shouldAutoGenerateVoiceInsight(normalizedContent)) {
                ensureAiInsight(document.note.id)
            }
            onNoteMutated(document.note.id)
            refreshWidget()
        }

        return document.note.id
    }

    override suspend fun updateNote(
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
    ) {
        val updated = storageMutex.withLock {
            val existing = storeState.value[noteId] ?: return
            val normalizedContent = content.trim()
            val topicContent = topicExtractionContent(normalizedContent)
            val normalizedTopic = topic.trim().ifBlank { topicExtractor.extractRule(topicContent).topic }
            val normalizedFolderKey = folderKey?.trim()?.ifBlank { null }
            val normalizedTags = NoteTagCodec.normalize(tags)
            val now = System.currentTimeMillis()
            val contentChanged = normalizedContent != existing.note.content
            val nextSource = when {
                topicManuallyEdited -> TopicSource.MANUAL
                normalizedTopic != existing.note.topic -> TopicSource.MANUAL
                else -> existing.note.topicSource
            }
            val nextFolderSource = when {
                folderManuallyEdited -> FolderSource.MANUAL
                normalizedFolderKey != existing.note.folderKey -> FolderSource.MANUAL
                else -> existing.note.folderSource
            }
            val nextTagSource = when {
                tagsManuallyEdited -> TagSource.MANUAL
                normalizedTags != existing.note.tags -> TagSource.MANUAL
                else -> existing.note.tagSource
            }

            val nextHistory = if (existing.note.status != status) {
                existing.history + NoteStatusHistoryEntity(
                    id = existing.history.size.toLong() + 1L,
                    noteId = noteId,
                    fromStatus = existing.note.status,
                    toStatus = status,
                    changedAt = now,
                )
            } else {
                existing.history
            }
            val next = existing.copy(
                note = existing.note.copy(
                    content = normalizedContent,
                    topic = normalizedTopic,
                    topicSource = nextSource,
                    folderKey = normalizedFolderKey,
                    folderSource = nextFolderSource,
                    tags = normalizedTags,
                    tagSource = nextTagSource,
                    status = status,
                    horizon = horizon,
                    knowledgeTrust = knowledgeTrust,
                    isArchived = isArchived,
                    aiSummary = if (contentChanged) "" else existing.note.aiSummary,
                    aiKeyPoints = if (contentChanged) emptyList() else existing.note.aiKeyPoints,
                    aiInsightContentHash = if (contentChanged) "" else existing.note.aiInsightContentHash,
                    aiInsightUpdatedAt = if (contentChanged) 0L else existing.note.aiInsightUpdatedAt,
                    updatedAt = now,
                ),
                history = nextHistory,
            )
            upsertLocked(next)
            UpdateOutcome(
                note = next.note,
                contentChanged = contentChanged,
                shouldRefreshFolder = contentChanged && nextFolderSource != FolderSource.MANUAL,
                shouldRefreshTopic = contentChanged && nextSource != TopicSource.MANUAL,
                shouldRefreshVoiceInsight = contentChanged && shouldAutoGenerateVoiceInsight(normalizedContent),
            )
        }

        if (updated.shouldRefreshTopic || updated.shouldRefreshFolder || updated.shouldRefreshVoiceInsight) {
            applicationScope.launch {
                if (updated.shouldRefreshTopic) {
                    refreshTopicIfPossible(noteId, updateTimestamp = false).notice?.let(::emitSystemNotice)
                }
                if (updated.shouldRefreshFolder) {
                    refreshFolderIfPossible(noteId, updateTimestamp = false).notice?.let(::emitSystemNotice)
                }
                if (updated.shouldRefreshVoiceInsight) {
                    ensureAiInsight(noteId)
                }
                onNoteMutated(noteId)
                refreshWidget()
            }
        } else {
            onNoteMutated(noteId)
        }
        refreshWidget()
    }

    override suspend fun setArchived(noteId: Long, archived: Boolean) {
        storageMutex.withLock {
            val existing = storeState.value[noteId] ?: return
            if (existing.note.isArchived == archived) return
            upsertLocked(
                existing.copy(
                    note = existing.note.copy(
                        isArchived = archived,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            )
        }
        refreshWidget()
    }

    override suspend fun deleteNote(noteId: Long) {
        storageMutex.withLock {
            val current = storeState.value
            if (current[noteId] == null) return
            deleteNoteFile(noteId)
            storeState.value = current - noteId
        }
        refreshWidget()
    }

    override suspend fun ensureAiInsight(noteId: Long): Boolean {
        val snapshot = storageMutex.withLock {
            val existing = storeState.value[noteId] ?: return false
            val contentHash = NoteInsightPlanner.contentHash(existing.note.content)
            if (
                existing.note.aiSummary.isNotBlank() &&
                existing.note.aiKeyPoints.isNotEmpty() &&
                existing.note.aiInsightContentHash == contentHash
            ) {
                return false
            }
            existing
        }

        val result = noteInsightPlanner.generate(snapshot.note.content)
        val insight = (result as? NoteInsightResult.Success)?.insight ?: return false
        val contentHash = NoteInsightPlanner.contentHash(snapshot.note.content)

        return storageMutex.withLock {
            val latest = storeState.value[noteId] ?: return false
            if (latest.note.content != snapshot.note.content) {
                return false
            }
            val topicContent = topicExtractionContent(latest.note.content)
            val topicSuggestion = if (latest.note.topicSource == TopicSource.MANUAL || topicContent.isBlank()) {
                null
            } else {
                runCatching {
                    topicExtractor.extract(topicContent, AiAutomaticPreference.PREFER_ON_DEVICE).suggestion
                }.getOrNull()
            }
            upsertLocked(
                latest.copy(
                    note = latest.note.copy(
                        topic = topicSuggestion?.topic ?: latest.note.topic,
                        topicSource = topicSuggestion?.source ?: latest.note.topicSource,
                        aiSummary = insight.summary,
                        aiKeyPoints = insight.keyPoints,
                        aiInsightContentHash = contentHash,
                        aiInsightUpdatedAt = insight.generatedAt,
                    ),
                ),
            )
            true
        }
    }

    override suspend fun classifyPendingFolders(): Int {
        val pendingNoteIds = storeState.value.values
            .map { it.note }
            .filter { !it.isArchived && it.folderKey == null && it.folderSource != FolderSource.MANUAL }
            .map { it.id }
        var classifiedCount = 0
        pendingNoteIds.forEach { noteId ->
            val result = refreshFolderIfPossible(
                noteId = noteId,
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
        refreshFolderIfPossible(
            noteId = noteId,
            force = true,
            updateTimestamp = true,
            automaticPreference = AiAutomaticPreference.PREFER_CLOUD,
        )

    override suspend fun retriggerTopicExtraction(noteId: Long): TopicRefreshResult =
        refreshTopicIfPossible(
            noteId = noteId,
            force = true,
            updateTimestamp = true,
            automaticPreference = AiAutomaticPreference.PREFER_CLOUD,
        )

    override suspend fun retriggerTagExtraction(noteId: Long): TagRefreshResult =
        refreshTagsIfPossible(
            noteId = noteId,
            force = true,
            updateTimestamp = true,
            automaticPreference = AiAutomaticPreference.PREFER_CLOUD,
        )

    override suspend fun exportAllNotes(): ExportPayload {
        val generatedAt = System.currentTimeMillis()
        val documents = storeState.value.values.sortedBy { it.note.createdAt }
        val fileStamp = TimeFormatter.fileStamp(generatedAt)
        return ExportPayload(
            fileName = "mindflow-$fileStamp.md",
            content = markdownExporter.export(
                notes = documents.map { it.note },
                historyEntries = documents.flatMap { it.history },
                generatedAt = generatedAt,
            ),
        )
    }

    override suspend fun exportCloudBackupSnapshot(): CloudBackupSnapshot =
        CloudBackupSnapshot(
            notes = storeState.value.values
                .sortedBy { it.note.createdAt }
                .map { document ->
                    CloudBackupNoteSnapshot(
                        note = document.note,
                        history = document.history,
                    )
                }
        )

    override suspend fun replaceAllFromCloudBackup(snapshot: CloudBackupSnapshot): ImportResult {
        val documents = snapshot.notes.map { noteSnapshot ->
            StoredNoteDocument(
                note = noteSnapshot.note.copy(
                    topicSource = TopicSource.MANUAL,
                    folderSource = FolderSource.MANUAL,
                    tagSource = TagSource.MANUAL,
                ),
                history = noteSnapshot.history.restableIds(noteSnapshot.note.id),
            )
        }
        storageMutex.withLock {
            replaceAllLocked(documents)
        }
        refreshWidget()
        return ImportResult(
            noteCount = documents.size,
            historyCount = documents.sumOf { it.history.size },
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
        automaticPreference: AiAutomaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
    ): TopicRefreshResult {
        val snapshot = storageMutex.withLock {
            val existing = storeState.value[noteId] ?: return TopicRefreshResult()
            if (!force && existing.note.topicSource == TopicSource.MANUAL) {
                return TopicRefreshResult()
            }
            existing
        }

        val extraction = topicExtractor.extract(topicExtractionContent(snapshot.note.content), automaticPreference)
        val suggestion = extraction.suggestion
        return storageMutex.withLock {
            val latest = storeState.value[noteId] ?: return TopicRefreshResult()
            if (
                !shouldApplyAiRefreshResult(
                    extractedFromContent = snapshot.note.content,
                    latestContent = latest.note.content,
                    latestSourceIsManual = latest.note.topicSource == TopicSource.MANUAL,
                    force = force,
                )
            ) {
                return TopicRefreshResult(notice = STALE_REFRESH_NOTICE)
            }

            val topicChanged =
                suggestion.topic != latest.note.topic || suggestion.source != latest.note.topicSource
            if (!topicChanged && !force) {
                return TopicRefreshResult(suggestion = suggestion, notice = extraction.notice)
            }

            upsertLocked(
                latest.copy(
                    note = latest.note.copy(
                        topic = suggestion.topic,
                        topicSource = suggestion.source,
                        updatedAt = if (updateTimestamp) System.currentTimeMillis() else latest.note.updatedAt,
                    ),
                )
            )
            TopicRefreshResult(suggestion = suggestion, notice = extraction.notice)
        }
    }

    private fun topicExtractionContent(content: String): String =
        noteInsightSourceContent(content).ifBlank { content }

    private suspend fun refreshFolderIfPossible(
        noteId: Long,
        force: Boolean = false,
        updateTimestamp: Boolean,
        automaticPreference: AiAutomaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
    ): FolderRefreshResult {
        val snapshot = storageMutex.withLock {
            val existing = storeState.value[noteId] ?: return FolderRefreshResult()
            if (!force && existing.note.folderSource == FolderSource.MANUAL) {
                return FolderRefreshResult()
            }
            existing
        }

        val extraction = folderClassifier.classify(snapshot.note.content, automaticPreference)
        val suggestion = extraction.suggestion
        return storageMutex.withLock {
            val latest = storeState.value[noteId] ?: return FolderRefreshResult()
            if (
                !shouldApplyAiRefreshResult(
                    extractedFromContent = snapshot.note.content,
                    latestContent = latest.note.content,
                    latestSourceIsManual = latest.note.folderSource == FolderSource.MANUAL,
                    force = force,
                )
            ) {
                return FolderRefreshResult(notice = STALE_REFRESH_NOTICE)
            }

            val folderChanged =
                suggestion.folderKey != latest.note.folderKey || suggestion.source != latest.note.folderSource
            if (!folderChanged && !force) {
                return FolderRefreshResult(suggestion = suggestion, notice = extraction.notice)
            }

            upsertLocked(
                latest.copy(
                    note = latest.note.copy(
                        folderKey = suggestion.folderKey,
                        folderSource = suggestion.source,
                        updatedAt = if (updateTimestamp) System.currentTimeMillis() else latest.note.updatedAt,
                    ),
                )
            )
            FolderRefreshResult(suggestion = suggestion, notice = extraction.notice)
        }
    }

    private suspend fun refreshTagsIfPossible(
        noteId: Long,
        force: Boolean = false,
        updateTimestamp: Boolean,
        automaticPreference: AiAutomaticPreference = AiAutomaticPreference.PREFER_ON_DEVICE,
    ): TagRefreshResult {
        val snapshot = storageMutex.withLock {
            val existing = storeState.value[noteId] ?: return TagRefreshResult()
            if (!force && existing.note.tagSource == TagSource.MANUAL) {
                return TagRefreshResult()
            }
            existing
        }

        val extraction = tagExtractor.extract(snapshot.note.content, automaticPreference)
        val suggestion = extraction.suggestion
        return storageMutex.withLock {
            val latest = storeState.value[noteId] ?: return TagRefreshResult()
            if (
                !shouldApplyAiRefreshResult(
                    extractedFromContent = snapshot.note.content,
                    latestContent = latest.note.content,
                    latestSourceIsManual = latest.note.tagSource == TagSource.MANUAL,
                    force = force,
                )
            ) {
                return TagRefreshResult(notice = STALE_REFRESH_NOTICE)
            }

            val tagsChanged = suggestion.tags != latest.note.tags || suggestion.source != latest.note.tagSource
            if (!tagsChanged && !force) {
                return TagRefreshResult(suggestion = suggestion, notice = extraction.notice)
            }

            upsertLocked(
                latest.copy(
                    note = latest.note.copy(
                        tags = suggestion.tags,
                        tagSource = suggestion.source,
                        updatedAt = if (updateTimestamp) System.currentTimeMillis() else latest.note.updatedAt,
                    ),
                )
            )
            TagRefreshResult(suggestion = suggestion, notice = extraction.notice)
        }
    }

    private suspend fun storeImportedNotes(
        parsedNotes: List<ImportedNote>,
        replaceExisting: Boolean,
    ): ImportResult {
        var importedHistoryCount = 0
        storageMutex.withLock {
            var nextId = if (replaceExisting) 1L else nextNoteIdLocked()
            val importedDocuments = parsedNotes.map { importedNote ->
                val noteId = nextId++
                val history = importedNote.history.withStableIds(noteId)
                importedHistoryCount += history.size
                StoredNoteDocument(
                    note = importedNote.toNoteEntity(noteId),
                    history = history,
                )
            }

            if (replaceExisting) {
                replaceAllLocked(importedDocuments)
            } else {
                importedDocuments.forEach(::upsertLocked)
            }
        }
        refreshWidget()
        return ImportResult(
            noteCount = parsedNotes.size,
            historyCount = importedHistoryCount,
        )
    }

    private fun loadRecordsFromDisk(): List<StoredNoteDocument> =
        notesDir
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "md" }
            .sortedBy { file -> parseNoteId(file.name) ?: Long.MAX_VALUE }
            .map { file ->
                val restored = cloudNoteDocumentCodec.decode(file.name, file.readText())
                StoredNoteDocument(
                    note = restored.note.toNoteEntity(restored.noteId),
                    history = restored.note.history.withStableIds(restored.noteId),
                )
            }
            .toList()

    private fun nextNoteIdLocked(): Long = (storeState.value.keys.maxOrNull() ?: 0L) + 1L

    private fun upsertLocked(document: StoredNoteDocument) {
        writeNoteFile(document)
        storeState.value = storeState.value.toMutableMap().apply {
            put(document.note.id, document)
        }
    }

    private fun replaceAllLocked(documents: List<StoredNoteDocument>) {
        stagingDir.deleteRecursively()
        backupDir.deleteRecursively()
        stagingDir.mkdirs()
        documents.forEach { document ->
            writeNoteFile(document, baseDir = stagingDir)
        }

        if (notesDir.exists()) {
            if (!notesDir.renameTo(backupDir)) {
                backupDir.deleteRecursively()
                notesDir.copyRecursively(backupDir, overwrite = true)
                notesDir.deleteRecursively()
            }
        }

        if (!stagingDir.renameTo(notesDir)) {
            notesDir.mkdirs()
            stagingDir.listFiles().orEmpty().forEach { file ->
                file.copyTo(File(notesDir, file.name), overwrite = true)
            }
            stagingDir.deleteRecursively()
        }

        backupDir.deleteRecursively()
        storeState.value = documents.associateBy { it.note.id }
    }

    private fun writeNoteFile(
        document: StoredNoteDocument,
        baseDir: File = notesDir,
    ) {
        baseDir.mkdirs()
        val file = File(baseDir, cloudNoteDocumentCodec.fileName(document.note.id))
        writeFileAtomically(
            file = file,
            content = cloudNoteDocumentCodec.encode(document.note, document.history),
        )
    }

    private fun deleteNoteFile(noteId: Long) {
        File(notesDir, cloudNoteDocumentCodec.fileName(noteId)).delete()
    }

    private fun writeFileAtomically(
        file: File,
        content: String,
    ) {
        val parent = file.parentFile ?: return
        parent.mkdirs()
        val tempFile = File(parent, "${file.name}.tmp")
        tempFile.writeText(content)
        if (!tempFile.renameTo(file)) {
            file.writeText(content)
            tempFile.delete()
        }
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

    private fun refreshWidget() {
        QuickCaptureWidgetProvider.refreshAll(appContext)
    }

    private fun ImportedNote.toNoteEntity(noteId: Long): NoteEntity =
        NoteEntity(
            id = noteId,
            content = content,
            topic = topic.ifBlank { "未命名想法" },
            topicSource = TopicSource.MANUAL,
            folderKey = folderKey,
            folderSource = FolderSource.MANUAL,
            tags = tags,
            tagSource = TagSource.MANUAL,
            status = status,
            horizon = horizon,
            knowledgeTrust = knowledgeTrust,
            isArchived = isArchived,
            aiSummary = aiSummary,
            aiKeyPoints = aiKeyPoints,
            aiInsightContentHash = aiInsightContentHash,
            aiInsightUpdatedAt = aiInsightUpdatedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun List<ImportedStatusHistory>.withStableIds(noteId: Long): List<NoteStatusHistoryEntity> =
        sortedBy { it.changedAt }
            .mapIndexed { index, entry ->
                NoteStatusHistoryEntity(
                    id = index.toLong() + 1L,
                    noteId = noteId,
                    fromStatus = entry.fromStatus,
                    toStatus = entry.toStatus,
                    changedAt = entry.changedAt,
                )
            }

    private fun List<NoteStatusHistoryEntity>.restableIds(noteId: Long): List<NoteStatusHistoryEntity> =
        sortedBy { it.changedAt }
            .mapIndexed { index, entry ->
                entry.copy(
                    id = index.toLong() + 1L,
                    noteId = noteId,
                )
            }

    private fun NoteEntity.matches(filters: SearchFilters): Boolean {
        if (filters.archivedOnly) {
            if (!isArchived) return false
        } else if (!filters.includeArchived && isArchived) {
            return false
        }

        val trimmedQuery = filters.query.trim()
        if (trimmedQuery.isNotEmpty()) {
            val matchesQuery = topic.contains(trimmedQuery, ignoreCase = true) ||
                content.contains(trimmedQuery, ignoreCase = true) ||
                aiSummary.contains(trimmedQuery, ignoreCase = true) ||
                aiKeyPoints.any { it.contains(trimmedQuery, ignoreCase = true) }
            if (!matchesQuery) return false
        }

        filters.tag?.let { selectedTag ->
            val normalizedTag = NoteTagCodec.normalizeOne(selectedTag)
            if (normalizedTag == null || normalizedTag !in tags) return false
        }

        filters.folderKey?.let { selectedFolder ->
            if (selectedFolder == MindFlowDestinations.UNCATEGORIZED_FOLDER) {
                if (folderKey != null) return false
            } else if (folderKey != selectedFolder) {
                return false
            }
        }

        filters.status?.let { selectedStatus ->
            if (status != selectedStatus) return false
        }

        filters.timeRange.startFrom()?.let { startFrom ->
            if (createdAt < startFrom) return false
        }

        return true
    }

    private fun parseNoteId(fileName: String): Long? =
        Regex("^note-(\\d+)\\.md$")
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private data class UpdateOutcome(
        val note: NoteEntity,
        val contentChanged: Boolean,
        val shouldRefreshFolder: Boolean,
        val shouldRefreshTopic: Boolean,
        val shouldRefreshVoiceInsight: Boolean,
    )

    private companion object {
        const val STALE_REFRESH_NOTICE = "这条记录刚刚有变化，本次 AI 结果没有自动覆盖。"
        const val NOTES_DIR_NAME = "notes"
    }
}

internal fun shouldApplyAiRefreshResult(
    extractedFromContent: String,
    latestContent: String,
    latestSourceIsManual: Boolean,
    force: Boolean,
): Boolean = extractedFromContent == latestContent && (force || !latestSourceIsManual)
