package com.mindflow.app.data.wiki

import com.google.common.truth.Truth.assertThat
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
        assertThat(snapshot.source).isEqualTo("llm+rule")
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.first().fromConceptId).isEqualTo("sleep")
        assertThat(snapshot.edges.first().relationType).isEqualTo(ConceptGraphRelationType.SUPPORTS)
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

        assertThat(snapshot.source).isEqualTo("llm+rule")
        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("sleep", "recovery").inOrder()
        assertThat(snapshot.edges).hasSize(1)
        assertThat(snapshot.edges.first().fromConceptId).isEqualTo("sleep")
        assertThat(snapshot.edges.first().toConceptId).isEqualTo("recovery")
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

        assertThat(snapshot.source).isEqualTo("llm+rule")
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

        assertThat(snapshot.source).isEqualTo("llm+rule")
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

        assertThat(snapshot.source).isEqualTo("llm+rule")
        assertThat(snapshot.nodes.map { it.conceptId }).containsExactly("focus")
        assertThat(snapshot.edges).isEmpty()
    }

    private fun planner(aiResponse: String): ConceptGraphPlanner =
        ConceptGraphPlanner(
            aiSettingsRepository = FakeAiSettingsRepository(
                initial = AiSettings(
                    apiKey = "test-key",
                    aiEnabled = true,
                ),
            ),
            aiServiceClient = AiServiceClient(),
            conceptGraphGenerator = { _, _ ->
                AiChatResult.Success(content = aiResponse)
            },
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

    private class FakeAiSettingsRepository(
        initial: AiSettings,
    ) : AiSettingsRepository {
        private val state = MutableStateFlow(initial)

        override val settings: Flow<AiSettings> = state

        override suspend fun getCurrent(): AiSettings = state.value

        override suspend fun save(settings: AiSettings) {
            state.value = settings
        }

        override suspend fun updateVerificationStatus(
            fingerprint: String,
            success: Boolean,
            message: String,
            verifiedAt: Long,
        ) = Unit

        override suspend fun recordUsage(
            requestIncrement: Int,
            successIncrement: Int,
            tokenIncrement: Int,
            dayKey: String,
        ) = Unit

        override suspend fun clear() {
            state.value = AiSettings()
        }
    }
}
