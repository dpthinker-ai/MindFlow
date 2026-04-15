package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.wiki.ConceptGraphEdge
import com.mindflow.app.data.wiki.ConceptGraphNode
import com.mindflow.app.data.wiki.ConceptGraphRelationType
import com.mindflow.app.data.wiki.ConceptGraphSnapshot
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import org.junit.Test

class KnowledgeGraphScreenTest {
    @Test
    fun `buildConceptGraphViewport uses snapshot default center`() {
        val snapshot = conceptSnapshot(
            defaultCenterNodeId = "learning",
            nodes = listOf(
                conceptNode("work", "工作", hotnessScore = 0.9, updatedAt = 100),
                conceptNode("learning", "学习", hotnessScore = 0.7, updatedAt = 200),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "work",
                    toConceptId = "learning",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.8,
                ),
            ),
        )

        val viewport = buildConceptGraphViewport(snapshot)

        assertThat(viewport.centerNode?.conceptId).isEqualTo("learning")
        assertThat(viewport.neighbors.map { it.node.conceptId }).containsExactly("work")
        assertThat(viewport.hiddenNeighborCount).isEqualTo(0)
    }

    @Test
    fun `buildConceptGraphViewport caps first hop neighbors at six`() {
        val snapshot = denselyConnectedSnapshot()

        val viewport = buildConceptGraphViewport(snapshot)

        assertThat(viewport.centerNode?.conceptId).isEqualTo("center")
        assertThat(viewport.neighbors.map { it.node.conceptId })
            .containsExactly("node-1", "node-2", "node-3", "node-4", "node-5", "node-6")
            .inOrder()
        assertThat(viewport.hiddenNeighborCount).isEqualTo(2)
    }

    @Test
    fun `buildConceptGraphViewport reveals more neighbors after explicit expansion`() {
        val snapshot = denselyConnectedSnapshot()

        val collapsed = buildConceptGraphViewport(snapshot)
        val expanded = buildConceptGraphViewport(
            snapshot = snapshot,
            expandedCenterNodeIds = setOf("center"),
        )

        assertThat(collapsed.neighbors).hasSize(6)
        assertThat(expanded.neighbors.map { it.node.conceptId })
            .containsExactly(
                "node-1",
                "node-2",
                "node-3",
                "node-4",
                "node-5",
                "node-6",
                "node-7",
                "node-8",
            )
            .inOrder()
        assertThat(expanded.hiddenNeighborCount).isEqualTo(0)
    }

    private fun denselyConnectedSnapshot(): DirectionWikiSnapshot =
        conceptSnapshot(
            defaultCenterNodeId = "center",
            nodes = buildList {
                add(conceptNode("center", "中心知识点", hotnessScore = 1.0, updatedAt = 1_000))
                repeat(8) { index ->
                    add(
                        conceptNode(
                            conceptId = "node-${index + 1}",
                            label = "关联${index + 1}",
                            hotnessScore = 0.9 - (index * 0.05),
                            updatedAt = 900L - index,
                        ),
                    )
                }
            },
            edges = List(8) { index ->
                conceptEdge(
                    fromConceptId = "center",
                    toConceptId = "node-${index + 1}",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.95 - (index * 0.05),
                )
            },
        )

    private fun conceptSnapshot(
        defaultCenterNodeId: String,
        nodes: List<ConceptGraphNode>,
        edges: List<ConceptGraphEdge>,
    ) = DirectionWikiSnapshot(
        conceptGraph = ConceptGraphSnapshot(
            defaultCenterNodeId = defaultCenterNodeId,
            nodes = nodes,
            edges = edges,
        ),
    )

    private fun conceptNode(
        conceptId: String,
        label: String,
        hotnessScore: Double,
        updatedAt: Long,
        summary: String = "$label 的摘要。",
    ) = ConceptGraphNode(
        conceptId = conceptId,
        label = label,
        summary = summary,
        hotnessScore = hotnessScore,
        updatedAt = updatedAt,
    )

    private fun conceptEdge(
        fromConceptId: String,
        toConceptId: String,
        relationType: ConceptGraphRelationType,
        confidence: Double,
        reasonLine: String = "这是一条关系解释。",
    ) = ConceptGraphEdge(
        fromConceptId = fromConceptId,
        toConceptId = toConceptId,
        relationType = relationType,
        reasonLine = reasonLine,
        confidence = confidence,
    )
}
