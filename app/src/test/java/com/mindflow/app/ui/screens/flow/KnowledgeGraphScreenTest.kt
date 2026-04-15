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
        val snapshot = largeConnectedSnapshot()

        val collapsed = buildConceptGraphViewport(snapshot)
        val firstExpansion = buildConceptGraphViewport(
            snapshot = snapshot,
            expandedCenterNodeIds = listOf("center"),
        )
        val secondExpansion = buildConceptGraphViewport(
            snapshot = snapshot,
            expandedCenterNodeIds = listOf("center", "center"),
        )

        assertThat(collapsed.neighbors).hasSize(6)
        assertThat(firstExpansion.neighbors.map { it.node.conceptId })
            .containsExactly(
                "node-1",
                "node-2",
                "node-3",
                "node-4",
                "node-5",
                "node-6",
                "node-7",
                "node-8",
                "node-9",
                "node-10",
                "node-11",
                "node-12",
            )
            .inOrder()
        assertThat(firstExpansion.hiddenNeighborCount).isEqualTo(2)
        assertThat(secondExpansion.neighbors.map { it.node.conceptId })
            .containsExactly(
                "node-1",
                "node-2",
                "node-3",
                "node-4",
                "node-5",
                "node-6",
                "node-7",
                "node-8",
                "node-9",
                "node-10",
                "node-11",
                "node-12",
                "node-13",
                "node-14",
            )
            .inOrder()
        assertThat(secondExpansion.hiddenNeighborCount).isEqualTo(0)
    }

    @Test
    fun `buildConceptGraphViewport exposes switchable nodes when center is isolated`() {
        val snapshot = conceptSnapshot(
            defaultCenterNodeId = "solo",
            nodes = listOf(
                conceptNode("solo", "独立概念", hotnessScore = 0.4, updatedAt = 400),
                conceptNode("connected", "连通概念", hotnessScore = 0.9, updatedAt = 900),
                conceptNode("satellite", "卫星概念", hotnessScore = 0.7, updatedAt = 700),
                conceptNode("island", "孤岛概念", hotnessScore = 0.6, updatedAt = 600),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "connected",
                    toConceptId = "satellite",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.8,
                ),
            ),
        )

        val viewport = buildConceptGraphViewport(snapshot)

        assertThat(viewport.centerNode?.conceptId).isEqualTo("solo")
        assertThat(viewport.neighbors).isEmpty()
        assertThat(viewport.switchableNodes.map { it.conceptId })
            .containsExactly("connected", "satellite", "island")
            .inOrder()
    }

    @Test
    fun `buildConceptGraphCenterRelation uses best ranked edge for selected neighbor`() {
        val graph = ConceptGraphSnapshot(
            defaultCenterNodeId = "work",
            nodes = listOf(
                conceptNode("work", "工作系统", hotnessScore = 0.9, updatedAt = 900),
                conceptNode("learning", "学习循环", hotnessScore = 0.8, updatedAt = 800),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "work",
                    toConceptId = "learning",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.4,
                    reasonLine = "低优先级关系。",
                ),
                conceptEdge(
                    fromConceptId = "work",
                    toConceptId = "learning",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.9,
                    reasonLine = "高优先级关系。",
                ),
            ),
        )

        val relation = buildConceptGraphCenterRelation(
            graph = graph,
            previousCenterNodeId = "work",
            currentCenterNodeId = "learning",
        )

        assertThat(relation).isEqualTo(
            ConceptGraphCenterRelation(
                relationWord = "支持",
                reasonLine = "高优先级关系。",
            ),
        )
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

    private fun largeConnectedSnapshot(): DirectionWikiSnapshot =
        conceptSnapshot(
            defaultCenterNodeId = "center",
            nodes = buildList {
                add(conceptNode("center", "中心知识点", hotnessScore = 1.0, updatedAt = 1_000))
                repeat(14) { index ->
                    add(
                        conceptNode(
                            conceptId = "node-${index + 1}",
                            label = "关联${index + 1}",
                            hotnessScore = 0.95 - (index * 0.03),
                            updatedAt = 950L - index,
                        ),
                    )
                }
            },
            edges = List(14) { index ->
                conceptEdge(
                    fromConceptId = "center",
                    toConceptId = "node-${index + 1}",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.99 - (index * 0.01),
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
