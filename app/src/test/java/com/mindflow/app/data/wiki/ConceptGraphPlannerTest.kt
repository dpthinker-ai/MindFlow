package com.mindflow.app.data.wiki

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiProvider
import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskProvider
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskType
import com.mindflow.app.data.ai.GraphRelation
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiServiceClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConceptGraphPlannerTest {
    @Test
    fun `buildAiContext emits structured json contract`() {
        val planner = planner(aiResponse = """{"nodes":[],"edges":[]}""")

        val context = invokeBuildAiContext(
            planner = planner,
            candidates = listOf(
                candidate(
                    conceptId = "sleep",
                    title = "睡眠",
                    aliases = listOf("rest"),
                    hotnessScore = 0.8,
                    updatedAt = 1_000,
                    summary = "包含 \"引号\"、竖线 | 和换行\n第二行",
                    sourceIds = listOf("note-1", "note|2"),
                ),
            ),
        )

        assertThat(context.trim().first()).isEqualTo('{')
        assertThat(context).contains("\"allowedRelationTypes\"")
        assertThat(context).contains("\"outputSchema\"")
        assertThat(context).contains("\"fromConceptId\"")
        assertThat(context).contains("\"toConceptId\"")
        assertThat(context).contains("\\\"引号\\\"")
        assertThat(context).contains("\\n第二行")
        assertThat(context).doesNotContain("conceptId=sleep | title=睡眠")
    }

    @Test
    fun `summarize uses staged router requests before rule fallback`() = runTest {
        val planner = plannerWithRouter(
            conceptPayload = listOf("sleep", "recovery"),
            canonicalPayload = mapOf("sleep" to listOf("rest"), "recovery" to emptyList()),
            relationPayload = listOf(
                relation(
                    from = "sleep",
                    to = "recovery",
                    type = "supports",
                    reason = "睡眠支持恢复",
                    confidence = 0.9f,
                ),
            ),
        )

        val snapshot = planner.summarize(
            listOf(
                candidate(conceptId = "sleep", title = "睡眠", hotnessScore = 0.9, updatedAt = 1_000),
                candidate(conceptId = "recovery", title = "恢复", hotnessScore = 0.8, updatedAt = 900),
            ),
        )

        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("sleep", "recovery").inOrder()
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.single().fromConceptId).isEqualTo("sleep")
        assertThat(snapshot.edges.single().toConceptId).isEqualTo("recovery")
        assertThat(snapshot.source).isEqualTo("router+rule")
    }

    @Test
    fun `summarize falls back only relation stage when relation payload fails`() = runTest {
        val planner = plannerWithRouter(
            conceptPayload = listOf("sleep", "recovery"),
            canonicalPayload = mapOf("sleep" to listOf("rest"), "recovery" to emptyList()),
            relationPayload = emptyList(),
        )

        val snapshot = planner.summarize(
            listOf(
                candidate(
                    conceptId = "sleep",
                    title = "睡眠",
                    hotnessScore = 0.9,
                    updatedAt = 1_000,
                    sourceIds = listOf("note-1"),
                ),
                candidate(
                    conceptId = "recovery",
                    title = "恢复",
                    hotnessScore = 0.8,
                    updatedAt = 900,
                    sourceIds = listOf("note-1"),
                ),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.edges).isNotEmpty()
        assertThat(snapshot.edges.single().reasonLine).contains("一起出现在同一条记录")
    }

    @Test
    fun `summarize bounds concept-graph ai context as valid json and keeps omitted candidates`() = runTest {
        var capturedContext = ""
        val planner = planner(
            resultFactory = { context ->
                capturedContext = context
                val visibleConceptId = extractCandidateConceptIds(context).first()
                AiChatResult.Success(
                    content = """
                        {
                          "nodes": [
                            {
                              "conceptId": "$visibleConceptId",
                              "label": "AI-$visibleConceptId",
                              "summary": "AI 仅覆盖可见子集。"
                            }
                          ],
                          "edges": []
                        }
                    """.trimIndent(),
                )
            },
        )

        val candidates = (1..18).map { index ->
            candidate(
                conceptId = "concept-$index",
                title = "概念 $index",
                aliases = listOf("alias-$index-${"x".repeat(120)}"),
                hotnessScore = 1.0 - (index * 0.01),
                updatedAt = 10_000L - index,
                summary = "summary-$index ".repeat(220),
                sourceIds = listOf("source-$index-${"y".repeat(120)}"),
            )
        }

        val snapshot = planner.summarize(candidates)
        val visibleCandidateIds = extractCandidateConceptIds(capturedContext)

        assertJsonObjectParses(capturedContext)
        assertThat(capturedContext.length).isAtMost(CONCEPT_GRAPH_AI_CONTEXT_MAX_CHARS)
        assertThat(visibleCandidateIds).isNotEmpty()
        assertThat(visibleCandidateIds.size).isLessThan(candidates.size)
        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.nodes.map { it.conceptId })
            .containsExactlyElementsIn(candidates.map { it.conceptId })
            .inOrder()
        assertThat(snapshot.nodes.first().label).isEqualTo("AI-concept-1")
        assertThat(snapshot.nodes.last().label).isEqualTo("概念 18")
        assertThat(snapshot.nodes.last().summary).isEqualTo(candidates.last().summary)
    }

    @Test
    fun `summarize merges aliases into canonical nodes`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "sleep",
                      "label": "睡眠",
                      "aliases": ["rest", "睡觉"],
                      "summary": "睡眠是恢复的基础。",
                      "hotnessScore": 0.8,
                      "updatedAt": 1000
                    },
                    {
                      "conceptId": "recovery",
                      "label": "恢复",
                      "aliases": [],
                      "summary": "恢复帮助睡眠。",
                      "hotnessScore": 0.7,
                      "updatedAt": 900
                    }
                  ],
                  "edges": [
                    {
                      "fromConceptId": "rest",
                      "toConceptId": "recovery",
                      "relationType": "supports",
                      "reasonLine": "休息支持恢复。"
                    }
                  ]
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "sleep",
                    title = "睡眠",
                    aliases = listOf("rest", "睡觉"),
                    hotnessScore = 0.8,
                    updatedAt = 1000,
                ),
                candidate(
                    conceptId = "recovery",
                    title = "恢复",
                    aliases = listOf("reset"),
                    hotnessScore = 0.7,
                    updatedAt = 900,
                ),
            ),
        )

        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("sleep", "recovery").inOrder()
        assertThat(snapshot.nodes.first().aliases).containsExactly("rest", "睡觉")
        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.first().fromConceptId).isEqualTo("sleep")
        assertThat(snapshot.edges.first().relationType).isEqualTo(ConceptGraphRelationType.SUPPORTS)
    }

    @Test
    fun `summarize keeps omitted candidates when ai returns only a partial node set`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "sleep",
                      "label": "深度睡眠",
                      "aliases": ["rest"],
                      "summary": "AI 补充后的睡眠摘要。",
                      "sourceIds": ["note-1", "note-2"]
                    },
                    {
                      "conceptId": "recovery",
                      "label": "恢复",
                      "summary": "AI 只覆盖了部分候选。"
                    }
                  ],
                  "edges": [
                    {
                      "fromConceptId": "sleep",
                      "toConceptId": "recovery",
                      "relationType": "supports",
                      "reasonLine": "睡眠支持恢复。"
                    }
                  ]
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "sleep",
                    title = "睡眠",
                    aliases = listOf("rest"),
                    hotnessScore = 0.8,
                    updatedAt = 1_000,
                    sourceIds = listOf("note-1"),
                ),
                candidate(
                    conceptId = "recovery",
                    title = "恢复",
                    hotnessScore = 0.7,
                    updatedAt = 900,
                    sourceIds = listOf("note-2"),
                ),
                candidate(
                    conceptId = "focus",
                    title = "专注",
                    aliases = listOf("flow"),
                    hotnessScore = 0.6,
                    updatedAt = 800,
                    summary = "未被 AI 提及的候选概念。",
                    sourceIds = listOf("note-3"),
                ),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("sleep", "recovery", "focus").inOrder()
        assertThat(snapshot.nodes.first { it.conceptId == "sleep" }.label).isEqualTo("深度睡眠")
        assertThat(snapshot.nodes.first { it.conceptId == "sleep" }.sourceIds).containsExactly("note-1")
        assertThat(snapshot.nodes.first { it.conceptId == "focus" }.label).isEqualTo("专注")
        assertThat(snapshot.nodes.first { it.conceptId == "focus" }.aliases).containsExactly("flow")
        assertThat(snapshot.nodes.first { it.conceptId == "focus" }.summary).isEqualTo("未被 AI 提及的候选概念。")
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.first().fromConceptId).isEqualTo("sleep")
        assertThat(snapshot.edges.first().toConceptId).isEqualTo("recovery")
    }

    @Test
    fun `summarize infers fallback edges from shared source ids when ai omits edges`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "sleep",
                      "label": "睡眠"
                    },
                    {
                      "conceptId": "recovery",
                      "label": "恢复"
                    },
                    {
                      "conceptId": "focus",
                      "label": "专注"
                    }
                  ],
                  "edges": []
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "sleep",
                    title = "睡眠",
                    hotnessScore = 0.9,
                    updatedAt = 1_000,
                    sourceIds = listOf("note:1", "note:2"),
                ),
                candidate(
                    conceptId = "recovery",
                    title = "恢复",
                    hotnessScore = 0.8,
                    updatedAt = 900,
                    sourceIds = listOf("note:2", "note:3"),
                ),
                candidate(
                    conceptId = "focus",
                    title = "专注",
                    hotnessScore = 0.7,
                    updatedAt = 800,
                    sourceIds = listOf("note:4"),
                ),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.single().fromConceptId).isEqualTo("recovery")
        assertThat(snapshot.edges.single().toConceptId).isEqualTo("sleep")
        assertThat(snapshot.edges.single().relationType).isEqualTo(ConceptGraphRelationType.PARALLEL)
        assertThat(snapshot.edges.single().supportIds).containsExactly("note:2")
    }

    @Test
    fun `summarize resolves nodes and edges from unique alias and label references`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "rest",
                      "label": "睡眠",
                      "summary": "通过别名识别。"
                    },
                    {
                      "label": "恢复",
                      "summary": "通过标题识别。"
                    }
                  ],
                  "edges": [
                    {
                      "fromConceptId": "rest",
                      "toConceptId": "恢复",
                      "relationType": "supports",
                      "reasonLine": "睡眠支持恢复。"
                    }
                  ]
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "sleep",
                    title = "睡眠",
                    aliases = listOf("rest"),
                    hotnessScore = 0.8,
                    updatedAt = 1_000,
                ),
                candidate(
                    conceptId = "recovery",
                    title = "恢复",
                    aliases = listOf("reset"),
                    hotnessScore = 0.7,
                    updatedAt = 900,
                ),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("sleep", "recovery").inOrder()
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.first().fromConceptId).isEqualTo("sleep")
        assertThat(snapshot.edges.first().toConceptId).isEqualTo("recovery")
    }

    @Test
    fun `summarize prefers the stronger later duplicate edge`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "sleep",
                      "label": "睡眠"
                    },
                    {
                      "conceptId": "recovery",
                      "label": "恢复"
                    }
                  ],
                  "edges": [
                    {
                      "fromConceptId": "sleep",
                      "toConceptId": "recovery",
                      "relationType": "supports",
                      "reasonLine": "较弱证据。",
                      "supportIds": ["note-1"],
                      "confidence": 0.2
                    },
                    {
                      "fromConceptId": "sleep",
                      "toConceptId": "recovery",
                      "relationType": "supports",
                      "reasonLine": "更强证据。",
                      "supportIds": ["note-1", "note-2"],
                      "confidence": 0.9
                    }
                  ]
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "sleep",
                    title = "睡眠",
                    hotnessScore = 0.8,
                    updatedAt = 1_000,
                ),
                candidate(
                    conceptId = "recovery",
                    title = "恢复",
                    hotnessScore = 0.7,
                    updatedAt = 900,
                ),
            ),
        )

        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.first().reasonLine).isEqualTo("更强证据。")
        assertThat(snapshot.edges.first().supportIds).isEmpty()
        assertThat(snapshot.edges.first().confidence).isWithin(1e-6).of(0.9)
    }

    @Test
    fun `summarize prefers the hottest node and breaks ties by recency`() = runTest {
        val planner = planner(aiResponse = """{"nodes":[],"edges":[]}""")

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "older-hot",
                    title = "旧热点",
                    hotnessScore = 0.95,
                    updatedAt = 1_000,
                ),
                candidate(
                    conceptId = "newer-hot",
                    title = "新热点",
                    hotnessScore = 0.95,
                    updatedAt = 2_000,
                ),
                candidate(
                    conceptId = "cool",
                    title = "普通概念",
                    hotnessScore = 0.4,
                    updatedAt = 3_000,
                ),
            ),
        )

        assertThat(snapshot.defaultCenterNodeId).isEqualTo("newer-hot")
    }

    @Test
    fun `summarize prefers a connected node over a slightly hotter isolated node when edges exist`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "isolated-hot",
                      "label": "孤立热点"
                    },
                    {
                      "conceptId": "connected-center",
                      "label": "连接中心"
                    },
                    {
                      "conceptId": "supporting-node",
                      "label": "支撑节点"
                    }
                  ],
                  "edges": [
                    {
                      "fromConceptId": "connected-center",
                      "toConceptId": "supporting-node",
                      "relationType": "supports",
                      "reasonLine": "连接中心有实际关联。"
                    }
                  ]
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "isolated-hot",
                    title = "孤立热点",
                    hotnessScore = 0.92,
                    updatedAt = 1_500,
                ),
                candidate(
                    conceptId = "connected-center",
                    title = "连接中心",
                    hotnessScore = 0.9,
                    updatedAt = 1_400,
                ),
                candidate(
                    conceptId = "supporting-node",
                    title = "支撑节点",
                    hotnessScore = 0.5,
                    updatedAt = 1_300,
                ),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.defaultCenterNodeId).isEqualTo("connected-center")
    }

    @Test
    fun `summarize keeps candidate hotness and recency after ai parse`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "hot-candidate",
                      "label": "真正热点",
                      "hotnessScore": 0.01,
                      "updatedAt": 10
                    },
                    {
                      "conceptId": "cool-candidate",
                      "label": "模型误判热点",
                      "hotnessScore": 0.99,
                      "updatedAt": 999999
                    }
                  ],
                  "edges": []
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "hot-candidate",
                    title = "真正热点",
                    hotnessScore = 0.91,
                    updatedAt = 1_500,
                ),
                candidate(
                    conceptId = "cool-candidate",
                    title = "模型误判热点",
                    hotnessScore = 0.35,
                    updatedAt = 9_000,
                ),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.defaultCenterNodeId).isEqualTo("hot-candidate")
        assertThat(snapshot.nodes.first { it.conceptId == "hot-candidate" }.hotnessScore).isEqualTo(0.91)
        assertThat(snapshot.nodes.first { it.conceptId == "hot-candidate" }.updatedAt).isEqualTo(1_500)
        assertThat(snapshot.nodes.first { it.conceptId == "cool-candidate" }.hotnessScore).isEqualTo(0.35)
        assertThat(snapshot.nodes.first { it.conceptId == "cool-candidate" }.updatedAt).isEqualTo(9_000)
    }

    @Test
    fun `summarize rejects illegal relation labels outside v1 set`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "sleep",
                      "label": "睡眠",
                      "hotnessScore": 0.8,
                      "updatedAt": 1000
                    },
                    {
                      "conceptId": "exercise",
                      "label": "运动",
                      "hotnessScore": 0.7,
                      "updatedAt": 900
                    }
                  ],
                  "edges": [
                    {
                      "fromConceptId": "sleep",
                      "toConceptId": "exercise",
                      "relationType": "depends_on",
                      "reasonLine": "非法关系。"
                    }
                  ]
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate("sleep", "睡眠", hotnessScore = 0.8, updatedAt = 1000),
                candidate("exercise", "运动", hotnessScore = 0.7, updatedAt = 900),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.edges).isEmpty()
    }

    @Test
    fun `summarize drops ambiguous alias or title matches instead of guessing`() = runTest {
        val planner = planner(
            aiResponse = """
                {
                  "nodes": [
                    {
                      "conceptId": "rest",
                      "label": "恢复"
                    },
                    {
                      "conceptId": "focus",
                      "label": "专注"
                    }
                  ],
                  "edges": [
                    {
                      "fromConceptId": "rest",
                      "toConceptId": "focus",
                      "relationType": "references",
                      "reasonLine": "这条边不应该保留。"
                    }
                  ]
                }
            """.trimIndent(),
        )

        val snapshot = planner.summarize(
            candidates = listOf(
                candidate(
                    conceptId = "sleep",
                    title = "恢复",
                    aliases = listOf("rest"),
                    hotnessScore = 0.8,
                    updatedAt = 1_000,
                ),
                candidate(
                    conceptId = "recovery",
                    title = "恢复",
                    aliases = listOf("rest"),
                    hotnessScore = 0.7,
                    updatedAt = 900,
                ),
                candidate(
                    conceptId = "focus",
                    title = "专注",
                    hotnessScore = 0.6,
                    updatedAt = 800,
                ),
            ),
        )

        assertThat(snapshot.source).isEqualTo("router+rule")
        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("sleep", "recovery", "focus").inOrder()
        assertThat(snapshot.nodes.first { it.conceptId == "sleep" }.label).isEqualTo("恢复")
        assertThat(snapshot.nodes.first { it.conceptId == "recovery" }.label).isEqualTo("恢复")
        assertThat(snapshot.nodes.first { it.conceptId == "focus" }.label).isEqualTo("专注")
        assertThat(snapshot.edges).isEmpty()
    }

    private fun planner(
        aiResponse: String = """{"nodes":[],"edges":[]}""",
        resultFactory: ((String) -> AiChatResult)? = null,
    ): ConceptGraphPlanner {
        var parsed = parseLegacySnapshotPayload(aiResponse)
        return plannerWithProvider { request ->
            if (request.type == AiTaskType.GRAPH_EXTRACT_CONCEPTS && resultFactory != null) {
                parsed = when (val result = resultFactory((request.input as AiTaskInput.GraphContext).contextSummary)) {
                    is AiChatResult.Success -> parseLegacySnapshotPayload(result.content)
                    is AiChatResult.Failure -> ParsedLegacySnapshotPayload()
                }
            }
            when (request.type) {
                AiTaskType.GRAPH_EXTRACT_CONCEPTS -> AiTaskPayload.GraphConcepts(parsed.concepts)
                AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> AiTaskPayload.GraphCanonicalization(parsed.canonical)
                AiTaskType.GRAPH_GENERATE_RELATIONS -> AiTaskPayload.GraphRelations(parsed.relations)
                else -> null
            }
        }
    }

    private fun plannerWithRouter(
        conceptPayload: List<String>,
        canonicalPayload: Map<String, List<String>>,
        relationPayload: List<GraphRelation>,
    ): ConceptGraphPlanner = plannerWithProvider { request ->
        when (request.type) {
            AiTaskType.GRAPH_EXTRACT_CONCEPTS -> AiTaskPayload.GraphConcepts(conceptPayload)
            AiTaskType.GRAPH_CANONICALIZE_CONCEPTS -> AiTaskPayload.GraphCanonicalization(canonicalPayload)
            AiTaskType.GRAPH_GENERATE_RELATIONS -> AiTaskPayload.GraphRelations(relationPayload)
            else -> null
        }
    }

    private fun plannerWithProvider(
        payloadForRequest: (AiTaskRequest<*>) -> AiTaskPayload?,
    ): ConceptGraphPlanner = ConceptGraphPlanner(
        aiTaskRouter = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = FakeGraphTaskProvider(payloadForRequest),
            cloudProvider = FakeGraphTaskProvider { null },
        ),
    )

    private fun relation(
        from: String,
        to: String,
        type: String,
        reason: String,
        confidence: Float,
    ) = GraphRelation(
        fromConceptId = from,
        toConceptId = to,
        relationType = type,
        reasonLine = reason,
        confidence = confidence,
    )

    private fun parseLegacySnapshotPayload(
        raw: String,
    ): ParsedLegacySnapshotPayload {
        val jsonText = extractJsonObject(raw) ?: return ParsedLegacySnapshotPayload()
        val root = parseJsonFields(jsonText)
        val nodes = root.objectList("nodes")
        val edges = root.objectList("edges")
        return ParsedLegacySnapshotPayload(
            concepts = nodes.mapNotNull { node ->
                node.string("conceptId").takeIf { it.isNotBlank() }
                    ?: node.string("label").takeIf { it.isNotBlank() }
            },
            canonical = buildMap {
                nodes.forEach { node ->
                    val conceptId = node.string("conceptId")
                    val label = node.string("label").ifBlank { conceptId }
                    if (label.isBlank()) return@forEach
                    put(
                        label,
                        buildList {
                            if (conceptId.isNotBlank() && conceptId != label) add(conceptId)
                            addAll(node.stringList("aliases"))
                        }.distinct(),
                    )
                }
            },
            relations = edges.mapNotNull { edge ->
                val from = edge.string("fromConceptId")
                val to = edge.string("toConceptId")
                val type = edge.string("relationType")
                if (from.isBlank() || to.isBlank() || type.isBlank()) return@mapNotNull null
                relation(
                    from = from,
                    to = to,
                    type = type,
                    reason = edge.string("reasonLine"),
                    confidence = edge.double("confidence")?.toFloat() ?: 0f,
                )
            },
        )
    }

    private data class ParsedLegacySnapshotPayload(
        val concepts: List<String> = emptyList(),
        val canonical: Map<String, List<String>> = emptyMap(),
        val relations: List<GraphRelation> = emptyList(),
    )

    private fun candidate(
        conceptId: String,
        title: String,
        aliases: List<String> = emptyList(),
        hotnessScore: Double,
        updatedAt: Long,
        summary: String = "$title 的摘要。",
        sourceIds: List<String> = emptyList(),
    ) = ConceptGraphCandidate(
        conceptId = conceptId,
        title = title,
        aliases = aliases,
        summary = summary,
        hotnessScore = hotnessScore,
        updatedAt = updatedAt,
        sourceIds = sourceIds,
    )

    private fun invokeBuildAiContext(
        planner: ConceptGraphPlanner,
        candidates: List<ConceptGraphCandidate>,
    ): String {
        val method = ConceptGraphPlanner::class.java.getDeclaredMethod(
            "buildAiContext",
            List::class.java,
        )
        method.isAccessible = true
        return method.invoke(planner, candidates) as String
    }

    private fun extractCandidateConceptIds(
        context: String,
    ): List<String> {
        val candidatesArrayStart = context.indexOf("\"candidates\":[")
        require(candidatesArrayStart >= 0) { "Missing candidates array in context: $context" }

        val arrayStart = context.indexOf('[', startIndex = candidatesArrayStart)
        var depth = 0
        var inString = false
        var escaping = false

        for (index in arrayStart until context.length) {
            val character = context[index]
            if (escaping) {
                escaping = false
                continue
            }
            when (character) {
                '\\' -> if (inString) escaping = true
                '"' -> inString = !inString
                '[' -> if (!inString) depth += 1
                ']' -> if (!inString) {
                    depth -= 1
                    if (depth == 0) {
                        val candidatesArray = context.substring(arrayStart, index + 1)
                        return Regex("\\{\"conceptId\":\"([^\"]+)\"")
                            .findAll(candidatesArray)
                            .map { match -> match.groupValues[1] }
                            .toList()
                    }
                }
            }
        }

        error("Unterminated candidates array in context: $context")
    }

    private fun assertJsonObjectParses(
        json: String,
    ) {
        val parserClass = Class.forName(
            "com.mindflow.app.data.wiki.ConceptGraphPlanner\$MiniJsonParser",
        )
        val parserConstructor = parserClass.getDeclaredConstructor(String::class.java)
        parserConstructor.isAccessible = true
        val parser = parserConstructor.newInstance(json)
        val parseObject = parserClass.getDeclaredMethod("parseObject")
        parseObject.isAccessible = true
        parseObject.invoke(parser)
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

    private fun parseJsonFields(json: String): ReflectiveJsonFields {
        val parserClass = Class.forName("com.mindflow.app.data.wiki.ConceptGraphPlanner\$MiniJsonParser")
        val parserConstructor = parserClass.getDeclaredConstructor(String::class.java)
        parserConstructor.isAccessible = true
        val parser = parserConstructor.newInstance(json)
        val parseObject = parserClass.getDeclaredMethod("parseObject")
        parseObject.isAccessible = true
        return ReflectiveJsonFields(parseObject.invoke(parser) ?: error("Parser returned null"))
    }

    private class ReflectiveJsonFields(
        private val delegate: Any,
    ) {
        private val clazz = delegate.javaClass

        fun string(key: String): String {
            val method = clazz.getDeclaredMethod("string", String::class.java)
            method.isAccessible = true
            return method.invoke(delegate, key) as String
        }

        fun stringList(key: String): List<String> {
            val method = clazz.getDeclaredMethod("stringList", String::class.java)
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return method.invoke(delegate, key) as List<String>
        }

        fun objectList(key: String): List<ReflectiveJsonFields> {
            val method = clazz.getDeclaredMethod("objectList", String::class.java)
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return (method.invoke(delegate, key) as List<*>).mapNotNull { item -> item?.let(::ReflectiveJsonFields) }
        }

        fun double(key: String): Double? {
            val method = clazz.getDeclaredMethod("double", String::class.java)
            method.isAccessible = true
            return method.invoke(delegate, key) as Double?
        }
    }

    private class FakeGraphTaskProvider(
        private val payloadForRequest: (AiTaskRequest<*>) -> AiTaskPayload?,
    ) : AiTaskProvider {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? =
            payloadForRequest(request) as T?
    }
}
