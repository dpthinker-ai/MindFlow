package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.markdown.SimpleMarkdown

enum class DirectionAssetType(
    val label: String,
) {
    JUDGEMENT("当前判断"),
    VERIFIED("已查证"),
    VALIDATED("已验证"),
}

data class DirectionAsset(
    val type: DirectionAssetType,
    val summary: String,
    val noteId: Long,
    val updatedAt: Long,
)

object DirectionAssetAnalyzer {
    fun build(notes: List<NoteEntity>): List<DirectionAsset> =
        notes
            .asSequence()
            .mapNotNull(::toAsset)
            .distinctBy { "${it.type}:${it.summary}" }
            .sortedWith(
                compareByDescending<DirectionAsset> { it.type.priority() }
                    .thenByDescending { it.updatedAt },
            )
            .take(3)
            .toList()

    private fun toAsset(note: NoteEntity): DirectionAsset? {
        val type = when (ResearchEvidenceAnalyzer.classify(note)) {
            ResearchEvidenceType.VALIDATED -> DirectionAssetType.VALIDATED
            ResearchEvidenceType.VERIFIED -> DirectionAssetType.VERIFIED
            else -> if (note.topic.contains("当前判断") || note.topic.contains("本周推进")) {
                DirectionAssetType.JUDGEMENT
            } else {
                null
            }
        } ?: return null

        val summary = extractSummary(note).takeIf { it.isNotBlank() } ?: return null
        return DirectionAsset(
            type = type,
            summary = summary,
            noteId = note.id,
            updatedAt = note.updatedAt,
        )
    }

    private fun extractSummary(note: NoteEntity): String {
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
        )
            .asSequence()
            .mapNotNull { key ->
                lines.firstOrNull { line ->
                    line.contains(key) && line.contains("：")
                }?.substringAfter("：")?.trim()
            }
            .firstOrNull { it.isNotBlank() }

        if (!preferred.isNullOrBlank()) return preferred.take(72)

        return SimpleMarkdown.toPlainText(note.content)
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(72)
            .orEmpty()
    }

    private fun DirectionAssetType.priority(): Int =
        when (this) {
            DirectionAssetType.VALIDATED -> 3
            DirectionAssetType.VERIFIED -> 2
            DirectionAssetType.JUDGEMENT -> 1
        }
}
