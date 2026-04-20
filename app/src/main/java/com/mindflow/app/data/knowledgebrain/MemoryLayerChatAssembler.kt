package com.mindflow.app.data.knowledgebrain

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.reviewchat.ReviewChatMessage
import com.mindflow.app.data.reviewchat.ReviewChatMessageRole
import com.mindflow.app.data.reviewchat.ReviewChatRawNoteDetail
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val chatAssemblerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val fullRecordHints = listOf("完整", "全文", "原文", "全部内容")
private enum class RequestedDateScope {
    RELATIVE_DAY,
    MONTH_DAY,
}

private data class RequestedDateConstraint(
    val scope: RequestedDateScope,
    val localDate: LocalDate? = null,
    val monthDay: MonthDay? = null,
)

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
        val requestedDate = resolveRequestedDate(question)
        val digest = loadDayDigestIfRequested(question, notes, requestedDate)
        val threads = memoryLayerRepository.loadThreadsForQuery(extractKeywords(question), limit = 4)
        val rawMatches = loadMatchingNotes(
            question = question,
            notes = notes,
            requireFullContent = wantsFullContent(question),
            requestedDate = requestedDate,
        )

        return MemoryLayerChatContext(
            memoryDigestSnippets = listOfNotNull(digest?.summary),
            memoryThreadSnippets = threads.map { thread -> "线程｜${thread.title}｜${thread.summary}" },
            rawNoteSnippets = rawMatches.map { note ->
                "记录｜${formatDate(note.updatedAt)}｜${note.topic.ifBlank { "未命名记录" }}｜${compact(note.content, 120)}"
            },
            rawNoteDetails = if (wantsFullContent(question) || requestedDate != null) {
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
        requestedDate: RequestedDateConstraint? = resolveRequestedDate(question),
    ): MemoryDigest? {
        val resolvedDate = requestedDate ?: return null
        val matchingNote = notes.firstOrNull { note ->
            val localDate = Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            matchesRequestedDate(localDate, resolvedDate)
        } ?: return null
        val scopeKey = formatDate(matchingNote.updatedAt)
        return memoryLayerRepository.loadDigest(MemoryDigestScopeType.DAY, scopeKey)
    }

    private fun loadMatchingNotes(
        question: String,
        notes: List<NoteEntity>,
        requireFullContent: Boolean,
        requestedDate: RequestedDateConstraint?,
    ): List<NoteEntity> {
        val keywords = extractKeywords(question)
        val ranked = notes
            .asSequence()
            .filter { note ->
                requestedDate == null ||
                    matchesRequestedDate(
                        Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                        requestedDate,
                    )
            }
            .map { note ->
                note to scoreMatch(question, keywords, listOf(note.topic, note.content, note.folderKey.orEmpty()) + note.tags)
            }
            .sortedWith(compareByDescending<Pair<NoteEntity, Int>> { it.second }.thenByDescending { it.first.updatedAt })

        val limit = when {
            requestedDate != null -> Int.MAX_VALUE
            requireFullContent -> 2
            else -> 4
        }
        return ranked
            .filter { (_, score) -> score > 0 || requireFullContent }
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun wantsFullContent(question: String): Boolean = fullRecordHints.any(question::contains)

    private fun extractMonthDay(question: String): Pair<Int, Int>? {
        val match = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})").find(question) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        return month to day
    }

    private fun resolveRequestedDate(question: String): RequestedDateConstraint? {
        val today = LocalDate.now(ZoneId.systemDefault())
        return when {
            "今天" in question -> RequestedDateConstraint(
                scope = RequestedDateScope.RELATIVE_DAY,
                localDate = today,
            )
            "昨天" in question || "昨日" in question -> RequestedDateConstraint(
                scope = RequestedDateScope.RELATIVE_DAY,
                localDate = today.minusDays(1),
            )
            "前天" in question -> RequestedDateConstraint(
                scope = RequestedDateScope.RELATIVE_DAY,
                localDate = today.minusDays(2),
            )
            else -> {
                val monthDay = extractMonthDay(question) ?: return null
                RequestedDateConstraint(
                    scope = RequestedDateScope.MONTH_DAY,
                    monthDay = MonthDay.of(monthDay.first, monthDay.second),
                )
            }
        }
    }

    private fun matchesRequestedDate(
        noteDate: LocalDate,
        requestedDate: RequestedDateConstraint,
    ): Boolean = when (requestedDate.scope) {
        RequestedDateScope.RELATIVE_DAY -> requestedDate.localDate == noteDate
        RequestedDateScope.MONTH_DAY -> requestedDate.monthDay == MonthDay.from(noteDate)
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
