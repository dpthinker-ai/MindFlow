package com.mindflow.app.data.backup

import com.mindflow.app.data.importing.ImportedNote
import com.mindflow.app.data.importing.ImportedStatusHistory
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.util.TimeFormatter

data class CloudRestoredNote(
    val noteId: Long,
    val note: ImportedNote,
)

class CloudNoteDocumentCodec {
    fun fileName(noteId: Long): String = "note-$noteId.md"

    fun encode(
        note: NoteEntity,
        history: List<NoteStatusHistoryEntity>,
    ): String = buildString {
        append("# MindFlow Note\n\n")
        append("- 主题: ${note.topic.ifBlank { "未命名想法" }}\n")
        append("- 文件夹: ${MindFolderCatalog.fromKey(note.folderKey)?.name ?: "未分类"}\n")
        append("- 标签: ${note.tags.joinToString("、").ifBlank { "无" }}\n")
        append("- 状态: ${note.status.label}\n")
        append("- 周期: ${note.horizon.label}（${note.horizon.windowLabel}）\n")
        append("- 研究状态: ${note.knowledgeTrust.label}\n")
        append("- 创建时间: ${TimeFormatter.full(note.createdAt)}\n")
        append("- 更新时间: ${TimeFormatter.full(note.updatedAt)}\n")
        append("- 已归档: ${if (note.isArchived) "是" else "否"}\n\n")
        append("## 内容\n\n")
        append(note.content.trim())
        append("\n\n")
        append("## 状态历史\n\n")
        history
            .sortedBy { it.changedAt }
            .forEach { entry ->
                val from = entry.fromStatus?.label ?: "初始"
                append("- ${TimeFormatter.full(entry.changedAt)}: $from -> ${entry.toStatus.label}\n")
            }
    }

    fun decode(
        fileName: String,
        markdown: String,
    ): CloudRestoredNote {
        val noteId = Regex("^note-(\\d+)\\.md$")
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: throw IllegalArgumentException("无法识别云端记录文件名：$fileName")

        val normalized = markdown.replace("\r\n", "\n")
        val topic = requireMatch(normalized, "(?m)^- 主题: (.+)$")
        val folderKey = Regex("(?m)^- 文件夹: (.+)$")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeUnless { it == "未分类" || it == "无" }
            ?.let { MindFolderCatalog.fromName(it)?.key }
        val tags = NoteTagCodec.normalize(
            requireMatch(normalized, "(?m)^- 标签: (.+)$")
                .takeUnless { it == "无" }
                ?.split(Regex("[、,，]"))
                .orEmpty()
        )
        val status = parseStatus(requireMatch(normalized, "(?m)^- 状态: (.+)$"))
        val horizon = Regex("(?m)^- 周期: (.+)$")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let(::parseHorizon)
            ?: NoteHorizon.MEDIUM
        val knowledgeTrust = Regex("(?m)^- 研究状态: (.+)$")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let(::parseKnowledgeTrust)
            ?: KnowledgeTrust.NONE
        val createdAt = parseTime(requireMatch(normalized, "(?m)^- 创建时间: (.+)$"))
        val updatedAt = parseTime(requireMatch(normalized, "(?m)^- 更新时间: (.+)$"))
        val isArchived = requireMatch(normalized, "(?m)^- 已归档: (.+)$") == "是"
        val content = extractBlock(normalized, "## 内容", listOf("## 状态历史")).trim()
        val history = extractBlock(normalized, "## 状态历史", emptyList())
            .lineSequence()
            .map(String::trim)
            .filter { it.startsWith("- ") }
            .mapNotNull(::parseHistoryLine)
            .toList()
            .ifEmpty { listOf(ImportedStatusHistory(fromStatus = null, toStatus = status, changedAt = createdAt)) }

        return CloudRestoredNote(
            noteId = noteId,
            note = ImportedNote(
                topic = topic,
                folderKey = folderKey,
                tags = tags,
                content = content,
                status = status,
                horizon = horizon,
                knowledgeTrust = knowledgeTrust,
                isArchived = isArchived,
                createdAt = createdAt,
                updatedAt = updatedAt,
                history = history,
            ),
        )
    }

    private fun parseHistoryLine(line: String): ImportedStatusHistory? {
        val match = Regex("^-\\s+(.+?):\\s+(.+)\\s+->\\s+(.+)$").find(line) ?: return null
        return ImportedStatusHistory(
            fromStatus = parseOptionalStatus(match.groupValues[2].trim()),
            toStatus = parseStatus(match.groupValues[3].trim()),
            changedAt = parseTime(match.groupValues[1].trim()),
        )
    }

    private fun parseStatus(label: String): NoteStatus = when (label.trim()) {
        NoteStatus.IDEA.label -> NoteStatus.IDEA
        NoteStatus.IN_PROGRESS.label -> NoteStatus.IN_PROGRESS
        NoteStatus.DONE.label -> NoteStatus.DONE
        else -> throw IllegalArgumentException("未知状态：$label")
    }

    private fun parseOptionalStatus(label: String): NoteStatus? =
        if (label == "初始") null else parseStatus(label)

    private fun parseHorizon(raw: String): NoteHorizon {
        val label = raw.substringBefore("（").trim()
        return when (label) {
            NoteHorizon.SHORT.label -> NoteHorizon.SHORT
            NoteHorizon.MEDIUM.label -> NoteHorizon.MEDIUM
            NoteHorizon.LONG.label -> NoteHorizon.LONG
            else -> NoteHorizon.MEDIUM
        }
    }

    private fun parseKnowledgeTrust(label: String): KnowledgeTrust = when (label.trim()) {
        KnowledgeTrust.NONE.label -> KnowledgeTrust.NONE
        KnowledgeTrust.SIGNAL.label -> KnowledgeTrust.SIGNAL
        KnowledgeTrust.HYPOTHESIS.label -> KnowledgeTrust.HYPOTHESIS
        KnowledgeTrust.VERIFIED.label -> KnowledgeTrust.VERIFIED
        KnowledgeTrust.VALIDATED.label -> KnowledgeTrust.VALIDATED
        else -> KnowledgeTrust.NONE
    }

    private fun parseTime(raw: String): Long =
        TimeFormatter.parseFull(raw) ?: throw IllegalArgumentException("无法解析时间：$raw")

    private fun requireMatch(section: String, pattern: String): String =
        Regex(pattern).find(section)?.groupValues?.get(1)?.trim()
            ?: throw IllegalArgumentException("云端记录缺少字段：$pattern")

    private fun extractBlock(
        section: String,
        startMarker: String,
        endMarkers: List<String>,
    ): String {
        val startIndex = section.indexOf(startMarker)
        if (startIndex == -1) return ""

        val contentStart = startIndex + startMarker.length
        val trailing = section.substring(contentStart)
        val endIndex = if (endMarkers.isEmpty()) {
            trailing.length
        } else {
            endMarkers
                .map { trailing.indexOf(it).takeIf { index -> index >= 0 } ?: trailing.length }
                .minOrNull()
                ?: trailing.length
        }

        return trailing.substring(0, endIndex).trim()
    }
}
