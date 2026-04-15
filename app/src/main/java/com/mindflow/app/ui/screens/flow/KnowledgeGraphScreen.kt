package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.wiki.ConceptGraphEdge
import com.mindflow.app.data.wiki.ConceptGraphNode
import com.mindflow.app.data.wiki.ConceptGraphRelationType
import com.mindflow.app.data.wiki.ConceptGraphSnapshot
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.TextMain
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import kotlin.math.absoluteValue

internal const val KnowledgeGraphCanvasTag = "knowledge-graph-canvas"
internal const val KnowledgeGraphInfoPanelTag = "knowledge-graph-info-panel"

internal fun graphNodeTestTag(nodeId: String): String =
    "graph-node-" + nodeId.replace(Regex("[^A-Za-z0-9_-]"), "_")

private const val ConceptGraphBatchSize = 6

internal data class ConceptGraphViewport(
    val centerNode: ConceptGraphNode? = null,
    val neighbors: List<ConceptGraphViewportNeighbor> = emptyList(),
    val hiddenNeighborCount: Int = 0,
    val switchableNodes: List<ConceptGraphNode> = emptyList(),
    val hiddenSwitchableNodeCount: Int = 0,
)

internal data class ConceptGraphViewportNeighbor(
    val node: ConceptGraphNode,
    val relation: ConceptGraphEdge,
    val relationWord: String,
)

internal data class ConceptGraphCenterRelation(
    val relationWord: String,
    val reasonLine: String,
)

private data class RankedConceptNeighbor(
    val node: ConceptGraphNode,
    val relation: ConceptGraphEdge,
    val fromCenter: Boolean,
)

private data class ConceptComponentSwitchTarget(
    val node: ConceptGraphNode,
    val componentSize: Int,
    val connectedConceptCount: Int,
)

private val rankedConceptNeighborComparator =
    compareByDescending<RankedConceptNeighbor> { it.relation.confidence }
        .thenByDescending { it.fromCenter }
        .thenByDescending { it.node.hotnessScore }
        .thenByDescending { it.node.updatedAt }
        .thenBy { it.node.label }

private val conceptNodeComparator =
    compareByDescending<ConceptGraphNode> { it.hotnessScore }
        .thenByDescending { it.updatedAt }
        .thenBy { it.label }

private val conceptComponentSwitchTargetComparator =
    compareByDescending<ConceptComponentSwitchTarget> { it.componentSize }
        .thenByDescending { it.connectedConceptCount }
        .thenByDescending { it.node.hotnessScore }
        .thenByDescending { it.node.updatedAt }
        .thenBy { it.node.label }

@Suppress("UNUSED_PARAMETER")
@Composable
fun KnowledgeGraphRoute(
    noteRepository: NoteRepository,
    directionWikiCoordinator: DirectionWikiCoordinator,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val snapshot = directionWikiCoordinator.snapshot.collectAsStateWithLifecycle().value
    KnowledgeGraphScreen(
        snapshot = snapshot,
        notes = emptyList(),
        onOpenNote = onOpenNote,
    )
}

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KnowledgeGraphScreen(
    snapshot: DirectionWikiSnapshot,
    notes: List<NoteEntity>,
    onOpenNote: (Long) -> Unit,
) {
    val defaultCenterNodeId = remember(snapshot.conceptGraph) {
        resolveConceptGraphCenterNodeId(snapshot).orEmpty()
    }
    val validNodeIds = remember(snapshot.conceptGraph.nodes) {
        snapshot.conceptGraph.nodes.map { it.conceptId }.toSet()
    }
    var centerNodeId by rememberSaveable { mutableStateOf(defaultCenterNodeId) }
    var previousCenterNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedNeighborCenterNodeIds by rememberSaveable(snapshot.conceptGraph) {
        mutableStateOf(emptyList<String>())
    }
    var expandedSwitchableCenterNodeIds by rememberSaveable(snapshot.conceptGraph) {
        mutableStateOf(emptyList<String>())
    }

    LaunchedEffect(snapshot.conceptGraph, defaultCenterNodeId, validNodeIds) {
        if (centerNodeId.isBlank() || centerNodeId !in validNodeIds) {
            centerNodeId = defaultCenterNodeId
            previousCenterNodeId = null
        }
    }

    val viewport = remember(
        snapshot.conceptGraph,
        centerNodeId,
        expandedNeighborCenterNodeIds,
        expandedSwitchableCenterNodeIds,
    ) {
        buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = centerNodeId.takeIf { it.isNotBlank() },
            expandedCenterNodeIds = expandedNeighborCenterNodeIds,
            expandedSwitchableCenterNodeIds = expandedSwitchableCenterNodeIds,
        )
    }
    val relationFromPreviousCenter = remember(
        snapshot.conceptGraph,
        previousCenterNodeId,
        viewport.centerNode?.conceptId,
    ) {
        buildConceptGraphCenterRelation(
            graph = snapshot.conceptGraph,
            previousCenterNodeId = previousCenterNodeId,
            currentCenterNodeId = viewport.centerNode?.conceptId,
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "知识图谱",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "围绕一个中心知识点展开，只先看一跳关系，再按需继续展开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    }
                }

                item {
                    if (viewport.centerNode == null) {
                        EmptyConceptGraphPanel()
                    } else {
                        ConceptGraphPanel(
                            viewport = viewport,
                            relationFromPreviousCenter = relationFromPreviousCenter,
                            onExpandNeighbors = { centerId ->
                                expandedNeighborCenterNodeIds = expandedNeighborCenterNodeIds + centerId
                            },
                            onExpandSwitchTargets = { centerId ->
                                expandedSwitchableCenterNodeIds = expandedSwitchableCenterNodeIds + centerId
                            },
                            onSelectNode = { nodeId ->
                                if (nodeId != centerNodeId) {
                                    previousCenterNodeId = centerNodeId.takeIf { it.isNotBlank() }
                                    centerNodeId = nodeId
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConceptGraphPanel() {
    PanelCard {
        SectionHeader(
            title = "知识图谱",
            headline = "还没有结构",
        )
        Text(
            text = "还没有可展示的知识点。继续积累记录后，这里会从一个中心知识点开始展开关系。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSoft,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConceptGraphPanel(
    viewport: ConceptGraphViewport,
    relationFromPreviousCenter: ConceptGraphCenterRelation?,
    onExpandNeighbors: (String) -> Unit,
    onExpandSwitchTargets: (String) -> Unit,
    onSelectNode: (String) -> Unit,
) {
    val centerNode = viewport.centerNode ?: return

    PanelCard {
        SectionHeader(
            title = centerNode.label,
            headline = when {
                viewport.neighbors.isEmpty() -> "还没有直接关系"
                viewport.hiddenNeighborCount > 0 -> "先看 ${viewport.neighbors.size} 个一跳关联"
                else -> "${viewport.neighbors.size} 个一跳关联"
            },
        )
        ConceptGraphInfoCard(
            centerNode = centerNode,
            relationFromPreviousCenter = relationFromPreviousCenter,
            visibleNeighborCount = viewport.neighbors.size,
            hiddenNeighborCount = viewport.hiddenNeighborCount,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(KnowledgeGraphCanvasTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CenterConceptNodeCard(node = centerNode)
            if (viewport.neighbors.isEmpty()) {
                Text(
                    text = "这个知识点还没有连接起来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                Text(
                    text = "一跳关联",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    viewport.neighbors.forEach { neighbor ->
                        NeighborConceptNodeCard(
                            neighbor = neighbor,
                            onClick = { onSelectNode(neighbor.node.conceptId) },
                        )
                    }
                }
                if (viewport.hiddenNeighborCount > 0) {
                    TextButton(onClick = { onExpandNeighbors(centerNode.conceptId) }) {
                        Text("展开其余 ${viewport.hiddenNeighborCount} 个关联知识点")
                    }
                }
            }
            if (viewport.switchableNodes.isNotEmpty()) {
                Text(
                    text = if (viewport.neighbors.isEmpty()) {
                        "切换到其他知识点"
                    } else {
                        "切换到其他知识簇"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSoft,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    viewport.switchableNodes.forEach { node ->
                        SwitchableConceptNodeCard(
                            node = node,
                            onClick = { onSelectNode(node.conceptId) },
                        )
                    }
                }
                if (viewport.hiddenSwitchableNodeCount > 0) {
                    TextButton(onClick = { onExpandSwitchTargets(centerNode.conceptId) }) {
                        Text(
                            if (viewport.neighbors.isEmpty()) {
                                "展开其余 ${viewport.hiddenSwitchableNodeCount} 个可切换知识点"
                            } else {
                                "展开其余 ${viewport.hiddenSwitchableNodeCount} 个知识簇入口"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterConceptNodeCard(
    node: ConceptGraphNode,
) {
    val accent = conceptNodeAccent(node.conceptId)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(graphNodeTestTag(node.conceptId))
            .semantics(mergeDescendants = true) {
                contentDescription = "知识点 ${node.label}"
                stateDescription = "中心节点"
                selected = true
            },
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "当前中心",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NeighborConceptNodeCard(
    neighbor: ConceptGraphViewportNeighbor,
    onClick: () -> Unit,
) {
    val accent = conceptNodeAccent(neighbor.node.conceptId)
    Surface(
        modifier = Modifier
            .widthIn(min = 148.dp, max = 196.dp)
            .testTag(graphNodeTestTag(neighbor.node.conceptId))
            .semantics(mergeDescendants = true) {
                contentDescription = "知识点 ${neighbor.node.label}"
                stateDescription = "一跳邻居"
                selected = false
                role = Role.Button
            }
            .clickable(onClick = onClick),
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = CardShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    text = neighbor.relationWord,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            }
            Text(
                text = neighbor.node.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (neighbor.node.summary.isNotBlank()) {
                Text(
                    text = neighbor.node.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SwitchableConceptNodeCard(
    node: ConceptGraphNode,
    onClick: () -> Unit,
) {
    val accent = conceptNodeAccent(node.conceptId)
    Surface(
        modifier = Modifier
            .widthIn(min = 148.dp, max = 196.dp)
            .testTag(graphNodeTestTag(node.conceptId))
            .semantics(mergeDescendants = true) {
                contentDescription = "知识点 ${node.label}"
                stateDescription = "可切换知识点"
                selected = false
                role = Role.Button
            }
            .clickable(onClick = onClick),
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = CardShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    text = "切换查看",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            }
            Text(
                text = node.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.summary.isNotBlank()) {
                Text(
                    text = node.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConceptGraphInfoCard(
    centerNode: ConceptGraphNode,
    relationFromPreviousCenter: ConceptGraphCenterRelation?,
    visibleNeighborCount: Int,
    hiddenNeighborCount: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(KnowledgeGraphInfoPanelTag)
            .semantics(mergeDescendants = true) {
                contentDescription = "知识点详情 ${centerNode.label}"
            },
        color = WhiteGlass.copy(alpha = 0.76f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = centerNode.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            Text(
                text = centerNode.summary.ifBlank { "这个知识点已经成为当前观察中心。" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMain,
            )
            relationFromPreviousCenter?.let { relation ->
                Surface(
                    color = Accent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Accent.copy(alpha = 0.18f)),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        text = "${relation.relationWord} · ${relation.reasonLine}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMain,
                    )
                }
            }
            Text(
                text = when {
                    visibleNeighborCount == 0 -> "这个知识点还没有连接起来。"
                    hiddenNeighborCount > 0 -> "当前先展示 $visibleNeighborCount 个直接关联知识点，还有 $hiddenNeighborCount 个等待展开。"
                    else -> "当前展示 $visibleNeighborCount 个直接关联知识点。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
            )
        }
    }
}

internal fun buildConceptGraphViewport(
    snapshot: DirectionWikiSnapshot,
    currentCenterNodeId: String? = null,
    expandedCenterNodeIds: Collection<String> = emptyList(),
    expandedSwitchableCenterNodeIds: Collection<String> = expandedCenterNodeIds,
    batchSize: Int = ConceptGraphBatchSize,
): ConceptGraphViewport {
    val graph = snapshot.conceptGraph
    val nodeById = graph.nodes.associateBy { it.conceptId }
    val centerNodeId = resolveConceptGraphCenterNodeId(snapshot, currentCenterNodeId) ?: return ConceptGraphViewport()
    val centerNode = nodeById[centerNodeId] ?: return ConceptGraphViewport()

    val rankedNeighbors = buildBestRankedConceptNeighbors(
        graph = graph,
        centerNodeId = centerNodeId,
        nodeById = nodeById,
    ).values.sortedWith(rankedConceptNeighborComparator)
    val normalizedBatchSize = batchSize.coerceAtLeast(0)
    val visibleNeighborCount =
        normalizedBatchSize * (expandedCenterNodeIds.count { it == centerNodeId } + 1)
    val visibleSwitchableNodeCount =
        normalizedBatchSize * (expandedSwitchableCenterNodeIds.count { it == centerNodeId } + 1)
    val allSwitchableNodes = if (rankedNeighbors.isEmpty()) {
        buildSwitchableConceptNodes(
            graph = graph,
            centerNodeId = centerNodeId,
        )
    } else {
        buildDisconnectedComponentSwitchableNodes(
            graph = graph,
            centerNodeId = centerNodeId,
        )
    }
    val visibleSwitchableNodes = allSwitchableNodes.take(visibleSwitchableNodeCount)
    val visibleNeighbors = rankedNeighbors.take(visibleNeighborCount)

    return ConceptGraphViewport(
        centerNode = centerNode,
        neighbors = visibleNeighbors.map { neighbor ->
            ConceptGraphViewportNeighbor(
                node = neighbor.node,
                relation = neighbor.relation,
                relationWord = conceptRelationWord(
                    relationType = neighbor.relation.relationType,
                    fromCenter = neighbor.fromCenter,
                ),
            )
        },
        hiddenNeighborCount = (rankedNeighbors.size - visibleNeighbors.size).coerceAtLeast(0),
        switchableNodes = visibleSwitchableNodes,
        hiddenSwitchableNodeCount = (allSwitchableNodes.size - visibleSwitchableNodes.size).coerceAtLeast(0),
    )
}

internal fun resolveConceptGraphCenterNodeId(
    snapshot: DirectionWikiSnapshot,
    requestedCenterNodeId: String? = null,
): String? {
    val graph = snapshot.conceptGraph
    if (graph.nodes.isEmpty()) return null
    val validNodeIds = graph.nodes.map { it.conceptId }.toSet()
    val requested = requestedCenterNodeId
        ?.takeIf { it.isNotBlank() && it in validNodeIds }
    val defaultCenter = graph.defaultCenterNodeId
        .takeIf { it.isNotBlank() && it in validNodeIds }
    return requested
        ?: defaultCenter
        ?: graph.nodes
            .sortedWith(conceptNodeComparator)
            .firstOrNull()
            ?.conceptId
}

internal fun buildConceptGraphCenterRelation(
    graph: ConceptGraphSnapshot,
    previousCenterNodeId: String?,
    currentCenterNodeId: String?,
): ConceptGraphCenterRelation? {
    val previousId = previousCenterNodeId?.takeIf { it.isNotBlank() } ?: return null
    val currentId = currentCenterNodeId?.takeIf { it.isNotBlank() } ?: return null
    if (previousId == currentId) return null

    val nodeById = graph.nodes.associateBy { it.conceptId }
    val edge = buildBestRankedConceptNeighbors(
        graph = graph,
        centerNodeId = previousId,
        nodeById = nodeById,
    )[currentId]?.relation ?: return null
    val previousLabel = nodeById[previousId]?.label.orEmpty()
    val currentLabel = nodeById[currentId]?.label.orEmpty()
    return ConceptGraphCenterRelation(
        relationWord = conceptRelationWord(
            relationType = edge.relationType,
            fromCenter = edge.fromConceptId == previousId,
        ),
        reasonLine = edge.reasonLine.ifBlank {
            if (previousLabel.isNotBlank() && currentLabel.isNotBlank()) {
                "$previousLabel 和 $currentLabel 之间已经出现了直接关系。"
            } else {
                "这两个知识点之间已经出现了直接关系。"
            }
        },
    )
}

internal fun conceptRelationWord(
    relationType: ConceptGraphRelationType,
    fromCenter: Boolean = true,
): String =
    when (relationType) {
        ConceptGraphRelationType.SUPPORTS -> if (fromCenter) "支持" else "被支持"
        ConceptGraphRelationType.ADVANCES -> if (fromCenter) "推进" else "被推进"
        ConceptGraphRelationType.PARALLEL -> "并行"
        ConceptGraphRelationType.REFERENCES -> if (fromCenter) "参考" else "被参考"
        ConceptGraphRelationType.CONTRASTS -> "对比"
    }

private fun buildBestRankedConceptNeighbors(
    graph: ConceptGraphSnapshot,
    centerNodeId: String,
    nodeById: Map<String, ConceptGraphNode>,
): Map<String, RankedConceptNeighbor> {
    val bestNeighborById = linkedMapOf<String, RankedConceptNeighbor>()
    graph.edges.forEach { edge ->
        val candidate = edge.toRankedConceptNeighbor(
            centerNodeId = centerNodeId,
            nodeById = nodeById,
        ) ?: return@forEach
        val existing = bestNeighborById[candidate.node.conceptId]
        if (existing == null || rankedConceptNeighborComparator.compare(candidate, existing) < 0) {
            bestNeighborById[candidate.node.conceptId] = candidate
        }
    }
    return bestNeighborById
}

private fun ConceptGraphEdge.toRankedConceptNeighbor(
    centerNodeId: String,
    nodeById: Map<String, ConceptGraphNode>,
): RankedConceptNeighbor? {
    val neighborId = when (centerNodeId) {
        fromConceptId -> toConceptId
        toConceptId -> fromConceptId
        else -> null
    } ?: return null
    if (neighborId == centerNodeId) return null
    val neighborNode = nodeById[neighborId] ?: return null
    return RankedConceptNeighbor(
        node = neighborNode,
        relation = this,
        fromCenter = fromConceptId == centerNodeId,
    )
}

private fun buildSwitchableConceptNodes(
    graph: ConceptGraphSnapshot,
    centerNodeId: String,
): List<ConceptGraphNode> {
    val connectedNodeIdsByNodeId = buildConnectedNodeIdsByNodeId(graph)
    val comparator = buildSwitchableConceptNodeComparator(connectedNodeIdsByNodeId)
    return graph.nodes
        .asSequence()
        .filter { it.conceptId != centerNodeId }
        .sortedWith(comparator)
        .toList()
}

private fun buildDisconnectedComponentSwitchableNodes(
    graph: ConceptGraphSnapshot,
    centerNodeId: String,
): List<ConceptGraphNode> {
    val connectedNodeIdsByNodeId = buildConnectedNodeIdsByNodeId(graph)
    val nodeById = graph.nodes.associateBy { it.conceptId }
    val comparator = buildSwitchableConceptNodeComparator(connectedNodeIdsByNodeId)
    return buildConceptComponents(
        nodeIds = graph.nodes.map { it.conceptId },
        connectedNodeIdsByNodeId = connectedNodeIdsByNodeId,
    )
        .asSequence()
        .filterNot { centerNodeId in it }
        .mapNotNull { componentNodeIds ->
            componentNodeIds
                .asSequence()
                .mapNotNull(nodeById::get)
                .minWithOrNull(comparator)
                ?.let { representative ->
                    ConceptComponentSwitchTarget(
                        node = representative,
                        componentSize = componentNodeIds.size,
                        connectedConceptCount = connectedNodeIdsByNodeId[representative.conceptId]?.size ?: 0,
                    )
                }
        }
        .sortedWith(conceptComponentSwitchTargetComparator)
        .map { it.node }
        .toList()
}

private fun buildConnectedNodeIdsByNodeId(
    graph: ConceptGraphSnapshot,
): Map<String, Set<String>> {
    val connectedNodeIdsByNodeId = mutableMapOf<String, MutableSet<String>>()
    graph.edges.forEach { edge ->
        if (edge.fromConceptId == edge.toConceptId) return@forEach
        connectedNodeIdsByNodeId
            .getOrPut(edge.fromConceptId) { linkedSetOf() }
            .add(edge.toConceptId)
        connectedNodeIdsByNodeId
            .getOrPut(edge.toConceptId) { linkedSetOf() }
            .add(edge.fromConceptId)
    }
    return connectedNodeIdsByNodeId
}

private fun buildConceptComponents(
    nodeIds: List<String>,
    connectedNodeIdsByNodeId: Map<String, Set<String>>,
): List<Set<String>> {
    val visitedNodeIds = mutableSetOf<String>()
    val components = mutableListOf<Set<String>>()
    nodeIds.forEach { nodeId ->
        if (!visitedNodeIds.add(nodeId)) return@forEach
        val componentNodeIds = linkedSetOf<String>()
        val pendingNodeIds = ArrayDeque<String>()
        pendingNodeIds.addLast(nodeId)
        while (pendingNodeIds.isNotEmpty()) {
            val currentNodeId = pendingNodeIds.removeFirst()
            if (!componentNodeIds.add(currentNodeId)) continue
            connectedNodeIdsByNodeId[currentNodeId].orEmpty().forEach { neighborNodeId ->
                if (visitedNodeIds.add(neighborNodeId)) {
                    pendingNodeIds.addLast(neighborNodeId)
                }
            }
        }
        components += componentNodeIds
    }
    return components
}

private fun buildSwitchableConceptNodeComparator(
    connectedNodeIdsByNodeId: Map<String, Set<String>>,
): Comparator<ConceptGraphNode> =
    compareByDescending<ConceptGraphNode> {
        connectedNodeIdsByNodeId[it.conceptId]?.size ?: 0
    }
        .thenByDescending { it.hotnessScore }
        .thenByDescending { it.updatedAt }
        .thenBy { it.label }

private fun conceptNodeAccent(
    conceptId: String,
): Color {
    val palette = listOf(
        Accent,
        Color(0xFF0F766E),
        Color(0xFF2563EB),
        Color(0xFFB45309),
        Color(0xFFDB2777),
    )
    return palette[conceptId.hashCode().absoluteValue % palette.size]
}
