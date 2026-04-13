package com.mindflow.app.data.wiki

import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

class KnowledgeGraphPlanner(
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiServiceClient: AiServiceClient,
) {
    private val cache = linkedMapOf<String, DirectionWikiGraphSnapshot>()

    suspend fun summarize(
        summaries: List<DirectionWikiDirectionSummary>,
        knowledgeItems: List<KnowledgeLayerSearchItem>,
    ): DirectionWikiGraphSnapshot {
        if (summaries.isEmpty()) return DirectionWikiGraphSnapshot()

        val signature = buildSignature(summaries, knowledgeItems)
        cache[signature]?.let { return it }

        val fallback = buildFallbackGraph(summaries)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        val resolved = if (settings.aiEnabled && settings.isConfigured) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (
                val result = aiServiceClient.generateKnowledgeGraphSnapshot(
                    settings = settings,
                    contextSummary = buildAiContext(summaries, knowledgeItems, fallback),
                )
            ) {
                is AiChatResult.Success -> {
                    val parsed = parseGraphJson(
                        raw = result.content,
                        validThreadKeys = summaries.map { it.threadKey }.toSet(),
                    )
                    if (parsed != null && parsed.nodes.isNotEmpty()) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        parsed.copy(
                            source = "ai",
                            generatedAt = System.currentTimeMillis(),
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

    private fun buildFallbackGraph(
        summaries: List<DirectionWikiDirectionSummary>,
    ): DirectionWikiGraphSnapshot {
        val nodes = summaries
            .sortedByDescending { it.updatedAt }
            .take(5)
            .mapIndexed { index, summary ->
                DirectionWikiGraphNode(
                    threadKey = summary.threadKey,
                    label = compactLabel(summary.title),
                    summaryLine = summary.conclusionLine
                        .ifBlank { summary.assetSummary }
                        .ifBlank { summary.continuityLine }
                        .ifBlank { "这条主题还在继续形成。" },
                    gapLine = summary.openQuestions.firstOrNull()
                        .orEmpty()
                        .ifBlank { summary.nextShiftLine }
                        .ifBlank { "继续补一条更硬的新材料。" },
                    priority = max(1, 5 - index),
                )
            }
        return DirectionWikiGraphSnapshot(
            overviewLine = "先把几条稳定主题看清，再让关系慢慢长出来。",
            nodes = nodes,
            edges = emptyList(),
            source = "rule",
            generatedAt = System.currentTimeMillis(),
        )
    }

    private fun buildAiContext(
        summaries: List<DirectionWikiDirectionSummary>,
        knowledgeItems: List<KnowledgeLayerSearchItem>,
        fallback: DirectionWikiGraphSnapshot,
    ): String {
        val itemsByThread = knowledgeItems
            .filter { it.threadKey.isNotBlank() }
            .groupBy { it.threadKey }
        return buildString {
            appendLine("你正在为一个 AI-first 的个人知识系统生成一张移动端信息图谱。")
            appendLine("目标不是展示所有对象，而是抽出最值得用户看懂的主题结构。")
            appendLine("请只返回 JSON，不要 Markdown，不要解释，不要代码块。")
            appendLine("JSON schema:")
            appendLine("""{"overview":"一句整体判断","nodes":[{"threadKey":"exact key","label":"2到6字主题名","summaryLine":"一句解释这条主题现在在讲什么","gapLine":"一句说明现在最值得补什么","priority":1-5}],"edges":[{"fromThreadKey":"exact key","toThreadKey":"exact key","strength":1-5,"reasonLine":"一句说明两者为何相关"}]}""")
            appendLine("规则：")
            appendLine("- 只能从提供的方向里选节点，绝对不要创造新 threadKey。")
            appendLine("- 节点数量 4 到 6 个。")
            appendLine("- 不要把结论、证据、问题、方法、实验当成节点。节点只能是主题。")
            appendLine("- 边数量 0 到 6 条，只有在关系真实而且对用户有价值时才画。")
            appendLine("- 边的依据只能来自共享概念、共享问题、共享方法/实验、长期共同出现、或明显依赖关系。")
            appendLine("- 如果关系不够硬，就宁可不画边。")
            appendLine("- 文案必须是用户能看懂的话，不能出现维护、证据对象、结论对象之类后台术语。")
            appendLine("已有的保守参考：")
            appendLine("overall=${fallback.overviewLine}")
            fallback.nodes.forEach { node ->
                appendLine("- ${node.threadKey} | ${node.label} | ${node.summaryLine} | ${node.gapLine}")
            }
            appendLine("候选方向：")
            summaries
                .sortedByDescending { it.updatedAt }
                .take(8)
                .forEach { summary ->
                    appendLine("threadKey=${summary.threadKey}")
                    appendLine("title=${summary.title}")
                    appendLine("stage=${summary.stage.label}")
                    appendLine("summary=${summary.conclusionLine.ifBlank { summary.assetSummary.ifBlank { summary.healthLine } }}")
                    appendLine("next=${summary.nextShiftLine.ifBlank { summary.maintenanceFocusLine.ifBlank { summary.maintenanceTargetLine } }}")
                    appendLine("trust=${summary.trustLine}")
                    summary.openQuestions.take(2).forEachIndexed { index, question ->
                        appendLine("open_question_${index + 1}=$question")
                    }
                    itemsByThread[summary.threadKey]
                        .orEmpty()
                        .filter { it.type in aiGraphRelevantTypes }
                        .sortedByDescending { it.updatedAt }
                        .take(6)
                        .forEach { item ->
                            appendLine("knowledge=${item.type.name}:${item.title}:${item.summary.ifBlank { item.supportLine }}")
                        }
                    appendLine()
                }
        }
    }

    private fun parseGraphJson(
        raw: String,
        validThreadKeys: Set<String>,
    ): DirectionWikiGraphSnapshot? {
        val jsonText = extractJsonObject(raw) ?: return null
        return runCatching {
            val root = JSONObject(jsonText)
            val nodes = buildList {
                val array = root.optJSONArray("nodes") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val threadKey = item.optString("threadKey").trim()
                    val label = item.optString("label").trim()
                    if (threadKey !in validThreadKeys || label.isBlank()) continue
                    add(
                        DirectionWikiGraphNode(
                            threadKey = threadKey,
                            label = label.take(12),
                            summaryLine = item.optString("summaryLine").trim(),
                            gapLine = item.optString("gapLine").trim(),
                            priority = item.optInt("priority").coerceIn(1, 5).takeIf { it > 0 } ?: 3,
                        ),
                    )
                }
            }.distinctBy { it.threadKey }.take(6)

            val allowedKeys = nodes.map { it.threadKey }.toSet()
            val edges = buildList {
                val array = root.optJSONArray("edges") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val from = item.optString("fromThreadKey").trim()
                    val to = item.optString("toThreadKey").trim()
                    if (from == to || from !in allowedKeys || to !in allowedKeys) continue
                    add(
                        DirectionWikiGraphEdge(
                            fromThreadKey = from,
                            toThreadKey = to,
                            strength = item.optInt("strength").coerceIn(1, 5).takeIf { it > 0 } ?: 3,
                            reasonLine = item.optString("reasonLine").trim(),
                        ),
                    )
                }
            }.distinctBy { normalizedEdgeKey(it.fromThreadKey, it.toThreadKey) }.take(6)

            DirectionWikiGraphSnapshot(
                overviewLine = root.optString("overview").trim(),
                nodes = nodes,
                edges = edges,
            )
        }.getOrNull()
    }

    private fun extractJsonObject(raw: String): String? {
        val cleaned = raw
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }

    private fun buildSignature(
        summaries: List<DirectionWikiDirectionSummary>,
        knowledgeItems: List<KnowledgeLayerSearchItem>,
    ): String = buildString {
        summaries
            .sortedBy { it.threadKey }
            .forEach { append("${it.threadKey}:${it.updatedAt}:${it.title};") }
        append("|")
        knowledgeItems
            .sortedWith(compareBy<KnowledgeLayerSearchItem> { it.threadKey }.thenBy { it.id }.thenByDescending { it.updatedAt })
            .take(32)
            .forEach { append("${it.id}:${it.updatedAt};") }
    }

    private fun putCache(
        key: String,
        snapshot: DirectionWikiGraphSnapshot,
    ) {
        cache[key] = snapshot
        if (cache.size > 24) {
            val firstKey = cache.entries.firstOrNull()?.key ?: return
            cache.remove(firstKey)
        }
    }

    private fun compactLabel(raw: String): String {
        val firstSegment = raw
            .split('｜', '|', '·', ':', '：', '，', ',', '。', '、')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        val cleaned = firstSegment.replace(
            Regex("^(方向|主线|结论|证据|方法|问题|概念|实验|项目|主题)\\s*"),
            "",
        ).trim().ifBlank { firstSegment }
        return cleaned.take(8).ifBlank { "未命名" }
    }

    private fun normalizedEdgeKey(
        left: String,
        right: String,
    ): String = listOf(left, right).sorted().joinToString("::")

    private companion object {
        val aiGraphRelevantTypes = setOf(
            KnowledgeLayerSearchType.CONCEPT,
            KnowledgeLayerSearchType.QUESTION,
            KnowledgeLayerSearchType.METHOD,
            KnowledgeLayerSearchType.EXPERIMENT,
        )
    }
}
