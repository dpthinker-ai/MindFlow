package com.mindflow.app.data.wiki

import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.connect.ResearchGroundingSnapshot
import com.mindflow.app.data.connect.ThreadExecutionSummary
import com.mindflow.app.data.local.entity.NoteEntity
import java.util.concurrent.TimeUnit

data class KnowledgeLayerLintSummary(
    val healthLine: String,
    val lintIssues: List<String>,
)

object KnowledgeLayerLintAnalyzer {
    fun summarize(
        stage: DirectionStage,
        notes: List<NoteEntity>,
        grounding: ResearchGroundingSnapshot,
        execution: ThreadExecutionSummary,
        questionCount: Int,
        methodCount: Int,
        experimentCount: Int,
    ): KnowledgeLayerLintSummary {
        if (notes.isEmpty()) {
            return KnowledgeLayerLintSummary(
                healthLine = "这条方向还没有形成可维护的知识材料。",
                lintIssues = listOf("先补一条更具体的记录，再开始沉淀知识对象。"),
            )
        }

        val latestUpdatedAt = notes.maxOfOrNull { it.updatedAt } ?: 0L
        val daysSinceUpdate = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - latestUpdatedAt).coerceAtLeast(0)
        val issues = buildList {
            if (grounding.verifiedItems.isEmpty() && grounding.validatedItems.isEmpty()) {
                add("证据还偏薄，先补一条查证或验证记录。")
            }
            if (execution.nextStep.isBlank()) {
                add("还没有清晰的下一步，先把当前最小动作写出来。")
            }
            if (methodCount == 0 && experimentCount == 0) {
                add("还没有形成方法或实验沉淀，后续容易重复摸索。")
            }
            if (stage == DirectionStage.FORMING && questionCount == 0) {
                add("形成期还缺一个明确问题，容易一直发散。")
            }
            if (daysSinceUpdate >= 21L) {
                add("这条方向已经放缓一段时间，适合重新接上一小步。")
            }
        }.distinct().take(3)

        val healthLine = when {
            issues.isEmpty() ->
                "知识层状态稳定，当前已有可复用沉淀，可继续把判断压成更稳的结论。"
            issues.size == 1 ->
                "当前最值得先补的是：${issues.first()}"
            else ->
                "当前最该先补证据、方法和下一步，避免方向只停在判断层。"
        }

        return KnowledgeLayerLintSummary(
            healthLine = healthLine,
            lintIssues = issues,
        )
    }
}
