package com.mindflow.app.data.wiki

import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import java.time.LocalDate
import kotlin.math.roundToInt
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
        threadNoteCounts: Map<String, Int>,
    ): DirectionWikiGraphSnapshot {
        if (summaries.isEmpty()) return DirectionWikiGraphSnapshot()

        val signature = buildSignature(summaries, knowledgeItems, threadNoteCounts)
        cache[signature]?.let { return it }

        val generatedAt = System.currentTimeMillis()
        val canonical = buildCanonicalGraph(
            summaries = summaries,
            knowledgeItems = knowledgeItems,
            threadNoteCounts = threadNoteCounts,
            generatedAt = generatedAt,
        )
        val fallbackPresentation = buildFallbackPresentation(canonical)
        val settings = aiSettingsRepository.getCurrent()
        val dayKey = LocalDate.now().toString()

        val (presentation, source) = if (settings.aiEnabled && settings.isConfigured) {
            aiSettingsRepository.recordUsage(
                requestIncrement = 1,
                dayKey = dayKey,
            )
            when (
                val result = aiServiceClient.generateKnowledgeGraphSnapshot(
                    settings = settings,
                    contextSummary = buildPresentationAiContext(canonical),
                )
            ) {
                is AiChatResult.Success -> {
                    val parsed = parseGraphPresentationJson(
                        raw = result.content,
                        canonical = canonical,
                    )
                    if (parsed != null && parsed.nodes.isNotEmpty()) {
                        aiSettingsRepository.recordUsage(
                            successIncrement = 1,
                            tokenIncrement = result.totalTokens ?: 0,
                            dayKey = dayKey,
                        )
                        parsed to "llm+rule"
                    } else {
                        fallbackPresentation to "rule"
                    }
                }

                is AiChatResult.Failure -> fallbackPresentation to "rule"
            }
        } else {
            fallbackPresentation to "rule"
        }

        val resolved = canonical.copy(
            presentation = presentation,
            source = source,
            generatedAt = generatedAt,
        )
        putCache(signature, resolved)
        return resolved
    }

    private fun buildCanonicalGraph(
        summaries: List<DirectionWikiDirectionSummary>,
        knowledgeItems: List<KnowledgeLayerSearchItem>,
        threadNoteCounts: Map<String, Int>,
        generatedAt: Long,
    ): DirectionWikiGraphSnapshot {
        val itemsByThread = knowledgeItems
            .filter { it.threadKey.isNotBlank() }
            .groupBy { it.threadKey }
        val canonicalEdges = buildCanonicalEdges(
            summaries = summaries,
            knowledgeItems = knowledgeItems,
        )
        val relationCountByThread = buildMap {
            canonicalEdges.forEach { edge ->
                put(edge.fromThreadKey, getOrDefault(edge.fromThreadKey, 0) + 1)
                put(edge.toThreadKey, getOrDefault(edge.toThreadKey, 0) + 1)
            }
        }

        val orderedSummaries = summaries.sortedByDescending { it.updatedAt }
        val recencyRaw = orderedSummaries.associate { summary ->
            summary.threadKey to summary.updatedAt.coerceAtLeast(0L).toDouble()
        }
        val densityRaw = orderedSummaries.associate { summary ->
            val itemCount = itemsByThread[summary.threadKey].orEmpty().size
            val noteCount = threadNoteCounts[summary.threadKey] ?: 0
            val relationCount = relationCountByThread[summary.threadKey] ?: 0
            val stageBonus = when (summary.stage) {
                DirectionStage.SETTLING -> 3.0
                DirectionStage.ADVANCING -> 2.0
                DirectionStage.VALIDATING -> 1.5
                DirectionStage.FORMING -> 1.0
            }
            summary.threadKey to ((noteCount * 1.2) + itemCount + (relationCount * 2.0) + stageBonus)
        }
        val recencyScoreByThread = normalizeScores(recencyRaw)
        val densityScoreByThread = normalizeScores(densityRaw)

        val nodes = orderedSummaries.map { summary ->
            val threadItems = itemsByThread[summary.threadKey].orEmpty()
            val noteCount = threadNoteCounts[summary.threadKey] ?: 0
            DirectionWikiGraphNode(
                threadKey = summary.threadKey,
                label = compactLabel(summary.title),
                summaryLine = nodeSummaryLine(summary),
                gapLine = nodeGapLine(summary),
                maturity = summary.stage.toGraphMaturity(),
                recencyScore = recencyScoreByThread[summary.threadKey] ?: 0.0,
                densityScore = densityScoreByThread[summary.threadKey] ?: 0.0,
                supportIds = buildList {
                    add("direction:${summary.threadKey}")
                    addAll(threadItems.sortedByDescending { it.updatedAt }.take(6).map { it.id })
                }.distinct(),
                noteCount = noteCount,
                updatedAt = summary.updatedAt,
            )
        }
        val overview = buildGraphOverview(nodes, canonicalEdges)
        return DirectionWikiGraphSnapshot(
            version = 2,
            overview = overview,
            nodes = nodes,
            edges = canonicalEdges,
            source = "rule",
            generatedAt = generatedAt,
        )
    }

    private fun buildCanonicalEdges(
        summaries: List<DirectionWikiDirectionSummary>,
        knowledgeItems: List<KnowledgeLayerSearchItem>,
    ): List<DirectionWikiGraphEdge> {
        val validThreadKeys = summaries.map { it.threadKey }.toSet()
        val relevantItems = knowledgeItems.filter { item ->
            item.threadKey in validThreadKeys && item.type in graphRelevantTypes
        }
        val aggregates = linkedMapOf<String, EdgeAccumulator>()

        relevantItems
            .groupBy { item -> item.type to normalizeGraphPhrase(item.title) }
            .forEach { (key, items) ->
                val normalizedTitle = key.second
                if (normalizedTitle.isBlank()) return@forEach
                val itemsByThread = items
                    .groupBy { it.threadKey }
                    .filterKeys { it.isNotBlank() }
                val threadKeys = itemsByThread.keys.sorted()
                if (threadKeys.size < 2) return@forEach
                threadKeys.forEachIndexed { index, left ->
                    threadKeys.drop(index + 1).forEach { right ->
                        val leftItems = itemsByThread[left].orEmpty()
                        val rightItems = itemsByThread[right].orEmpty()
                        if (leftItems.isEmpty() || rightItems.isEmpty()) return@forEach
                        val edgeKey = normalizedEdgeKey(left, right)
                        val accumulator = aggregates.getOrPut(edgeKey) {
                            EdgeAccumulator(
                                fromThreadKey = left,
                                toThreadKey = right,
                            )
                        }
                        val relationType = key.first.toRelationType()
                        val groupWeight = key.first.toRelationWeight() * minOf(leftItems.size, rightItems.size)
                        accumulator.score += groupWeight
                        accumulator.registerRelation(
                            type = relationType,
                            score = groupWeight,
                            phrase = leftItems.firstOrNull()?.title
                                ?.ifBlank { rightItems.firstOrNull()?.title.orEmpty() }
                                .orEmpty(),
                        )
                        accumulator.supportIds += (leftItems + rightItems).map { it.id }
                        val timestamps = (leftItems + rightItems).map { it.updatedAt }.filter { it > 0L }
                        if (timestamps.isNotEmpty()) {
                            val minSeen = timestamps.minOrNull() ?: 0L
                            val maxSeen = timestamps.maxOrNull() ?: 0L
                            accumulator.firstSeenAt = minOf(accumulator.firstSeenAt, minSeen)
                            accumulator.lastSeenAt = maxOf(accumulator.lastSeenAt, maxSeen)
                        }
                    }
                }
            }

        return aggregates.values
            .map { it.toEdge() }
            .sortedWith(
                compareByDescending<DirectionWikiGraphEdge> { it.strength }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.lastSeenAt }
                    .thenBy { it.fromThreadKey + it.toThreadKey },
            )
            .take(MaxCanonicalGraphEdges)
    }

    private fun buildGraphOverview(
        nodes: List<DirectionWikiGraphNode>,
        edges: List<DirectionWikiGraphEdge>,
    ): DirectionWikiGraphOverview {
        if (nodes.isEmpty()) return DirectionWikiGraphOverview()
        val degreeByThread = buildMap {
            edges.forEach { edge ->
                put(edge.fromThreadKey, getOrDefault(edge.fromThreadKey, 0) + 1)
                put(edge.toThreadKey, getOrDefault(edge.toThreadKey, 0) + 1)
            }
        }
        val hubs = nodes
            .sortedByDescending { degreeByThread[it.threadKey] ?: 0 }
            .filter { (degreeByThread[it.threadKey] ?: 0) > 0 }
            .take(2)
            .map { it.threadKey }
        val isolated = nodes
            .filter { (degreeByThread[it.threadKey] ?: 0) == 0 }
            .sortedByDescending { it.recencyScore }
            .map { it.threadKey }
        val densifying = nodes
            .sortedWith(compareByDescending<DirectionWikiGraphNode> { it.densityScore }.thenByDescending { it.recencyScore })
            .take(3)
            .map { it.threadKey }
        val missingLinks = buildMissingLinkCandidates(nodes, edges)

        val summaryLine = when {
            edges.isEmpty() -> "${nodes.size} 个主题已经浮出来了，但关系还在形成。"
            hubs.isNotEmpty() -> "${nodes.size} 个主题里，${hubs.size} 个已经开始成为结构中心。"
            else -> "${nodes.size} 个主题正在慢慢连起来。"
        }

        return DirectionWikiGraphOverview(
            summaryLine = summaryLine,
            hubThreadKeys = hubs,
            isolatedThreadKeys = isolated,
            densifyingThreadKeys = densifying,
            missingLinkCandidates = missingLinks,
        )
    }

    private fun buildMissingLinkCandidates(
        nodes: List<DirectionWikiGraphNode>,
        edges: List<DirectionWikiGraphEdge>,
    ): List<String> {
        val existing = edges.map { normalizedEdgeKey(it.fromThreadKey, it.toThreadKey) }.toSet()
        return nodes
            .flatMapIndexed { index, left ->
                nodes.drop(index + 1).mapNotNull { right ->
                    val edgeKey = normalizedEdgeKey(left.threadKey, right.threadKey)
                    if (edgeKey in existing) return@mapNotNull null
                    val overlap = graphTokenOverlap(left, right)
                    if (overlap <= 0) return@mapNotNull null
                    Triple(left, right, overlap)
                }
            }
            .sortedByDescending { it.third }
            .take(3)
            .map { (left, right, _) -> "${left.label} ↔ ${right.label}" }
    }

    private fun graphTokenOverlap(
        left: DirectionWikiGraphNode,
        right: DirectionWikiGraphNode,
    ): Int {
        val leftTokens = graphTokens(listOf(left.label, left.summaryLine, left.gapLine))
        val rightTokens = graphTokens(listOf(right.label, right.summaryLine, right.gapLine))
        return leftTokens.intersect(rightTokens).count()
    }

    private fun buildFallbackPresentation(
        canonical: DirectionWikiGraphSnapshot,
    ): DirectionWikiGraphPresentationSnapshot {
        val visibleNodes = canonical.nodes
            .sortedWith(
                compareByDescending<DirectionWikiGraphNode> { it.densityScore }
                    .thenByDescending { it.recencyScore }
                    .thenByDescending { it.noteCount }
                    .thenBy { it.label },
            )
            .take(MaxPresentationNodes)
        if (visibleNodes.isEmpty()) return DirectionWikiGraphPresentationSnapshot()

        val visibleIds = visibleNodes.map { it.threadKey }.toSet()
        val visibleEdges = canonical.edges
            .filter { it.fromThreadKey in visibleIds && it.toThreadKey in visibleIds }
            .sortedWith(
                compareByDescending<DirectionWikiGraphEdge> { it.strength }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.lastSeenAt },
            )
            .take(MaxPresentationEdges)
        val relationCountByThread = buildMap {
            visibleEdges.forEach { edge ->
                put(edge.fromThreadKey, getOrDefault(edge.fromThreadKey, 0) + 1)
                put(edge.toThreadKey, getOrDefault(edge.toThreadKey, 0) + 1)
            }
        }
        val presentationNodes = visibleNodes.map { node ->
            DirectionWikiGraphPresentationNode(
                threadKey = node.threadKey,
                label = node.label,
                summaryLine = node.summaryLine,
                gapLine = node.gapLine,
                relationCount = relationCountByThread[node.threadKey] ?: 0,
                densityScore = node.densityScore,
                maturity = node.maturity,
                noteCount = node.noteCount,
            )
        }
        val focusNode = presentationNodes
            .sortedWith(
                compareByDescending<DirectionWikiGraphPresentationNode> { it.relationCount }
                    .thenByDescending { it.densityScore }
                    .thenByDescending { it.noteCount },
            )
            .firstOrNull()
        val focusEdge = focusNode?.let { node ->
            visibleEdges
                .filter { it.fromThreadKey == node.threadKey || it.toThreadKey == node.threadKey }
                .maxWithOrNull(
                    compareBy<DirectionWikiGraphEdge> { it.strength }
                        .thenBy { it.confidence },
                )
        }
        val relatedThreadKey = focusEdge?.let { edge ->
            if (edge.fromThreadKey == focusNode?.threadKey) edge.toThreadKey else edge.fromThreadKey
        }.orEmpty()
        val relatedNode = presentationNodes.firstOrNull { it.threadKey == relatedThreadKey }

        return DirectionWikiGraphPresentationSnapshot(
            headline = buildString {
                append("${presentationNodes.size} 个主题")
                if (visibleEdges.isNotEmpty()) {
                    append(" · ${visibleEdges.size} 条关系")
                }
            },
            summaryLine = canonical.overview.summaryLine,
            nodes = presentationNodes,
            edges = visibleEdges.map { edge ->
                DirectionWikiGraphPresentationEdge(
                    fromThreadKey = edge.fromThreadKey,
                    toThreadKey = edge.toThreadKey,
                    strength = edge.strength,
                    reasonLine = edge.reasonLine,
                )
            },
            focus = focusNode?.let { node ->
                DirectionWikiGraphPresentationFocus(
                    threadKey = node.threadKey,
                    label = node.label,
                    summaryLine = node.summaryLine,
                    gapLine = node.gapLine,
                    relatedThreadKey = relatedNode?.threadKey.orEmpty(),
                    relatedReasonLine = focusEdge?.reasonLine.orEmpty(),
                )
            },
        )
    }

    private fun buildPresentationAiContext(
        canonical: DirectionWikiGraphSnapshot,
    ): String = buildString {
        appendLine("你正在为移动端生成一张“信息图谱”卡片。")
        appendLine("你拿到的是一份已经裁决过的正式信息图谱结构。")
        appendLine("目标不是展示所有结构，而是让用户在一屏内看懂当前最重要的主题关系。")
        appendLine("请只返回 JSON，不要 Markdown，不要解释，不要代码块。")
        appendLine("JSON schema:")
        appendLine(
            """{"headline":"一句短标题","summaryLine":"一句整体结构判断","nodes":[{"threadKey":"exact key","label":"主题名","summaryLine":"一句说明","gapLine":"一句缺口","relationCount":0,"densityScore":0.0,"maturity":"forming|strengthening|stable","noteCount":0}],"edges":[{"fromThreadKey":"exact key","toThreadKey":"exact key","strength":1-5,"reasonLine":"一句关系解释"}],"focus":{"threadKey":"exact key","label":"主题名","summaryLine":"一句说明","gapLine":"一句缺口","relatedThreadKey":"exact key or empty","relatedReasonLine":"一句关系解释或空"}}""",
        )
        appendLine("规则：")
        appendLine("- 节点只能从提供的 canonical nodes 里选。")
        appendLine("- 边只能从提供的 canonical edges 里选，绝对不要创造新边。")
        appendLine("- 节点数量 4 到 $MaxPresentationNodes 个。")
        appendLine("- 边数量 0 到 $MaxPresentationEdges 条。")
        appendLine("- 文案必须是用户能看懂的话，不能出现维护、证据对象、结论对象等后台术语。")
        appendLine("- 如果关系不够硬，宁可不画边。")
        appendLine("- focus 必须指向 nodes 里的一个主题。")
        appendLine("正式 overview：")
        appendLine("summary=${canonical.overview.summaryLine}")
        if (canonical.overview.hubThreadKeys.isNotEmpty()) {
            appendLine("hubs=${canonical.overview.hubThreadKeys.joinToString(",")}")
        }
        if (canonical.overview.isolatedThreadKeys.isNotEmpty()) {
            appendLine("isolated=${canonical.overview.isolatedThreadKeys.joinToString(",")}")
        }
        if (canonical.overview.densifyingThreadKeys.isNotEmpty()) {
            appendLine("densifying=${canonical.overview.densifyingThreadKeys.joinToString(",")}")
        }
        if (canonical.overview.missingLinkCandidates.isNotEmpty()) {
            appendLine("missing=${canonical.overview.missingLinkCandidates.joinToString(" | ")}")
        }
        appendLine("canonical nodes:")
        canonical.nodes.forEach { node ->
            appendLine(
                "threadKey=${node.threadKey} | label=${node.label} | maturity=${node.maturity.wireName} | density=${node.densityScore.formatScore()} | recency=${node.recencyScore.formatScore()} | noteCount=${node.noteCount}",
            )
            appendLine("summary=${node.summaryLine}")
            appendLine("gap=${node.gapLine}")
        }
        appendLine("canonical edges:")
        if (canonical.edges.isEmpty()) {
            appendLine("none")
        } else {
            canonical.edges.forEach { edge ->
                appendLine(
                    "from=${edge.fromThreadKey} | to=${edge.toThreadKey} | relationType=${edge.relationType.wireName} | strength=${edge.strength} | confidence=${edge.confidence.formatScore()} | reason=${edge.reasonLine}",
                )
            }
        }
    }

    private fun parseGraphPresentationJson(
        raw: String,
        canonical: DirectionWikiGraphSnapshot,
    ): DirectionWikiGraphPresentationSnapshot? {
        val jsonText = extractJsonObject(raw) ?: return null
        return runCatching {
            val root = JSONObject(jsonText)
            val canonicalNodesByThread = canonical.nodes.associateBy { it.threadKey }
            val allowedKeys = canonicalNodesByThread.keys
            val allowedEdges = canonical.edges
                .map { normalizedEdgeKey(it.fromThreadKey, it.toThreadKey) }
                .toSet()
            val nodes = buildList {
                val array = root.optJSONArray("nodes") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val threadKey = item.optString("threadKey").trim()
                    val base = canonicalNodesByThread[threadKey] ?: continue
                    add(
                        DirectionWikiGraphPresentationNode(
                            threadKey = threadKey,
                            label = item.optString("label").trim().ifBlank { base.label }.take(12),
                            summaryLine = item.optString("summaryLine").trim().ifBlank { base.summaryLine },
                            gapLine = item.optString("gapLine").trim().ifBlank { base.gapLine },
                            relationCount = item.optInt("relationCount").coerceAtLeast(0),
                            densityScore = item.optDouble("densityScore").takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0) ?: base.densityScore,
                            maturity = item.optString("maturity").toGraphMaturity(base.maturity),
                            noteCount = item.optInt("noteCount").takeIf { it >= 0 } ?: base.noteCount,
                        ),
                    )
                }
            }.distinctBy { it.threadKey }.take(MaxPresentationNodes)
            if (nodes.isEmpty()) return null

            val visibleIds = nodes.map { it.threadKey }.toSet()
            val edges = buildList {
                val array = root.optJSONArray("edges") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val from = item.optString("fromThreadKey").trim()
                    val to = item.optString("toThreadKey").trim()
                    if (from !in visibleIds || to !in visibleIds || from == to) continue
                    if (normalizedEdgeKey(from, to) !in allowedEdges) continue
                    add(
                        DirectionWikiGraphPresentationEdge(
                            fromThreadKey = from,
                            toThreadKey = to,
                            strength = item.optInt("strength").coerceIn(1, 5).takeIf { it > 0 } ?: 3,
                            reasonLine = item.optString("reasonLine").trim(),
                        ),
                    )
                }
            }.distinctBy { normalizedEdgeKey(it.fromThreadKey, it.toThreadKey) }.take(MaxPresentationEdges)

            val relationCountByThread = buildMap {
                edges.forEach { edge ->
                    put(edge.fromThreadKey, getOrDefault(edge.fromThreadKey, 0) + 1)
                    put(edge.toThreadKey, getOrDefault(edge.toThreadKey, 0) + 1)
                }
            }
            val normalizedNodes = nodes.map { node ->
                if (node.relationCount > 0) {
                    node
                } else {
                    node.copy(relationCount = relationCountByThread[node.threadKey] ?: 0)
                }
            }

            val focusObject = root.optJSONObject("focus")
            val focus = focusObject?.optString("threadKey")
                ?.takeIf { it in visibleIds }
                ?.let { threadKey ->
                    val node = normalizedNodes.first { it.threadKey == threadKey }
                    DirectionWikiGraphPresentationFocus(
                        threadKey = threadKey,
                        label = focusObject.optString("label").trim().ifBlank { node.label },
                        summaryLine = focusObject.optString("summaryLine").trim().ifBlank { node.summaryLine },
                        gapLine = focusObject.optString("gapLine").trim().ifBlank { node.gapLine },
                        relatedThreadKey = focusObject.optString("relatedThreadKey").trim().takeIf { it in visibleIds }.orEmpty(),
                        relatedReasonLine = focusObject.optString("relatedReasonLine").trim(),
                    )
                }
                ?: buildFallbackPresentation(
                    canonical.copy(
                        presentation = DirectionWikiGraphPresentationSnapshot(
                            nodes = normalizedNodes,
                            edges = edges,
                        ),
                    ),
                ).focus

            DirectionWikiGraphPresentationSnapshot(
                headline = root.optString("headline").trim().ifBlank {
                    buildString {
                        append("${normalizedNodes.size} 个主题")
                        if (edges.isNotEmpty()) append(" · ${edges.size} 条关系")
                    }
                },
                summaryLine = root.optString("summaryLine").trim().ifBlank { canonical.overview.summaryLine },
                nodes = normalizedNodes,
                edges = edges,
                focus = focus,
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
        threadNoteCounts: Map<String, Int>,
    ): String = buildString {
        summaries
            .sortedBy { it.threadKey }
            .forEach { append("${it.threadKey}:${it.updatedAt}:${it.title};") }
        append("|")
        threadNoteCounts
            .toSortedMap()
            .forEach { (threadKey, count) -> append("$threadKey:$count;") }
        append("|")
        knowledgeItems
            .sortedWith(compareBy<KnowledgeLayerSearchItem> { it.threadKey }.thenBy { it.id }.thenByDescending { it.updatedAt })
            .take(48)
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

    private fun nodeSummaryLine(summary: DirectionWikiDirectionSummary): String =
        summary.conclusionLine
            .ifBlank { summary.assetSummary }
            .ifBlank { summary.continuityLine }
            .ifBlank { summary.healthLine }
            .ifBlank { "这条主题还在继续长。" }

    private fun nodeGapLine(summary: DirectionWikiDirectionSummary): String =
        summary.openQuestions.firstOrNull()
            .orEmpty()
            .ifBlank { summary.nextShiftLine }
            .ifBlank { summary.maintenanceFocusLine }
            .ifBlank { summary.maintenanceTargetLine }
            .ifBlank { "继续补一条更硬的新材料。" }

    private fun DirectionStage.toGraphMaturity(): DirectionWikiGraphMaturity =
        when (this) {
            DirectionStage.FORMING -> DirectionWikiGraphMaturity.FORMING
            DirectionStage.VALIDATING,
            DirectionStage.ADVANCING,
            -> DirectionWikiGraphMaturity.STRENGTHENING
            DirectionStage.SETTLING -> DirectionWikiGraphMaturity.STABLE
        }

    private fun KnowledgeLayerSearchType.toRelationType(): DirectionWikiGraphRelationType =
        when (this) {
            KnowledgeLayerSearchType.CONCEPT -> DirectionWikiGraphRelationType.SHARED_CONCEPT
            KnowledgeLayerSearchType.QUESTION -> DirectionWikiGraphRelationType.SHARED_QUESTION
            KnowledgeLayerSearchType.METHOD -> DirectionWikiGraphRelationType.SHARED_METHOD
            KnowledgeLayerSearchType.EXPERIMENT -> DirectionWikiGraphRelationType.SHARED_EXPERIMENT
            else -> DirectionWikiGraphRelationType.CO_OCCURRENCE
        }

    private fun KnowledgeLayerSearchType.toRelationWeight(): Int =
        when (this) {
            KnowledgeLayerSearchType.CONCEPT -> 3
            KnowledgeLayerSearchType.METHOD -> 3
            KnowledgeLayerSearchType.EXPERIMENT -> 3
            KnowledgeLayerSearchType.QUESTION -> 2
            else -> 1
        }

    private fun normalizeGraphPhrase(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^(结论|证据|问题|方法|实验|概念)\\s*[:：-]?\\s*"), "")
            .trim()

    private fun normalizeScores(
        raw: Map<String, Double>,
    ): Map<String, Double> {
        if (raw.isEmpty()) return emptyMap()
        val values = raw.values
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        if (max <= min) return raw.mapValues { 1.0 }
        return raw.mapValues { (_, value) ->
            ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        }
    }

    private fun graphTokens(lines: List<String>): Set<String> =
        Regex("[\\p{IsHan}]{2,}|[a-z0-9]{3,}")
            .findAll(lines.joinToString(" ").lowercase())
            .map { it.value.trim() }
            .filter { it.isNotBlank() && it !in graphStopTokens }
            .toSet()

    private fun normalizedEdgeKey(
        left: String,
        right: String,
    ): String = listOf(left, right).sorted().joinToString("::")

    private fun Double.formatScore(): String = ((this.coerceIn(0.0, 1.0) * 100.0).roundToInt() / 100.0).toString()

    private data class EdgeAccumulator(
        val fromThreadKey: String,
        val toThreadKey: String,
        var score: Int = 0,
        var strongestType: DirectionWikiGraphRelationType = DirectionWikiGraphRelationType.CO_OCCURRENCE,
        var strongestTypeScore: Int = 0,
        val supportIds: MutableSet<String> = linkedSetOf(),
        val phrases: MutableSet<String> = linkedSetOf(),
        var firstSeenAt: Long = Long.MAX_VALUE,
        var lastSeenAt: Long = 0L,
    ) {
        fun registerRelation(
            type: DirectionWikiGraphRelationType,
            score: Int,
            phrase: String,
        ) {
            if (score > strongestTypeScore) {
                strongestType = type
                strongestTypeScore = score
            }
            phrase.takeIf { it.isNotBlank() }?.let { phrases += it.take(20) }
        }

        fun toEdge(): DirectionWikiGraphEdge {
            val boundedScore = score.coerceAtLeast(1)
            val strength = boundedScore.coerceIn(1, 5)
            val confidence = (0.42 + (boundedScore * 0.1)).coerceIn(0.0, 0.95)
            val phrase = phrases.firstOrNull().orEmpty()
            val reasonLine = when (strongestType) {
                DirectionWikiGraphRelationType.SHARED_CONCEPT ->
                    if (phrase.isBlank()) "这两个主题在同一组关键概念上反复重叠。" else "这两个主题都反复回到“$phrase”这个概念。"
                DirectionWikiGraphRelationType.SHARED_QUESTION ->
                    if (phrase.isBlank()) "这两个主题在追同一类问题。" else "这两个主题都在追“$phrase”这类问题。"
                DirectionWikiGraphRelationType.SHARED_METHOD ->
                    if (phrase.isBlank()) "这两个主题在使用相近的方法路径。" else "这两个主题都在借用“$phrase”这类方法。"
                DirectionWikiGraphRelationType.SHARED_EXPERIMENT ->
                    if (phrase.isBlank()) "这两个主题正在验证相近的实验路径。" else "这两个主题都在试“$phrase”这类实验。"
                DirectionWikiGraphRelationType.DEPENDENCY ->
                    if (phrase.isBlank()) "这两个主题之间存在明显承接关系。" else "这两个主题都依赖“$phrase”这类承接。"
                DirectionWikiGraphRelationType.CO_OCCURRENCE ->
                    if (phrase.isBlank()) "这两个主题长期一起出现。" else "这两个主题会围绕“$phrase”一起出现。"
            }
            return DirectionWikiGraphEdge(
                fromThreadKey = fromThreadKey,
                toThreadKey = toThreadKey,
                relationType = strongestType,
                strength = strength,
                reasonLine = reasonLine,
                supportIds = supportIds.take(8),
                firstSeenAt = if (firstSeenAt == Long.MAX_VALUE) 0L else firstSeenAt,
                lastSeenAt = lastSeenAt,
                confidence = confidence,
            )
        }
    }

    private fun String.toGraphMaturity(
        fallback: DirectionWikiGraphMaturity,
    ): DirectionWikiGraphMaturity =
        DirectionWikiGraphMaturity.entries.firstOrNull { it.wireName == this.trim().lowercase() } ?: fallback

    private companion object {
        const val MaxCanonicalGraphEdges = 18
        const val MaxPresentationNodes = 6
        const val MaxPresentationEdges = 8

        val graphRelevantTypes = setOf(
            KnowledgeLayerSearchType.CONCEPT,
            KnowledgeLayerSearchType.QUESTION,
            KnowledgeLayerSearchType.METHOD,
            KnowledgeLayerSearchType.EXPERIMENT,
        )

        val graphStopTokens = setOf(
            "这个",
            "那个",
            "因为",
            "所以",
            "我们",
            "自己",
            "已经",
            "现在",
            "需要",
            "继续",
            "主题",
            "记录",
            "问题",
            "方法",
            "实验",
            "概念",
        )
    }
}
