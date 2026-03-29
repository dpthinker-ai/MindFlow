package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import java.util.Locale

data class ThemeThread(
    val key: String,
    val title: String,
    val summary: String,
    val noteCount: Int,
)

object NoteConnectionAnalyzer {
    fun buildRelatedNotes(
        currentNoteId: Long?,
        topic: String,
        content: String,
        folderKey: String?,
        tags: List<String>,
        notes: List<NoteEntity>,
        limit: Int = 3,
    ): List<NoteEntity> {
        val currentTags = tags.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val currentTokens = tokenize(topic).union(tokenize(content.take(280)))

        return notes
            .asSequence()
            .filter { !it.isArchived && it.id != currentNoteId }
            .mapNotNull { note ->
                val sharedTags = currentTags.intersect(note.tags.toSet()).size
                val sameFolder = folderKey != null && folderKey == note.folderKey
                val sharedTokens = currentTokens
                    .intersect(tokenize(note.topic).union(tokenize(note.content.take(280))))
                    .size
                val score = (sharedTags * 6) + (if (sameFolder) 3 else 0) + sharedTokens
                if (score <= 0) null else note to score
            }
            .sortedWith(compareByDescending<Pair<NoteEntity, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .map { it.first }
            .take(limit)
            .toList()
    }

    fun buildThemeThreads(
        notes: List<NoteEntity>,
        limit: Int = 3,
    ): List<ThemeThread> {
        val activeNotes = notes.filter { !it.isArchived }
        val tagThreads = activeNotes
            .flatMap { note -> note.tags.distinct().map { tag -> tag to note } }
            .groupBy({ it.first }, { it.second })
            .filter { (tag, groupedNotes) -> tag.isNotBlank() && groupedNotes.size >= 2 }
            .map { (tag, groupedNotes) ->
                ThemeThread(
                    key = "tag:$tag",
                    title = "#$tag",
                    summary = groupedNotes
                        .sortedByDescending { it.updatedAt }
                        .take(2)
                        .joinToString(" · ") { it.topic.ifBlank { "未命名记录" } },
                    noteCount = groupedNotes.size,
                )
            }

        val folderThreads = MindFolderCatalog.all.mapNotNull { folder ->
            val groupedNotes = activeNotes.filter { MindFolderCatalog.normalizedKey(it.folderKey) == folder.key }
            if (groupedNotes.size < 2) {
                null
            } else {
                ThemeThread(
                    key = "folder:${folder.key}",
                    title = folder.name,
                    summary = groupedNotes
                        .sortedByDescending { it.updatedAt }
                        .take(2)
                        .joinToString(" · ") { it.topic.ifBlank { "未命名记录" } },
                    noteCount = groupedNotes.size,
                )
            }
        }

        return (tagThreads + folderThreads)
            .distinctBy { it.title }
            .sortedWith(compareByDescending<ThemeThread> { it.noteCount }.thenBy { it.title.length })
            .take(limit)
    }

    fun threadFromKey(
        threadKey: String,
        notes: List<NoteEntity>,
    ): ThemeThread {
        val groupedNotes = notesForThread(threadKey, notes)
        val summary = groupedNotes
            .take(2)
            .joinToString(" · ") { it.topic.ifBlank { "未命名记录" } }
            .ifBlank {
                when {
                    threadKey.startsWith("folder:") -> "先把这个方向的记录慢慢积累起来。"
                    threadKey.startsWith("tag:") -> "等更多相关记录出现后，这里会形成更稳定的主线。"
                    else -> "这个方向还在形成期。"
                }
            }
        return ThemeThread(
            key = threadKey,
            title = titleForThread(threadKey),
            summary = summary,
            noteCount = groupedNotes.size,
        )
    }

    fun notesForThread(
        threadKey: String,
        notes: List<NoteEntity>,
    ): List<NoteEntity> {
        val activeNotes = notes.filter { !it.isArchived }
        return when {
            threadKey.startsWith("tag:") -> {
                val tag = threadKey.removePrefix("tag:").trim()
                activeNotes.filter { note -> tag.isNotBlank() && tag in note.tags }
            }

            threadKey.startsWith("folder:") -> {
                val folderKey = threadKey.removePrefix("folder:").trim()
                activeNotes.filter { note -> MindFolderCatalog.normalizedKey(note.folderKey) == folderKey }
            }

            else -> emptyList()
        }.sortedByDescending { it.updatedAt }
    }

    fun titleForThread(threadKey: String): String = when {
        threadKey.startsWith("tag:") -> "#${threadKey.removePrefix("tag:").trim()}"
        threadKey.startsWith("folder:") -> MindFolderCatalog.fromKey(threadKey.removePrefix("folder:").trim())?.name ?: "主题"
        else -> "主题"
    }

    fun buildRuleFusionSuggestions(
        notes: List<NoteEntity>,
        threads: List<ThemeThread>,
    ): List<String> {
        val activeNotes = notes.filter { !it.isArchived }
        val workCount = activeNotes.count { MindFolderCatalog.normalizedKey(it.folderKey) == "work" }
        val projectCount = activeNotes.count { MindFolderCatalog.normalizedKey(it.folderKey) == "project" }
        val lifeCount = activeNotes.count { MindFolderCatalog.normalizedKey(it.folderKey) == "life" }
        val healthCount = activeNotes.count { MindFolderCatalog.normalizedKey(it.folderKey) == "health" }
        val suggestions = mutableListOf<String>()

        if (workCount >= 2 && projectCount >= 2) {
            suggestions += "把工作里反复出现的问题抽成一个项目实验，用最小实现去验证它是否值得长期投入。"
        }
        if (healthCount >= 2 && (projectCount >= 1 || lifeCount >= 1)) {
            suggestions += "把健康观察和个人工具结合起来，做一个能长期陪跑自己的轻量实验。"
        }
        if (threads.size >= 2) {
            suggestions += "尝试把「${threads[0].title}」和「${threads[1].title}」串成一条更大的主线，而不是分别推进。"
        }
        if (suggestions.isEmpty()) {
            suggestions += "从最近两条最像一个方向的记录里，挑出共同问题，再把它压成一个可验证的小方案。"
        }

        return suggestions.distinct().take(2)
    }

    private fun tokenize(raw: String): Set<String> {
        val normalized = raw.lowercase(Locale.getDefault())
        val regex = Regex("[\\p{IsHan}]{2,}|[a-z0-9]{3,}")
        return regex.findAll(normalized)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
