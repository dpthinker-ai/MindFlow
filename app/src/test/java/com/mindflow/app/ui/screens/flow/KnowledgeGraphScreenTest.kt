package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
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

        val viewport = buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = "solo",
        )

        assertThat(viewport.centerNode?.conceptId).isEqualTo("learning")
        assertThat(viewport.neighbors.map { it.node.conceptId }).containsExactly("work")
        assertThat(viewport.hiddenNeighborCount).isEqualTo(0)
    }

    @Test
    fun `buildConceptGraphViewport falls back to a connected center when default center is isolated`() {
        val snapshot = conceptSnapshot(
            defaultCenterNodeId = "solo",
            nodes = listOf(
                conceptNode("solo", "独立点", hotnessScore = 1.0, updatedAt = 1_000),
                conceptNode("hub", "中心知识点", hotnessScore = 0.9, updatedAt = 900),
                conceptNode("leaf-a", "关联A", hotnessScore = 0.8, updatedAt = 800),
                conceptNode("leaf-b", "关联B", hotnessScore = 0.7, updatedAt = 700),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "hub",
                    toConceptId = "leaf-a",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.95,
                ),
                conceptEdge(
                    fromConceptId = "hub",
                    toConceptId = "leaf-b",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.9,
                ),
            ),
        )

        val viewport = buildConceptGraphViewport(snapshot)

        assertThat(viewport.centerNode?.conceptId).isEqualTo("hub")
        assertThat(viewport.neighbors.map { it.node.conceptId })
            .containsExactly("leaf-a", "leaf-b")
            .inOrder()
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

        val collapsed = buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = "solo",
        )
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

        val viewport = buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = "solo",
        )

        assertThat(viewport.centerNode?.conceptId).isEqualTo("solo")
        assertThat(viewport.neighbors).isEmpty()
        assertThat(viewport.switchableNodes.map { it.conceptId })
            .containsExactly("connected", "satellite", "island")
            .inOrder()
    }

    @Test
    fun `buildConceptGraphViewport batches switchable nodes for isolated sparse graph`() {
        val snapshot = isolatedSparseSnapshot()

        val collapsed = buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = "solo",
        )
        val firstExpansion = buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = "solo",
            expandedCenterNodeIds = listOf("solo"),
        )
        val secondExpansion = buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = "solo",
            expandedCenterNodeIds = listOf("solo", "solo"),
        )

        assertThat(collapsed.centerNode?.conceptId).isEqualTo("solo")
        assertThat(collapsed.neighbors).isEmpty()
        assertThat(collapsed.switchableNodes.map { it.conceptId })
            .containsExactly("node-1", "node-2", "node-3", "node-4", "node-5", "node-6")
            .inOrder()
        assertThat(firstExpansion.neighbors).isEmpty()
        assertThat(firstExpansion.switchableNodes.map { it.conceptId })
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
        assertThat(secondExpansion.switchableNodes.map { it.conceptId })
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
    }

    @Test
    fun `buildConceptGraphViewport ranks isolated switch targets by distinct connected concepts`() {
        val snapshot = conceptSnapshot(
            defaultCenterNodeId = "solo",
            nodes = listOf(
                conceptNode("solo", "独立概念", hotnessScore = 0.1, updatedAt = 100),
                conceptNode("spread", "分散连接", hotnessScore = 0.2, updatedAt = 200),
                conceptNode("duplicate", "重复连接", hotnessScore = 0.99, updatedAt = 990),
                conceptNode("bridge", "桥梁概念", hotnessScore = 0.98, updatedAt = 980),
                conceptNode("leaf-1", "叶子1", hotnessScore = 0.4, updatedAt = 400),
                conceptNode("leaf-2", "叶子2", hotnessScore = 0.3, updatedAt = 300),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "duplicate",
                    toConceptId = "bridge",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.9,
                ),
                conceptEdge(
                    fromConceptId = "duplicate",
                    toConceptId = "bridge",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.8,
                ),
                conceptEdge(
                    fromConceptId = "bridge",
                    toConceptId = "duplicate",
                    relationType = ConceptGraphRelationType.REFERENCES,
                    confidence = 0.7,
                ),
                conceptEdge(
                    fromConceptId = "spread",
                    toConceptId = "leaf-1",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.6,
                ),
                conceptEdge(
                    fromConceptId = "spread",
                    toConceptId = "leaf-2",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.5,
                ),
            ),
        )

        val viewport = buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = "solo",
            batchSize = 10,
        )

        assertThat(viewport.centerNode?.conceptId).isEqualTo("solo")
        assertThat(viewport.neighbors).isEmpty()
        assertThat(viewport.switchableNodes.map { it.conceptId })
            .containsExactly("spread", "duplicate", "bridge", "leaf-1", "leaf-2")
            .inOrder()
    }

    @Test
    fun `buildConceptGraphViewport keeps other disconnected components reachable when center has neighbors`() {
        val snapshot = conceptSnapshot(
            defaultCenterNodeId = "core-a",
            nodes = listOf(
                conceptNode("core-a", "核心A", hotnessScore = 0.95, updatedAt = 950),
                conceptNode("core-b", "核心B", hotnessScore = 0.9, updatedAt = 940),
                conceptNode("island-a", "孤岛A", hotnessScore = 0.85, updatedAt = 930),
                conceptNode("island-b", "孤岛B", hotnessScore = 0.8, updatedAt = 920),
                conceptNode("island-c", "孤岛C", hotnessScore = 0.75, updatedAt = 910),
                conceptNode("island-d", "孤岛D", hotnessScore = 0.7, updatedAt = 900),
                conceptNode("island-e", "孤岛E", hotnessScore = 0.65, updatedAt = 890),
                conceptNode("island-f", "孤岛F", hotnessScore = 0.6, updatedAt = 880),
                conceptNode("island-g", "孤岛G", hotnessScore = 0.55, updatedAt = 870),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "core-a",
                    toConceptId = "core-b",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.95,
                ),
                conceptEdge(
                    fromConceptId = "island-a",
                    toConceptId = "island-b",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.91,
                ),
                conceptEdge(
                    fromConceptId = "island-c",
                    toConceptId = "island-d",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.88,
                ),
                conceptEdge(
                    fromConceptId = "island-e",
                    toConceptId = "island-f",
                    relationType = ConceptGraphRelationType.REFERENCES,
                    confidence = 0.83,
                ),
                conceptEdge(
                    fromConceptId = "island-g",
                    toConceptId = "island-g",
                    relationType = ConceptGraphRelationType.PARALLEL,
                    confidence = 0.4,
                ),
            ),
        )

        val viewport = buildConceptGraphViewport(
            snapshot = snapshot,
            batchSize = 3,
        )

        assertThat(viewport.centerNode?.conceptId).isEqualTo("core-a")
        assertThat(viewport.neighbors.map { it.node.conceptId }).containsExactly("core-b")
        assertThat(viewport.switchableNodes.map { it.conceptId })
            .containsExactly("island-a", "island-c", "island-e")
            .inOrder()
        assertThat(viewport.hiddenSwitchableNodeCount).isEqualTo(1)
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

    @Test
    fun `buildConceptGraphViewport uses reverse relation word for incoming edge neighbor`() {
        val snapshot = conceptSnapshot(
            defaultCenterNodeId = "learning",
            nodes = listOf(
                conceptNode("work", "工作系统", hotnessScore = 0.9, updatedAt = 900),
                conceptNode("learning", "学习循环", hotnessScore = 0.8, updatedAt = 800),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "work",
                    toConceptId = "learning",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.9,
                ),
            ),
        )

        val viewport = buildConceptGraphViewport(snapshot)

        assertThat(viewport.centerNode?.conceptId).isEqualTo("learning")
        assertThat(viewport.neighbors.map { it.node.conceptId }).containsExactly("work")
        assertThat(viewport.neighbors.single().relationWord).isEqualTo("被支持")
    }

    @Test
    fun `buildConceptGraphViewport prefers center outgoing relation word for opposite direction pair`() {
        val snapshot = conceptSnapshot(
            defaultCenterNodeId = "work",
            nodes = listOf(
                conceptNode("work", "工作系统", hotnessScore = 0.9, updatedAt = 900),
                conceptNode("learning", "学习循环", hotnessScore = 0.8, updatedAt = 800),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "work",
                    toConceptId = "learning",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.6,
                    reasonLine = "从工作出发时应该展示这条关系。",
                ),
                conceptEdge(
                    fromConceptId = "learning",
                    toConceptId = "work",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.95,
                    reasonLine = "反向关系虽然更强，但不该改写当前中心的文案。",
                ),
            ),
        )

        val viewport = buildConceptGraphViewport(snapshot)

        assertThat(viewport.centerNode?.conceptId).isEqualTo("work")
        assertThat(viewport.neighbors.map { it.node.conceptId }).containsExactly("learning")
        assertThat(viewport.neighbors.single().relationWord).isEqualTo("支持")
    }

    @Test
    fun `buildConceptGraphCenterRelation uses reverse relation word for backward traversal`() {
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
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.9,
                    reasonLine = "把工作拆回可执行练习。",
                ),
            ),
        )

        val relation = buildConceptGraphCenterRelation(
            graph = graph,
            previousCenterNodeId = "learning",
            currentCenterNodeId = "work",
        )

        assertThat(relation).isEqualTo(
            ConceptGraphCenterRelation(
                relationWord = "被支持",
                reasonLine = "把工作拆回可执行练习。",
            ),
        )
    }

    @Test
    fun `buildConceptGraphCenterRelation prefers previous center outgoing edge for opposite direction pair`() {
        val graph = ConceptGraphSnapshot(
            defaultCenterNodeId = "work",
            nodes = listOf(
                conceptNode("work", "工作系统", hotnessScore = 0.9, updatedAt = 900),
                conceptNode("learning", "学习循环", hotnessScore = 0.8, updatedAt = 800),
            ),
            edges = listOf(
                conceptEdge(
                    fromConceptId = "learning",
                    toConceptId = "work",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.55,
                    reasonLine = "从学习回到工作时应该沿这条关系解释。",
                ),
                conceptEdge(
                    fromConceptId = "work",
                    toConceptId = "learning",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.97,
                    reasonLine = "反向关系更强，但不该覆盖当前切换方向。",
                ),
            ),
        )

        val relation = buildConceptGraphCenterRelation(
            graph = graph,
            previousCenterNodeId = "learning",
            currentCenterNodeId = "work",
        )

        assertThat(relation).isEqualTo(
            ConceptGraphCenterRelation(
                relationWord = "支持",
                reasonLine = "从学习回到工作时应该沿这条关系解释。",
            ),
        )
    }

    @Test
    fun `buildActivatedGraphNodes highlights source backed concept nodes for selected notes`() {
        val graphNodes = listOf(
            GraphNodeUi(
                id = "sleep",
                label = "睡眠",
                summaryLine = "睡眠相关。",
                structureStatus = GraphStructureStatus.LINKED,
                noteCount = 3,
                relationCount = 2,
                sourceIds = listOf("note:11"),
            ),
            GraphNodeUi(
                id = "recovery",
                label = "恢复",
                summaryLine = "恢复相关。",
                structureStatus = GraphStructureStatus.LINKED,
                noteCount = 2,
                relationCount = 1,
                sourceIds = listOf("note:22"),
            ),
            GraphNodeUi(
                id = "focus",
                label = "专注",
                summaryLine = "专注相关。",
                structureStatus = GraphStructureStatus.EMERGING,
                noteCount = 1,
                relationCount = 0,
                sourceIds = listOf("note:33"),
            ),
        )

        val activated = buildActivatedGraphNodes(
            notes = listOf(
                note(22L, "恢复记录"),
                note(11L, "睡眠记录"),
            ),
            graphNodes = graphNodes,
        )

        assertThat(activated.map { it.id }).containsExactly("sleep", "recovery").inOrder()
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

    private fun isolatedSparseSnapshot(): DirectionWikiSnapshot =
        conceptSnapshot(
            defaultCenterNodeId = "solo",
            nodes = buildList {
                add(conceptNode("solo", "独立概念", hotnessScore = 0.2, updatedAt = 1_000))
                repeat(14) { index ->
                    add(
                        conceptNode(
                            conceptId = "node-${index + 1}",
                            label = "候选${index + 1}",
                            hotnessScore = 0.98 - (index * 0.02),
                            updatedAt = 980L - index,
                        ),
                    )
                }
            },
            edges = listOf(
                conceptEdge(
                    fromConceptId = "node-1",
                    toConceptId = "node-2",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    confidence = 0.9,
                ),
                conceptEdge(
                    fromConceptId = "node-1",
                    toConceptId = "node-3",
                    relationType = ConceptGraphRelationType.ADVANCES,
                    confidence = 0.85,
                ),
                conceptEdge(
                    fromConceptId = "node-4",
                    toConceptId = "node-5",
                    relationType = ConceptGraphRelationType.REFERENCES,
                    confidence = 0.7,
                ),
            ),
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

    private fun note(
        id: Long,
        topic: String,
    ) = NoteEntity(
        id = id,
        content = "$topic 内容",
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = null,
        folderSource = FolderSource.RULE,
        tags = emptyList(),
        tagSource = TagSource.RULE,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = id * 10,
        updatedAt = id * 10 + 1,
    )
}
