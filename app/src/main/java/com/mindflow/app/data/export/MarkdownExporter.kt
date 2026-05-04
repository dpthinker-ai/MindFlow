package com.mindflow.app.data.export

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.util.TimeFormatter

class MarkdownExporter {
    fun export(
        notes: List<NoteEntity>,
        historyEntries: List<NoteStatusHistoryEntity>,
        generatedAt: Long = System.currentTimeMillis(),
    ): String {
        val historyByNote = historyEntries.groupBy { it.noteId }

        return buildString {
            append("# MindFlow Export\n\n")
            append("- 导出时间: ${TimeFormatter.full(generatedAt)}\n")
            append("- 记录数量: ${notes.size}\n\n")

            if (notes.isEmpty()) {
                append("当前还没有记录。\n")
                return@buildString
            }

            notes.forEachIndexed { index, note ->
                append("## ${index + 1}. ${note.topic.ifBlank { "未命名想法" }}\n\n")
                append("- 状态: ${note.status.label}\n")
                append("- 周期: ${note.horizon.label}（${note.horizon.windowLabel}）\n")
                append("- 研究状态: ${note.knowledgeTrust.label}\n")
                append("- 文件夹: ${MindFolderCatalog.fromKey(note.folderKey)?.name ?: "未分类"}\n")
                append("- 标签: ${note.tags.joinToString("、").ifBlank { "无" }}\n")
                append("- 创建时间: ${TimeFormatter.full(note.createdAt)}\n")
                append("- 更新时间: ${TimeFormatter.full(note.updatedAt)}\n")
                append("- 已归档: ${if (note.isArchived) "是" else "否"}\n\n")
                append("### 内容\n\n")
                append(note.content.trim())
                append("\n\n")
                if (note.aiSummary.isNotBlank() || note.aiKeyPoints.isNotEmpty()) {
                    append("### AI 洞察\n\n")
                    append("- 内容指纹: ${note.aiInsightContentHash}\n")
                    append("- 更新时间: ${note.aiInsightUpdatedAt.takeIf { it > 0L }?.let(TimeFormatter::full) ?: "未知"}\n\n")
                    if (note.aiSummary.isNotBlank()) {
                        append("#### 摘要\n\n")
                        append(note.aiSummary.trim())
                        append("\n\n")
                    }
                    if (note.aiKeyPoints.isNotEmpty()) {
                        append("#### 要点\n\n")
                        note.aiKeyPoints.forEach { point ->
                            append("- ${point.trim()}\n")
                        }
                        append("\n")
                    }
                }

                val noteHistory = historyByNote[note.id].orEmpty()
                if (noteHistory.isNotEmpty()) {
                    append("### 状态历史\n\n")
                    noteHistory
                        .sortedBy { it.changedAt }
                        .forEach { entry ->
                            val from = entry.fromStatus?.label ?: "初始"
                            append("- ${TimeFormatter.full(entry.changedAt)}: $from -> ${entry.toStatus.label}\n")
                        }
                    append("\n")
                }

                append("---\n\n")
            }
        }
    }
}
