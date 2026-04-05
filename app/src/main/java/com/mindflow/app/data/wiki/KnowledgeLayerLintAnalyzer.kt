package com.mindflow.app.data.wiki

import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.connect.ResearchGroundingSnapshot
import com.mindflow.app.data.connect.ThreadExecutionSummary
import com.mindflow.app.data.local.entity.NoteEntity
import java.util.concurrent.TimeUnit

data class KnowledgeLayerLintSummary(
    val healthLine: String,
    val maintenanceLine: String,
    val maintenanceTargetLine: String,
    val maintenanceSourceLine: String,
    val lintIssues: List<String>,
)

object KnowledgeLayerLintAnalyzer {
    fun summarize(
        stage: DirectionStage,
        notes: List<NoteEntity>,
        grounding: ResearchGroundingSnapshot,
        execution: ThreadExecutionSummary,
        conclusionLine: String,
        nextShiftLine: String,
        questionCount: Int,
        methodCount: Int,
        experimentCount: Int,
    ): KnowledgeLayerLintSummary {
        if (notes.isEmpty()) {
            return KnowledgeLayerLintSummary(
                healthLine = "这条方向还没有形成可维护的知识材料。",
                maintenanceLine = "先补一条更具体的记录，再开始沉淀问题、方法或实验。",
                maintenanceTargetLine = "原始记录",
                maintenanceSourceLine = "补一条更具体的记录",
                lintIssues = listOf("先补一条更具体的记录，再开始沉淀知识对象。"),
            )
        }

        val latestUpdatedAt = notes.maxOfOrNull { it.updatedAt } ?: 0L
        val daysSinceUpdate = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - latestUpdatedAt).coerceAtLeast(0)
        val hasConflict = hasPolarityConflict(grounding)
        val issues = buildList {
            if (hasConflict) {
                add("已查证或已验证里出现相反信号，当前结论需要重新压实。")
            }
            if (conclusionLine.isNotBlank() && grounding.verifiedItems.isEmpty() && grounding.validatedItems.isEmpty()) {
                add("已经形成结论，但缺少查证或验证支撑。")
            }
            if (grounding.verifiedItems.isEmpty() && grounding.validatedItems.isEmpty()) {
                add("证据还偏薄，先补一条查证或验证记录。")
            }
            if (conclusionLine.isNotBlank() && nextShiftLine.isBlank()) {
                add("结论已经形成，但还没有明确承接动作。")
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
            if (conclusionLine.isNotBlank() && daysSinceUpdate >= 35L) {
                add("当前结论已经有一段时间没被新材料刷新，可能开始变旧。")
            }
            if (daysSinceUpdate >= 21L) {
                add("这条方向已经放缓一段时间，适合重新接上一小步。")
            }
        }.distinct().take(3)

        val healthLine = when {
            hasConflict ->
                "当前先别继续扩写结论，先处理相互打架的查证或验证信号。"
            issues.isEmpty() ->
                "知识层状态稳定，当前已有可复用沉淀，可继续把判断压成更稳的结论。"
            issues.size == 1 ->
                "当前最值得先补的是：${issues.first()}"
            else ->
                "当前最该先补证据、方法和下一步，避免方向只停在判断层。"
        }
        val maintenanceLine = when {
            hasConflict ->
                "先维护证据页：补一条新的查证或验证记录，把相反信号拆开，再重写当前结论。"
            grounding.verifiedItems.isEmpty() && grounding.validatedItems.isEmpty() ->
                "先维护证据页：新增一条查证或验证记录，把判断压实。"
            stage == DirectionStage.FORMING && questionCount == 0 ->
                "先维护问题对象：把这条方向当前最核心的问题单独写出来。"
            methodCount == 0 && experimentCount == 0 ->
                "先维护方法或实验对象：让这条方向开始沉淀可复用做法。"
            conclusionLine.isNotBlank() && nextShiftLine.isBlank() ->
                "先维护结论页：给当前结论补一句下一步怎么接。"
            conclusionLine.isNotBlank() && daysSinceUpdate >= 35L ->
                "先刷新结论页：用一条新的查证、验证或反思刷新旧结论。"
            daysSinceUpdate >= 21L ->
                "先补最近进展：新增一条最近进展，让这条方向重新开始流动。"
            else ->
                "继续维护结论与对象：把当前判断压成更稳的结论，并补一条可复用对象。"
        }

        val maintenanceTargetLine = when {
            hasConflict || grounding.verifiedItems.isEmpty() && grounding.validatedItems.isEmpty() -> "证据页"
            stage == DirectionStage.FORMING && questionCount == 0 -> "问题对象"
            methodCount == 0 && experimentCount == 0 -> "方法 / 实验对象"
            conclusionLine.isNotBlank() && (nextShiftLine.isBlank() || daysSinceUpdate >= 35L) -> "结论页"
            daysSinceUpdate >= 21L -> "方向页"
            else -> "结论与知识对象"
        }
        val maintenanceSourceLine = when {
            hasConflict ->
                "一条新的查证或验证记录"
            grounding.verifiedItems.isEmpty() && grounding.validatedItems.isEmpty() ->
                "一条查证或验证记录"
            stage == DirectionStage.FORMING && questionCount == 0 ->
                "一条明确问题记录"
            methodCount == 0 && experimentCount == 0 ->
                "一条方法、实验或复盘记录"
            conclusionLine.isNotBlank() && nextShiftLine.isBlank() ->
                "一条承接动作记录"
            conclusionLine.isNotBlank() && daysSinceUpdate >= 35L ->
                "一条新的查证、验证或反思记录"
            daysSinceUpdate >= 21L ->
                "一条最近进展记录"
            else ->
                "一条新的查证、验证或方法记录"
        }

        return KnowledgeLayerLintSummary(
            healthLine = healthLine,
            maintenanceLine = maintenanceLine,
            maintenanceTargetLine = maintenanceTargetLine,
            maintenanceSourceLine = maintenanceSourceLine,
            lintIssues = issues,
        )
    }

    private fun hasPolarityConflict(
        grounding: ResearchGroundingSnapshot,
    ): Boolean {
        val evidenceTexts = (grounding.verifiedItems + grounding.validatedItems)
            .map { it.summary }
            .filter { it.isNotBlank() }
        if (evidenceTexts.isEmpty()) return false
        val hasPositive = evidenceTexts.any(::looksPositive)
        val hasNegative = evidenceTexts.any(::looksNegative)
        return hasPositive && hasNegative
    }

    private fun looksPositive(text: String): Boolean =
        positiveMarkers.any { marker -> text.contains(marker, ignoreCase = true) }

    private fun looksNegative(text: String): Boolean =
        negativeMarkers.any { marker -> text.contains(marker, ignoreCase = true) }

    private val positiveMarkers = listOf(
        "成立",
        "有效",
        "可行",
        "适合",
        "验证通过",
        "证明",
        "跑通",
        "有帮助",
        "更好",
    )

    private val negativeMarkers = listOf(
        "不成立",
        "无效",
        "失败",
        "不适合",
        "问题",
        "卡住",
        "风险",
        "不建议",
        "并没有",
        "没效果",
    )
}
