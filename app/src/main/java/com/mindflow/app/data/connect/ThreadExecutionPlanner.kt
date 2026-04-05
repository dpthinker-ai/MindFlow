package com.mindflow.app.data.connect

import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate

class ThreadExecutionPlanner(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    private val cache = linkedMapOf<String, ThreadExecutionSummary>()

    suspend fun summarize(
        threadKey: String,
        notes: List<NoteEntity>,
    ): ThreadExecutionSummary {
        if (notes.isEmpty()) return ThreadExecutionSummary()

        val signature = buildSignature(threadKey, notes)
        cache[signature]?.let { return it }

        val threadTitle = NoteConnectionAnalyzer.titleForThread(threadKey)
        val fallback = buildRuleSummary(threadTitle, notes)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        val resolved = if (settings.aiEnabled && settings.isConfigured) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (
                val result = aiServiceClient.generateThreadExecutionSummary(
                    settings = settings,
                    contextSummary = buildAiContext(threadTitle, notes, fallback),
                )
            ) {
                is AiChatResult.Success -> {
                    val lines = parseAiLines(result.content)
                    if (lines.size >= 7) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        fallback.copy(
                            summary = lines.getOrElse(0) { fallback.summary },
                            blocker = lines.getOrElse(1) { fallback.blocker },
                            whyNow = lines.getOrElse(2) { fallback.whyNow },
                            nextStep = lines.getOrElse(3) { fallback.nextStep },
                            validationStep = lines.getOrElse(4) { fallback.validationStep },
                            validationReason = lines.getOrElse(5) { fallback.validationReason },
                            postValidationAction = lines.getOrElse(6) { fallback.postValidationAction },
                            source = DailyBriefSource.AI,
                        )
                    } else {
                        fallback
                    }
                }

                is AiChatResult.Failure -> fallback
            }
        } else {
            fallback
        }

        putCache(signature, resolved)
        return resolved
    }

    private fun buildRuleSummary(
        threadTitle: String,
        notes: List<NoteEntity>,
    ): ThreadExecutionSummary {
        val focusNote = pickFocusNote(notes)
        val rhythm = DirectionRhythmAnalyzer.analyze(
            notes = notes,
            focusNote = focusNote,
            hasResearch = notes.any(ThreadResearchAnalyzer::isResearchMemoryNote),
        )
        val loop = DirectionExecutionLoopAnalyzer.summarize(
            notes = notes,
            focusNote = focusNote,
            stage = rhythm.stage,
            dominantHorizon = rhythm.dominantHorizon,
        )
        val researchLead = ThreadResearchAnalyzer.buildResearchClusters(
            notes = notes.filter(ThreadResearchAnalyzer::isResearchMemoryNote).take(3),
            threadTitle = threadTitle,
        ).firstOrNull()
        val repeatedTag = notes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val summary = when {
            notes.any { it.status == NoteStatus.IN_PROGRESS } ->
                "这条方向已经进入持续推进阶段，关键是把最近的判断压成可验证动作。"
            notes.size >= 4 ->
                "这条方向已经形成稳定主线，接下来重点不在多记，而在收敛并验证。"
            else ->
                "这条方向还在形成期，先把最值得做的一条记录压成明确动作。"
        }
        val blocker = when {
            researchLead?.validationStep?.isNotBlank() == true ->
                "最大的卡点不是没有线索，而是还没把研究主线真正压成一次可验证动作。"
            repeatedTag != null ->
                "最大的卡点是围绕「$repeatedTag」还没有形成稳定的问题定义和验证标准。"
            focusNote != null ->
                "最大的卡点是最近这条焦点记录还停在描述层，没有被压成执行动作。"
            else ->
                "最大的卡点是有效记录还不够密集，暂时难以形成稳定推进节奏。"
        }
        val whyNow = when {
            focusNote?.status == NoteStatus.IN_PROGRESS ->
                "这条方向已经有正在推进的抓手，趁上下文还新，继续推进成本最低。"
            researchLead?.followUpReason?.isNotBlank() == true ->
                researchLead.followUpReason
            notes.size >= 3 ->
                "${rhythm.stageReason} 现在最值得先做一次小验证。"
            else ->
                "${rhythm.stageReason} 比继续积累更多想法更值钱。"
        }
        val nextStep = when {
            focusNote != null -> focusNote.topic.ifBlank { "先把当前焦点记录补成一个最小动作" }
            else -> "先补一条更具体的记录，把这条方向从想法压成动作。"
        }.let { focus ->
            when {
                focusNote != null -> "围绕「$focus」先补一句最新进展，再明确今天这一步。"
                else -> focus
            }
        }
        val validationStep = researchLead?.validationStep
            ?.takeIf { it.isNotBlank() }
            ?: when {
                focusNote != null ->
                    "把「${focusNote.topic.ifBlank { "当前焦点" }}」压成一次可验证的最小实验，写清预期和结果判断。"
                else ->
                    "先把这个方向压成一个最小验证问题，明确看什么结果算成立。"
            }
        val validationReason = researchLead?.followUpReason
            ?.takeIf { it.isNotBlank() }
            ?: if (focusNote?.status == NoteStatus.IN_PROGRESS) {
                "已经有推进中的抓手，现在验证一小步最容易把方向继续往前拱。"
            } else {
                "先验证一小步，比继续摊开更多想法更能让方向真正往前走。"
            }
        val postValidationAction = researchLead?.executionPrompt
            ?.takeIf { it.isNotBlank() }
            ?: if (focusNote != null) {
                "如果验证成立，就把结果沉淀成一条新的推进记录，并继续沿着这条方向压实。"
            } else {
                "如果验证成立，就补一条推进记录，把方向从判断推进到实际动作。"
            }

        return ThreadExecutionSummary(
            focusNoteId = focusNote?.id,
            summary = summary,
            blocker = blocker,
            stage = rhythm.stage,
            stageReason = rhythm.stageReason,
            rhythmLine = rhythm.rhythmLine,
            dominantHorizon = rhythm.dominantHorizon,
            whyNow = whyNow,
            lastProgressLine = loop.lastProgressLine,
            nextStep = nextStep,
            nextCheckInLine = loop.nextCheckInLine,
            validationStep = validationStep,
            validationReason = validationReason,
            postValidationAction = postValidationAction,
            source = DailyBriefSource.RULE,
        )
    }

    private fun buildAiContext(
        threadTitle: String,
        notes: List<NoteEntity>,
        fallback: ThreadExecutionSummary,
    ): String {
        val latestNotes = notes.sortedByDescending { it.updatedAt }.take(8)
        val researchLead = ThreadResearchAnalyzer.buildResearchClusters(
            notes = notes.filter(ThreadResearchAnalyzer::isResearchMemoryNote).take(3),
            threadTitle = threadTitle,
        ).firstOrNull()
        return buildString {
            appendLine("你正在为一个长期方向生成执行摘要。")
            appendLine("请恰好输出 7 行中文，顺序固定为：")
            appendLine("1. 当前方向总结")
            appendLine("2. 当前最大卡点")
            appendLine("3. 为什么现在值得推进")
            appendLine("4. 当前最小动作")
            appendLine("5. 当前验证动作")
            appendLine("6. 为什么现在做这次验证")
            appendLine("7. 如果验证成立，下一步怎么推进")
            appendLine("不要编号，不要空话，不要复述原文。")
            appendLine("方向标题：$threadTitle")
            appendLine("记录数：${notes.size} 条；进行中 ${notes.count { it.status == NoteStatus.IN_PROGRESS }} 条；已实现 ${notes.count { it.status == NoteStatus.DONE }} 条。")
            researchLead?.let {
                appendLine("当前研究主线：${it.label}")
                appendLine("当前研究验证：${it.validationStep}")
            }
            appendLine("规则参考：")
            appendLine("总结：${fallback.summary}")
            appendLine("卡点：${fallback.blocker}")
            appendLine("为什么现在：${fallback.whyNow}")
            appendLine("最近推进：${fallback.lastProgressLine}")
            appendLine("最小动作：${fallback.nextStep}")
            appendLine("下次检查：${fallback.nextCheckInLine}")
            appendLine("验证动作：${fallback.validationStep}")
            appendLine("验证原因：${fallback.validationReason}")
            appendLine("验证成立后：${fallback.postValidationAction}")
            appendLine("最近记录：")
            latestNotes.forEach { note ->
                appendLine("${note.topic.ifBlank { "未命名记录" }}｜${note.status.label}｜${note.content.compactPreview(120)}")
            }
        }
    }

    private fun parseAiLines(raw: String): List<String> =
        raw.replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                it.removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("1.")
                    .removePrefix("2.")
                    .removePrefix("3.")
                    .removePrefix("4.")
                    .removePrefix("5.")
                    .removePrefix("6.")
                    .removePrefix("7.")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(7)
            .toList()

    private fun buildSignature(threadKey: String, notes: List<NoteEntity>): String =
        "$threadKey:${notes.size}:${notes.maxOfOrNull { it.updatedAt } ?: 0L}"

    private fun putCache(key: String, summary: ThreadExecutionSummary) {
        cache[key] = summary
        if (cache.size > 48) {
            val firstKey = cache.entries.firstOrNull()?.key ?: return
            cache.remove(firstKey)
        }
    }

    private fun pickFocusNote(notes: List<NoteEntity>): NoteEntity? =
        notes
            .filter { it.status == NoteStatus.IN_PROGRESS }
            .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
            .firstOrNull()
            ?: notes
                .filter { it.status == NoteStatus.IDEA }
                .sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt })
                .firstOrNull()
            ?: notes.sortedWith(compareByDescending<NoteEntity> { it.horizon.priority }.thenByDescending { it.updatedAt }).firstOrNull()
}

private fun String.compactPreview(maxLength: Int): String =
    replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
