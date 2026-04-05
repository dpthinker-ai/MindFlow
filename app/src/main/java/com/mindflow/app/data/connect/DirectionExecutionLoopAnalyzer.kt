package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.util.TimeFormatter

data class DirectionExecutionLoopSummary(
    val lastProgressLine: String = "",
    val nextCheckInLine: String = "",
)

object DirectionExecutionLoopAnalyzer {
    fun summarize(
        notes: List<NoteEntity>,
        focusNote: NoteEntity?,
        stage: DirectionStage,
        dominantHorizon: NoteHorizon,
    ): DirectionExecutionLoopSummary {
        if (notes.isEmpty()) return DirectionExecutionLoopSummary()

        val latestRelevant = notes
            .sortedByDescending { it.updatedAt }
            .firstOrNull()

        val lastProgressLine = latestRelevant?.let { note ->
            val whenText = TimeFormatter.compact(note.updatedAt)
            when (note.status) {
                NoteStatus.DONE -> "最近一次推进：$whenText 把「${note.topic.ifBlank { "这条记录" }}」做成了。"
                NoteStatus.IN_PROGRESS -> "最近一次推进：$whenText 更新了「${note.topic.ifBlank { "这条记录" }}」。"
                NoteStatus.IDEA -> "最近一次推进：$whenText 补了「${note.topic.ifBlank { "这条记录" }}」的新想法。"
            }
        }.orEmpty()

        val nextCheckInLine = when (stage) {
            DirectionStage.ADVANCING -> when (focusNote?.horizon ?: dominantHorizon) {
                NoteHorizon.SHORT -> "下次检查：最好 2 天内再补一次进展，别让短期动作断掉。"
                NoteHorizon.MEDIUM -> "下次检查：最好 4 天内再补一次结果，保持推进连续。"
                NoteHorizon.LONG -> "下次检查：最好一周内再确认一次推进结果，别让长期方向失速。"
            }
            DirectionStage.VALIDATING -> when (dominantHorizon) {
                NoteHorizon.SHORT -> "下次检查：最好 3 天内记下这次验证结果。"
                NoteHorizon.MEDIUM -> "下次检查：最好一周内把这次验证压成结论。"
                NoteHorizon.LONG -> "下次检查：最好 10 天内补一次验证判断，避免方向只停在研究层。"
            }
            DirectionStage.SETTLING -> "下次检查：先在一周内判断这条方向要继续沉淀，还是重新启动。"
            DirectionStage.FORMING -> when (dominantHorizon) {
                NoteHorizon.SHORT -> "下次检查：最好 2 天内把它压成一次真实动作。"
                NoteHorizon.MEDIUM -> "下次检查：最好 5 天内再补一条更具体的记录。"
                NoteHorizon.LONG -> "下次检查：最好一周内把主问题再写实一点。"
            }
        }

        return DirectionExecutionLoopSummary(
            lastProgressLine = lastProgressLine,
            nextCheckInLine = nextCheckInLine,
        )
    }
}
