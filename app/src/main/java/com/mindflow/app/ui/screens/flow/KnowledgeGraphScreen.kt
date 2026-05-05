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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextAlign
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
import com.mindflow.app.ui.components.GridTwo
import com.mindflow.app.ui.components.InsightBlock
import com.mindflow.app.ui.components.InsightLine
import com.mindflow.app.ui.components.InsightTone
import com.mindflow.app.ui.components.MetricTile
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentLavender
import com.mindflow.app.ui.theme.AccentTeal
import com.mindflow.app.util.TimeFormatter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
internal fun heatmapPhaseTestTag(slot: GraphActivitySlot): String =
    "heatmap-slot-${slot.date}-${slot.phase.name.lowercase()}"
internal fun graphActivityTimestampTestTag(noteId: Long): String = "graph-activity-timestamp-$noteId"

private const val ConceptGraphBatchSize = 5

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
    val suggestedNeighbors: List<ConceptGraphViewportNeighbor> = emptyList(),
    val returnNodeId: String? = null,
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

private val freshConceptNeighborComparator =
    compareByDescending<RankedConceptNeighbor> { it.node.updatedAt }
        .thenByDescending { it.node.hotnessScore }
        .thenByDescending { it.relation.confidence }
        .thenByDescending { it.fromCenter }
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
    val graphOverview = remember(snapshot) { buildConceptGraphVisualState(snapshot) }
    val graphNodes = graphOverview.nodes
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var graphQuery by rememberSaveable { mutableStateOf("") }
    var structureFilter by rememberSaveable { mutableStateOf(GraphStructureFilter.ALL) }
    var requestedGraphCenterNodeId by rememberSaveable { mutableStateOf<String?>(null) }

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
                    KnowledgeGraphHeader(
                        searchExpanded = searchExpanded,
                        onToggleSearch = { searchExpanded = !searchExpanded },
                    )
                }

                if (searchExpanded) {
                    item {
                        GraphSearchFilterCard(
                            nodes = graphNodes,
                            query = graphQuery,
                            onQueryChange = { graphQuery = it },
                            selectedFilter = structureFilter,
                            onFilterChange = { structureFilter = it },
                            onSelectNode = { nodeId ->
                                requestedGraphCenterNodeId = nodeId
                                searchExpanded = false
                            },
                        )
                    }
                }

                item {
                    KnowledgeGraphPanel(
                        snapshot = snapshot,
                        notes = notes,
                        requestedCenterNodeId = requestedGraphCenterNodeId,
                        onCenterNodeResolved = { requestedGraphCenterNodeId = it },
                        onOpenNote = onOpenNote,
                    )
                }

                item {
                    RecordsHeatmapPanel(
                        notes = notes,
                        graphNodes = graphNodes,
                        onOpenNote = onOpenNote,
                    )
                }

                item {
                    KnowledgeGraphOverviewCard(
                        overview = graphOverview,
                        graphNodes = graphNodes,
                        activeNoteCount = notes.count { !it.isArchived },
                    )
                }
            }
        }
    }
}

private enum class GraphStructureFilter(val label: String) {
    ALL("全部"),
    HUB("中心"),
    LINKED("连接"),
    ISOLATED("孤立"),
}

private enum class GraphTopicTab(val label: String) {
    GRAPH("图谱"),
    STATS("统计"),
    RECORDS("记录"),
}

@Composable
private fun KnowledgeGraphHeader(
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "图谱",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "知识连接，价值沉淀",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onToggleSearch,
            color = if (searchExpanded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
            },
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)),
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "搜索图谱",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GraphSearchFilterCard(
    nodes: List<GraphNodeUi>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: GraphStructureFilter,
    onFilterChange: (GraphStructureFilter) -> Unit,
    onSelectNode: (String) -> Unit,
) {
    val filteredNodes = remember(nodes, query, selectedFilter) {
        val normalizedQuery = query.trim().lowercase()
        nodes
            .asSequence()
            .filter { node ->
                when (selectedFilter) {
                    GraphStructureFilter.ALL -> true
                    GraphStructureFilter.HUB -> node.structureStatus == GraphStructureStatus.HUB
                    GraphStructureFilter.LINKED -> node.structureStatus == GraphStructureStatus.LINKED
                    GraphStructureFilter.ISOLATED -> node.structureStatus == GraphStructureStatus.ISOLATED
                }
            }
            .filter { node ->
                normalizedQuery.isBlank() ||
                    node.label.lowercase().contains(normalizedQuery) ||
                    node.summaryLine.lowercase().contains(normalizedQuery) ||
                    node.aliases.any { it.lowercase().contains(normalizedQuery) }
            }
            .take(8)
            .toList()
    }

    PanelCard {
        SectionHeader(title = "搜索与筛选", headline = "${filteredNodes.size} 个结果")
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索概念") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GraphStructureFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label, maxLines = 1) },
                    leadingIcon = if (filter == GraphStructureFilter.ALL) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        if (filteredNodes.isEmpty()) {
            Text(
                text = "没有匹配的概念。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filteredNodes.forEach { node ->
                    SwitchableConceptChip(
                        node = ConceptGraphNode(
                            conceptId = node.id,
                            label = node.label,
                            summary = node.summaryLine,
                            hotnessScore = node.densityScore,
                        ),
                        onClick = { onSelectNode(node.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeGraphOverviewCard(
    overview: GraphOverviewUi,
    graphNodes: List<GraphNodeUi>,
    activeNoteCount: Int,
) {
    val hubCount = graphNodes.count { it.structureStatus == GraphStructureStatus.HUB }
    val isolatedCount = graphNodes.count { it.structureStatus == GraphStructureStatus.ISOLATED }
    val strongestEdge = remember(overview.edges) { overview.edges.maxByOrNull { it.weight } }
    val nodeById = remember(graphNodes) { graphNodes.associateBy { it.id } }
    PanelCard {
        SectionHeader(
            title = "结构洞察",
            headline = "推荐整理",
        )
        GridTwo {
            MetricTile(
                label = "中心主题",
                value = hubCount.toString(),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
            )
            MetricTile(
                label = "待连接",
                value = isolatedCount.toString(),
                modifier = Modifier.weight(1f),
                accent = AccentLavender,
            )
        }
        GridTwo {
            MetricTile(
                label = "稳定连接",
                value = overview.edges.size.toString(),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary,
            )
            MetricTile(
                label = "记录沉淀",
                value = activeNoteCount.toString(),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (overview.verdictLine.isNotBlank()) {
            InsightBlock(tone = InsightTone.Primary) {
                InsightLine(label = "结构判断", text = overview.verdictLine, maxLines = 3)
            }
        }
        if (strongestEdge != null) {
            val left = nodeById[strongestEdge.fromId]?.label.orEmpty()
            val right = nodeById[strongestEdge.toId]?.label.orEmpty()
            if (left.isNotBlank() && right.isNotBlank()) {
                GraphRecommendationCard(
                    title = "$left × $right",
                    summary = strongestEdge.reasonLine.ifBlank { "这两个概念已经出现稳定关联，适合继续整理成主题。" },
                )
            }
        }
        HotTopicsStrip(graphNodes = graphNodes)
        Text(
            text = "主题、连接和热度会随记录增长自动更新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HotTopicsStrip(graphNodes: List<GraphNodeUi>) {
    if (graphNodes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = "热门主题", headline = "前 ${graphNodes.take(4).size}")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            graphNodes.take(4).forEachIndexed { index, node ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = 0.18f)),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 132.dp)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = node.accent,
                        )
                        Text(
                            text = node.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${node.noteCount.coerceAtLeast(1)} 记录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphRecommendationCard(
    title: String,
    summary: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "推荐关联",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GraphHeatSummary(
    monthRecordCount: Int,
    monthActiveDayCount: Int,
    weekRecordCount: Int,
    activeTopicCount: Int,
    longestStreakDays: Int,
    trendBuckets: List<Int>,
    hotTopics: List<GraphHotTopicUi>,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.88f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↗",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "记录趋势",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "$monthActiveDayCount 个活跃日 · $activeTopicCount 个主题被点亮",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "$monthRecordCount",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "近 30 天热度曲线",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${trendBuckets.sum()} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GraphTrendCurve(buckets = trendBuckets)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GraphHeatMetricPill(
                    label = "本周",
                    value = "${weekRecordCount}条",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                GraphHeatMetricPill(
                    label = "连续",
                    value = "${longestStreakDays}天",
                    accent = AccentLavender,
                    modifier = Modifier.weight(1f),
                )
            }
            GraphHotTopicsList(hotTopics = hotTopics)
        }
    }
}

@Composable
private fun GraphHeatMetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GraphHotTopicsList(
    hotTopics: List<GraphHotTopicUi>,
) {
    if (hotTopics.isEmpty()) return
    val peak = hotTopics.maxOf { it.recentRecordCount }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "热门主题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${hotTopics.size} 个热门主题",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        hotTopics.forEachIndexed { index, topic ->
            GraphHotTopicRow(
                topic = topic,
                progress = topic.recentRecordCount.toFloat() / peak.toFloat(),
                accent = if (index % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun GraphHotTopicRow(
    topic: GraphHotTopicUi,
    progress: Float,
    accent: Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = topic.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = accent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        text = "↑ ${topic.trendPercent}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        maxLines = 1,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent.copy(alpha = 0.74f)),
                )
            }
        }
    }
}

@Composable
private fun GraphTrendCurve(
    buckets: List<Int>,
) {
    val values = if (buckets.isEmpty()) listOf(0) else buckets
    val peak = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val guideLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
    val pointHaloColor = MaterialTheme.colorScheme.surface
    val lineColor = MaterialTheme.colorScheme.primary
    val accentPointColor = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
    ) {
        val left = 4f
        val right = size.width - 4f
        val top = 10f
        val bottom = size.height - 18f
        repeat(3) { index ->
            val y = top + ((bottom - top) * (index + 1) / 4f)
            drawLine(
                color = guideLineColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1f,
            )
        }
        val points = values.mapIndexed { index, value ->
            val x = if (values.size == 1) {
                (left + right) / 2f
            } else {
                left + ((right - left) * index / (values.lastIndex).toFloat())
            }
            val y = bottom - ((bottom - top) * (value.coerceAtLeast(0).toFloat() / peak.toFloat()))
            Offset(x, y)
        }
        val linePath = buildSmoothGraphPath(points)
        val areaPath = buildSmoothGraphPath(points).apply {
            lineTo(points.last().x, bottom)
            lineTo(points.first().x, bottom)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.18f),
                    accentPointColor.copy(alpha = 0.04f),
                    Color.Transparent,
                ),
                startY = top,
                endY = bottom,
            ),
        )
        drawPath(
            path = linePath,
            color = lineColor.copy(alpha = 0.84f),
            style = Stroke(width = 4.5f, cap = StrokeCap.Round),
        )
        points.forEachIndexed { index, point ->
            if (index == points.lastIndex || values[index] == peak) {
                drawCircle(
                    color = pointHaloColor,
                    radius = 6.5f,
                    center = point,
                )
                drawCircle(
                    color = if (index == points.lastIndex) accentPointColor else lineColor,
                    radius = 4.2f,
                    center = point,
                )
            }
        }
    }
}

private fun buildSmoothGraphPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    points.zipWithNext().forEach { (previous, current) ->
        val midX = (previous.x + current.x) / 2f
        path.cubicTo(
            midX,
            previous.y,
            midX,
            current.y,
            current.x,
            current.y,
        )
    }
    return path
}

@Composable
private fun GraphMonthSwitcher(
    selectedMonth: YearMonth,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = canGoPrevious,
                onClick = onPrevious,
            ) {
                Text("上月")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${selectedMonth.year}年${selectedMonth.monthValue}月",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "记录日历",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(
                enabled = canGoNext,
                onClick = onNext,
            ) {
                Text("下月")
            }
        }
    }
}

@Composable
private fun MonthHeatCalendar(
    month: YearMonth,
    activityBySlot: Map<GraphActivitySlot, Int>,
    peakSlotCount: Int,
    selectedSlot: GraphActivitySlot?,
    onSelectSlot: (GraphActivitySlot) -> Unit,
) {
    val dates = remember(month) { buildGraphMonthCalendarDates(month) }
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val phases = remember { GraphActivityPhase.values().toList() }
    val dayGap = 2.dp
    val phaseGap = 1.dp
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val matrixWidth = maxWidth
        val phaseCellSize = (matrixWidth - dayGap * 6 - phaseGap * 14) / 21
        val dayGroupWidth = phaseCellSize * 3 + phaseGap * 2
        Column(
            modifier = Modifier.width(matrixWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dayGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                weekdayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.width(dayGroupWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
            dates.chunked(7).forEach { week ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dayGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEach { date ->
                        Row(
                            modifier = Modifier.width(dayGroupWidth),
                            horizontalArrangement = Arrangement.spacedBy(phaseGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            phases.forEach { phase ->
                                val slot = GraphActivitySlot(date = date, phase = phase)
                                MonthHeatPhaseCell(
                                    slot = slot,
                                    inMonth = YearMonth.from(date) == month,
                                    count = activityBySlot[slot] ?: 0,
                                    peakCount = peakSlotCount,
                                    selected = selectedSlot == slot,
                                    onClick = { onSelectSlot(slot) },
                                    modifier = Modifier.size(phaseCellSize),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeatPhaseCell(
    slot: GraphActivitySlot,
    inMonth: Boolean,
    count: Int,
    peakCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(14.dp),
) {
    val tone = if (inMonth) activityTone(count, peakCount) else null
    val cellShape = RoundedCornerShape(0.dp)
    val stateBorder = when {
        selected -> Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f), cellShape)
        tone != null -> Modifier.border(0.6.dp, tone.border, cellShape)
        else -> Modifier
    }
    val containerColor = when {
        !inMonth -> Color.Transparent
        else -> tone?.base ?: Color.Transparent
    }

    Box(
        modifier = modifier
            .testTag(heatmapPhaseTestTag(slot))
            .semantics(mergeDescendants = true) {
                contentDescription = "${slot.date.monthValue}月${slot.date.dayOfMonth}日 ${slot.phase.label}"
                stateDescription = when {
                    !inMonth -> "非本月"
                    selected -> "已选中，$count 条记录"
                    count > 0 -> "$count 条记录"
                    else -> "无记录"
                }
                this.selected = selected
            }
            .then(if (inMonth) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cellShape)
                .background(containerColor)
                .then(stateBorder),
        )
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
    val activityBySlot = remember(activeNotes, zoneId) {
        buildGraphActivityBySlot(activeNotes, zoneId)
    }
    val currentMonth = remember(today) { YearMonth.from(today) }
    val availableMonths = remember(activityByDate, currentMonth) {
        (activityByDate.keys.map(YearMonth::from) + currentMonth)
            .distinct()
            .sorted()
    }
    val minMonth = remember(availableMonths, currentMonth) {
        availableMonths.firstOrNull() ?: currentMonth
    }
    val maxMonth = remember(availableMonths, currentMonth) {
        val latest = availableMonths.lastOrNull() ?: currentMonth
        if (latest.isAfter(currentMonth)) latest else currentMonth
    }
    val defaultSelectedMonth = remember(availableMonths, currentMonth) {
        if (currentMonth in availableMonths) currentMonth else availableMonths.lastOrNull() ?: currentMonth
    }
    var selectedMonthKey by rememberSaveable { mutableStateOf(defaultSelectedMonth.toString()) }
    val selectedMonth = remember(selectedMonthKey) { YearMonth.parse(selectedMonthKey) }
    val currentPhase = remember(zoneId) { graphActivityPhaseForHour(LocalTime.now(zoneId).hour) }
    var selectedDateKey by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    var selectedPhaseKey by rememberSaveable { mutableStateOf(currentPhase.name) }
    val selectedDate = selectedDateKey?.let(LocalDate::parse)
    val selectedPhase = remember(selectedPhaseKey, currentPhase) {
        runCatching { GraphActivityPhase.valueOf(selectedPhaseKey) }.getOrDefault(currentPhase)
    }
    val selectedSlot = remember(selectedDate, selectedPhase) {
        selectedDate?.let { date -> GraphActivitySlot(date = date, phase = selectedPhase) }
    }
    val selectedSlotNotes = remember(activeNotes, selectedSlot, zoneId) {
        if (selectedSlot == null) {
            emptyList()
        } else {
            activeNotes
                .filter { note ->
                    graphActivitySlotForNote(note, zoneId) == selectedSlot
                }
                .sortedByDescending { it.updatedAt }
        }
    }
    val activatedNodes = remember(selectedSlotNotes, graphNodes) {
        buildActivatedGraphNodes(
            notes = selectedSlotNotes,
            graphNodes = graphNodes,
        )
    }

    LaunchedEffect(minMonth, maxMonth, defaultSelectedMonth) {
        when {
            selectedMonth.isBefore(minMonth) || selectedMonth.isAfter(maxMonth) -> {
                selectedMonthKey = defaultSelectedMonth.toString()
            }
        }
    }
    LaunchedEffect(selectedMonthKey, selectedDateKey, today) {
        val date = selectedDateKey?.let(LocalDate::parse)
        if (date != null && YearMonth.from(date) != selectedMonth) {
            selectedDateKey = null
        } else if (selectedMonth == currentMonth && date == null) {
            selectedDateKey = today.toString()
            selectedPhaseKey = currentPhase.name
        }
    }
    val selectedMonthActivity = remember(activityByDate, selectedMonth) {
        activityByDate.filterKeys { YearMonth.from(it) == selectedMonth }
    }
    val selectedMonthSlotActivity = remember(activityBySlot, selectedMonth) {
        activityBySlot.filterKeys { slot -> YearMonth.from(slot.date) == selectedMonth }
    }
    val weekStart = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weekRecordCount = remember(activityByDate, weekStart, today) {
        countGraphActivityBetween(
            activityByDate = activityByDate,
            startDate = weekStart,
            endDate = today,
        )
    }
    val longestStreakDays = remember(activityByDate) {
        longestGraphActivityStreak(activityByDate)
    }
    val trendBuckets = remember(activityByDate, today) {
        buildGraphTrendBuckets(
            activityByDate = activityByDate,
            endDate = today,
            days = 30,
            bucketCount = 12,
        )
    }
    val activeTopicCount = remember(graphNodes) {
        graphNodes.count { node -> node.noteCount > 0 || node.relationCount > 0 }
    }
    val hotTopics = remember(activeNotes, graphNodes, zoneId, today) {
        buildGraphHotTopics(
            notes = activeNotes,
            graphNodes = graphNodes,
            zoneId = zoneId,
            today = today,
            limit = 3,
        )
    }
    val monthRecordCount = remember(selectedMonthActivity) {
        selectedMonthActivity.values.sum()
    }
    val monthActiveDayCount = remember(selectedMonthActivity) {
        selectedMonthActivity.count { it.value > 0 }
    }

    PanelCard {
        SectionHeader(
            title = "记录热度",
            headline = "${selectedMonth.monthValue}月 $monthRecordCount 条",
        )
        GraphHeatSummary(
            monthRecordCount = monthRecordCount,
            monthActiveDayCount = monthActiveDayCount,
            weekRecordCount = weekRecordCount,
            activeTopicCount = activeTopicCount,
            longestStreakDays = longestStreakDays,
            trendBuckets = trendBuckets,
            hotTopics = hotTopics,
        )
        GraphMonthSwitcher(
            selectedMonth = selectedMonth,
            canGoPrevious = selectedMonth.isAfter(minMonth),
            canGoNext = selectedMonth.isBefore(maxMonth),
            onPrevious = { selectedMonthKey = selectedMonth.minusMonths(1).toString() },
            onNext = { selectedMonthKey = selectedMonth.plusMonths(1).toString() },
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonthHeatCalendar(
                    month = selectedMonth,
                    activityBySlot = activityBySlot,
                    peakSlotCount = selectedMonthSlotActivity.values.maxOrNull() ?: 0,
                    selectedSlot = selectedSlot,
                    onSelectSlot = { slot ->
                        selectedDateKey = slot.date.toString()
                        selectedPhaseKey = slot.phase.name
                    },
                )
                Text(
                    text = buildGraphSelectedSlotHeadline(
                        slot = selectedSlot,
                        recordCount = selectedSlotNotes.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedSlot != null && shouldShowUnmappedGraphActivityHint(
                        recordCount = selectedSlotNotes.size,
                        activatedNodeCount = activatedNodes.size,
                    )
                ) {
                    Text(
                        text = "这个时段有记录变化，但还没有点亮稳定主题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (activatedNodes.isNotEmpty()) {
                    ActivatedThreadStrip(nodes = activatedNodes)
                }
                if (selectedSlot != null) {
                    if (selectedSlotNotes.isEmpty()) {
                        Text(
                            text = "这个时段没有记录变动。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        selectedSlotNotes.take(4).forEach { note ->
                            GraphActivityNoteCard(
                                note = note,
                                onOpenNote = onOpenNote,
                            )
                        }
                        if (selectedSlotNotes.size > 4) {
                            Text(
                                text = "还有 ${selectedSlotNotes.size - 4} 条，先打开其中一条继续看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    notes: List<NoteEntity>,
    requestedCenterNodeId: String?,
    onCenterNodeResolved: (String?) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    var activeCenterNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var previousCenterNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var neighborExpansionCount by rememberSaveable(activeCenterNodeId) { mutableIntStateOf(0) }
    var switchableExpansionCount by rememberSaveable(activeCenterNodeId) { mutableIntStateOf(0) }
    var selectedTopicTab by rememberSaveable(activeCenterNodeId) { mutableStateOf(GraphTopicTab.GRAPH) }

    val viewport = remember(
        snapshot,
        activeCenterNodeId,
        previousCenterNodeId,
        neighborExpansionCount,
        switchableExpansionCount,
    ) {
        buildConceptGraphViewport(
            snapshot = snapshot,
            currentCenterNodeId = activeCenterNodeId,
            previousCenterNodeId = previousCenterNodeId,
            expandedCenterNodeIds = activeCenterNodeId?.let { centerId ->
                List(neighborExpansionCount) { centerId }
            }.orEmpty(),
            expandedSwitchableCenterNodeIds = activeCenterNodeId?.let { centerId ->
                List(switchableExpansionCount) { centerId }
            }.orEmpty(),
        )
    }
    val centerNode = viewport.centerNode
    val graph = snapshot.conceptGraph
    val relationFromPreviousCenter = remember(graph, previousCenterNodeId, centerNode?.conceptId) {
        buildConceptGraphCenterRelation(
            graph = graph,
            previousCenterNodeId = previousCenterNodeId,
            currentCenterNodeId = centerNode?.conceptId,
        )
    }
    val relatedNotes = remember(centerNode, notes) {
        if (centerNode == null) {
            emptyList()
        } else {
            buildRelatedNotesForConcept(centerNode, notes)
        }
    }

    LaunchedEffect(requestedCenterNodeId) {
        val nextCenterNodeId = requestedCenterNodeId ?: return@LaunchedEffect
        if (nextCenterNodeId != activeCenterNodeId) {
            previousCenterNodeId = centerNode?.conceptId
            activeCenterNodeId = nextCenterNodeId
            neighborExpansionCount = 0
            switchableExpansionCount = 0
        }
    }

    LaunchedEffect(centerNode?.conceptId) {
        val resolvedCenterNodeId = centerNode?.conceptId ?: return@LaunchedEffect
        if (activeCenterNodeId == null) {
            activeCenterNodeId = resolvedCenterNodeId
        }
        onCenterNodeResolved(resolvedCenterNodeId)
    }

    fun selectCenter(nodeId: String) {
        val currentCenter = centerNode?.conceptId
        if (nodeId == currentCenter) return
        previousCenterNodeId = currentCenter
        activeCenterNodeId = nodeId
        onCenterNodeResolved(nodeId)
        neighborExpansionCount = 0
        switchableExpansionCount = 0
    }

    PanelCard {
        SectionHeader(
            title = "信息图谱",
            headline = if (graph.nodes.isNotEmpty()) {
                "${graph.nodes.size} 个主题 · ${graph.edges.size} 条连接"
            } else {
                "继续记录"
            },
        )
        if (centerNode == null) {
            Text(
                text = "记录继续积累，这里会慢慢长出最近思路之间的连接。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@PanelCard
        }

        ConceptGraphViewportCanvas(
            viewport = viewport,
            onSelectNode = ::selectCenter,
        )
        GraphTopicTabs(
            selectedTab = selectedTopicTab,
            onSelectTab = { selectedTopicTab = it },
        )
        when (selectedTopicTab) {
            GraphTopicTab.GRAPH -> ConceptGraphInfoCard(
                centerNode = centerNode,
                relationFromPreviousCenter = relationFromPreviousCenter,
                visibleNeighborCount = viewport.neighbors.size,
                visibleSuggestedNeighborCount = viewport.suggestedNeighbors.size,
                hiddenNeighborCount = viewport.hiddenNeighborCount,
                visibleSwitchableCount = viewport.switchableNodes.size,
                hiddenSwitchableCount = viewport.hiddenSwitchableNodeCount,
            )
            GraphTopicTab.STATS -> GraphTopicStatsCard(
                visibleNeighborCount = viewport.neighbors.size,
                visibleSuggestedNeighborCount = viewport.suggestedNeighbors.size,
                hiddenNeighborCount = viewport.hiddenNeighborCount,
                relatedRecordCount = relatedNotes.size,
            )
            GraphTopicTab.RECORDS -> GraphRelatedRecordsCard(
                records = relatedNotes,
                onOpenNote = onOpenNote,
                emptyText = "这个主题还没有匹配到具体记录。",
            )
        }
        GraphTopicActionCard(
            centerNode = centerNode,
            relatedNotes = relatedNotes,
            onOpenNote = onOpenNote,
        )
        if (viewport.neighbors.isEmpty() && viewport.hiddenSwitchableNodeCount > 0) {
            TextButton(onClick = { switchableExpansionCount += 1 }) {
                Text("再展开 ${viewport.hiddenSwitchableNodeCount} 个相关主题")
            }
        }
    }
}

@Composable
private fun GraphTopicTabs(
    selectedTab: GraphTopicTab,
    onSelectTab: (GraphTopicTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GraphTopicTab.entries.forEach { tab ->
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                label = {
                    Text(
                        text = tab.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                },
                    colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun GraphTopicStatsCard(
    visibleNeighborCount: Int,
    visibleSuggestedNeighborCount: Int,
    hiddenNeighborCount: Int,
    relatedRecordCount: Int,
) {
    val relationCount = visibleNeighborCount + visibleSuggestedNeighborCount + hiddenNeighborCount
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GridTwo {
                MetricTile(
                    label = "关联主题",
                    value = relationCount.toString(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary,
                )
                MetricTile(
                    label = "相关记录",
                    value = relatedRecordCount.toString(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = when {
                    relationCount >= 4 -> "这个主题已经形成清晰连接，可以继续提炼结构。"
                    relationCount > 0 -> "这个主题有初步关系，适合补一两条关键记录。"
                    else -> "这个主题暂时偏独立，继续记录后会更容易连接。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GraphTopicActionCard(
    centerNode: ConceptGraphNode,
    relatedNotes: List<NoteEntity>,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "是否整理为项目结构？",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${centerNode.label} 已经有可追踪线索，先从相关记录提炼项目骨架。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                enabled = relatedNotes.isNotEmpty(),
                onClick = { relatedNotes.firstOrNull()?.id?.let(onOpenNote) },
            ) {
                Text(if (relatedNotes.isNotEmpty()) "立即整理" else "待补记录")
            }
        }
    }
}

@Composable
private fun GraphRelatedRecordsCard(
    records: List<NoteEntity>,
    onOpenNote: (Long) -> Unit,
    emptyText: String? = null,
) {
    if (records.isEmpty() && emptyText == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(title = "相关记录", headline = "${records.size} 条")
            if (records.isEmpty()) {
                Text(
                    text = emptyText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                records.take(3).forEach { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenNote(note.id) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = note.topic.ifBlank { "未命名记录" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = TimeFormatter.compact(note.updatedAt.coerceAtLeast(note.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun buildRelatedNotesForConcept(
    centerNode: ConceptGraphNode,
    notes: List<NoteEntity>,
): List<NoteEntity> {
    if (notes.isEmpty()) return emptyList()
    val sourceNoteIds = centerNode.sourceIds
        .mapNotNull { sourceId -> sourceId.removePrefix("note:").toLongOrNull() }
        .toSet()
    val conceptTerms = (listOf(centerNode.label) + centerNode.aliases)
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
    return notes
        .filterNot { it.isArchived }
        .mapNotNull { note ->
            val sourceScore = if (note.id in sourceNoteIds) 10 else 0
            val haystack = buildString {
                append(note.topic)
                append('\n')
                append(note.content.take(500))
                append('\n')
                append(note.tags.joinToString(" "))
            }.lowercase()
            val termScore = conceptTerms.count { term -> haystack.contains(term) }
            val score = sourceScore + termScore
            if (score <= 0) null else note to score
        }
        .sortedWith(
            compareByDescending<Pair<NoteEntity, Int>> { it.second }
                .thenByDescending { it.first.updatedAt.coerceAtLeast(it.first.createdAt) },
        )
        .map { it.first }
        .take(6)
}

private fun conceptGraphCanvasHeight(cardWidth: Dp, neighborCount: Int): Dp {
    val proportional = cardWidth * when {
        neighborCount <= 0 -> 0.34f
        neighborCount <= 2 -> 0.42f
        neighborCount <= 4 -> 0.5f
        else -> 0.56f
    }
    val baseline = when {
        neighborCount <= 0 -> 128.dp
        neighborCount <= 2 -> 148.dp
        neighborCount <= 4 -> 172.dp
        else -> 196.dp
    }
    return maxOf(proportional, baseline).coerceAtMost(236.dp)
}

@Composable
private fun ConceptGraphViewportCanvas(
    viewport: ConceptGraphViewport,
    onSelectNode: (String) -> Unit,
) {
    viewport.centerNode ?: return
    var renderError by remember(viewport.centerNode?.conceptId) { mutableStateOf<String?>(null) }
    var retryNonce by remember(viewport.centerNode?.conceptId) { mutableIntStateOf(0) }
    val payload = remember(viewport) { viewport.toWebPayload() }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val canvasHeight = remember(maxWidth, viewport.neighbors.size) {
            conceptGraphCanvasHeight(maxWidth, viewport.neighbors.size)
        }

        if (renderError != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .testTag(KnowledgeGraphCanvasTag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "思路连接暂时没有加载出来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val reasonLine = renderError?.trim().orEmpty()
                if (reasonLine.isNotBlank()) {
                    Text(
                        text = reasonLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        renderError = null
                        retryNonce += 1
                    },
                ) {
                    Text("重试")
                }
            }
        } else {
            key(retryNonce) {
                WebViewGraphCanvas(
                    payload = payload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight),
                    onNodeClick = onSelectNode,
                    onRenderError = { message ->
                        renderError = message.ifBlank { "graph_render_failed" }
                    },
                )
            }
        }
    }
}

private fun buildConceptGraphSummaryLine(
    centerNode: ConceptGraphNode,
    relationFromPreviousCenter: ConceptGraphCenterRelation?,
): String =
    relationFromPreviousCenter?.let { relation ->
        relation.reasonLine.takeIf { it.isNotBlank() }?.let { reason ->
            "${relation.relationWord} · $reason"
        } ?: relation.relationWord
    } ?: centerNode.summary.ifBlank {
        "你最近的思考，正在围绕它继续长。"
    }

private fun buildConceptGraphNextHint(
    visibleNeighborCount: Int,
    visibleSuggestedNeighborCount: Int,
    hiddenNeighborCount: Int,
    visibleSwitchableCount: Int,
    hiddenSwitchableCount: Int,
): String =
    when {
        visibleNeighborCount == 0 && visibleSwitchableCount > 0 && hiddenSwitchableCount > 0 ->
            "这条思路先长到这里。直接点图里的相关想法继续展开，图下还能再展开 $hiddenSwitchableCount 个。"
        visibleNeighborCount == 0 && visibleSwitchableCount > 0 ->
            "这条思路先长到这里。直接点图里的相关想法继续往下看。"
        visibleNeighborCount == 0 -> "这个主题当前先长到这里。"
        hiddenNeighborCount > 0 && visibleSuggestedNeighborCount > 0 ->
            "点图里的主题继续展开。颜色更浅的是下一层线索。"
        hiddenNeighborCount > 0 -> "点图里的主题继续展开。"
        else -> "点图里的主题继续展开。"
    }

@Composable
private fun ConceptGraphInfoCard(
    centerNode: ConceptGraphNode,
    relationFromPreviousCenter: ConceptGraphCenterRelation?,
    visibleNeighborCount: Int,
    visibleSuggestedNeighborCount: Int,
    hiddenNeighborCount: Int,
    visibleSwitchableCount: Int,
    hiddenSwitchableCount: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(KnowledgeGraphInfoPanelTag)
            .semantics(mergeDescendants = true) {
                contentDescription = "主题详情 ${centerNode.label}"
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = centerNode.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildConceptGraphSummaryLine(
                    centerNode = centerNode,
                    relationFromPreviousCenter = relationFromPreviousCenter,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildConceptGraphNextHint(
                    visibleNeighborCount = visibleNeighborCount,
                    visibleSuggestedNeighborCount = visibleSuggestedNeighborCount,
                    hiddenNeighborCount = hiddenNeighborCount,
                    visibleSwitchableCount = visibleSwitchableCount,
                    hiddenSwitchableCount = hiddenSwitchableCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class ConceptGraphNodeLabelPlacement {
    BOTTOM,
    START,
    END,
}

@Composable
private fun ConceptGraphDotNode(
    node: ConceptGraphNode,
    accent: Color,
    modifier: Modifier,
    selected: Boolean,
    labelPlacement: ConceptGraphNodeLabelPlacement,
    onClick: () -> Unit,
) {
    val containerModifier = modifier
        .testTag(graphNodeTestTag(node.conceptId))
        .semantics(mergeDescendants = true) {
            contentDescription = "主题 ${node.label}"
            stateDescription = if (selected) "中心主题" else "直接关联主题"
            this.selected = selected
            role = Role.Button
        }
        .clickable(onClick = onClick)

    val dotSize = if (selected) 24.dp else 16.dp
    val textStyle = if (selected) {
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.labelSmall
    }
    val textColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    val dotContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (selected) 0.94f else 0.88f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)),
                )
            }
        }
    }

    if (!selected && labelPlacement != ConceptGraphNodeLabelPlacement.BOTTOM) {
        Row(
            modifier = containerModifier.widthIn(max = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (labelPlacement == ConceptGraphNodeLabelPlacement.START) {
                Text(
                    text = node.label,
                    style = textStyle,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                dotContent()
            } else {
                dotContent()
                Text(
                    text = node.label,
                    style = textStyle,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Column(
            modifier = containerModifier.width(if (selected) 96.dp else 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dotContent()
            Text(
                text = node.label,
                style = textStyle,
                color = textColor,
                maxLines = if (selected) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class ConceptGraphNeighborLayout(
    val position: Pair<Float, Float>,
    val labelPlacement: ConceptGraphNodeLabelPlacement,
)

private fun buildConceptGraphCenterPosition(
    neighborCount: Int,
    widthPx: Float,
    heightPx: Float,
): Pair<Float, Float> {
    if (neighborCount <= 0) return widthPx / 2f to (heightPx * 0.5f)
    return clamp(widthPx * 0.47f, 168f, widthPx - 168f) to
        clamp(heightPx * 0.54f, 124f, heightPx - 124f)
}

private fun buildConceptGraphNeighborLayouts(
    neighborCount: Int,
    center: Pair<Float, Float>,
    widthPx: Float,
    heightPx: Float,
): List<ConceptGraphNeighborLayout> {
    if (neighborCount <= 0) return emptyList()
    val centerX = center.first
    val centerY = center.second
    val orbitX = min(widthPx * 0.28f, 224f)
    val orbitY = min(heightPx * 0.26f, 172f)
    val slots = listOf(
        Triple(0.02f, -1.92f, ConceptGraphNodeLabelPlacement.BOTTOM),
        Triple(1.22f, -1.18f, ConceptGraphNodeLabelPlacement.END),
        Triple(1.96f, -0.02f, ConceptGraphNodeLabelPlacement.END),
        Triple(1.26f, 1.24f, ConceptGraphNodeLabelPlacement.END),
        Triple(-0.08f, 1.96f, ConceptGraphNodeLabelPlacement.BOTTOM),
        Triple(-0.88f, 0.72f, ConceptGraphNodeLabelPlacement.BOTTOM),
    )
    val pattern = when (neighborCount) {
        1 -> listOf(2)
        2 -> listOf(1, 4)
        3 -> listOf(0, 2, 4)
        4 -> listOf(0, 1, 3, 5)
        5 -> listOf(0, 1, 2, 4, 5)
        else -> listOf(0, 1, 2, 3, 4, 5)
    }
    return pattern.take(neighborCount).map { slotIndex ->
        val slot = slots[slotIndex]
        val x = clamp(centerX + (orbitX * slot.first), 124f, widthPx - 124f)
        val y = clamp(centerY + (orbitY * slot.second), 116f, heightPx - 116f)
        ConceptGraphNeighborLayout(
            position = x to y,
            labelPlacement = slot.third,
        )
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
                color = MaterialTheme.colorScheme.onSurface,
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
    val title = note.topic.ifBlank { "未命名记录" }
    val preview = note.content.lineSequence().firstOrNull { it.isNotBlank() }
        ?: "打开这条记录继续看内容。"
    var titleLineCount by remember(note.id, title) { mutableIntStateOf(1) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenNote(note.id) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    titleLineCount = result.lineCount.coerceAtLeast(1)
                },
            )
            Text(
                modifier = Modifier
                    .testTag(graphActivityTimestampTestTag(note.id)),
                text = TimeFormatter.compact(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (titleLineCount >= 2) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${note.status.label} · ${note.horizon.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.84f),
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
                        color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            visibleSuggestedNeighborCount = viewport.suggestedNeighbors.size,
            hiddenNeighborCount = viewport.hiddenNeighborCount,
            visibleSwitchableCount = viewport.switchableNodes.size,
            hiddenSwitchableCount = viewport.hiddenSwitchableNodeCount,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "一跳关联",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurface,
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (neighbor.node.summary.isNotBlank()) {
                Text(
                    text = neighbor.node.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.summary.isNotBlank()) {
                Text(
                    text = node.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        val backboneEdgeColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        val mutedBackboneEdgeColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        val focusEdgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            displayEdges.forEach { edge ->
                val from = positionById[edge.fromId] ?: return@forEach
                val to = positionById[edge.toId] ?: return@forEach
                val edgeColor = when (edge.emphasis) {
                    GraphEdgeEmphasis.FOCUS -> focusEdgeColor
                    GraphEdgeEmphasis.BACKBONE -> if (selectedNodeId == null) {
                        backboneEdgeColor
                    } else {
                        mutedBackboneEdgeColor
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
            !emphasized -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            node.structureStatus == GraphStructureStatus.HUB -> MaterialTheme.colorScheme.surface
            node.structureStatus == GraphStructureStatus.ISOLATED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
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
                color = if (emphasized || selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        shadowElevation = 0.dp,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = graphStructureLine(node),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (relations.isEmpty()) {
                Text(
                    text = "这条知识点暂时还比较独立。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "直接关系",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f)),
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = relation.relatedNode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = relation.reasonLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            append("${nodes.size} 个主题")
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
    val selectedAccent = MaterialTheme.colorScheme.primary
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

@Composable
private fun activityTone(
    count: Int,
    peakCount: Int,
): HeatTone {
    val heat = MaterialTheme.colorScheme.primary
    val softSurface = MaterialTheme.colorScheme.surfaceVariant
    val softBorder = MaterialTheme.colorScheme.outlineVariant
    if (count <= 0 || peakCount <= 0) {
        return HeatTone(
            base = softSurface.copy(alpha = 0.46f),
            glowInner = softSurface.copy(alpha = 0.26f),
            glowOuter = Color.Transparent,
            border = softBorder.copy(alpha = 0.46f),
        )
    }
    val progress = (count.toFloat() / peakCount.toFloat()).coerceIn(0f, 1f)
    return when {
        progress <= 0.2f -> HeatTone(
            base = heat.copy(alpha = 0.22f),
            glowInner = heat.copy(alpha = 0.08f),
            glowOuter = Color.Transparent,
            border = heat.copy(alpha = 0.24f),
        )
        progress <= 0.4f -> HeatTone(
            base = heat.copy(alpha = 0.34f),
            glowInner = heat.copy(alpha = 0.12f),
            glowOuter = heat.copy(alpha = 0.03f),
            border = heat.copy(alpha = 0.30f),
        )
        progress <= 0.65f -> HeatTone(
            base = heat.copy(alpha = 0.50f),
            glowInner = heat.copy(alpha = 0.16f),
            glowOuter = heat.copy(alpha = 0.05f),
            border = heat.copy(alpha = 0.38f),
        )
        progress <= 0.85f -> HeatTone(
            base = heat.copy(alpha = 0.68f),
            glowInner = heat.copy(alpha = 0.20f),
            glowOuter = heat.copy(alpha = 0.08f),
            border = heat.copy(alpha = 0.46f),
        )
        else -> HeatTone(
            base = heat.copy(alpha = 0.86f),
            glowInner = heat.copy(alpha = 0.24f),
            glowOuter = heat.copy(alpha = 0.12f),
            border = heat.copy(alpha = 0.56f),
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

internal fun buildConceptGraphViewport(
    snapshot: DirectionWikiSnapshot,
    currentCenterNodeId: String? = null,
    previousCenterNodeId: String? = null,
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
    val visibleNeighbors = selectVisibleConceptNeighbors(
        rankedNeighbors = rankedNeighbors,
        visibleNeighborCount = visibleNeighborCount,
    )
    val suggestedNeighbors = if (visibleNeighbors.isNotEmpty()) {
        selectSuggestedConceptNeighbors(
            rankedNeighbors = rankedNeighbors,
            visibleNeighbors = visibleNeighbors,
            prioritizedNodeId = previousCenterNodeId,
        )
    } else {
        emptyList()
    }

    fun toViewportNeighbor(neighbor: RankedConceptNeighbor): ConceptGraphViewportNeighbor {
        val displayRelation = resolveConceptGraphDisplayRelation(
            graph = graph,
            centerNodeId = centerNodeId,
            neighborNodeId = neighbor.node.conceptId,
            fallbackEdge = neighbor.relation,
        )
        return ConceptGraphViewportNeighbor(
            node = neighbor.node,
            relation = displayRelation,
            relationWord = conceptRelationWord(
                relationType = displayRelation.relationType,
                fromCenter = displayRelation.fromConceptId == centerNodeId,
            ),
        )
    }

    return ConceptGraphViewport(
        centerNode = centerNode,
        neighbors = visibleNeighbors.map(::toViewportNeighbor),
        suggestedNeighbors = suggestedNeighbors.map(::toViewportNeighbor),
        returnNodeId = previousCenterNodeId?.takeIf { previousId ->
            previousId != centerNodeId && (
                visibleNeighbors.any { it.node.conceptId == previousId } ||
                    suggestedNeighbors.any { it.node.conceptId == previousId }
            )
        },
        hiddenNeighborCount = (rankedNeighbors.size - visibleNeighbors.size).coerceAtLeast(0),
        switchableNodes = visibleSwitchableNodes,
        hiddenSwitchableNodeCount = (allSwitchableNodes.size - visibleSwitchableNodes.size).coerceAtLeast(0),
    )
}

private fun selectVisibleConceptNeighbors(
    rankedNeighbors: List<RankedConceptNeighbor>,
    visibleNeighborCount: Int,
): List<RankedConceptNeighbor> {
    val cappedCount = visibleNeighborCount.coerceAtLeast(0)
    if (cappedCount == 0 || rankedNeighbors.isEmpty()) return emptyList()
    if (rankedNeighbors.size <= cappedCount) return rankedNeighbors

    val anchorCount = minOf(2, cappedCount)
    val anchors = rankedNeighbors.take(anchorCount)
    val remainingSlots = cappedCount - anchorCount
    if (remainingSlots == 0) return anchors

    val freshNeighbors = rankedNeighbors
        .drop(anchorCount)
        .sortedWith(freshConceptNeighborComparator)
        .take(remainingSlots)

    return anchors + freshNeighbors
}

private fun selectSuggestedConceptNeighbors(
    rankedNeighbors: List<RankedConceptNeighbor>,
    visibleNeighbors: List<RankedConceptNeighbor>,
    prioritizedNodeId: String? = null,
    previewCount: Int = 2,
): List<RankedConceptNeighbor> {
    if (previewCount <= 0 || rankedNeighbors.isEmpty()) return emptyList()
    val visibleIds = visibleNeighbors.map { it.node.conceptId }.toSet()
    val remaining = rankedNeighbors
        .asSequence()
        .filterNot { it.node.conceptId in visibleIds }
        .toList()
    if (remaining.isEmpty()) return emptyList()

    val prioritized = prioritizedNodeId
        ?.takeIf { it.isNotBlank() }
        ?.let { preferredId -> remaining.firstOrNull { it.node.conceptId == preferredId } }

    return buildList {
        prioritized?.let(::add)
        remaining.forEach { neighbor ->
            if (neighbor == prioritized) return@forEach
            if (size >= previewCount) return@forEach
            add(neighbor)
        }
    }.take(previewCount)
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
