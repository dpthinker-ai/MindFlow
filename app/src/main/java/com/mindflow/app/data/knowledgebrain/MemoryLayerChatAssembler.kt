package com.mindflow.app.data.knowledgebrain

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatRawNoteDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val chatAssemblerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val fullRecordHints = listOf("完整", "全文", "原文", "全部内容")

data class MemoryLayerChatContext(
    val memoryDigestSnippets: List<String>,
    val memoryThreadSnippets: List<String>,
    val rawNoteSnippets: List<String>,
    val rawNoteDetails: List<ReviewChatRawNoteDetail>,
)

class MemoryLayerChatAssembler(
    private val memoryLayerRepository: MemoryLayerRepository,
    private val loadNotes: suspend () -> List<NoteEntity>,
) {
    suspend fun assemble(
        question: String,
        priorMessages: List<ReviewChatMessage>,
    ): MemoryLayerChatContext {
        val notes = loadNotes()
        val digest = loadDayDigestIfRequested(question, notes)
        val threads = memoryLayerRepository.loadThreadsForQuery(extractKeywords(question), limit = 4)
        val rawMatches = loadMatchingNotes(question, notes, requireFullContent = wantsFullContent(question))

        return MemoryLayerChatContext(
            memoryDigestSnippets = listOfNotNull(digest?.summary),
            memoryThreadSnippets = threads.map { thread -> "线程｜${thread.title}｜${thread.summary}" },
            rawNoteSnippets = rawMatches.map { note ->
                "记录｜${formatDate(note.updatedAt)}｜${note.topic.ifBlank { "未命名记录" }}｜${compact(note.content, 120)}"
            },
            rawNoteDetails = if (wantsFullContent(question)) {
                rawMatches.map { note ->
                    ReviewChatRawNoteDetail(
                        noteId = note.id,
                        title = note.topic.ifBlank { "未命名记录" },
                        dateLabel = formatDate(note.updatedAt),
                        fullContent = note.content.trim(),
                    )
                }
            } else {
                emptyList()
            },
        )
    }

    private suspend fun loadDayDigestIfRequested(
        question: String,
        notes: List<NoteEntity>,
    ): MemoryDigest? {
        val requested = extractMonthDay(question) ?: return null
        val matchingNote = notes.firstOrNull { note ->
            val localDate = Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            localDate.monthValue == requested.first && localDate.dayOfMonth == requested.second
        } ?: return null
        val scopeKey = formatDate(matchingNote.updatedAt)
        return memoryLayerRepository.loadDigest(MemoryDigestScopeType.DAY, scopeKey)
    }

    private fun loadMatchingNotes(
        question: String,
        notes: List<NoteEntity>,
        requireFullContent: Boolean,
    ): List<NoteEntity> {
        val keywords = extractKeywords(question)
        val ranked = notes
            .map { note ->
                note to scoreMatch(question, keywords, listOf(note.topic, note.content, note.folderKey.orEmpty()) + note.tags)
            }
            .sortedWith(compareByDescending<Pair<NoteEntity, Int>> { it.second }.thenByDescending { it.first.updatedAt })

        val limit = if (requireFullContent) 2 else 4
        return ranked
            .filter { (_, score) -> score > 0 || requireFullContent }
            .map { it.first }
            .take(limit)
    }

    private fun wantsFullContent(question: String): Boolean = fullRecordHints.any(question::contains)

    private fun extractMonthDay(question: String): Pair<Int, Int>? {
        val match = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})").find(question) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        return month to day
    }

    private fun extractKeywords(question: String): List<String> =
        Regex("[\\p{IsHan}A-Za-z0-9]{2,}")
            .findAll(question)
            .flatMap { match ->
                tokenizeAssemblerQueryChunk(match.value).asSequence()
            }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    private fun tokenizeAssemblerQueryChunk(token: String): List<String> {
        val normalized = token.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        val isHan = normalized.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        if (!isHan || normalized.length <= 4) return listOf(normalized)

        val pieces = linkedSetOf<String>()
        for (window in 2..4) {
            if (normalized.length < window) continue
            for (index in 0..normalized.length - window) {
                pieces += normalized.substring(index, index + window)
            }
        }
        pieces += normalized
        return pieces.toList()
    }

    private fun scoreMatch(
        question: String,
        keywords: List<String>,
        haystack: List<String>,
    ): Int {
        val joined = haystack.filter { it.isNotBlank() }.joinToString(" ").lowercase()
        if (joined.isBlank()) return 0
        var score = 0
        keywords.forEach { keyword ->
            val normalized = keyword.lowercase()
            if (joined.contains(normalized)) score += 3
        }
        if (wantsFullContent(question) && extractMonthDay(question) != null) {
            score += 2
        }
        return score
    }

    private fun formatDate(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().format(chatAssemblerDateFormatter)

    private fun compact(content: String, maxChars: Int): String {
        val normalized = content.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= maxChars) normalized else normalized.take(maxChars - 1).trimEnd() + "…"
    }
}
