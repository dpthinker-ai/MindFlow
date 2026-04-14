package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiGraphMaturity
import com.mindflow.app.data.wiki.DirectionWikiGraphOverview
import com.mindflow.app.data.wiki.DirectionWikiGraphPresentationEdge
import com.mindflow.app.data.wiki.DirectionWikiGraphPresentationNode
import com.mindflow.app.data.wiki.DirectionWikiGraphPresentationSnapshot
import com.mindflow.app.data.wiki.DirectionWikiGraphSnapshot
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import org.junit.Test

class KnowledgeGraphScreenTest {
    @Test
    fun `selectVisibleGraph prefers presentation nodes and filters edges to visible graph`() {
        val snapshot = DirectionWikiSnapshot(
            directions = mapOf(
                "folder:work" to directionSummary("folder:work", "工作"),
                "tag:learning" to directionSummary("tag:learning", "学习"),
            ),
            graph = DirectionWikiGraphSnapshot(
                overview = DirectionWikiGraphOverview(
                    hubThreadKeys = listOf("folder:work", "tag:missing"),
                    isolatedThreadKeys = listOf("tag:learning"),
                ),
                presentation = DirectionWikiGraphPresentationSnapshot(
                    nodes = listOf(
                        DirectionWikiGraphPresentationNode(
                            threadKey = "folder:work",
                            label = "工作",
                            summaryLine = "工作结构在变清楚。",
                            densityScore = 0.9,
                            maturity = DirectionWikiGraphMaturity.STABLE,
                            noteCount = 6,
                        ),
                        DirectionWikiGraphPresentationNode(
                            threadKey = "tag:learning",
                            label = "学习",
                            summaryLine = "学习主题持续出现。",
                            densityScore = 0.4,
                            maturity = DirectionWikiGraphMaturity.FORMING,
                            noteCount = 2,
                        ),
                    ),
                    edges = listOf(
                        DirectionWikiGraphPresentationEdge(
                            fromThreadKey = "folder:work",
                            toThreadKey = "tag:learning",
                            strength = 4,
                            reasonLine = "方法开始共享。",
                        ),
                        DirectionWikiGraphPresentationEdge(
                            fromThreadKey = "folder:work",
                            toThreadKey = "tag:missing",
                            strength = 3,
                            reasonLine = "这条边不应该被保留。",
                        ),
                    ),
                ),
            ),
        )

        val selection = selectVisibleGraph(snapshot)

        assertThat(selection.nodes.map { it.threadKey }).containsExactly("folder:work", "tag:learning")
        assertThat(selection.edges).hasSize(1)
        assertThat(selection.edges.single().fromThreadKey).isEqualTo("folder:work")
        assertThat(selection.edges.single().toThreadKey).isEqualTo("tag:learning")
        assertThat(selection.hubNodeIds).containsExactly("folder:work")
        assertThat(selection.isolatedNodeIds).containsExactly("tag:learning")
    }

    @Test
    fun `projectPureGraphInfo fills blank summaries from direction snapshot`() {
        val snapshot = DirectionWikiSnapshot(
            directions = mapOf(
                "folder:work" to directionSummary(
                    threadKey = "folder:work",
                    title = "工作",
                    conclusionLine = "工作主线已经更清楚了。",
                ),
            ),
        )
        val selection = SelectedGraphData(
            headline = "1 个主题",
            summaryLine = "",
            hubNodeIds = setOf("folder:work"),
            isolatedNodeIds = emptySet(),
            nodes = listOf(
                SelectedGraphNode(
                    threadKey = "folder:work",
                    label = "工作",
                    summaryLine = "",
                    densityScore = 0.8,
                    maturity = DirectionWikiGraphMaturity.STRENGTHENING,
                    noteCount = 5,
                ),
            ),
            edges = emptyList(),
        )

        val projection = projectPureGraphInfo(snapshot, selection)

        assertThat(projection.nodes).hasSize(1)
        assertThat(projection.nodes.single().summaryLine).isEqualTo("工作主线已经更清楚了。")
        assertThat(projection.nodes.single().relationCount).isEqualTo(0)
    }

    @Test
    fun `buildGraphVisualState assigns structure statuses from projection`() {
        val projection = GraphProjection(
            headline = "3 个主题",
            summaryLine = "",
            hubNodeIds = setOf("folder:work"),
            isolatedNodeIds = setOf("tag:health"),
            nodes = listOf(
                GraphProjectionNode(
                    threadKey = "folder:work",
                    label = "工作",
                    summaryLine = "工作主线更清楚了。",
                    densityScore = 0.9,
                    maturity = DirectionWikiGraphMaturity.STABLE,
                    noteCount = 6,
                    relationCount = 2,
                ),
                GraphProjectionNode(
                    threadKey = "tag:learning",
                    label = "学习",
                    summaryLine = "学习方法在成形。",
                    densityScore = 0.6,
                    maturity = DirectionWikiGraphMaturity.STRENGTHENING,
                    noteCount = 3,
                    relationCount = 1,
                ),
                GraphProjectionNode(
                    threadKey = "tag:health",
                    label = "健康",
                    summaryLine = "健康主题独立出现。",
                    densityScore = 0.3,
                    maturity = DirectionWikiGraphMaturity.FORMING,
                    noteCount = 1,
                    relationCount = 0,
                ),
            ),
            edges = listOf(
                GraphProjectionEdge(
                    fromThreadKey = "folder:work",
                    toThreadKey = "tag:learning",
                    strength = 4,
                    reasonLine = "方法共享。",
                ),
            ),
        )

        val overview = buildGraphVisualState(
            directions = emptyMap(),
            projection = projection,
        )

        assertThat(overview.nodes.first { it.id == "folder:work" }.structureStatus).isEqualTo(GraphStructureStatus.HUB)
        assertThat(overview.nodes.first { it.id == "tag:learning" }.structureStatus).isEqualTo(GraphStructureStatus.LINKED)
        assertThat(overview.nodes.first { it.id == "tag:health" }.structureStatus).isEqualTo(GraphStructureStatus.ISOLATED)
    }

    @Test
    fun `buildActivatedGraphNodes only uses exact folder and tag matches`() {
        val graphNodes = listOf(
            GraphNodeUi(
                id = "folder:work",
                label = "工作",
                summaryLine = "工作主线更清楚了。",
                threadKey = "folder:work",
                structureStatus = GraphStructureStatus.HUB,
                densityScore = 0.8,
                maturity = DirectionWikiGraphMaturity.STABLE,
                noteCount = 5,
                relationCount = 2,
                priority = 5,
            ),
        )

        val fuzzyOnly = buildActivatedGraphNodes(
            notes = listOf(
                note(
                    id = 1,
                    topic = "随手想法",
                    content = "今天一直在想工作节奏，但没有打标签。",
                ),
            ),
            graphNodes = graphNodes,
        )
        val exactMatch = buildActivatedGraphNodes(
            notes = listOf(
                note(
                    id = 2,
                    topic = "工作记录",
                    content = "补一条工作记录。",
                    folderKey = "work",
                ),
            ),
            graphNodes = graphNodes,
        )

        assertThat(fuzzyOnly).isEmpty()
        assertThat(exactMatch.map { it.id }).containsExactly("folder:work")
    }

    @Test
    fun `buildDisplayedGraphEdges keeps backbone and expands local relations for selected node`() {
        val nodes = listOf(
            graphNode(id = "folder:work", label = "工作", priority = 5, relationCount = 2),
            graphNode(id = "tag:learning", label = "学习", priority = 4, relationCount = 2),
            graphNode(id = "tag:writing", label = "写作", priority = 3, relationCount = 2),
        )
        val edges = listOf(
            GraphEdgeUi(fromId = "folder:work", toId = "tag:learning", weight = 5, reasonLine = "方法共享。"),
            GraphEdgeUi(fromId = "tag:learning", toId = "tag:writing", weight = 4, reasonLine = "表达互相支撑。"),
            GraphEdgeUi(fromId = "folder:work", toId = "tag:writing", weight = 2, reasonLine = "零散相关。"),
        )

        val defaultEdges = buildDisplayedGraphEdges(
            nodes = nodes,
            edges = edges,
            selectedNodeId = null,
        )
        val focusedEdges = buildDisplayedGraphEdges(
            nodes = nodes,
            edges = edges,
            selectedNodeId = "folder:work",
        )

        assertThat(defaultEdges.map { setOf(it.fromId, it.toId) })
            .containsExactly(setOf("folder:work", "tag:learning"), setOf("tag:learning", "tag:writing"))
        assertThat(defaultEdges.all { it.emphasis == GraphEdgeEmphasis.BACKBONE }).isTrue()
        assertThat(focusedEdges.map { setOf(it.fromId, it.toId) })
            .containsExactly(
                setOf("folder:work", "tag:learning"),
                setOf("tag:learning", "tag:writing"),
                setOf("folder:work", "tag:writing"),
            )
        assertThat(
            focusedEdges.first { setOf(it.fromId, it.toId) == setOf("folder:work", "tag:writing") }.emphasis,
        ).isEqualTo(GraphEdgeEmphasis.FOCUS)
    }

    private fun graphNode(
        id: String,
        label: String,
        priority: Int,
        relationCount: Int,
    ) = GraphNodeUi(
        id = id,
        label = label,
        summaryLine = "$label 正在成形。",
        threadKey = id,
        structureStatus = GraphStructureStatus.LINKED,
        densityScore = 0.5,
        maturity = DirectionWikiGraphMaturity.STRENGTHENING,
        noteCount = 3,
        relationCount = relationCount,
        priority = priority,
    )

    private fun directionSummary(
        threadKey: String,
        title: String,
        conclusionLine: String = "",
    ) = DirectionWikiDirectionSummary(
        threadKey = threadKey,
        slug = threadKey,
        title = title,
        stage = DirectionStage.FORMING,
        conclusionLine = conclusionLine,
        updatedAt = 1_000L,
    )

    private fun note(
        id: Long,
        topic: String,
        content: String,
        folderKey: String? = null,
        tags: List<String> = emptyList(),
    ) = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.RULE,
        folderKey = folderKey,
        folderSource = FolderSource.RULE,
        tags = tags,
        tagSource = TagSource.RULE,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}
