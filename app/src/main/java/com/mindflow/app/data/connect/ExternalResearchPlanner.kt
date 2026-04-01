package com.mindflow.app.data.connect

import com.mindflow.app.data.brief.DailyBriefSource
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate

class ExternalResearchPlanner(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    private val cache = linkedMapOf<String, ExternalResearchSnapshot>()

    suspend fun summarize(
        threadKey: String,
        notes: List<NoteEntity>,
    ): ExternalResearchSnapshot {
        if (notes.isEmpty()) return ExternalResearchSnapshot()

        val signature = buildSignature(threadKey, notes)
        cache[signature]?.let { return it }

        val threadTitle = NoteConnectionAnalyzer.titleForThread(threadKey)
        val fallback = buildRuleSnapshot(threadTitle, notes)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        val resolved = if (settings.aiEnabled && settings.isConfigured) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (
                val result = aiServiceClient.generateExternalResearchSnapshot(
                    settings = settings,
                    contextSummary = buildAiContext(threadTitle, notes, fallback),
                )
            ) {
                is AiChatResult.Success -> {
                    val lines = parseAiLines(result.content)
                    if (lines.size >= 4) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        fallback.copy(
                            outsideAngle = lines.getOrElse(0) { fallback.outsideAngle },
                            opportunityGap = lines.getOrElse(1) { fallback.opportunityGap },
                            contrarianQuestion = lines.getOrElse(2) { fallback.contrarianQuestion },
                            externalHypothesis = lines.getOrElse(3) { fallback.externalHypothesis },
                            queries = lines.drop(4).take(2).ifEmpty { fallback.queries },
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

    private fun buildRuleSnapshot(
        threadTitle: String,
        notes: List<NoteEntity>,
    ): ExternalResearchSnapshot {
        val repeatedTag = notes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val keyword = repeatedTag ?: threadTitle.removePrefix("#").trim()
        return ExternalResearchSnapshot(
            outsideAngle = "先看 2 到 3 个做过类似方向的产品、论文或个人实践，重点看他们是怎么切入问题的。",
            opportunityGap = if (repeatedTag != null) {
                "重点关注「$repeatedTag」在相邻领域里的成熟做法，看有没有可借来的结构。"
            } else {
                "别只看同类产品，也看相邻场景里的成熟方案，突破往往来自跨域借法。"
            },
            contrarianQuestion = "如果外部成熟做法都没有直接解决这个问题，是否说明真正缺的是更小、更可验证的切口？",
            externalHypothesis = "如果外部案例普遍在更小切口上先验证，说明这条方向也更适合先缩成一个具体实验。",
            queries = listOf(
                "$keyword 产品 方案 案例",
                "$keyword workflow design benchmark",
            ).distinct().take(2),
            source = DailyBriefSource.RULE,
        )
    }

    private fun buildAiContext(
        threadTitle: String,
        notes: List<NoteEntity>,
        fallback: ExternalResearchSnapshot,
    ): String {
        val latestNotes = notes.sortedByDescending { it.updatedAt }.take(8)
        return buildString {
            appendLine("你正在为一个长期方向生成外部视角。")
            appendLine("请恰好输出 6 行中文，顺序固定为：")
            appendLine("1. 一个值得看的外部角度")
            appendLine("2. 一个机会缺口")
            appendLine("3. 一个反常识问题或竞争判断")
            appendLine("4. 一个可验证的外部假设")
            appendLine("5. 一个可直接使用的中文搜索词")
            appendLine("6. 一个可直接使用的技术或英文搜索词")
            appendLine("不要编号，不要引用来源，不要空话。")
            appendLine("方向标题：$threadTitle")
            appendLine("记录数：${notes.size} 条")
            appendLine("规则参考：")
            appendLine("外部角度：${fallback.outsideAngle}")
            appendLine("机会缺口：${fallback.opportunityGap}")
            appendLine("反常识问题：${fallback.contrarianQuestion}")
            appendLine("外部假设：${fallback.externalHypothesis}")
            appendLine("最近记录：")
            latestNotes.forEach { note ->
                appendLine("${note.topic.ifBlank { "未命名记录" }}｜${note.content.compactPreview(100)}")
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
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(6)
            .toList()

    private fun buildSignature(threadKey: String, notes: List<NoteEntity>): String =
        "$threadKey:${notes.size}:${notes.maxOfOrNull { it.updatedAt } ?: 0L}"

    private fun putCache(key: String, summary: ExternalResearchSnapshot) {
        cache[key] = summary
        if (cache.size > 48) {
            val firstKey = cache.entries.firstOrNull()?.key ?: return
            cache.remove(firstKey)
        }
    }
}

private fun String.compactPreview(maxLength: Int): String =
    replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
