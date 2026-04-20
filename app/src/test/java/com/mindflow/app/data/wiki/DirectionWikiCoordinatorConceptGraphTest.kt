package com.mindflow.app.data.wiki

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.connect.ResearchEvidenceType
import com.mindflow.app.data.knowledgebrain.MemoryThread
import com.mindflow.app.data.knowledgebrain.MemoryThreadType
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import org.junit.Test

class DirectionWikiCoordinatorConceptGraphTest {
    @Test
    fun `memory thread hint can boost concept graph default center`() {
        val boosted = applyMemoryThreadCenterBoost(
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "concept:review",
                nodes = listOf(
                    ConceptGraphNode(
                        conceptId = "concept:review",
                        label = "复盘",
                    ),
                    ConceptGraphNode(
                        conceptId = "concept:recommend",
                        label = "推荐链路优化",
                        aliases = listOf("推荐链路"),
                    ),
                ),
            ),
            threadHints = listOf(
                MemoryThread(
                    id = "thread-1",
                    title = "推荐链路",
                    type = MemoryThreadType.QUESTION,
                    fragmentIds = listOf("fragment-1"),
                    summary = "最近持续在讨论推荐链路优化。",
                    currentState = "正在收口",
                    openQuestions = emptyList(),
                    updatedAt = 10L,
                ),
            ),
        )

        assertThat(boosted.defaultCenterNodeId).isEqualTo("concept:recommend")
    }

    @Test
    fun `direction wiki snapshot exposes conceptGraph as the only production graph field`() {
        val fields = DirectionWikiSnapshot::class.java.declaredFields.map { it.name }

        assertThat(fields).contains("conceptGraph")
        assertThat(fields).doesNotContain("graph")
    }

    @Test
    fun `buildGraphConceptSearchItems keeps shared concept entries per thread`() {
        val conceptItems = buildGraphConceptSearchItems(
            conceptEntries = buildSurfacedConceptEntries(
                conceptBuckets = mapOf(
                    "睡眠" to listOf(
                        directionSummary("folder:work", "工作") to note(1L, "记录 A", tags = listOf("睡眠")),
                        directionSummary("tag:health", "健康") to note(2L, "记录 B", tags = listOf("睡眠")),
                    ),
                ),
                objectCandidates = emptyList(),
            ),
        )

        assertThat(conceptItems).hasSize(2)
        assertThat(conceptItems.map { it.threadKey }).containsExactly("tag:health", "folder:work").inOrder()
        assertThat(conceptItems.map { it.title }.distinct()).containsExactly("睡眠")
        assertThat(conceptItems.map { it.supportLine }.distinct()).containsExactly("1 条记录 · 2 条方向")
        assertThat(conceptItems.map { it.noteId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `buildConceptBuckets ignores repeated tags that only repeat inside one direction`() {
        val conceptBuckets = buildConceptBuckets(
            summaries = listOf(
                directionSummary("folder:work", "工作"),
            ),
            allNotes = listOf(
                note(1L, "记录 A", tags = listOf("睡眠")),
                note(2L, "记录 B", tags = listOf("睡眠")),
            ),
        )

        assertThat(conceptBuckets).isEmpty()
    }

    @Test
    fun `buildConceptBuckets keeps tags reused across directions`() {
        val conceptBuckets = buildConceptBuckets(
            summaries = listOf(
                directionSummary("folder:work", "工作"),
                directionSummary("tag:health", "健康"),
            ),
            allNotes = listOf(
                note(1L, "工作记录", tags = listOf("睡眠")),
                note(2L, "健康记录", tags = listOf("health", "睡眠"), folderKey = "life"),
            ),
        )

        assertThat(conceptBuckets.keys).containsExactly("睡眠")
        assertThat(conceptBuckets.getValue("睡眠").map { it.first.threadKey })
            .containsExactly("folder:work", "tag:health")
            .inOrder()
        assertThat(conceptBuckets.getValue("睡眠").map { it.second.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `buildConceptBuckets normalizes equivalent tags before cross-direction reuse filtering`() {
        val conceptBuckets = buildConceptBuckets(
            summaries = listOf(
                directionSummary("folder:work", "工作"),
                directionSummary("folder:life", "生活"),
            ),
            allNotes = listOf(
                note(1L, "工作接口笔记", tags = listOf("REST API"), folderKey = "work"),
                note(2L, "生活系统整理", tags = listOf("rest api"), folderKey = "life"),
            ),
        )

        assertThat(conceptBuckets.keys).containsExactly("REST API")
        assertThat(conceptBuckets.getValue("REST API").map { it.first.threadKey })
            .containsExactly("folder:work", "folder:life")
            .inOrder()
        assertThat(conceptBuckets.getValue("REST API").map { it.second.id }).containsExactly(1L, 2L).inOrder()

        val candidates = buildConceptGraphCandidates(
            conceptBuckets = conceptBuckets,
            objectCandidates = listOf(
                objectCandidate(
                    noteId = 3L,
                    relatedConcepts = listOf("rest api"),
                ),
            ),
        )

        assertThat(candidates.map { it.title }).containsExactly("REST API")
        assertThat(candidates.single().sourceIds).containsAtLeast("note:1", "note:2", "methods:3")
    }

    @Test
    fun `buildGraphObjectSearchItems keeps shared object entries per thread`() {
        val objectItems = buildGraphObjectSearchItems(
            candidates = listOf(
                objectCandidate(threadKey = "folder:work", threadTitle = "工作", noteId = 1L, updatedAt = 1_001L),
                objectCandidate(threadKey = "tag:learning", threadTitle = "学习", noteId = 2L, updatedAt = 1_002L),
                objectCandidate(threadKey = "folder:work", threadTitle = "工作", noteId = 3L, updatedAt = 1_003L),
            ),
        )

        assertThat(objectItems).hasSize(2)
        assertThat(objectItems.map { it.threadKey }).containsExactly("folder:work", "tag:learning")
        assertThat(objectItems.map { it.title }.distinct()).containsExactly("固定复盘流程")
        assertThat(objectItems.all { it.type == KnowledgeLayerSearchType.METHOD }).isTrue()
        assertThat(objectItems.first { it.threadKey == "folder:work" }.noteId).isEqualTo(3L)
        assertThat(objectItems.first { it.threadKey == "folder:work" }.supportLine).isEqualTo("2 条来源 · 2 条方向")
        assertThat(objectItems.first { it.threadKey == "tag:learning" }.supportLine).isEqualTo("1 条来源 · 2 条方向")
    }

    @Test
    fun `buildSurfacedConceptEntries normalizes object concepts onto existing reusable concept pages`() {
        val entries = buildSurfacedConceptEntries(
            conceptBuckets = mapOf(
                "REST API" to listOf(
                    directionSummary("folder:work", "工作") to note(1L, "接口记录", tags = listOf("REST API")),
                    directionSummary("folder:life", "生活") to note(2L, "系统记录", tags = listOf("rest api"), folderKey = "life"),
                ),
            ),
            objectCandidates = listOf(
                objectCandidate(
                    noteId = 3L,
                    relatedConcepts = listOf("rest api", "结构化"),
                ),
            ),
        )

        assertThat(entries.map { it.title }).containsExactly("REST API", "结构化").inOrder()
        assertThat(entries.first { it.title == "REST API" }.notePairs.map { it.second.id }).containsExactly(1L, 2L).inOrder()
        assertThat(entries.first { it.title == "REST API" }.relatedObjects.map { it.noteId }).containsExactly(3L)
        assertThat(entries.first { it.title == "结构化" }.notePairs).isEmpty()
        assertThat(entries.first { it.title == "结构化" }.relatedObjects.map { it.noteId }).containsExactly(3L)
    }

    @Test
    fun `buildGraphConceptSearchItems surfaces single-direction object-derived concepts`() {
        val conceptItems = buildGraphConceptSearchItems(
            conceptEntries = buildSurfacedConceptEntries(
                conceptBuckets = emptyMap(),
                objectCandidates = listOf(
                    objectCandidate(
                        noteId = 3L,
                        threadKey = "tag:learning",
                        threadTitle = "学习",
                        relatedConcepts = listOf("结构化"),
                    ),
                ),
            ),
        )

        assertThat(conceptItems).hasSize(1)
        assertThat(conceptItems.single().title).isEqualTo("结构化")
        assertThat(conceptItems.single().threadKey).isEqualTo("tag:learning")
        assertThat(conceptItems.single().summary).isEqualTo("每周固定复盘一次。")
        assertThat(conceptItems.single().supportLine).isEqualTo("1 个对象 · 1 条方向")
        assertThat(conceptItems.single().noteId).isEqualTo(3L)
    }

    @Test
    fun `buildConceptGraphCandidates keeps object-derived concepts even without reusable concept surface`() {
        val candidates = buildConceptGraphCandidates(
            conceptBuckets = mapOf(
                "复盘" to listOf(
                    directionSummary("tag:health", "健康") to note(1L, "最近记录", tags = listOf("复盘")),
                ),
                "睡眠规律" to listOf(
                    directionSummary("tag:sleep", "睡眠") to note(2L, "规律记录", tags = listOf("睡眠规律")),
                ),
            ),
            objectCandidates = listOf(
                objectCandidate(
                    noteId = 3L,
                    relatedConcepts = listOf("复盘", "结构化"),
                ),
            ),
        )

        assertThat(candidates.map { it.title }).containsExactly("复盘", "睡眠规律", "结构化").inOrder()
        assertThat(candidates).hasSize(3)
        assertThat(candidates.maxOf { it.updatedAt }).isEqualTo(3_000L)

        val retrospective = candidates.first { it.title == "复盘" }
        assertThat(retrospective.sourceIds).containsAtLeast("note:1", "methods:3")
        assertThat(retrospective.updatedAt).isEqualTo(3_000L)

        val structured = candidates.first { it.title == "结构化" }
        assertThat(structured.sourceIds).containsExactly("methods:3")
        assertThat(structured.updatedAt).isEqualTo(3_000L)
    }

    @Test
    fun `buildConceptGraphCandidates keeps punctuation-heavy concepts distinct`() {
        val candidates = buildConceptGraphCandidates(
            conceptBuckets = mapOf(
                "C#" to listOf(
                    directionSummary("tag:dotnet", "Dotnet") to note(11L, "CSharp", tags = listOf("C#")),
                ),
                "C++" to listOf(
                    directionSummary("tag:cpp", "Cpp") to note(12L, "Cpp", tags = listOf("C++")),
                ),
            ),
            objectCandidates = emptyList(),
        )

        assertThat(candidates.map { it.title }).containsExactly("C++", "C#")
        assertThat(candidates.map { it.conceptId }.distinct()).hasSize(2)
    }

    @Test
    fun `concept graph json round-trip preserves default center and edges`() {
        val snapshot = ConceptGraphSnapshot(
            defaultCenterNodeId = "concept:a",
            nodes = listOf(
                ConceptGraphNode(
                    conceptId = "concept:a",
                    label = "A",
                    aliases = listOf("Alpha"),
                    summary = "中心节点",
                    hotnessScore = 0.9,
                    updatedAt = 4_000L,
                    sourceIds = listOf("note:1"),
                ),
                ConceptGraphNode(
                    conceptId = "concept:b",
                    label = "B",
                    aliases = listOf("Beta"),
                    summary = "邻接节点",
                    hotnessScore = 0.6,
                    updatedAt = 2_000L,
                    sourceIds = listOf("methods:3"),
                ),
            ),
            edges = listOf(
                ConceptGraphEdge(
                    fromConceptId = "concept:a",
                    toConceptId = "concept:b",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    reasonLine = "A 支撑 B。",
                    supportIds = listOf("note:1", "methods:3"),
                    confidence = 0.7,
                ),
            ),
            source = "llm",
            generatedAt = 5_000L,
        )

        val restored = snapshot.toConceptGraphJsonString().toConceptGraphSnapshot()

        assertThat(restored.defaultCenterNodeId).isEqualTo("concept:a")
        assertThat(restored.nodes.map { it.conceptId }).containsExactly("concept:a", "concept:b").inOrder()
        assertThat(restored.edges).hasSize(1)
        assertThat(restored.edges.single().fromConceptId).isEqualTo("concept:a")
        assertThat(restored.edges.single().toConceptId).isEqualTo("concept:b")
        assertThat(restored.edges.single().relationType).isEqualTo(ConceptGraphRelationType.SUPPORTS)
    }

    @Test
    fun `safe concept graph parser falls back to default snapshot on malformed json`() {
        val restored = parseConceptGraphSnapshotOrDefault("{not-valid")

        assertThat(restored).isEqualTo(ConceptGraphSnapshot())
    }

    @Test
    fun `buildConceptGraphMarkdown only lists relationships between rendered concepts`() {
        val markdown = buildConceptGraphMarkdown(
            generatedAt = 8_000L,
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "concept:1",
                nodes = (1..9).map { index ->
                    ConceptGraphNode(
                        conceptId = "concept:$index",
                        label = "概念$index",
                        summary = "概念$index 的摘要",
                        hotnessScore = (10 - index).toDouble(),
                        updatedAt = (10 - index).toLong(),
                        sourceIds = listOf("note:$index"),
                    )
                },
                edges = listOf(
                    ConceptGraphEdge(
                        fromConceptId = "concept:1",
                        toConceptId = "concept:9",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        reasonLine = "高置信边，但目标节点未进入展示列表。",
                        supportIds = listOf("note:1", "note:9"),
                        confidence = 0.95,
                    ),
                    ConceptGraphEdge(
                        fromConceptId = "concept:1",
                        toConceptId = "concept:2",
                        relationType = ConceptGraphRelationType.ADVANCES,
                        reasonLine = "展示列表内的稳定关系。",
                        supportIds = listOf("note:1", "note:2"),
                        confidence = 0.8,
                    ),
                ),
            ),
        )

        assertThat(markdown).contains("- 概念8：概念8 的摘要")
        assertThat(markdown).doesNotContain("- 概念9：概念9 的摘要")
        assertThat(markdown).contains("- 概念1 -> 概念2 · 推进")
        assertThat(markdown).doesNotContain("- 概念1 -> 概念9 · 支持")
    }

    @Test
    fun `buildConceptGraphMarkdown always renders the resolved center concept and only matching relationships`() {
        val markdown = buildConceptGraphMarkdown(
            generatedAt = 8_000L,
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "concept:9",
                nodes = (1..9).map { index ->
                    ConceptGraphNode(
                        conceptId = "concept:$index",
                        label = "概念$index",
                        summary = "概念$index 的摘要",
                        hotnessScore = (10 - index).toDouble(),
                        updatedAt = (10 - index).toLong(),
                        sourceIds = listOf("note:$index"),
                    )
                },
                edges = listOf(
                    ConceptGraphEdge(
                        fromConceptId = "concept:9",
                        toConceptId = "concept:1",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        reasonLine = "中心概念和概念1之间存在稳定关系。",
                        supportIds = listOf("note:9", "note:1"),
                        confidence = 0.9,
                    ),
                    ConceptGraphEdge(
                        fromConceptId = "concept:1",
                        toConceptId = "concept:8",
                        relationType = ConceptGraphRelationType.REFERENCES,
                        reasonLine = "概念8 未进入展示集合。",
                        supportIds = listOf("note:1", "note:8"),
                        confidence = 0.8,
                    ),
                ),
            ),
        )

        assertThat(markdown).contains("中心概念：概念9")
        assertThat(markdown).contains("- 概念9：概念9 的摘要")
        assertThat(markdown).doesNotContain("- 概念8：概念8 的摘要")
        assertThat(markdown).contains("- 概念9 -> 概念1 · 支持")
        assertThat(markdown).doesNotContain("- 概念1 -> 概念8 · 参考")
    }

    @Test
    fun `buildConceptGraphMarkdown uses shipped UI terminology`() {
        val markdown = buildConceptGraphMarkdown(
            generatedAt = 8_000L,
            conceptGraph = ConceptGraphSnapshot(
                defaultCenterNodeId = "concept:1",
                nodes = (1..4).map { index ->
                    ConceptGraphNode(
                        conceptId = "concept:$index",
                        label = "概念$index",
                        summary = "概念$index 的摘要",
                        hotnessScore = (10 - index).toDouble(),
                        updatedAt = (10 - index).toLong(),
                        sourceIds = listOf("note:$index"),
                    )
                },
                edges = listOf(
                    ConceptGraphEdge(
                        fromConceptId = "concept:1",
                        toConceptId = "concept:2",
                        relationType = ConceptGraphRelationType.SUPPORTS,
                        reasonLine = "概念1 支持概念2。",
                        supportIds = listOf("note:1", "note:2"),
                        confidence = 0.95,
                    ),
                    ConceptGraphEdge(
                        fromConceptId = "concept:1",
                        toConceptId = "concept:3",
                        relationType = ConceptGraphRelationType.REFERENCES,
                        reasonLine = "概念1 参考概念3。",
                        supportIds = listOf("note:1", "note:3"),
                        confidence = 0.9,
                    ),
                    ConceptGraphEdge(
                        fromConceptId = "concept:2",
                        toConceptId = "concept:4",
                        relationType = ConceptGraphRelationType.CONTRASTS,
                        reasonLine = "概念2 对比概念4。",
                        supportIds = listOf("note:2", "note:4"),
                        confidence = 0.85,
                    ),
                ),
            ),
        )

        assertThat(markdown).contains("# 知识图谱")
        assertThat(markdown).contains("- 概念1 -> 概念2 · 支持")
        assertThat(markdown).contains("- 概念1 -> 概念3 · 参考")
        assertThat(markdown).contains("- 概念2 -> 概念4 · 对比")
        assertThat(markdown).doesNotContain("# 概念图谱")
        assertThat(markdown).doesNotContain("· 支撑")
        assertThat(markdown).doesNotContain("· 引用")
        assertThat(markdown).doesNotContain("· 对照")
    }

    @Test
    fun `buildDirectionWikiExportJson writes conceptGraph and omits legacy graph key`() {
        val export = buildDirectionWikiExportJson(
            generatedAt = 7_000L,
            rootPath = "/tmp/knowledge-layer",
            summaries = listOf(
                directionSummary(
                    threadKey = "tag:health",
                    title = "健康",
                ).copy(
                    assetSummary = "稳定复盘节奏。",
                    stageHistorySummary = "2026-04-16FORMING",
                ),
            ),
            knowledgeItems = listOf(
                knowledgeItem(
                    id = "concept:health:review",
                    type = KnowledgeLayerSearchType.CONCEPT,
                    title = "复盘",
                ),
            ),
            conceptGraph = conceptGraphSnapshot(),
        )

        assertThat(export).contains("\"conceptGraph\":")
        assertThat(export).doesNotContain("\"graph\":")
        assertThat(export).contains("\"defaultCenterNodeId\":\"concept:review\"")
    }

    @Test
    fun `direction wiki export json round-trip preserves directions knowledge items and concept graph`() {
        val direction = directionSummary(
            threadKey = "tag:health",
            title = "健康",
        ).copy(
            assetSummary = "稳定复盘节奏。",
            conclusionLine = "本周的结论已经足够明确。",
            nextShiftLine = "下周继续固定睡眠窗口。",
            groundingLine = "已有两条查证和一条验证。",
            trustLine = "验证比猜测更多。",
            knowledgeObjectLine = "1 个问题 / 1 个方法 / 1 个实验",
            healthLine = "健康度高",
            maintenanceLine = "每周复盘一次",
            maintenanceTargetLine = "优先盯睡眠和复盘",
            maintenanceSourceLine = "来自过去 7 天记录",
            maintenanceDimensionLine = "行动和恢复双维度",
            maintenanceFocusLine = "先守住睡眠，再优化训练",
            continuityLine = "连续 3 周保持",
            trajectoryLine = "从 forming 向 strengthening 推进",
            stageHistorySummary = "forming -> strengthening",
            snapshotStageLine = "最近两次快照都在推进",
            snapshotCadenceLine = "每周一次快照",
            signalPoints = listOf("晚上 11 点前入睡更稳定"),
            hypothesisPoints = listOf("晨间散步可能提升执行力"),
            verifiedPoints = listOf("固定睡前流程后中断减少"),
            validatedPoints = listOf("连续两周都维持了固定窗口"),
            lintIssues = listOf("缺少长期实验对照"),
            openQuestions = listOf("是否需要增加午后恢复策略"),
            updatedAt = 9_000L,
        )
        val knowledgeItems = listOf(
            knowledgeItem(
                id = "concept:health:review",
                type = KnowledgeLayerSearchType.CONCEPT,
                title = "复盘",
            ),
            knowledgeItem(
                id = "method:health:sleep-window",
                type = KnowledgeLayerSearchType.METHOD,
                title = "固定睡眠窗口",
            ).copy(
                summary = "把睡眠窗口固定到 23:00-07:00。",
                supportLine = "2 条来源",
                trustLabel = "已验证",
                threadKey = "tag:health",
                noteId = 22L,
                updatedAt = 8_500L,
            ),
        )
        val conceptGraph = conceptGraphSnapshot()
        val export = buildDirectionWikiExportJson(
            generatedAt = 10_000L,
            rootPath = "/tmp/knowledge-layer",
            summaries = listOf(direction),
            knowledgeItems = knowledgeItems,
            conceptGraph = conceptGraph,
        )

        val restored = parseDirectionWikiSnapshotOrDefault(
            raw = export,
            defaultRootPath = "/fallback/root",
        )

        assertThat(restored.rootPath).isEqualTo("/tmp/knowledge-layer")
        assertThat(restored.lastGeneratedAt).isEqualTo(10_000L)
        assertThat(restored.directions).containsExactly("tag:health", direction)
        assertThat(restored.knowledgeItems).containsExactlyElementsIn(knowledgeItems).inOrder()
        assertThat(restored.conceptGraph).isEqualTo(conceptGraph)
    }

    @Test
    fun `parseDirectionWikiSnapshotOrDefault marks missing conceptGraph stale for prompt regeneration`() {
        val restored = parseDirectionWikiSnapshotOrDefault(
            raw = """
                {
                  "generatedAt": 7000,
                  "rootPath": "/tmp/knowledge-layer",
                  "directions": [
                    {
                      "threadKey": "tag:health",
                      "slug": "tag:health",
                      "title": "健康",
                      "stage": "FORMING",
                      "assetSummary": "稳定复盘节奏。",
                      "stageHistorySummary": "forming -> settling",
                      "updatedAt": 1000,
                      "signalPoints": [],
                      "hypothesisPoints": [],
                      "verifiedPoints": [],
                      "validatedPoints": [],
                      "lintIssues": [],
                      "openQuestions": []
                    }
                  ],
                  "knowledgeItems": [
                    {
                      "id": "concept:health:review",
                      "type": "CONCEPT",
                      "title": "复盘",
                      "summary": "复盘 的摘要",
                      "supportLine": "1 条来源",
                      "trustLabel": "已查证",
                      "threadKey": "tag:health",
                      "noteId": 10,
                      "updatedAt": 6000
                    }
                  ]
                }
            """.trimIndent(),
            defaultRootPath = "/fallback/root",
        )

        assertThat(restored.rootPath).isEqualTo("/tmp/knowledge-layer")
        assertThat(restored.lastGeneratedAt).isEqualTo(0L)
        assertThat(restored.directions.keys).containsExactly("tag:health")
        assertThat(restored.directions.getValue("tag:health").assetSummary).isEqualTo("稳定复盘节奏。")
        assertThat(restored.directions.getValue("tag:health").stageHistorySummary).isEqualTo("forming -> settling")
        assertThat(restored.knowledgeItems.map { it.id }).containsExactly("concept:health:review")
        assertThat(restored.conceptGraph).isEqualTo(ConceptGraphSnapshot())
        assertThat(
            shouldRefreshDirectionWikiSnapshot(
                current = restored,
                candidateThreadKeys = listOf("tag:health"),
                now = 12_000L,
                refreshIntervalMs = 18L * 60L * 60L * 1000L,
            ),
        ).isTrue()
    }

    @Test
    fun `parseDirectionWikiSnapshotOrDefault preserves non graph data and marks malformed conceptGraph stale`() {
        val restored = parseDirectionWikiSnapshotOrDefault(
            raw = """
                {
                  "generatedAt": 9000,
                  "rootPath": "/tmp/knowledge-layer",
                  "directions": [
                    {
                      "threadKey": "tag:sleep",
                      "slug": "tag:sleep",
                      "title": "睡眠",
                      "stage": "FORMING",
                      "assetSummary": "先固定入睡窗口。",
                      "stageHistorySummary": "forming -> strengthening",
                      "updatedAt": 1000,
                      "signalPoints": [],
                      "hypothesisPoints": [],
                      "verifiedPoints": [],
                      "validatedPoints": [],
                      "lintIssues": [],
                      "openQuestions": []
                    }
                  ],
                  "knowledgeItems": [
                    {
                      "id": "method:tag:sleep:bedtime",
                      "type": "METHOD",
                      "title": "固定入睡流程",
                      "summary": "固定入睡流程 的摘要",
                      "supportLine": "1 条来源",
                      "trustLabel": "已查证",
                      "threadKey": "tag:health",
                      "noteId": 10,
                      "updatedAt": 6000
                    }
                  ],
                  "conceptGraph": "{not-valid}"
                }
            """.trimIndent(),
            defaultRootPath = "/fallback/root",
        )

        assertThat(restored.rootPath).isEqualTo("/tmp/knowledge-layer")
        assertThat(restored.lastGeneratedAt).isEqualTo(0L)
        assertThat(restored.directions.keys).containsExactly("tag:sleep")
        assertThat(restored.directions.getValue("tag:sleep").assetSummary).isEqualTo("先固定入睡窗口。")
        assertThat(restored.directions.getValue("tag:sleep").stageHistorySummary).isEqualTo("forming -> strengthening")
        assertThat(restored.knowledgeItems.map { it.id }).containsExactly("method:tag:sleep:bedtime")
        assertThat(restored.knowledgeItems.single().type).isEqualTo(KnowledgeLayerSearchType.METHOD)
        assertThat(restored.conceptGraph).isEqualTo(ConceptGraphSnapshot())
        assertThat(
            shouldRefreshDirectionWikiSnapshot(
                current = restored,
                candidateThreadKeys = listOf("tag:sleep"),
                now = 12_000L,
                refreshIntervalMs = 18L * 60L * 60L * 1000L,
            ),
        ).isTrue()
    }

    @Test
    fun `parseDirectionWikiSnapshotOrDefault marks default-falling malformed conceptGraph object stale`() {
        val restored = parseDirectionWikiSnapshotOrDefault(
            raw = """
                {
                  "generatedAt": 1700000000000,
                  "rootPath": "/tmp/knowledge-layer",
                  "directions": [
                    {
                      "threadKey": "tag:health",
                      "slug": "tag:health",
                      "title": "健康",
                      "stage": "FORMING",
                      "assetSummary": "稳定复盘节奏。",
                      "stageHistorySummary": "forming -> strengthening",
                      "updatedAt": 1000,
                      "signalPoints": [],
                      "hypothesisPoints": [],
                      "verifiedPoints": [],
                      "validatedPoints": [],
                      "lintIssues": [],
                      "openQuestions": []
                    }
                  ],
                  "conceptGraph": {
                    "nodes": [],
                    "edges": []
                  }
                }
            """.trimIndent(),
            defaultRootPath = "/fallback/root",
        )

        assertThat(restored.rootPath).isEqualTo("/tmp/knowledge-layer")
        assertThat(restored.lastGeneratedAt).isEqualTo(0L)
        assertThat(restored.directions.keys).containsExactly("tag:health")
        assertThat(restored.conceptGraph).isEqualTo(ConceptGraphSnapshot())
        assertThat(
            shouldRefreshDirectionWikiSnapshot(
                current = restored,
                candidateThreadKeys = listOf("tag:health"),
                now = 1_700_000_005_000L,
                refreshIntervalMs = 18L * 60L * 60L * 1000L,
            ),
        ).isTrue()
    }

    @Test
    fun `parseDirectionWikiSnapshotOrDefault marks migrated legacy graph-only export stale for prompt regeneration`() {
        val restored = parseDirectionWikiSnapshotOrDefault(
            raw = """
                {
                  "generatedAt": 1700000000000,
                  "rootPath": "/tmp/direction-wiki",
                  "directions": [
                    {
                      "threadKey": "tag:health",
                      "slug": "tag:health",
                      "title": "健康",
                      "stage": "FORMING",
                      "assetSummary": "稳定复盘节奏。",
                      "stageHistorySummary": "forming -> strengthening",
                      "updatedAt": 1000,
                      "signalPoints": [],
                      "hypothesisPoints": [],
                      "verifiedPoints": [],
                      "validatedPoints": [],
                      "lintIssues": [],
                      "openQuestions": []
                    }
                  ],
                  "knowledgeItems": [
                    {
                      "id": "concept:health:review",
                      "type": "CONCEPT",
                      "title": "复盘",
                      "summary": "复盘 的摘要",
                      "supportLine": "1 条来源",
                      "trustLabel": "已查证",
                      "threadKey": "tag:health",
                      "noteId": 10,
                      "updatedAt": 1699999999000
                    }
                  ],
                  "graph": {
                    "nodes": [
                      {
                        "id": "legacy-review",
                        "title": "复盘"
                      }
                    ],
                    "edges": []
                  }
                }
            """.trimIndent(),
            defaultRootPath = "/tmp/knowledge-layer",
        )

        assertThat(restored.rootPath).isEqualTo("/tmp/knowledge-layer")
        assertThat(restored.lastGeneratedAt).isEqualTo(0L)
        assertThat(restored.directions.keys).containsExactly("tag:health")
        assertThat(restored.knowledgeItems.map { it.id }).containsExactly("concept:health:review")
        assertThat(restored.conceptGraph).isEqualTo(ConceptGraphSnapshot())
    }

    @Test
    fun `legacy graph-only snapshot triggers refresh even when timestamp was recently generated`() {
        val restored = parseDirectionWikiSnapshotOrDefault(
            raw = """
                {
                  "generatedAt": 1700000000000,
                  "rootPath": "/tmp/direction-wiki",
                  "directions": [
                    {
                      "threadKey": "tag:health",
                      "slug": "tag:health",
                      "title": "健康",
                      "stage": "FORMING",
                      "assetSummary": "稳定复盘节奏。",
                      "stageHistorySummary": "forming -> strengthening",
                      "updatedAt": 1000,
                      "signalPoints": [],
                      "hypothesisPoints": [],
                      "verifiedPoints": [],
                      "validatedPoints": [],
                      "lintIssues": [],
                      "openQuestions": []
                    }
                  ],
                  "graph": {
                    "nodes": [
                      {
                        "id": "legacy-review",
                        "title": "复盘"
                      }
                    ],
                    "edges": []
                  }
                }
            """.trimIndent(),
            defaultRootPath = "/tmp/knowledge-layer",
        )

        assertThat(restored.rootPath).isEqualTo("/tmp/knowledge-layer")
        assertThat(
            shouldRefreshDirectionWikiSnapshot(
                current = restored,
                candidateThreadKeys = listOf("tag:health"),
                now = 1_700_000_005_000L,
                refreshIntervalMs = 18L * 60L * 60L * 1000L,
            )
        ).isTrue()
    }

    @Test
    fun `shouldRefreshDirectionWikiSnapshot refreshes when covered thread notes changed after last generation`() {
        val current = DirectionWikiSnapshot(
            rootPath = "/tmp/knowledge-layer",
            lastGeneratedAt = 1_700_000_000_000L,
            directions = mapOf(
                "tag:health" to directionSummary("tag:health", "健康").copy(updatedAt = 1_000L),
            ),
        )

        assertThat(
            shouldRefreshDirectionWikiSnapshot(
                current = current,
                candidateThreadKeys = listOf("tag:health"),
                candidateThreadFreshness = mapOf("tag:health" to 2_000L),
                now = 1_700_000_005_000L,
                refreshIntervalMs = 18L * 60L * 60L * 1000L,
            ),
        ).isTrue()
    }

    private fun objectCandidate(
        noteId: Long,
        relatedConcepts: List<String> = listOf("复盘"),
        threadKey: String = "tag:learning",
        threadTitle: String = "学习",
        updatedAt: Long = 3_000L,
    ) = KnowledgeObjectCandidate(
        type = KnowledgeObjectType.METHOD,
        title = "固定复盘流程",
        summary = "每周固定复盘一次。",
        noteId = noteId,
        updatedAt = updatedAt,
        threadKey = threadKey,
        threadTitle = threadTitle,
        relatedConcepts = relatedConcepts,
        evidenceType = ResearchEvidenceType.VERIFIED,
    )

    private fun directionSummary(
        threadKey: String,
        title: String,
    ) = DirectionWikiDirectionSummary(
        threadKey = threadKey,
        slug = threadKey,
        title = title,
        stage = DirectionStage.FORMING,
        updatedAt = 1_000L,
    )

    private fun knowledgeItem(
        id: String,
        type: KnowledgeLayerSearchType,
        title: String,
    ) = KnowledgeLayerSearchItem(
        id = id,
        type = type,
        title = title,
        summary = "$title 的摘要",
        supportLine = "1 条来源",
        trustLabel = "已查证",
        threadKey = "tag:health",
        noteId = 10L,
        updatedAt = 6_000L,
    )

    private fun conceptGraphSnapshot() = ConceptGraphSnapshot(
        defaultCenterNodeId = "concept:review",
        nodes = listOf(
            ConceptGraphNode(
                conceptId = "concept:review",
                label = "复盘",
                summary = "固定复盘有助于形成反馈回路。",
                hotnessScore = 0.9,
                updatedAt = 5_000L,
                sourceIds = listOf("note:1"),
            ),
            ConceptGraphNode(
                conceptId = "concept:sleep",
                label = "睡眠",
                summary = "睡眠质量会影响白天执行力。",
                hotnessScore = 0.8,
                updatedAt = 4_000L,
                sourceIds = listOf("note:2"),
            ),
        ),
        edges = listOf(
            ConceptGraphEdge(
                fromConceptId = "concept:review",
                toConceptId = "concept:sleep",
                relationType = ConceptGraphRelationType.SUPPORTS,
                reasonLine = "复盘帮助调整睡眠策略。",
                supportIds = listOf("note:1", "note:2"),
                confidence = 0.75,
            ),
        ),
        source = "rule",
        generatedAt = 7_000L,
    )

    private fun note(
        id: Long,
        topic: String,
        tags: List<String>,
        folderKey: String = "work",
    ) = NoteEntity(
        id = id,
        content = "$topic 的内容",
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = folderKey,
        folderSource = FolderSource.MANUAL,
        tags = tags,
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L + id,
        updatedAt = 2_000L + id,
    )
}
