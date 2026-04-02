package com.mindflow.app.data.importing

import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.util.TimeFormatter

class MarkdownImportParser {
    fun parse(markdown: String): List<ImportedNote> {
        val normalized = markdown.replace("\r\n", "\n")
        val headerRegex = Regex("(?m)^##\\s+\\d+\\.\\s+(.+)$")
        val matches = headerRegex.findAll(normalized).toList()
        if (matches.isEmpty()) {
            throw IllegalArgumentException("未识别到 MindFlow 导出内容。")
        }

        return matches.mapIndexed { index, match ->
            val sectionStart = match.range.first
            val sectionEnd = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
            parseSection(topic = match.groupValues[1].trim(), section = normalized.substring(sectionStart, sectionEnd))
        }
    }

    private fun parseSection(topic: String, section: String): ImportedNote {
        val status = parseStatus(requireMatch(section, "(?m)^- 状态: (.+)$"))
        val horizon = Regex("(?m)^- 周期: (.+)$")
            .find(section)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let(::parseHorizon)
            ?: NoteHorizon.MEDIUM
        val folderKey = Regex("(?m)^- 文件夹: (.+)$")
            .find(section)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeUnless { it == "未分类" || it == "无" }
            ?.let { MindFolderCatalog.fromName(it)?.key }
        val tags = NoteTagCodec.normalize(
            requireMatch(section, "(?m)^- 标签: (.+)$")
                .takeUnless { it == "无" }
                ?.split(Regex("[、,，]"))
                .orEmpty()
        )
        val createdAt = parseTime(requireMatch(section, "(?m)^- 创建时间: (.+)$"))
        val updatedAt = parseTime(requireMatch(section, "(?m)^- 更新时间: (.+)$"))
        val isArchived = requireMatch(section, "(?m)^- 已归档: (.+)$") == "是"

        val content = extractBlock(
            section = section,
            startMarker = "### 内容",
            endMarkers = listOf("### 状态历史", "---"),
        )

        val historySection = extractBlock(
            section = section,
            startMarker = "### 状态历史",
            endMarkers = listOf("---"),
        )

        val parsedHistory = historySection
            .lineSequence()
            .map(String::trim)
            .filter { it.startsWith("- ") }
            .mapNotNull(::parseHistoryLine)
            .toList()

        val history = if (parsedHistory.isEmpty()) {
            listOf(ImportedStatusHistory(fromStatus = null, toStatus = status, changedAt = createdAt))
        } else {
            parsedHistory
        }

        return ImportedNote(
            topic = topic,
            folderKey = folderKey,
            tags = tags,
            content = content,
            status = status,
            horizon = horizon,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = updatedAt,
            history = history,
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
        else -> throw IllegalArgumentException("未知状态: $label")
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

    private fun parseTime(raw: String): Long =
        TimeFormatter.parseFull(raw) ?: throw IllegalArgumentException("无法解析时间: $raw")

    private fun extractBlock(
        section: String,
        startMarker: String,
        endMarkers: List<String>,
    ): String {
        val startIndex = section.indexOf(startMarker)
        if (startIndex == -1) return ""

        val contentStart = startIndex + startMarker.length
        val trailing = section.substring(contentStart)
        val endIndex = endMarkers
            .map { trailing.indexOf(it).takeIf { index -> index >= 0 } ?: trailing.length }
            .minOrNull()
            ?: trailing.length

        return trailing.substring(0, endIndex).trim()
    }

    private fun requireMatch(section: String, pattern: String): String =
        Regex(pattern).find(section)?.groupValues?.get(1)?.trim()
            ?: throw IllegalArgumentException("导入内容缺少字段: $pattern")
}
