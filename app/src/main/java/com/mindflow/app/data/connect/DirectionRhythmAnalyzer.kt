package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import java.util.concurrent.TimeUnit

enum class DirectionStage(
    val label: String,
) {
    FORMING("形成中"),
    VALIDATING("验证中"),
    ADVANCING("推进中"),
    SETTLING("沉淀中"),
}

data class DirectionRhythm(
    val stage: DirectionStage,
    val stageReason: String,
    val rhythmLine: String,
    val dominantHorizon: NoteHorizon,
)

object DirectionRhythmAnalyzer {
    fun analyze(
        notes: List<NoteEntity>,
        focusNote: NoteEntity?,
        hasResearch: Boolean,
    ): DirectionRhythm {
        if (notes.isEmpty()) {
            return DirectionRhythm(
                stage = DirectionStage.FORMING,
                stageReason = "这条方向还在形成期，先补一条更具体的记录。",
                rhythmLine = "阶段：形成中 · 周期：中期 · 还没有稳定节奏",
                dominantHorizon = NoteHorizon.MEDIUM,
            )
        }

        val now = System.currentTimeMillis()
        val latestUpdatedAt = notes.maxOfOrNull { it.updatedAt } ?: now
        val daysSinceUpdate = TimeUnit.MILLISECONDS.toDays(now - latestUpdatedAt).coerceAtLeast(0)
        val inProgressCount = notes.count { it.status == NoteStatus.IN_PROGRESS }
        val doneCount = notes.count { it.status == NoteStatus.DONE }
        val dominantHorizon = notes
            .groupingBy { it.horizon }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: NoteHorizon.MEDIUM

        val stage = when {
            inProgressCount > 0 -> DirectionStage.ADVANCING
            hasResearch || notes.size >= 4 || dominantHorizon == NoteHorizon.LONG -> DirectionStage.VALIDATING
            doneCount > 0 && daysSinceUpdate >= 21L -> DirectionStage.SETTLING
            else -> DirectionStage.FORMING
        }

        val stageReason = when (stage) {
            DirectionStage.ADVANCING -> when (focusNote?.horizon ?: dominantHorizon) {
                NoteHorizon.SHORT -> "现在处于快节奏推进期，最值钱的是把这一周内该落地的动作推完。"
                NoteHorizon.MEDIUM -> "现在已经有明确抓手，适合在两三周内把验证和推进衔接起来。"
                NoteHorizon.LONG -> "方向已经开始推进，但仍要守住长期主线，别只顾眼前的小动作。"
            }
            DirectionStage.VALIDATING -> when (dominantHorizon) {
                NoteHorizon.SHORT -> "这条方向现在更像短期验证题，先做一次小验证比继续扩散更值钱。"
                NoteHorizon.MEDIUM -> "这条方向已经进入验证期，适合在接下来两三周里把判断压实。"
                NoteHorizon.LONG -> "这条方向偏长期经营，当前重点不是快推，而是把关键假设验证清楚。"
            }
            DirectionStage.SETTLING -> "这条方向已经有结果沉淀，当前更适合收口判断，再决定要不要重启。"
            DirectionStage.FORMING -> when (dominantHorizon) {
                NoteHorizon.SHORT -> "这条方向更像短期想法，最好尽快压成一次真实动作。"
                NoteHorizon.MEDIUM -> "这条方向还在形成期，先把问题和路径写具体，才更容易推进。"
                NoteHorizon.LONG -> "这条方向偏长期，先明确主问题和阶段目标，不急着一次做大。"
            }
        }

        return DirectionRhythm(
            stage = stage,
            stageReason = stageReason,
            rhythmLine = "阶段：${stage.label} · 周期：${dominantHorizon.label} · 最近更新 ${daysSinceUpdate.toInt()} 天前",
            dominantHorizon = dominantHorizon,
        )
    }
}

fun pickContinueNote(notes: List<NoteEntity>): NoteEntity? =
    notes
        .filter { it.status == NoteStatus.IN_PROGRESS }
        .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
        .firstOrNull()
        ?: notes
            .filter { it.status == NoteStatus.IDEA }
            .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
            .firstOrNull()

fun pickStaleNote(
    notes: List<NoteEntity>,
    excludeNoteId: Long?,
): NoteEntity? {
    val threshold = System.currentTimeMillis() - 12L * 24 * 60 * 60 * 1_000
    return notes
        .filter { it.id != excludeNoteId }
        .filter { it.status != NoteStatus.DONE }
        .filter { it.updatedAt < threshold }
        .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenBy { it.updatedAt })
        .firstOrNull()
}
