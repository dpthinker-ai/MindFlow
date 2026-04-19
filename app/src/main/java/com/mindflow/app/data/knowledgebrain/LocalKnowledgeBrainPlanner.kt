package com.mindflow.app.data.knowledgebrain

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.topic.AiChatResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val memoryDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

class LocalKnowledgeBrainPlanner(
    private val memoryLayerRepository: MemoryLayerRepository,
    private val loadNoteById: suspend (Long) -> NoteEntity?,
    private val loadAllNotes: suspend () -> List<NoteEntity>,
    private val runOnDevice: suspend (String) -> AiChatResult,
    private val applicationScope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun enqueueNoteIngestion(noteId: Long) {
        applicationScope.launch {
            refreshNow(noteId)
        }
    }

    fun enqueueRebuild() {
        applicationScope.launch {
            rebuildAll()
        }
    }

    suspend fun refreshNow(noteId: Long? = null) {
        if (noteId != null) {
            ingestNote(noteId)
        } else {
            rebuildAll()
        }
    }

    suspend fun ingestNote(noteId: Long) {
        val note = loadNoteById(noteId) ?: return
        val result = runOnDevice(LocalKnowledgeBrainPromptFactory.fragment(note))
        val content = (result as? AiChatResult.Success)?.content?.trim().orEmpty()
        if (content.isBlank()) return

        val parsed = parseFragmentResult(note, content)
        memoryLayerRepository.upsertFragment(parsed.fragment)
        memoryLayerRepository.upsertThread(parsed.thread)
        memoryLayerRepository.upsertDigest(parsed.dayDigest)
    }

    suspend fun rebuildAll() {
        memoryLayerRepository.clearAll()
        loadAllNotes()
            .sortedBy(NoteEntity::updatedAt)
            .forEach { note -> ingestNote(note.id) }
    }

    private fun parseFragmentResult(
        note: NoteEntity,
        raw: String,
    ): ParsedFragmentResult {
        val parsed = raw
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                key.takeIf(String::isNotBlank)?.let { it to value }
            }
            .toMap()

        val timestamp = now()
        val fragmentId = "fragment-${note.id}-$timestamp"
        val topicKey = parsed["topicKey"].takeUnless { it.isNullOrBlank() } ?: fallbackTopicKey(note)
        val questionKey = parsed["questionKey"].orEmpty()
        val summary = parsed["fragmentSummary"].takeUnless { it.isNullOrBlank() }
            ?: note.content.replace("\n", " ").trim().take(80)
        val salience = parsed["salience"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5
        val threadId = "thread-$topicKey"
        val dayKey = Instant.ofEpochMilli(note.updatedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(memoryDayFormatter)

        return ParsedFragmentResult(
            fragment = MemoryFragment(
                id = fragmentId,
                sourceNoteIds = listOf(note.id),
                topicKey = topicKey,
                questionKey = questionKey,
                summary = summary,
                salience = salience,
                timeSpanStart = note.updatedAt,
                timeSpanEnd = note.updatedAt,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
            thread = MemoryThread(
                id = threadId,
                title = note.topic.ifBlank { topicKey.removePrefix("topic/") },
                type = if (questionKey.isNotBlank()) MemoryThreadType.QUESTION else MemoryThreadType.TOPIC,
                fragmentIds = listOf(fragmentId),
                summary = summary,
                currentState = "刚接入端侧本地知识层",
                openQuestions = questionKey.takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
                updatedAt = timestamp,
            ),
            dayDigest = MemoryDigest(
                id = "day-$dayKey",
                scopeType = MemoryDigestScopeType.DAY,
                scopeKey = dayKey,
                summary = summary,
                highlights = listOf(summary),
                sourceFragmentIds = listOf(fragmentId),
                updatedAt = timestamp,
            ),
        )
    }

    private fun fallbackTopicKey(note: NoteEntity): String =
        "topic/" + note.topic.ifBlank { "untitled" }
            .lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
            .trim('-')
            .ifBlank { "untitled" }

    private data class ParsedFragmentResult(
        val fragment: MemoryFragment,
        val thread: MemoryThread,
        val dayDigest: MemoryDigest,
    )
}
