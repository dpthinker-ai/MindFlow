package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import java.util.concurrent.TimeUnit

data class DirectionContinuitySummary(
    val trajectoryLine: String = "",
    val continuityLine: String = "",
)

object DirectionContinuityAnalyzer {
    fun summarize(
        notes: List<NoteEntity>,
        historyWeeks: Int = 8,
    ): DirectionContinuitySummary {
        if (notes.isEmpty()) return DirectionContinuitySummary()

        val history = DirectionStageHistoryAnalyzer.build(notes, weeks = historyWeeks)
        val trajectoryStages = history.map { it.stage }.distinct()
        val trajectoryLine = when {
            history.isEmpty() -> ""
            trajectoryStages.isEmpty() -> ""
            trajectoryStages.size == 1 -> "近${history.size}周一直处于${trajectoryStages.first().label}"
            else -> "近${history.size}周：${trajectoryStages.joinToString(" → ") { it.label }}"
        }

        val now = System.currentTimeMillis()
        val recent7 = notes.count { now - it.updatedAt <= TimeUnit.DAYS.toMillis(7) }
        val recent21 = notes.count { now - it.updatedAt <= TimeUnit.DAYS.toMillis(21) }
        val recent42 = notes.count { now - it.updatedAt <= TimeUnit.DAYS.toMillis(42) }
        val daysSinceUpdate = TimeUnit.MILLISECONDS
            .toDays(now - (notes.maxOfOrNull { it.updatedAt } ?: now))
            .coerceAtLeast(0)
        val hasRecentInProgress = notes.any {
            it.status == NoteStatus.IN_PROGRESS && now - it.updatedAt <= TimeUnit.DAYS.toMillis(14)
        }
        val hasRecentResearch = notes.any {
            ThreadResearchAnalyzer.isResearchMemoryNote(it) && now - it.updatedAt <= TimeUnit.DAYS.toMillis(21)
        }

        val continuityLine = when {
            recent42 <= 1 && daysSinceUpdate >= 28L ->
                "近一个月几乎没有新推进，这条方向容易停住，适合重新给它一个最小动作。"
            hasRecentInProgress && recent7 >= 2 ->
                "近一周持续有推进，这条方向已经形成比较稳定的执行节奏。"
            hasRecentResearch && recent21 >= 2 ->
                "近三周持续在验证，离真正推进只差把一条假设压成明确动作。"
            recent21 >= 3 ->
                "近三周仍在稳定更新，这条方向正在缓慢向前，保持每周一次真实推进最稳。"
            recent21 >= 1 ->
                "这条方向还在前进，但节奏偏松，最好尽快补一条带结果的记录。"
            else ->
                "最近推进明显放缓，先重新接上一条更具体的记录，方向会更容易继续流动。"
        }

        return DirectionContinuitySummary(
            trajectoryLine = trajectoryLine,
            continuityLine = continuityLine,
        )
    }
}
