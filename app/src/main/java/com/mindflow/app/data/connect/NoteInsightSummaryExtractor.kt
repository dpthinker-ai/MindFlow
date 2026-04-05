package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.markdown.SimpleMarkdown

object NoteInsightSummaryExtractor {
    fun extract(note: NoteEntity): String {
        val lines = note.content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        val preferred = listOf(
            "当前判断",
            "这次新的判断",
            "我现在更明确的判断",
            "查证结果",
            "结果表明",
            "已验证",
            "外部假设",
            "我查到的内容",
            "这对当前方向的判断",
        )
            .asSequence()
            .mapNotNull { key ->
                lines.firstOrNull { line ->
                    line.contains(key) && line.contains("：")
                }?.substringAfter("：")?.trim()
            }
            .firstOrNull { it.isNotBlank() }

        if (!preferred.isNullOrBlank()) return preferred.take(96)

        return SimpleMarkdown.toPlainText(note.content)
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(96)
            .orEmpty()
    }
}
