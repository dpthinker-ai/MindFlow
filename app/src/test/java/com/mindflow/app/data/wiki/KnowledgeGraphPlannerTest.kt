package com.mindflow.app.data.wiki

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.model.AiSettings
import com.mindflow.app.data.settings.AiSettingsRepository
import com.mindflow.app.data.topic.AiServiceClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class KnowledgeGraphPlannerTest {
    @Test
    fun `summarize keeps dense canonical and presentation edges when ai is disabled`() = runTest {
        val planner = planner(aiEnabled = false)

        val snapshot = planner.summarize(
            summaries = denseSummaries(count = 6),
            knowledgeItems = denseKnowledgeItems(count = 6),
            threadNoteCounts = (1..6).associate { index -> "tag:topic-$index" to 3 },
        )

        assertThat(snapshot.source).isEqualTo("rule")
        assertThat(snapshot.edges).hasSize(15)
        assertThat(snapshot.presentation.nodes).hasSize(6)
        assertThat(snapshot.presentation.edges).hasSize(8)
    }

    @Test
    fun `buildPresentationAiContext advertises expanded edge budget`() {
        val planner = planner(aiEnabled = false)
        val canonical = canonicalSnapshotForParse()

        val context = invokeBuildPresentationAiContext(
            planner = planner,
            canonical = canonical,
        )

        assertThat(context).contains("边数量 0 到 8 条")
    }

    private fun planner(aiEnabled: Boolean): KnowledgeGraphPlanner =
        KnowledgeGraphPlanner(
            aiSettingsRepository = FakeAiSettingsRepository(
                initial = AiSettings(
                    apiKey = "",
                    aiEnabled = aiEnabled,
                ),
            ),
            aiServiceClient = AiServiceClient(),
        )

    private fun denseSummaries(count: Int): List<DirectionWikiDirectionSummary> =
        (1..count).map { index ->
            DirectionWikiDirectionSummary(
                threadKey = "tag:topic-$index",
                slug = "topic-$index",
                title = "主题$index",
                stage = when (index) {
                    1 -> DirectionStage.SETTLING
                    2, 3 -> DirectionStage.ADVANCING
                    else -> DirectionStage.FORMING
                },
                conclusionLine = "主题$index 正在成形。",
                updatedAt = 1_000L + index,
            )
        }

    private fun denseKnowledgeItems(count: Int): List<KnowledgeLayerSearchItem> =
        (1..count).map { index ->
            KnowledgeLayerSearchItem(
                id = "concept-$index",
                type = KnowledgeLayerSearchType.CONCEPT,
                title = "共享概念",
                threadKey = "tag:topic-$index",
                updatedAt = 1_000L + index,
            )
        }

    private fun canonicalSnapshotForParse(): DirectionWikiGraphSnapshot =
        DirectionWikiGraphSnapshot(
            overview = DirectionWikiGraphOverview(
                summaryLine = "四个主题已经连起来了。",
                hubThreadKeys = listOf("tag:topic-1"),
                isolatedThreadKeys = emptyList(),
            ),
            nodes = listOf(
                canonicalNode("tag:topic-1", "主题1", 0.9, DirectionWikiGraphMaturity.STABLE),
                canonicalNode("tag:topic-2", "主题2", 0.8, DirectionWikiGraphMaturity.STRENGTHENING),
                canonicalNode("tag:topic-3", "主题3", 0.7, DirectionWikiGraphMaturity.STRENGTHENING),
                canonicalNode("tag:topic-4", "主题4", 0.6, DirectionWikiGraphMaturity.FORMING),
            ),
            edges = listOf(
                canonicalEdge("tag:topic-1", "tag:topic-2", 5, 0.9, "共享概念。"),
                canonicalEdge("tag:topic-1", "tag:topic-3", 4, 0.8, "共享方法。"),
                canonicalEdge("tag:topic-1", "tag:topic-4", 4, 0.8, "共享问题。"),
                canonicalEdge("tag:topic-2", "tag:topic-3", 3, 0.7, "共享实验。"),
                canonicalEdge("tag:topic-3", "tag:topic-4", 3, 0.7, "共享判断。"),
            ),
        )

    private fun canonicalNode(
        threadKey: String,
        label: String,
        densityScore: Double,
        maturity: DirectionWikiGraphMaturity,
    ) = DirectionWikiGraphNode(
        threadKey = threadKey,
        label = label,
        summaryLine = "$label 正在成形。",
        gapLine = "",
        maturity = maturity,
        recencyScore = densityScore,
        densityScore = densityScore,
        supportIds = emptyList(),
        noteCount = 4,
        updatedAt = 1_000L,
    )

    private fun canonicalEdge(
        fromThreadKey: String,
        toThreadKey: String,
        strength: Int,
        confidence: Double,
        reasonLine: String,
    ) = DirectionWikiGraphEdge(
        fromThreadKey = fromThreadKey,
        toThreadKey = toThreadKey,
        relationType = DirectionWikiGraphRelationType.CO_OCCURRENCE,
        strength = strength,
        reasonLine = reasonLine,
        supportIds = emptyList(),
        firstSeenAt = 1_000L,
        lastSeenAt = 1_000L,
        confidence = confidence,
    )

    private fun invokeBuildPresentationAiContext(
        planner: KnowledgeGraphPlanner,
        canonical: DirectionWikiGraphSnapshot,
    ): String {
        val method = KnowledgeGraphPlanner::class.java.getDeclaredMethod(
            "buildPresentationAiContext",
            DirectionWikiGraphSnapshot::class.java,
        )
        method.isAccessible = true
        return method.invoke(planner, canonical) as String
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
