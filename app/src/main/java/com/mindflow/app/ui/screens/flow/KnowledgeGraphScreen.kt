package com.mindflow.app.ui.screens.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.mindflow.app.util.TimeFormatter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val KnowledgeGraphCanvasTag = "knowledge-graph-canvas"
internal const val KnowledgeGraphInfoPanelTag = "knowledge-graph-info-panel"

internal fun graphNodeTestTag(nodeId: String): String =
    "graph-node-" + nodeId.replace(Regex("[^A-Za-z0-9_-]"), "_")

internal fun heatmapDayTestTag(date: LocalDate): String = "heatmap-day-$date"

private const val ConceptGraphBatchSize = 6

internal data class GraphNodeUi(
    val id: String,
    val label: String,
    val summaryLine: String,
    val structureStatus: GraphStructureStatus,
    val accent: Color = Accent,
    val densityScore: Double = 0.0,
    val noteCount: Int = 0,
    val relationCount: Int = 0,
    val priority: Int = 3,
    val aliases: List<String> = emptyList(),
    val sourceIds: List<String> = emptyList(),
)

internal data class GraphEdgeUi(
    val fromId: String,
    val toId: String,
    val weight: Int,
    val relationType: ConceptGraphRelationType,
    val reasonLine: String = "",
)

internal enum class GraphEdgeEmphasis {
    BACKBONE,
    FOCUS,
}

internal data class GraphDisplayEdgeUi(
    val fromId: String,
    val toId: String,
    val weight: Int,
    val relationType: ConceptGraphRelationType,
    val reasonLine: String = "",
    val emphasis: GraphEdgeEmphasis,
)

internal data class GraphOverviewUi(
    val headline: String,
    val verdictLine: String,
    val hubNodeIds: Set<String>,
    val isolatedNodeIds: Set<String>,
    val nodes: List<GraphNodeUi>,
    val edges: List<GraphEdgeUi>,
    val defaultSelectedNodeId: String? = null,
)

private data class GraphNodeLayout(
    val node: GraphNodeUi,
    val position: Pair<Float, Float>,
)

private data class GraphEdgeLabelLayout(
    val edge: GraphDisplayEdgeUi,
    val label: String,
    val position: Pair<Float, Float>,
)

private data class GraphRelationUi(
    val relatedNode: GraphNodeUi,
    val label: String,
    val reasonLine: String,
    val weight: Int,
)

internal enum class GraphStructureStatus(
    val label: String,
) {
    HUB("中心"),
    LINKED("成形中"),
    ISOLATED("独立"),
    EMERGING("持续出现"),
}

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

private val graphNodeComparator =
    compareByDescending<GraphNodeUi> { it.priority }
        .thenByDescending { it.relationCount }
        .thenByDescending { it.densityScore }
        .thenBy { it.label.length }

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
    val notes = noteRepository.observeAllNotes().collectAsStateWithLifecycle(initialValue = emptyList()).value
    KnowledgeGraphScreen(
        snapshot = snapshot,
        notes = notes,
        onOpenNote = onOpenNote,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun KnowledgeGraphScreen(
    snapshot: DirectionWikiSnapshot,
    notes: List<NoteEntity>,
    onOpenNote: (Long) -> Unit,
) {
    val graphNodes = remember(snapshot) {
        buildConceptGraphVisualState(snapshot).nodes
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
                            text = "图谱",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "先看记录热度，再看信息图谱。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    }
                }

                item {
                    RecordsHeatmapPanel(
                        notes = notes,
                        graphNodes = graphNodes,
                        onOpenNote = onOpenNote,
                    )
                }

                item {
                    KnowledgeGraphPanel(snapshot = snapshot)
                }
            }
        }
    }
}

@Composable
private fun RecordsHeatmapPanel(
    notes: List<NoteEntity>,
    graphNodes: List<GraphNodeUi>,
    onOpenNote: (Long) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember(zoneId) { LocalDate.now(zoneId) }
    val activeNotes = remember(notes) { notes.filterNot { it.isArchived } }
    val activityByDate = remember(activeNotes, zoneId) {
        activeNotes
            .groupingBy { note ->
                Instant.ofEpochMilli(note.updatedAt.coerceAtLeast(note.createdAt))
                    .atZone(zoneId)
                    .toLocalDate()
            }
            .eachCount()
            .toSortedMap()
    }
    val availableYears = remember(activityByDate, today) {
        (activityByDate.keys.map { it.year } + today.year).distinct().sortedDescending()
    }
    val defaultSelectedYear = remember(availableYears, today) {
        if (today.year in availableYears) today.year else availableYears.firstOrNull() ?: today.year
    }
    var selectedYear by rememberSaveable { mutableIntStateOf(defaultSelectedYear) }
    var selectedDateKey by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    val selectedDate = selectedDateKey?.let(LocalDate::parse)
    val selectedDateNotes = remember(activeNotes, selectedDate, zoneId) {
        if (selectedDate == null) {
            emptyList()
        } else {
            activeNotes
                .filter { note ->
                    Instant.ofEpochMilli(note.updatedAt.coerceAtLeast(note.createdAt))
                        .atZone(zoneId)
                        .toLocalDate() == selectedDate
                }
                .sortedByDescending { it.updatedAt }
        }
    }
    val activatedNodes = remember(selectedDateNotes, graphNodes) {
        buildActivatedGraphNodes(
            notes = selectedDateNotes,
            graphNodes = graphNodes,
        )
    }

    LaunchedEffect(availableYears, defaultSelectedYear) {
        if (selectedYear !in availableYears) {
            selectedYear = defaultSelectedYear
        }
    }
    LaunchedEffect(selectedYear, selectedDateKey, today) {
        val date = selectedDateKey?.let(LocalDate::parse)
        if (date != null && date.year != selectedYear) {
            selectedDateKey = null
        } else if (selectedYear == today.year && date == null) {
            selectedDateKey = today.toString()
        }
    }
    val selectedYearActivity = remember(activityByDate, selectedYear) {
        activityByDate.filterKeys { it.year == selectedYear }
    }

    PanelCard {
        SectionHeader(
            title = "记录热度",
            headline = "${selectedYear} · ${selectedYearActivity.values.sum()} 次变动",
        )
        if (availableYears.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableYears.forEach { year ->
                    FilterChip(
                        selected = selectedYear == year,
                        onClick = {
                            selectedYear = year
                            if (selectedDate?.year != year) {
                                selectedDateKey = null
                            }
                        },
                        label = { Text(year.toString(), maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.16f),
                            selectedLabelColor = Accent,
                        ),
                    )
                }
            }
        }
        Surface(
            color = WhiteGlass.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderSoft),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ContributionHeatmap(
                    year = selectedYear,
                    activityByDate = activityByDate,
                    peakCount = selectedYearActivity.values.maxOrNull() ?: 0,
                    selectedDate = selectedDate,
                    onSelectDate = { date -> selectedDateKey = date.toString() },
                )
                Text(
                    text = if (selectedDate != null) {
                        "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · ${selectedDateNotes.size} 条记录有变动"
                    } else {
                        "点某一天，看看它把哪些知识点点亮了。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
                if (selectedDate != null) {
                    if (activatedNodes.isEmpty()) {
                        Text(
                            text = "这一天有记录变化，但还没有点亮稳定知识点。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    } else {
                        ActivatedThreadStrip(nodes = activatedNodes)
                    }
                }
                if (selectedDate != null) {
                    if (selectedDateNotes.isEmpty()) {
                        Text(
                            text = "这一天没有记录变动。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    } else {
                        selectedDateNotes.take(4).forEach { note ->
                            GraphActivityNoteCard(
                                note = note,
                                onOpenNote = onOpenNote,
                            )
                        }
                        if (selectedDateNotes.size > 4) {
                            Text(
                                text = "还有 ${selectedDateNotes.size - 4} 条，先打开其中一条继续看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSoft,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KnowledgeGraphPanel(
    snapshot: DirectionWikiSnapshot,
) {
    var requestedCenterNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var previousCenterNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var neighborExpansionCount by rememberSaveable(requestedCenterNodeId) { mutableIntStateOf(0) }
    var switchableExpansionCount by rememberSaveable(requestedCenterNodeId) { mutableIntStateOf(0) }

    val viewport = remember(snapshot, requestedCenterNodeId, neighborExpansionCount, switchableExpansionCount) {
        buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = requestedCenterNodeId,
            expandedCenterNodeIds = requestedCenterNodeId?.let { centerId ->
                List(neighborExpansionCount) { centerId }
            }.orEmpty(),
            expandedSwitchableCenterNodeIds = requestedCenterNodeId?.let { centerId ->
                List(switchableExpansionCount) { centerId }
            }.orEmpty(),
        )
    }
    val centerNode = viewport.centerNode
    val graph = snapshot.conceptGraph
    val headline = buildString {
        append("${graph.nodes.size} 个知识点")
        if (graph.edges.isNotEmpty()) {
            append(" · ${graph.edges.size} 条连接")
        }
    }
    val relationFromPreviousCenter = remember(graph, previousCenterNodeId, centerNode?.conceptId) {
        buildConceptGraphCenterRelation(
            graph = graph,
            previousCenterNodeId = previousCenterNodeId,
            currentCenterNodeId = centerNode?.conceptId,
        )
    }

    LaunchedEffect(centerNode?.conceptId) {
        val resolvedCenterNodeId = centerNode?.conceptId ?: return@LaunchedEffect
        if (requestedCenterNodeId == null) {
            requestedCenterNodeId = resolvedCenterNodeId
        }
    }

    fun selectCenter(nodeId: String) {
        val currentCenter = centerNode?.conceptId
        if (nodeId == currentCenter) return
        previousCenterNodeId = currentCenter
        requestedCenterNodeId = nodeId
        neighborExpansionCount = 0
        switchableExpansionCount = 0
    }

    PanelCard {
        SectionHeader(
            title = "信息图谱",
            headline = headline,
        )
        if (centerNode == null) {
            Text(
                text = "先让记录继续积累，这里会慢慢长出你的信息图谱。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
            )
            return@PanelCard
        }

        Text(
            text = when {
                viewport.neighbors.isEmpty() && graph.edges.isEmpty() -> "这些知识点已经出现，但关系还没长出来。"
                viewport.neighbors.isEmpty() -> "先换到一个有连接的中心点，再看它周围的一跳关系。"
                viewport.hiddenNeighborCount > 0 -> "当前只展开 ${viewport.neighbors.size} 个一跳关联，先把结构看清楚。"
                else -> "先看 ${centerNode.label} 周围的一跳关联。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextMain,
        )
        ConceptGraphViewportCanvas(
            viewport = viewport,
            onSelectNode = ::selectCenter,
        )
        ConceptGraphInfoCard(
            centerNode = centerNode,
            relationFromPreviousCenter = relationFromPreviousCenter,
            visibleNeighborCount = viewport.neighbors.size,
            hiddenNeighborCount = viewport.hiddenNeighborCount,
        )
        if (viewport.hiddenNeighborCount > 0) {
            TextButton(onClick = { neighborExpansionCount += 1 }) {
                Text("展开其余 ${viewport.hiddenNeighborCount} 个关联知识点")
            }
        }
        if (viewport.switchableNodes.isNotEmpty()) {
            Text(
                text = if (viewport.neighbors.isEmpty()) {
                    "切换到其他知识点"
                } else {
                    "切换中心点"
                },
                style = MaterialTheme.typography.labelMedium,
                color = TextSoft,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                viewport.switchableNodes.forEach { node ->
                    SwitchableConceptChip(
                        node = node,
                        onClick = { selectCenter(node.conceptId) },
                    )
                }
            }
            if (viewport.hiddenSwitchableNodeCount > 0) {
                TextButton(onClick = { switchableExpansionCount += 1 }) {
                    Text("展开其余 ${viewport.hiddenSwitchableNodeCount} 个可切换知识点")
                }
            }
        }
    }
}

@Composable
private fun ConceptGraphViewportCanvas(
    viewport: ConceptGraphViewport,
    onSelectNode: (String) -> Unit,
) {
    val centerNode = viewport.centerNode ?: return
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .testTag(KnowledgeGraphCanvasTag),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val center = remember(viewport.neighbors.size, widthPx, heightPx) {
            buildConceptGraphCenterPosition(
                neighborCount = viewport.neighbors.size,
                widthPx = widthPx,
                heightPx = heightPx,
            )
        }
        val neighborPositions = remember(viewport.neighbors, center, widthPx, heightPx) {
            buildConceptGraphNeighborPositions(
                neighborCount = viewport.neighbors.size,
                center = center,
                widthPx = widthPx,
                heightPx = heightPx,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            viewport.neighbors.forEachIndexed { index, neighbor ->
                val target = neighborPositions.getOrElse(index) { center }
                drawLine(
                    color = BorderSoft.copy(alpha = 0.72f),
                    start = androidx.compose.ui.geometry.Offset(center.first, center.second),
                    end = androidx.compose.ui.geometry.Offset(target.first, target.second),
                    strokeWidth = 1.6f + (neighbor.relation.confidence * 1.2f).toFloat(),
                )
            }
        }

        ConceptGraphDotNode(
            node = centerNode,
            accent = conceptNodeAccent(centerNode.conceptId),
            modifier = Modifier.offset {
                IntOffset(
                    x = (center.first - 40.dp.toPx(density)).roundToInt(),
                    y = (center.second - 18.dp.toPx(density)).roundToInt(),
                )
            },
            selected = true,
            onClick = {},
        )

        viewport.neighbors.forEachIndexed { index, neighbor ->
            val position = neighborPositions.getOrElse(index) { center }
            ConceptGraphDotNode(
                node = neighbor.node,
                accent = conceptNodeAccent(neighbor.node.conceptId),
                modifier = Modifier.offset {
                    IntOffset(
                        x = (position.first - 34.dp.toPx(density)).roundToInt(),
                        y = (position.second - 14.dp.toPx(density)).roundToInt(),
                    )
                },
                selected = false,
                onClick = { onSelectNode(neighbor.node.conceptId) },
            )
        }
    }
}

@Composable
private fun ConceptGraphDotNode(
    node: ConceptGraphNode,
    accent: Color,
    modifier: Modifier,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .width(if (selected) 84.dp else 68.dp)
            .testTag(graphNodeTestTag(node.conceptId))
            .semantics(mergeDescendants = true) {
                contentDescription = "知识点 ${node.label}"
                stateDescription = if (selected) "中心节点" else "直接关联知识点"
                this.selected = selected
                role = Role.Button
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 24.dp else 16.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (selected) 0.94f else 0.86f)),
                contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.96f)),
                )
            }
        }
        Text(
            text = node.label,
            style = if (selected) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = if (selected) TextMain else TextSoft,
            maxLines = if (selected) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildConceptGraphCenterPosition(
    neighborCount: Int,
    widthPx: Float,
    heightPx: Float,
): Pair<Float, Float> {
    if (neighborCount <= 0) return widthPx / 2f to heightPx / 2f
    return clamp(widthPx * 0.24f, 56f, widthPx - 56f) to
        clamp(heightPx * 0.5f, 60f, heightPx - 60f)
}

private fun buildConceptGraphNeighborPositions(
    neighborCount: Int,
    center: Pair<Float, Float>,
    widthPx: Float,
    heightPx: Float,
): List<Pair<Float, Float>> {
    if (neighborCount <= 0) return emptyList()
    val centerX = center.first
    val centerY = center.second
    if (neighborCount == 1) {
        return listOf(
            clamp(centerX + min(widthPx * 0.46f, 176f), centerX + 84f, widthPx - 48f) to centerY,
        )
    }
    val verticalSpan = min(heightPx * 0.62f, 208f)
    val startY = centerY - (verticalSpan / 2f)
    val baseOffsetX = min(widthPx * 0.42f, 148f)
    val edgeOffsetX = min(widthPx * 0.1f, 34f)
    return List(neighborCount) { index ->
        val normalized = index.toFloat() / (neighborCount - 1).toFloat()
        val distanceFromMiddle = (normalized - 0.5f).absoluteValue * 2f
        val x = centerX + baseOffsetX + (distanceFromMiddle * edgeOffsetX)
        val y = startY + (normalized * verticalSpan)
        clamp(x, centerX + 80f, widthPx - 44f) to
            clamp(y, 42f, heightPx - 42f)
    }
}

@Composable
private fun SwitchableConceptChip(
    node: ConceptGraphNode,
    onClick: () -> Unit,
) {
    val accent = conceptNodeAccent(node.conceptId)
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.9f)),
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelMedium,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GraphActivityNoteCard(
    note: NoteEntity,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenNote(note.id) },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = note.topic.ifBlank { "未命名记录" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeFormatter.compact(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSoft,
                )
            }
            Text(
                text = note.content.lineSequence().firstOrNull { it.isNotBlank() }
                    ?: "打开这条记录继续看内容。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${note.status.label} · ${note.horizon.label}",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
        }
    }
}

@Composable
private fun ActivatedThreadStrip(
    nodes: List<GraphNodeUi>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        nodes.forEach { node ->
            Surface(
                color = node.accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, node.accent.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(node.accent),
                    )
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMain,
                        maxLines = 1,
                    )
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
private fun KnowledgeGraphCanvas(
    overview: GraphOverviewUi,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
) {
    val nodes = overview.nodes
    val density = LocalDensity.current
    val backboneEdges = remember(nodes, overview.edges) {
        buildBackboneGraphEdges(
            nodes = nodes,
            edges = overview.edges,
        )
    }
    val displayEdges = remember(nodes, overview.edges, selectedNodeId) {
        buildDisplayedGraphEdges(
            nodes = nodes,
            edges = overview.edges,
            selectedNodeId = selectedNodeId,
        )
    }
    val focusedNodeIds = remember(overview.edges, selectedNodeId) {
        buildFocusedGraphNodeIds(
            edges = overview.edges,
            selectedNodeId = selectedNodeId,
        )
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .testTag(KnowledgeGraphCanvasTag)
            .background(Color.Transparent),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val layouts = remember(nodes, backboneEdges, overview.hubNodeIds, overview.isolatedNodeIds, widthPx, heightPx) {
            buildGraphNodeLayouts(
                nodes = nodes,
                edges = backboneEdges,
                hubNodeIds = overview.hubNodeIds,
                isolatedNodeIds = overview.isolatedNodeIds,
                widthPx = widthPx,
                heightPx = heightPx,
            )
        }
        val positionById = remember(layouts) { layouts.associate { it.node.id to it.position } }
        val edgeLabels = remember(displayEdges, positionById, selectedNodeId, widthPx, heightPx) {
            buildGraphEdgeLabelLayouts(
                displayEdges = displayEdges,
                positionById = positionById,
                widthPx = widthPx,
                heightPx = heightPx,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            displayEdges.forEach { edge ->
                val from = positionById[edge.fromId] ?: return@forEach
                val to = positionById[edge.toId] ?: return@forEach
                val edgeColor = when (edge.emphasis) {
                    GraphEdgeEmphasis.FOCUS -> Accent.copy(alpha = 0.38f)
                    GraphEdgeEmphasis.BACKBONE -> if (selectedNodeId == null) {
                        BorderSoft.copy(alpha = 0.72f)
                    } else {
                        BorderSoft.copy(alpha = 0.22f)
                    }
                }
                drawLine(
                    color = edgeColor,
                    start = androidx.compose.ui.geometry.Offset(from.first, from.second),
                    end = androidx.compose.ui.geometry.Offset(to.first, to.second),
                    strokeWidth = when (edge.emphasis) {
                        GraphEdgeEmphasis.FOCUS -> 3.8f
                        GraphEdgeEmphasis.BACKBONE -> if (selectedNodeId == null) {
                            1.8f + edge.weight.coerceAtMost(6) * 0.3f
                        } else {
                            1.2f + edge.weight.coerceAtMost(6) * 0.18f
                        }
                    },
                )
            }
        }

        edgeLabels.forEach { label ->
            GraphEdgeLabelBubble(
                label = label.label,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (label.position.first - 34.dp.toPx(density)).roundToInt(),
                        y = (label.position.second - 13.dp.toPx(density)).roundToInt(),
                    )
                },
            )
        }

        layouts.forEachIndexed { index, layout ->
            GraphNodeBubble(
                node = layout.node,
                modifier = Modifier.offset {
                    val nodeWidth = graphNodeWidth(layout.node, selectedNodeId == layout.node.id)
                    IntOffset(
                        x = (layout.position.first - (nodeWidth.toPx(density) / 2f)).roundToInt(),
                        y = (layout.position.second - 22.dpToPx(density)).roundToInt(),
                    )
                },
                selected = selectedNodeId == layout.node.id,
                emphasized = selectedNodeId == null || layout.node.id in focusedNodeIds,
                onClick = { onSelectNode(layout.node.id) },
                width = graphNodeWidth(layout.node, selectedNodeId == layout.node.id),
                traversalOrder = index.toFloat(),
            )
        }
    }
}

private fun Int.dpToPx(density: Density): Float = with(density) { this@dpToPx.dp.toPx() }

private fun Dp.toPx(density: Density): Float = with(density) { this@toPx.toPx() }

@Composable
private fun GraphNodeBubble(
    node: GraphNodeUi,
    modifier: Modifier,
    selected: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit,
    width: Dp,
    traversalOrder: Float,
) {
    Surface(
        modifier = modifier
            .testTag(graphNodeTestTag(node.id))
            .semantics(mergeDescendants = true) {
                contentDescription = "知识点 ${node.label}"
                stateDescription = buildString {
                    append(node.structureStatus.label)
                    if (selected) {
                        append("，已选中")
                    }
                }
                this.selected = selected
                role = Role.Button
                traversalIndex = traversalOrder
            }
            .clickable(onClick = onClick),
        color = when {
            selected -> node.accent.copy(alpha = 0.16f)
            !emphasized -> WhiteGlass.copy(alpha = 0.48f)
            node.structureStatus == GraphStructureStatus.HUB -> WhiteGlass.copy(alpha = 0.96f)
            node.structureStatus == GraphStructureStatus.ISOLATED -> WhiteGlass.copy(alpha = 0.72f)
            else -> WhiteGlass.copy(alpha = 0.92f)
        },
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            node.accent.copy(
                alpha = when {
                    selected -> 0.72f
                    !emphasized -> 0.16f
                    node.structureStatus == GraphStructureStatus.HUB -> 0.46f
                    node.structureStatus == GraphStructureStatus.ISOLATED -> 0.22f
                    else -> 0.35f
                },
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .width(width)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (selected) 10.dp else 9.dp)
                    .clip(CircleShape)
                    .background(node.accent.copy(alpha = if (emphasized || selected) 0.88f else 0.34f)),
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized || selected) TextMain else TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GraphEdgeLabelBubble(
    label: String,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = WhiteGlass.copy(alpha = 0.94f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.22f)),
        shadowElevation = 2.dp,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GraphInfoCard(
    node: GraphNodeUi,
    relations: List<GraphRelationUi>,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(KnowledgeGraphInfoPanelTag)
            .semantics(mergeDescendants = true) {
                contentDescription = "知识点信息 ${node.label}"
                stateDescription = "结构状态 ${node.structureStatus.label}"
            },
        color = WhiteGlass.copy(alpha = 0.68f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextMain,
                    )
                    Text(
                        text = graphStructureLine(node),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSoft,
                    )
                }
                Surface(
                    color = node.accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = 0.24f)),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        text = node.structureStatus.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = node.accent,
                    )
                }
            }
            Text(
                text = node.summaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (relations.isEmpty()) {
                Text(
                    text = "这条知识点暂时还比较独立。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "直接关系",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSoft,
                    )
                    relations.take(3).forEach { relation ->
                        GraphRelationRow(relation = relation)
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphRelationRow(
    relation: GraphRelationUi,
) {
    Surface(
        color = WhiteGlass.copy(alpha = 0.58f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSoft.copy(alpha = 0.56f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = relation.relatedNode.accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, relation.relatedNode.accent.copy(alpha = 0.24f)),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        text = relation.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMain,
                    )
                }
                Text(
                    text = relation.relatedNode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = relation.reasonLine,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun graphStructureLine(node: GraphNodeUi): String =
    buildString {
        append(node.structureStatus.label)
        append(" · ")
        append(
            when {
                node.relationCount >= 3 -> "连接稳定"
                node.relationCount > 0 -> "关系在变清楚"
                else -> "还在慢慢成形"
            },
        )
    }

internal fun buildConceptGraphVisualState(
    snapshot: DirectionWikiSnapshot,
): GraphOverviewUi {
    val graph = snapshot.conceptGraph
    if (graph.nodes.isEmpty()) {
        return GraphOverviewUi(
            headline = "还没有足够结构",
            verdictLine = "",
            hubNodeIds = emptySet(),
            isolatedNodeIds = emptySet(),
            nodes = emptyList(),
            edges = emptyList(),
            defaultSelectedNodeId = null,
        )
    }

    val preferredEdgesByPairKey = linkedMapOf<String, ConceptGraphEdge>()
    graph.edges.forEach { edge ->
        if (edge.fromConceptId == edge.toConceptId) return@forEach
        val pairKey = canonicalGraphEdgeKey(edge.fromConceptId, edge.toConceptId)
        val existing = preferredEdgesByPairKey[pairKey]
        if (existing == null || edge.confidence > existing.confidence) {
            preferredEdgesByPairKey[pairKey] = edge
        }
    }
    val selectedEdges = preferredEdgesByPairKey.values.toList()
    val relationCountByNodeId = buildMap<String, Int> {
        selectedEdges.forEach { edge ->
            put(edge.fromConceptId, getOrDefault(edge.fromConceptId, 0) + 1)
            put(edge.toConceptId, getOrDefault(edge.toConceptId, 0) + 1)
        }
    }
    val maxRelationCount = relationCountByNodeId.values.maxOrNull() ?: 0
    val hubNodeIds = buildSet {
        graph.defaultCenterNodeId
            .takeIf { it.isNotBlank() && graph.nodes.any { node -> node.conceptId == it } }
            ?.let(::add)
        if (maxRelationCount >= 2) {
            relationCountByNodeId
                .filterValues { it == maxRelationCount }
                .keys
                .forEach(::add)
        }
    }
    val isolatedNodeIds = graph.nodes
        .asSequence()
        .map(ConceptGraphNode::conceptId)
        .filter { relationCountByNodeId.getOrDefault(it, 0) == 0 }
        .toSet()

    val nodes = graph.nodes
        .map { node ->
            val relationCount = relationCountByNodeId.getOrDefault(node.conceptId, 0)
            GraphNodeUi(
                id = node.conceptId,
                label = node.label,
                summaryLine = node.summary.ifBlank { "这个知识点还在继续沉淀。" },
                structureStatus = when {
                    node.conceptId in hubNodeIds -> GraphStructureStatus.HUB
                    relationCount > 0 -> GraphStructureStatus.LINKED
                    relationCount == 0 && node.sourceIds.isNotEmpty() -> GraphStructureStatus.ISOLATED
                    else -> GraphStructureStatus.EMERGING
                },
                accent = conceptNodeAccent(node.conceptId),
                densityScore = node.hotnessScore,
                noteCount = node.sourceIds.count { it.startsWith("note:") },
                relationCount = relationCount,
                priority = graphNodePriority(
                    densityScore = node.hotnessScore,
                    relationCount = relationCount,
                    isHub = node.conceptId in hubNodeIds,
                ),
                aliases = node.aliases,
                sourceIds = node.sourceIds,
            )
        }
        .sortedWith(graphNodeComparator)

    val nodeIds = nodes.map { it.id }.toSet()
    val edges = selectedEdges
        .filter { it.fromConceptId in nodeIds && it.toConceptId in nodeIds }
        .map { edge ->
            GraphEdgeUi(
                fromId = edge.fromConceptId,
                toId = edge.toConceptId,
                weight = graphEdgeWeight(edge),
                relationType = edge.relationType,
                reasonLine = edge.reasonLine,
            )
        }

    return GraphOverviewUi(
        headline = buildString {
            append("${nodes.size} 个知识点")
            if (edges.isNotEmpty()) append(" · ${edges.size} 条连接")
        },
        verdictLine = buildGraphVerdict(
            nodes = nodes,
            edges = edges,
            hubNodeIds = hubNodeIds,
            isolatedNodeIds = isolatedNodeIds,
        ),
        hubNodeIds = hubNodeIds,
        isolatedNodeIds = isolatedNodeIds,
        nodes = nodes,
        edges = edges,
        defaultSelectedNodeId = resolveConceptGraphCenterNodeId(snapshot),
    )
}

private fun graphEdgeWeight(
    edge: ConceptGraphEdge,
): Int = (
    1 +
        (edge.confidence * 3.0).roundToInt() +
        edge.supportIds.size.coerceAtMost(1)
    ).coerceIn(1, 5)

internal fun buildActivatedGraphNodes(
    notes: List<NoteEntity>,
    graphNodes: List<GraphNodeUi>,
): List<GraphNodeUi> {
    if (notes.isEmpty() || graphNodes.isEmpty()) return emptyList()
    val selectedNoteSourceIds = notes.map { note -> "note:${note.id}" }.toSet()
    val normalizedTags = notes
        .flatMap { note -> note.tags + note.topic.takeIf { it.isNotBlank() }.orEmpty() }
        .map(::normalizeActivatedConceptKey)
        .filter { it.isNotBlank() }
        .toSet()

    return graphNodes
        .mapNotNull { node ->
            val sourceMatchCount = node.sourceIds.count { it in selectedNoteSourceIds }
            val tagMatchCount = (listOf(node.label) + node.aliases)
                .map(::normalizeActivatedConceptKey)
                .distinct()
                .count { it in normalizedTags }
            val score = (sourceMatchCount * 10) + (tagMatchCount * 3) + node.relationCount
            if (score <= 0) null else node to score
        }
        .sortedWith(
            compareByDescending<Pair<GraphNodeUi, Int>> { it.second }
                .thenByDescending { it.first.relationCount }
                .thenByDescending { it.first.densityScore },
        )
        .map { it.first }
        .distinctBy { it.id }
        .take(3)
}

private fun normalizeActivatedConceptKey(
    raw: String,
): String = raw.trim().lowercase()

private fun buildGraphVerdict(
    nodes: List<GraphNodeUi>,
    edges: List<GraphEdgeUi>,
    hubNodeIds: Set<String>,
    isolatedNodeIds: Set<String>,
): String {
    if (nodes.isEmpty()) return ""
    val nodeById = nodes.associateBy { it.id }
    val hubLabel = hubNodeIds.firstNotNullOfOrNull { nodeById[it]?.label }
    val strongestEdge = edges.maxByOrNull { it.weight }
    val connectedLine = strongestEdge?.let { edge ->
        val left = nodeById[edge.fromId]?.label.orEmpty()
        val right = nodeById[edge.toId]?.label.orEmpty()
        if (left.isBlank() || right.isBlank()) null else "$left 和 $right 已经连起来了"
    }
    val isolatedLabel = isolatedNodeIds.firstNotNullOfOrNull { nodeById[it]?.label }

    return listOfNotNull(
        hubLabel?.let { "$it 正在成为中心" },
        connectedLine,
        isolatedLabel?.let { "$it 还比较孤" },
    ).joinToString("，").ifBlank {
        edges.firstOrNull()?.let { edge ->
            val left = nodeById[edge.fromId]?.label.orEmpty()
            val right = nodeById[edge.toId]?.label.orEmpty()
            if (left.isNotBlank() && right.isNotBlank()) {
                "$left 和 $right 正在慢慢连起来。"
            } else {
                "几个知识点正在慢慢长出结构。"
            }
        } ?: "几个知识点正在慢慢长出结构。"
    } + "。"
}

private fun graphNodePriority(
    densityScore: Double,
    relationCount: Int,
    isHub: Boolean,
): Int {
    val densityBoost = when {
        densityScore >= 0.8 -> 2
        densityScore >= 0.55 -> 1
        else -> 0
    }
    val relationBoost = relationCount.coerceAtMost(2)
    val hubBoost = if (isHub) 2 else 0
    return (3 + densityBoost + relationBoost + hubBoost).coerceIn(1, 5)
}

private fun graphNodeWidth(
    node: GraphNodeUi,
    selected: Boolean,
): Dp =
    when {
        selected -> 120.dp
        node.structureStatus == GraphStructureStatus.HUB || node.priority >= 5 -> 112.dp
        node.relationCount >= 2 || node.noteCount >= 5 -> 102.dp
        node.structureStatus == GraphStructureStatus.ISOLATED -> 84.dp
        else -> 94.dp
    }

internal fun buildBackboneGraphEdges(
    nodes: List<GraphNodeUi>,
    edges: List<GraphEdgeUi>,
): List<GraphEdgeUi> {
    if (nodes.isEmpty() || edges.isEmpty()) return emptyList()
    val nodeIds = nodes.map { it.id }.toSet()
    val priorityByNode = nodes.associate { it.id to it.priority }
    val parent = nodeIds.associateWith { it }.toMutableMap()

    fun find(id: String): String {
        val currentParent = parent[id] ?: id
        if (currentParent == id) return id
        return find(currentParent).also { root -> parent[id] = root }
    }

    fun union(left: String, right: String) {
        val leftRoot = find(left)
        val rightRoot = find(right)
        if (leftRoot != rightRoot) {
            parent[rightRoot] = leftRoot
        }
    }

    return edges
        .filter { it.fromId in nodeIds && it.toId in nodeIds }
        .sortedWith(
            compareByDescending<GraphEdgeUi> { it.weight }
                .thenByDescending {
                    (priorityByNode[it.fromId] ?: 0) + (priorityByNode[it.toId] ?: 0)
                }
                .thenBy { canonicalGraphEdgeKey(it.fromId, it.toId) },
        )
        .filter { edge ->
            val leftRoot = find(edge.fromId)
            val rightRoot = find(edge.toId)
            if (leftRoot == rightRoot) {
                false
            } else {
                union(edge.fromId, edge.toId)
                true
            }
        }
}

internal fun buildDisplayedGraphEdges(
    nodes: List<GraphNodeUi>,
    edges: List<GraphEdgeUi>,
    selectedNodeId: String?,
): List<GraphDisplayEdgeUi> {
    if (nodes.isEmpty() || edges.isEmpty()) return emptyList()
    val backboneEdges = buildBackboneGraphEdges(nodes, edges)
    val displayEdges = linkedMapOf<String, GraphDisplayEdgeUi>()
    backboneEdges.forEach { edge ->
        displayEdges[canonicalGraphEdgeKey(edge.fromId, edge.toId)] = GraphDisplayEdgeUi(
            fromId = edge.fromId,
            toId = edge.toId,
            weight = edge.weight,
            relationType = edge.relationType,
            reasonLine = edge.reasonLine,
            emphasis = GraphEdgeEmphasis.BACKBONE,
        )
    }
    if (selectedNodeId != null) {
        edges
            .filter { it.fromId == selectedNodeId || it.toId == selectedNodeId }
            .sortedWith(compareByDescending<GraphEdgeUi> { it.weight }.thenBy { canonicalGraphEdgeKey(it.fromId, it.toId) })
            .forEach { edge ->
                displayEdges[canonicalGraphEdgeKey(edge.fromId, edge.toId)] = GraphDisplayEdgeUi(
                    fromId = edge.fromId,
                    toId = edge.toId,
                    weight = edge.weight,
                    relationType = edge.relationType,
                    reasonLine = edge.reasonLine,
                    emphasis = GraphEdgeEmphasis.FOCUS,
                )
            }
    }
    return displayEdges.values.toList()
}

internal fun buildFocusedGraphNodeIds(
    edges: List<GraphEdgeUi>,
    selectedNodeId: String?,
): Set<String> {
    if (selectedNodeId == null) return emptySet()
    return buildSet {
        add(selectedNodeId)
        edges.forEach { edge ->
            when (selectedNodeId) {
                edge.fromId -> add(edge.toId)
                edge.toId -> add(edge.fromId)
            }
        }
    }
}

private fun buildGraphEdgeLabelLayouts(
    displayEdges: List<GraphDisplayEdgeUi>,
    positionById: Map<String, Pair<Float, Float>>,
    widthPx: Float,
    heightPx: Float,
): List<GraphEdgeLabelLayout> =
    displayEdges
        .filter { it.emphasis == GraphEdgeEmphasis.FOCUS }
        .sortedWith(compareByDescending<GraphDisplayEdgeUi> { it.weight }.thenBy { "${it.fromId}|${it.toId}" })
        .mapNotNull { edge ->
            val label = edge.reasonLine.takeIf { it.isNotBlank() }?.let(::compactGraphRelationLabel).orEmpty()
            if (label.isBlank()) return@mapNotNull null
            val from = positionById[edge.fromId] ?: return@mapNotNull null
            val to = positionById[edge.toId] ?: return@mapNotNull null
            Triple(edge, label, from to to)
        }
        .take(3)
        .mapIndexed { index, (edge, label, endpoints) ->
            val from = endpoints.first
            val to = endpoints.second
            val midX = (from.first + to.first) / 2f
            val midY = (from.second + to.second) / 2f
            val deltaX = to.first - from.first
            val deltaY = to.second - from.second
            val length = sqrt(deltaX * deltaX + deltaY * deltaY).coerceAtLeast(1f)
            val perpendicularX = -deltaY / length
            val perpendicularY = deltaX / length
            val offset = when (index) {
                0 -> 0f
                1 -> 18f
                else -> -18f
            }
            GraphEdgeLabelLayout(
                edge = edge,
                label = label,
                position = clamp(midX + perpendicularX * offset, 54f, widthPx - 54f) to
                    clamp(midY + perpendicularY * offset, 28f, heightPx - 28f),
            )
        }

internal fun compactGraphRelationLabel(
    reasonLine: String,
): String {
    val clause = reasonLine
        .replace('：', ' ')
        .replace(':', ' ')
        .split('。', '，', ',', '、', ';', '；', '\n')
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    if (clause.isBlank()) return "相关"
    val normalized = clause
        .replace(Regex("^(因为|说明|表示|意味着|显示|它们|两者|这条关系|关系是|联系在于)"), "")
        .trim()
    if (normalized.isBlank()) return "相关"
    val compact = if (normalized.any { it.isWhitespace() }) {
        normalized.split(Regex("\\s+")).take(2).joinToString(" ")
    } else {
        normalized.take(8)
    }
    return compact.ifBlank { "相关" }
}

private fun canonicalGraphEdgeKey(
    fromId: String,
    toId: String,
): String = listOf(fromId, toId).sorted().joinToString("|")

private fun graphAdjacency(
    edges: List<GraphEdgeUi>,
): Map<String, List<GraphEdgeUi>> =
    buildMap {
        edges.forEach { edge ->
            put(edge.fromId, getOrDefault(edge.fromId, emptyList()) + edge)
            put(edge.toId, getOrDefault(edge.toId, emptyList()) + edge)
        }
    }

private fun connectedGraphComponents(
    nodeIds: Set<String>,
    adjacency: Map<String, List<GraphEdgeUi>>,
): List<Set<String>> {
    if (nodeIds.isEmpty()) return emptyList()
    val visited = mutableSetOf<String>()
    val components = mutableListOf<Set<String>>()
    nodeIds.forEach { startId ->
        if (!visited.add(startId)) return@forEach
        val queue = ArrayDeque<String>()
        val component = linkedSetOf<String>()
        queue.add(startId)
        component.add(startId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            adjacency[current]
                .orEmpty()
                .map { edge -> if (edge.fromId == current) edge.toId else edge.fromId }
                .forEach { neighbor ->
                    if (neighbor in nodeIds && visited.add(neighbor)) {
                        queue.add(neighbor)
                        component.add(neighbor)
                    }
                }
        }
        components += component
    }
    return components
}

private data class GraphBranchPlacement(
    val nodeId: String,
    val anchorId: String,
    val depth: Int,
)

private fun buildGraphBranchPlacements(
    componentIds: Set<String>,
    trunkIds: List<String>,
    adjacency: Map<String, List<GraphEdgeUi>>,
): Map<String, List<GraphBranchPlacement>> {
    val trunkSet = trunkIds.toSet()
    if (componentIds.isEmpty() || trunkIds.isEmpty()) return emptyMap()
    val queue = ArrayDeque<GraphBranchPlacement>()
    val visited = trunkSet.toMutableSet()
    val placements = linkedMapOf<String, MutableList<GraphBranchPlacement>>()

    trunkIds.forEach { trunkId ->
        adjacency[trunkId]
            .orEmpty()
            .map { edge -> if (edge.fromId == trunkId) edge.toId else edge.fromId }
            .filter { it in componentIds && it !in trunkSet }
            .sorted()
            .forEach { branchId ->
                if (visited.add(branchId)) {
                    queue.add(GraphBranchPlacement(branchId, trunkId, 1))
                }
            }
    }

    while (queue.isNotEmpty()) {
        val placement = queue.removeFirst()
        placements.getOrPut(placement.anchorId) { mutableListOf() } += placement
        adjacency[placement.nodeId]
            .orEmpty()
            .map { edge -> if (edge.fromId == placement.nodeId) edge.toId else edge.fromId }
            .filter { it in componentIds && it !in visited }
            .sorted()
            .forEach { childId ->
                if (visited.add(childId)) {
                    queue.add(GraphBranchPlacement(childId, placement.anchorId, placement.depth + 1))
                }
            }
    }

    return placements
}

private fun buildComponentTrunk(
    componentIds: Set<String>,
    adjacency: Map<String, List<GraphEdgeUi>>,
    preferredRootId: String?,
): List<String> {
    if (componentIds.isEmpty()) return emptyList()
    if (componentIds.size == 1) return listOf(componentIds.first())
    val startId = preferredRootId?.takeIf { it in componentIds } ?: componentIds.first()
    val first = farthestGraphNode(startId, componentIds, adjacency)
    val second = farthestGraphNode(first.nodeId, componentIds, adjacency)
    return restoreGraphPath(second.nodeId, first.nodeId, second.parents)
}

private data class FarthestGraphNodeResult(
    val nodeId: String,
    val parents: Map<String, String?>,
)

private fun farthestGraphNode(
    startId: String,
    componentIds: Set<String>,
    adjacency: Map<String, List<GraphEdgeUi>>,
): FarthestGraphNodeResult {
    val queue = ArrayDeque<String>()
    val distances = mutableMapOf(startId to 0)
    val parents = mutableMapOf<String, String?>(startId to null)
    queue.add(startId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        adjacency[current]
            .orEmpty()
            .map { edge -> if (edge.fromId == current) edge.toId else edge.fromId }
            .filter { it in componentIds && it !in distances }
            .forEach { neighbor ->
                distances[neighbor] = (distances[current] ?: 0) + 1
                parents[neighbor] = current
                queue.add(neighbor)
            }
    }
    val farthest = distances.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })!!
    return FarthestGraphNodeResult(
        nodeId = farthest.key,
        parents = parents,
    )
}

private fun restoreGraphPath(
    targetId: String,
    startId: String,
    parents: Map<String, String?>,
): List<String> {
    val path = mutableListOf<String>()
    var current: String? = targetId
    while (current != null) {
        path += current
        if (current == startId) break
        current = parents[current]
    }
    return path.reversed()
}

private fun positionForGraphComponent(
    componentIndex: Int,
    centerX: Float,
    centerY: Float,
    widthPx: Float,
    heightPx: Float,
): Pair<Float, Float> {
    if (componentIndex == 0) return centerX to centerY
    val anchors = listOf(
        clamp(centerX - widthPx * 0.24f, 74f, widthPx - 74f) to clamp(centerY - heightPx * 0.18f, 68f, heightPx - 68f),
        clamp(centerX + widthPx * 0.24f, 74f, widthPx - 74f) to clamp(centerY + heightPx * 0.14f, 68f, heightPx - 68f),
        clamp(centerX - widthPx * 0.2f, 74f, widthPx - 74f) to clamp(centerY + heightPx * 0.2f, 68f, heightPx - 68f),
    )
    return anchors.getOrElse((componentIndex - 1) % anchors.size) { centerX to centerY }
}

private fun buildGraphNodeLayouts(
    nodes: List<GraphNodeUi>,
    edges: List<GraphEdgeUi>,
    hubNodeIds: Set<String>,
    isolatedNodeIds: Set<String>,
    widthPx: Float,
    heightPx: Float,
): List<GraphNodeLayout> {
    if (nodes.isEmpty()) return emptyList()
    val ordered = nodes.sortedWith(graphNodeComparator)
    val centerX = widthPx / 2f
    val centerY = heightPx / 2f
    if (ordered.size == 1) {
        return listOf(GraphNodeLayout(ordered.first(), centerX to centerY))
    }
    val positions = linkedMapOf<String, Pair<Float, Float>>()
    val adjacency = graphAdjacency(edges)
    val connectedNodeIds = ordered
        .map { it.id }
        .filter { adjacency[it].orEmpty().isNotEmpty() && it !in isolatedNodeIds }
        .toSet()
    val preferredRootId = ordered.firstOrNull { it.id in hubNodeIds }?.id
        ?: ordered.firstOrNull { it.id in connectedNodeIds }?.id

    val components = connectedGraphComponents(connectedNodeIds, adjacency)
        .sortedWith(
            compareByDescending<Set<String>> { component -> component.size }
                .thenBy { component -> component.sorted().joinToString("|") },
        )

    components.forEachIndexed { componentIndex, componentIds ->
        val componentCenter = positionForGraphComponent(
            componentIndex = componentIndex,
            centerX = centerX,
            centerY = centerY,
            widthPx = widthPx,
            heightPx = heightPx,
        )
        val trunkIds = buildComponentTrunk(
            componentIds = componentIds,
            adjacency = adjacency,
            preferredRootId = preferredRootId,
        )
        if (trunkIds.isEmpty()) return@forEachIndexed
        val trunkSpacing = min(
            if (componentIndex == 0) 132f else 108f,
            widthPx * if (componentIndex == 0) 0.22f else 0.16f,
        )
        val trunkStartX = componentCenter.first - ((trunkIds.size - 1) * trunkSpacing) / 2f
        trunkIds.forEachIndexed { index, nodeId ->
            positions[nodeId] = clamp(trunkStartX + index * trunkSpacing, 62f, widthPx - 62f) to
                clamp(componentCenter.second, 56f, heightPx - 56f)
        }

        val branchPlacements = buildGraphBranchPlacements(
            componentIds = componentIds,
            trunkIds = trunkIds,
            adjacency = adjacency,
        )
        val branchCountByAnchor = mutableMapOf<String, Int>()
        trunkIds.forEach { anchorId ->
            branchPlacements[anchorId]
                .orEmpty()
                .sortedWith(compareBy<GraphBranchPlacement> { it.depth }.thenBy { it.nodeId })
                .forEach { placement ->
                    val anchorPosition = positions[anchorId] ?: return@forEach
                    val ordinal = branchCountByAnchor.getOrDefault(anchorId, 0)
                    branchCountByAnchor[anchorId] = ordinal + 1
                    val verticalDirection = if (ordinal % 2 == 0) -1 else 1
                    val horizontalOffset = when (placement.depth) {
                        1 -> (ordinal / 2) * 18f
                        else -> placement.depth * 28f
                    }
                    positions[placement.nodeId] = clamp(
                        anchorPosition.first + horizontalOffset,
                        58f,
                        widthPx - 58f,
                    ) to clamp(
                        anchorPosition.second + verticalDirection * (92f + (placement.depth - 1) * 62f),
                        52f,
                        heightPx - 52f,
                    )
                }
        }
    }

    val orbitNodes = ordered.filter { it.id !in positions.keys }
    val orbitRadiusX = widthPx * 0.34f
    val orbitRadiusY = heightPx * 0.32f
    orbitNodes.forEachIndexed { index, node ->
        val angle = when (orbitNodes.size) {
            1 -> -Math.PI / 2
            else -> (-Math.PI / 2) + (index.toDouble() * (2 * Math.PI / orbitNodes.size))
        }
        positions[node.id] = clamp((centerX + kotlin.math.cos(angle).toFloat() * orbitRadiusX).toFloat(), 60f, widthPx - 60f) to
            clamp((centerY + kotlin.math.sin(angle).toFloat() * orbitRadiusY).toFloat(), 52f, heightPx - 52f)
    }

    return ordered.map { node ->
        GraphNodeLayout(node, positions[node.id] ?: (centerX to centerY))
    }
}

@Composable
private fun ContributionHeatmap(
    year: Int,
    activityByDate: Map<LocalDate, Int>,
    peakCount: Int,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val start = remember(year) {
        LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val end = remember(year) {
        LocalDate.of(year, 12, 31).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    }
    val weeks = remember(start, end) {
        buildList<List<LocalDate>> {
            var current = start
            while (!current.isAfter(end)) {
                add(List(7) { index -> current.plusDays(index.toLong()) })
                current = current.plusWeeks(1)
            }
        }
    }
    val scrollState = rememberScrollState()
    val cellGap = 4.dp
    val density = LocalDensity.current
    val targetDate = remember(year, selectedDate) {
        when {
            selectedDate?.year == year -> selectedDate
            year == today.year -> today
            else -> activityByDate.keys.lastOrNull { it.year == year } ?: LocalDate.of(year, 12, 31)
        }
    }
    LaunchedEffect(year, targetDate, density, scrollState.maxValue) {
        val weekIndex = weeks.indexOfFirst { week -> targetDate in week }.coerceAtLeast(0)
        val weekStridePx = with(density) { (16.dp + cellGap).roundToPx() }
        val targetOffset = min(
            scrollState.maxValue,
            max(0, (weekIndex - 2) * weekStridePx),
        )
        scrollState.scrollTo(targetOffset)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(cellGap),
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                week.forEach { date ->
                    val visible = date.year == year
                    ContributionCell(
                        date = date,
                        visible = visible,
                        count = if (visible) activityByDate[date] ?: 0 else 0,
                        peakCount = peakCount,
                        selected = selectedDate == date,
                        onClick = { if (visible) onSelectDate(date) },
                    )
                }
            }
        }
    }
}

private fun clamp(
    value: Float,
    minValue: Float,
    maxValue: Float,
): Float = max(minValue, min(value, maxValue))

@Composable
private fun ContributionCell(
    date: LocalDate,
    visible: Boolean,
    count: Int,
    peakCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tone = if (visible) activityTone(count, peakCount) else null
    val selectedAccent = Color(0xFF3B82F6)
    Box(
        modifier = Modifier
            .size(16.dp)
            .testTag(heatmapDayTestTag(date))
            .semantics(mergeDescendants = true) {
                contentDescription = "${date.monthValue}月${date.dayOfMonth}日"
                stateDescription = when {
                    !visible -> "不可用"
                    selected -> "已选中，$count 条记录"
                    count > 0 -> "$count 条记录"
                    else -> "无记录"
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (tone == null) {
                    Modifier.background(Color.Transparent)
                } else {
                    Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(
                                tone.glowOuter,
                                tone.glowInner,
                                Color.Transparent,
                            ),
                        ),
                    )
                }
            )
            .then(
                if (visible) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .then(
                    if (tone == null) {
                        Modifier.background(Color.Transparent)
                    } else {
                        Modifier.background(tone.base)
                    }
                )
                .then(
                    if (selected) {
                        Modifier.border(1.dp, selectedAccent, RoundedCornerShape(3.dp))
                    } else if (tone != null) {
                        Modifier.border(1.dp, tone.border, RoundedCornerShape(3.dp))
                    } else {
                        Modifier
                    }
                ),
        )
    }
}

private fun activityTone(
    count: Int,
    peakCount: Int,
): HeatTone {
    if (count <= 0 || peakCount <= 0) {
        return HeatTone(
            base = Color(0xFFF0F7FF),
            glowInner = Color(0xFFF9FCFF),
            glowOuter = Color(0x00FFFFFF),
            border = Color(0xFFD8EAFD),
        )
    }
    val progress = (count.toFloat() / peakCount.toFloat()).coerceIn(0f, 1f)
    return when {
        progress <= 0.2f -> HeatTone(
            base = Color(0xFFDDEEFF),
            glowInner = Color(0xFFF4FAFF),
            glowOuter = Color(0x6679C6FF),
            border = Color(0xFFC5E2FF),
        )
        progress <= 0.4f -> HeatTone(
            base = Color(0xFFB8DCFF),
            glowInner = Color(0xFFE7F4FF),
            glowOuter = Color(0x888AD6FF),
            border = Color(0xFFA1CCFF),
        )
        progress <= 0.65f -> HeatTone(
            base = Color(0xFF84C4FF),
            glowInner = Color(0xFFCFEAFF),
            glowOuter = Color(0xB39EE4FF),
            border = Color(0xFF69B2FF),
        )
        progress <= 0.85f -> HeatTone(
            base = Color(0xFF4FA7FF),
            glowInner = Color(0xFFADE0FF),
            glowOuter = Color(0xE0A9ECFF),
            border = Color(0xFF3794F3),
        )
        else -> HeatTone(
            base = Color(0xFF2E8FFF),
            glowInner = Color(0xFF8BD7FF),
            glowOuter = Color(0xFFBDF5FF),
            border = Color(0xFF1F7DEB),
        )
    }
}

private data class HeatTone(
    val base: Color,
    val glowInner: Color,
    val glowOuter: Color,
    val border: Color,
)

private fun graphRelationsFor(
    overview: GraphOverviewUi,
    nodeId: String,
): List<GraphRelationUi> {
    if (overview.edges.isEmpty()) return emptyList()
    val nodeById = overview.nodes.associateBy { it.id }
    return overview.edges
        .filter { it.fromId == nodeId || it.toId == nodeId }
        .sortedByDescending { it.weight }
        .mapNotNull { edge ->
            val relatedId = if (edge.fromId == nodeId) edge.toId else edge.fromId
            nodeById[relatedId]?.let { relatedNode ->
                GraphRelationUi(
                    relatedNode = relatedNode,
                    label = conceptRelationWord(
                        relationType = edge.relationType,
                        fromCenter = edge.fromId == nodeId,
                    ),
                    reasonLine = edge.reasonLine.ifBlank {
                        "${relatedNode.label} 和当前知识点正在形成联系。"
                    },
                    weight = edge.weight,
                )
            }
        }
        .distinctBy { it.relatedNode.id }
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
            val displayRelation = resolveConceptGraphDisplayRelation(
                graph = graph,
                centerNodeId = centerNodeId,
                neighborNodeId = neighbor.node.conceptId,
                fallbackEdge = neighbor.relation,
            )
            ConceptGraphViewportNeighbor(
                node = neighbor.node,
                relation = displayRelation,
                relationWord = conceptRelationWord(
                    relationType = displayRelation.relationType,
                    fromCenter = displayRelation.fromConceptId == centerNodeId,
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
    val connectedNodeIdsByNodeId = buildConnectedNodeIdsByNodeId(graph)
    val requested = requestedCenterNodeId
        ?.takeIf { it.isNotBlank() && it in validNodeIds }
    val defaultCenter = graph.defaultCenterNodeId
        .takeIf { it.isNotBlank() && it in validNodeIds }
    val preferredConnectedCenter = graph.nodes
        .asSequence()
        .filter { connectedNodeIdsByNodeId[it.conceptId].orEmpty().isNotEmpty() }
        .sortedWith(
            compareByDescending<ConceptGraphNode> { connectedNodeIdsByNodeId[it.conceptId].orEmpty().size }
                .then(conceptNodeComparator),
        )
        .firstOrNull()
        ?.conceptId

    return requested
        ?: defaultCenter?.takeIf { connectedNodeIdsByNodeId[it].orEmpty().isNotEmpty() }
        ?: preferredConnectedCenter
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
    val fallbackEdge = buildBestRankedConceptNeighbors(
        graph = graph,
        centerNodeId = previousId,
        nodeById = nodeById,
    )[currentId]?.relation ?: return null
    val edge = resolveConceptGraphDisplayRelation(
        graph = graph,
        centerNodeId = previousId,
        neighborNodeId = currentId,
        fallbackEdge = fallbackEdge,
    )
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

private fun resolveConceptGraphDisplayRelation(
    graph: ConceptGraphSnapshot,
    centerNodeId: String,
    neighborNodeId: String,
    fallbackEdge: ConceptGraphEdge,
): ConceptGraphEdge =
    highestConfidenceConceptEdge(
        graph = graph,
        fromConceptId = centerNodeId,
        toConceptId = neighborNodeId,
    )
        ?: highestConfidenceConceptEdge(
            graph = graph,
            fromConceptId = neighborNodeId,
            toConceptId = centerNodeId,
        )
        ?: fallbackEdge

private fun highestConfidenceConceptEdge(
    graph: ConceptGraphSnapshot,
    fromConceptId: String,
    toConceptId: String,
): ConceptGraphEdge? =
    graph.edges
        .asSequence()
        .filter { edge ->
            edge.fromConceptId == fromConceptId && edge.toConceptId == toConceptId
        }
        .maxByOrNull(ConceptGraphEdge::confidence)

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
