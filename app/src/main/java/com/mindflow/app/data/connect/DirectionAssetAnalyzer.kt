package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity

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

        val summary = NoteInsightSummaryExtractor.extract(note).takeIf { it.isNotBlank() } ?: return null
        return DirectionAsset(
            type = type,
            summary = summary,
            noteId = note.id,
            updatedAt = note.updatedAt,
        )
    }

    private fun DirectionAssetType.priority(): Int =
        when (this) {
            DirectionAssetType.VALIDATED -> 3
            DirectionAssetType.VERIFIED -> 2
            DirectionAssetType.JUDGEMENT -> 1
        }
}
