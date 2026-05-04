package com.mindflow.app.data.backup

import com.mindflow.app.data.model.CloudBackupSettings
import com.mindflow.app.data.model.ImportResult
import com.mindflow.app.data.model.CloudBackupNoteSnapshot
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.CloudBackupSettingsRepository
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CloudBackupCoordinator(
    private val noteRepository: NoteRepository,
    private val cloudBackupSettingsRepository: CloudBackupSettingsRepository,
    private val cloudBackupIndexRepository: CloudBackupIndexRepository,
    private val webDavBackupClient: WebDavBackupClient,
    private val cloudNoteDocumentCodec: CloudNoteDocumentCodec,
    private val applicationScope: CoroutineScope,
) {
    private val syncMutex = Mutex()
    private var started = false
    @Volatile
    private var hasPendingChanges = false

    fun startAutoBackup() {
        if (started) return
        started = true

        applicationScope.launch {
            noteRepository.observeAllNotes()
                .drop(1)
                .collect { _ ->
                    hasPendingChanges = true
                }
        }
    }

    fun syncInBackgroundIfNeeded() {
        applicationScope.launch {
            val settings = cloudBackupSettingsRepository.getCurrent()
            if (!settings.autoBackupEnabled || !settings.isConfigured || !hasPendingChanges) return@launch
            if (settings.lastBackupAt > 0L && System.currentTimeMillis() - settings.lastBackupAt < AUTO_SYNC_MIN_INTERVAL_MS) {
                return@launch
            }
            runCatching {
                syncMutex.withLock {
                    syncNotes(settings, forceUploadAll = false)
                }
            }
                .onFailure {
                    cloudBackupSettingsRepository.updateBackupStatus(
                        errorMessage = it.message.orEmpty().ifBlank { "后台同步失败" },
                    )
                }
        }
    }

    suspend fun backupNow(): Long = syncMutex.withLock {
        val settings = cloudBackupSettingsRepository.getCurrent()
        requireConfigured(settings)
        syncNotes(settings, forceUploadAll = false)
    }

    suspend fun restoreLatest(): ImportResult = syncMutex.withLock {
        val settings = cloudBackupSettingsRepository.getCurrent()
        requireConfigured(settings)

        val files = webDavBackupClient.listMarkdownFiles(settings, NOTES_DIR)
        if (files.isEmpty()) {
            throw IllegalStateException("云端还没有可恢复的记录")
        }

        val remoteDocuments = files.associateWith { fileName ->
            webDavBackupClient.downloadText(settings, "$NOTES_DIR/$fileName")
        }
        val restoredNotes = remoteDocuments.map { (fileName, markdown) ->
            cloudNoteDocumentCodec.decode(fileName, markdown)
        }
        val restoreResult = noteRepository.replaceAllFromCloudBackup(
            com.mindflow.app.data.model.CloudBackupSnapshot(
                notes = restoredNotes.map { restored ->
                    CloudBackupNoteSnapshot(
                        note = NoteEntity(
                            id = restored.noteId,
                            content = restored.note.content,
                            topic = restored.note.topic.ifBlank { "未命名想法" },
                            topicSource = com.mindflow.app.data.model.TopicSource.MANUAL,
                            folderKey = restored.note.folderKey,
                            folderSource = com.mindflow.app.data.model.FolderSource.MANUAL,
                            tags = restored.note.tags,
                            tagSource = com.mindflow.app.data.model.TagSource.MANUAL,
                            status = restored.note.status,
                            horizon = restored.note.horizon,
                            knowledgeTrust = restored.note.knowledgeTrust,
                            isArchived = restored.note.isArchived,
                            aiSummary = restored.note.aiSummary,
                            aiKeyPoints = restored.note.aiKeyPoints,
                            aiInsightContentHash = restored.note.aiInsightContentHash,
                            aiInsightUpdatedAt = restored.note.aiInsightUpdatedAt,
                            createdAt = restored.note.createdAt,
                            updatedAt = restored.note.updatedAt,
                        ),
                        history = restored.note.history.mapIndexed { index, entry ->
                            com.mindflow.app.data.local.entity.NoteStatusHistoryEntity(
                                id = index.toLong() + 1L,
                                noteId = restored.noteId,
                                fromStatus = entry.fromStatus,
                                toStatus = entry.toStatus,
                                changedAt = entry.changedAt,
                            )
                        },
                    )
                },
            )
        )

        cloudBackupIndexRepository.save(
            CloudBackupIndex(
                targetKey = listOf(
                    settings.normalizedBaseUrl,
                    settings.username.trim(),
                    settings.normalizedRemoteDir,
                ).joinToString("|"),
                noteHashes = remoteDocuments.mapValues { (_, markdown) -> sha256(markdown) },
            )
        )
        cloudBackupSettingsRepository.updateBackupStatus(
            succeededAt = System.currentTimeMillis(),
            errorMessage = "",
        )
        hasPendingChanges = false
        return restoreResult
    }

    private fun requireConfigured(settings: CloudBackupSettings) {
        require(settings.isConfigured) {
            "请先填好 WebDAV 地址、用户名和应用密码"
        }
    }

    private suspend fun syncNotes(
        settings: CloudBackupSettings,
        forceUploadAll: Boolean,
    ): Long {
        val targetKey = listOf(settings.normalizedBaseUrl, settings.username.trim(), settings.normalizedRemoteDir)
            .joinToString("|")
        val index = cloudBackupIndexRepository.getCurrent()
        val previousHashes = if (index.targetKey == targetKey) index.noteHashes else emptyMap()
        val remoteFiles = webDavBackupClient.listMarkdownFiles(settings, NOTES_DIR).toSet()
        val snapshot = noteRepository.exportCloudBackupSnapshot()

        val currentHashes = mutableMapOf<String, String>()
        snapshot.notes.forEach { noteSnapshot ->
            val fileName = cloudNoteDocumentCodec.fileName(noteSnapshot.note.id)
            val document = cloudNoteDocumentCodec.encode(noteSnapshot.note, noteSnapshot.history)
            val hash = sha256(document)
            currentHashes[fileName] = hash

            if (forceUploadAll || previousHashes[fileName] != hash || fileName !in remoteFiles) {
                webDavBackupClient.uploadText(
                    settings = settings,
                    relativePath = "$NOTES_DIR/$fileName",
                    content = document,
                )
            }
        }

        (remoteFiles - currentHashes.keys).forEach { fileName ->
            webDavBackupClient.moveFile(
                settings = settings,
                fromRelativePath = "$NOTES_DIR/$fileName",
                toRelativePath = "$TRASH_DIR/${System.currentTimeMillis()}-$fileName",
            )
        }

        pruneTrash(settings)

        cloudBackupIndexRepository.save(
            CloudBackupIndex(
                targetKey = targetKey,
                noteHashes = currentHashes,
            )
        )

        val now = System.currentTimeMillis()
        cloudBackupSettingsRepository.updateBackupStatus(
            succeededAt = now,
            errorMessage = "",
        )
        hasPendingChanges = false
        return now
    }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private suspend fun pruneTrash(settings: CloudBackupSettings) {
        val now = System.currentTimeMillis()
        webDavBackupClient.listMarkdownFiles(settings, TRASH_DIR)
            .mapNotNull { fileName ->
                val deletedAt = fileName.substringBefore('-').toLongOrNull() ?: return@mapNotNull null
                if (now - deletedAt >= TRASH_RETENTION_MS) fileName else null
            }
            .forEach { fileName ->
                webDavBackupClient.deleteFile(
                    settings = settings,
                    relativePath = "$TRASH_DIR/$fileName",
                )
            }
    }

    private companion object {
        const val NOTES_DIR = "notes"
        const val TRASH_DIR = "trash"
        val TRASH_RETENTION_MS: Long = TimeUnit.DAYS.toMillis(30)
        val AUTO_SYNC_MIN_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(1)
    }
}
