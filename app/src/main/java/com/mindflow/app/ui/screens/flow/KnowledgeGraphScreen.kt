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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiGraphMaturity
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.CardShape
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.AccentBlue
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val KnowledgeGraphCanvasTag = "knowledge-graph-canvas"
internal const val KnowledgeGraphInfoPanelTag = "knowledge-graph-info-panel"

internal fun graphNodeTestTag(nodeId: String): String =
    "graph-node-" + nodeId.replace(Regex("[^A-Za-z0-9_-]"), "_")

internal fun heatmapDayTestTag(date: LocalDate): String = "heatmap-day-$date"

internal data class GraphNodeUi(
    val id: String,
    val label: String,
    val summaryLine: String,
    val threadKey: String,
    val structureStatus: GraphStructureStatus,
    val accent: Color = AccentBlue,
    val densityScore: Double = 0.0,
    val maturity: DirectionWikiGraphMaturity = DirectionWikiGraphMaturity.FORMING,
    val noteCount: Int = 0,
    val relationCount: Int = 0,
    val priority: Int = 3,
)

internal data class GraphEdgeUi(
    val fromId: String,
    val toId: String,
    val weight: Int,
    val reasonLine: String = "",
)

internal data class GraphOverviewUi(
    val headline: String,
    val verdictLine: String,
    val hubNodeIds: Set<String>,
    val isolatedNodeIds: Set<String>,
    val nodes: List<GraphNodeUi>,
    val edges: List<GraphEdgeUi>,
)

private data class GraphNodeLayout(
    val node: GraphNodeUi,
    val position: Pair<Float, Float>,
)

internal data class SelectedGraphNode(
    val threadKey: String,
    val label: String,
    val summaryLine: String,
    val densityScore: Double,
    val maturity: DirectionWikiGraphMaturity,
    val noteCount: Int,
)

internal data class SelectedGraphEdge(
    val fromThreadKey: String,
    val toThreadKey: String,
    val strength: Int,
    val reasonLine: String,
)

internal data class SelectedGraphData(
    val headline: String,
    val summaryLine: String,
    val hubNodeIds: Set<String>,
    val isolatedNodeIds: Set<String>,
    val nodes: List<SelectedGraphNode>,
    val edges: List<SelectedGraphEdge>,
)

internal data class GraphProjectionNode(
    val threadKey: String,
    val label: String,
    val summaryLine: String,
    val densityScore: Double,
    val maturity: DirectionWikiGraphMaturity,
    val noteCount: Int,
    val relationCount: Int,
)

internal data class GraphProjectionEdge(
    val fromThreadKey: String,
    val toThreadKey: String,
    val strength: Int,
    val reasonLine: String,
)

internal data class GraphProjection(
    val headline: String,
    val summaryLine: String,
    val hubNodeIds: Set<String>,
    val isolatedNodeIds: Set<String>,
    val nodes: List<GraphProjectionNode>,
    val edges: List<GraphProjectionEdge>,
)

internal enum class GraphStructureStatus(
    val label: String,
) {
    HUB("中心"),
    LINKED("成形中"),
    ISOLATED("独立"),
    EMERGING("持续出现"),
}

private enum class GraphDomainBucket(
    val label: String,
    val accent: Color,
    val keywords: List<String>,
) {
    WORK(
        label = "工作",
        accent = AccentBlue,
        keywords = listOf("工作", "项目", "产品", "设计", "开发", "交付", "任务", "客户", "团队", "ai", "模型"),
    ),
    HEALTH(
        label = "健康",
        accent = Accent.copy(alpha = 0.92f),
        keywords = listOf("健康", "身体", "睡眠", "运动", "饮食", "体重", "恢复"),
    ),
    LIFE(
        label = "生活",
        accent = Color(0xFF0F766E),
        keywords = listOf("生活", "家庭", "日常", "家务", "居住", "出行", "习惯", "情绪"),
    ),
    LEARNING(
        label = "学习",
        accent = Color(0xFF7C3AED),
        keywords = listOf("学习", "阅读", "写作", "研究", "知识", "思考", "复盘"),
    ),
    RELATION(
        label = "关系",
        accent = Color(0xFFDB2777),
        keywords = listOf("关系", "沟通", "朋友", "家人", "社交", "协作"),
    ),
    OTHER(
        label = "其他",
        accent = Color(0xFF64748B),
        keywords = emptyList(),
    ),
}

@androidx.compose.runtime.Composable
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

@androidx.compose.runtime.Composable
internal fun KnowledgeGraphScreen(
    snapshot: DirectionWikiSnapshot,
    notes: List<NoteEntity>,
    onOpenNote: (Long) -> Unit,
) {
    val graphOverview = remember(snapshot) {
        buildGraphVisualState(
            directions = snapshot.directions,
            projection = projectPureGraphInfo(snapshot, selectVisibleGraph(snapshot)),
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
                        graphNodes = graphOverview.nodes,
                        onOpenNote = onOpenNote,
                    )
                }

                item {
                    KnowledgeGraphPanel(overview = graphOverview)
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
                    onSelectDate = { date ->
                        selectedDateKey = date.toString()
                    },
                )
                Text(
                    text = if (selectedDate != null) {
                        "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · ${selectedDateNotes.size} 条记录有变动"
                    } else {
                        "点某一天，看看它把哪些主题点亮了。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
                if (selectedDate != null) {
                    if (activatedNodes.isEmpty()) {
                        Text(
                            text = "这一天有记录变化，但还没有点亮稳定主题。",
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

@Composable
private fun KnowledgeGraphPanel(
    overview: GraphOverviewUi,
) {
    var selectedNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(overview.nodes) {
        val visibleIds = overview.nodes.map { it.id }.toSet()
        if (selectedNodeId !in visibleIds) {
            selectedNodeId = overview.nodes.firstOrNull()?.id
        }
    }
    val selectedGraphNode = remember(overview, selectedNodeId) {
        overview.nodes.firstOrNull { it.id == selectedNodeId } ?: overview.nodes.firstOrNull()
    }
    val relatedGraphNodes = remember(overview, selectedGraphNode) {
        selectedGraphNode?.let { node ->
            relatedNodesFor(overview, node.id)
        }.orEmpty()
    }

    PanelCard {
        SectionHeader(
            title = "信息图谱",
            headline = overview.headline,
        )
        Text(
            text = overview.verdictLine,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMain,
        )
        if (overview.nodes.isEmpty()) {
            Text(
                text = "先让记录继续积累，这里会慢慢长出你的信息图谱。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
            )
        } else {
            KnowledgeGraphCanvas(
                overview = overview,
                selectedNodeId = selectedGraphNode?.id,
                onSelectNode = { selectedNodeId = it },
            )
            selectedGraphNode?.let { node ->
                GraphInfoCard(
                    node = node,
                    relatedNodes = relatedGraphNodes,
                )
            }
            if (overview.edges.isEmpty()) {
                Text(
                    text = "这些主题已经出现，但彼此关系还不够清楚，先继续记录，关系会慢慢长出来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
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
                color = AccentBlue,
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
                            .clip(RoundedCornerShape(999.dp))
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

@androidx.compose.runtime.Composable
private fun KnowledgeGraphCanvas(
    overview: GraphOverviewUi,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
) {
    val nodes = overview.nodes
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .testTag(KnowledgeGraphCanvasTag)
            .background(Color.Transparent),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val layouts = remember(nodes, overview.edges, overview.hubNodeIds, overview.isolatedNodeIds, widthPx, heightPx) {
            buildGraphNodeLayouts(
                nodes = nodes,
                edges = overview.edges,
                hubNodeIds = overview.hubNodeIds,
                isolatedNodeIds = overview.isolatedNodeIds,
                widthPx = widthPx,
                heightPx = heightPx,
            )
        }
        val positionById = remember(layouts) { layouts.associate { it.node.id to it.position } }

        Canvas(modifier = Modifier.fillMaxSize()) {
            overview.edges.forEach { edge ->
                val from = positionById[edge.fromId] ?: return@forEach
                val to = positionById[edge.toId] ?: return@forEach
                val selected = selectedNodeId != null && (edge.fromId == selectedNodeId || edge.toId == selectedNodeId)
                val edgeColor = when {
                    selected -> AccentBlue.copy(alpha = 0.4f)
                    else -> BorderSoft.copy(alpha = 0.72f)
                }
                drawLine(
                    color = edgeColor,
                    start = androidx.compose.ui.geometry.Offset(from.first, from.second),
                    end = androidx.compose.ui.geometry.Offset(to.first, to.second),
                    strokeWidth = if (selected) 4f else (1.6f + edge.weight.coerceAtMost(6) * 0.35f),
                )
            }
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
                onClick = { onSelectNode(layout.node.id) },
                width = graphNodeWidth(layout.node, selectedNodeId == layout.node.id),
                traversalOrder = index.toFloat(),
            )
        }
    }
}

private fun Int.dpToPx(density: Density): Float = with(density) { this@dpToPx.dp.toPx() }

private fun androidx.compose.ui.unit.Dp.toPx(density: Density): Float = with(density) { this@toPx.toPx() }

@androidx.compose.runtime.Composable
private fun GraphNodeBubble(
    node: GraphNodeUi,
    modifier: Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    traversalOrder: Float,
) {
    Surface(
        modifier = modifier
            .testTag(graphNodeTestTag(node.id))
            .semantics(mergeDescendants = true) {
                contentDescription = "主题 ${node.label}"
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
                    .clip(RoundedCornerShape(999.dp))
                    .background(node.accent.copy(alpha = 0.88f)),
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelLarge,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GraphInfoCard(
    node: GraphNodeUi,
    relatedNodes: List<GraphNodeUi>,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(KnowledgeGraphInfoPanelTag)
            .semantics(mergeDescendants = true) {
                contentDescription = "主题信息 ${node.label}"
                stateDescription = "结构状态 ${node.structureStatus.label}"
            },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "主题信息",
                style = MaterialTheme.typography.labelMedium,
                color = TextSoft,
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            FocusLine(label = "一句概述", value = node.summaryLine)
            FocusLine(
                label = "相关主题",
                value = relatedNodes
                    .map { it.label }
                    .distinct()
                    .take(3)
                    .joinToString(" / ")
                    .ifBlank { "这条主题暂时还比较独立。" },
            )
            FocusLine(label = "结构状态", value = graphStructureLine(node))
        }
    }
}

@Composable
private fun FocusLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSoft,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMain,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun graphStructureLine(node: GraphNodeUi): String =
    buildString {
        append(node.structureStatus.label)
        append(" · ")
        append(
            when (node.maturity) {
                DirectionWikiGraphMaturity.STABLE -> "结构稳定"
                DirectionWikiGraphMaturity.STRENGTHENING -> "关系在变清楚"
                DirectionWikiGraphMaturity.FORMING -> "还在慢慢成形"
            },
        )
    }

private fun relatedNodesFor(
    overview: GraphOverviewUi,
    nodeId: String,
): List<GraphNodeUi> {
    if (overview.edges.isEmpty()) return emptyList()
    val nodeById = overview.nodes.associateBy { it.id }
    return overview.edges
        .filter { it.fromId == nodeId || it.toId == nodeId }
        .sortedByDescending { it.weight }
        .mapNotNull { edge ->
            val relatedId = if (edge.fromId == nodeId) edge.toId else edge.fromId
            nodeById[relatedId]
        }
        .distinctBy { it.id }
}

internal fun selectVisibleGraph(
    snapshot: DirectionWikiSnapshot,
): SelectedGraphData {
    val presentation = snapshot.graph.presentation
    val selectedNodes = when {
        presentation.nodes.isNotEmpty() -> presentation.nodes.map { node ->
            SelectedGraphNode(
                threadKey = node.threadKey,
                label = node.label,
                summaryLine = node.summaryLine,
                densityScore = node.densityScore,
                maturity = node.maturity,
                noteCount = node.noteCount,
            )
        }
        snapshot.graph.nodes.isNotEmpty() -> snapshot.graph.nodes
            .sortedWith(compareByDescending<com.mindflow.app.data.wiki.DirectionWikiGraphNode> { it.densityScore }.thenByDescending { it.recencyScore })
            .take(6)
            .map { node ->
                SelectedGraphNode(
                    threadKey = node.threadKey,
                    label = node.label,
                    summaryLine = node.summaryLine,
                    densityScore = node.densityScore,
                    maturity = node.maturity,
                    noteCount = node.noteCount,
                )
            }
        else -> snapshot.directions.values
            .sortedByDescending { it.updatedAt }
            .take(6)
            .map { direction ->
                SelectedGraphNode(
                    threadKey = direction.threadKey,
                    label = compactDirectionLabel(direction.title),
                    summaryLine = graphSummaryLine(direction),
                    densityScore = 0.5,
                    maturity = direction.stage.toGraphMaturity(),
                    noteCount = 0,
                )
            }
    }
    if (selectedNodes.isEmpty()) {
        return SelectedGraphData(
            headline = "还没有足够结构",
            summaryLine = "",
            hubNodeIds = emptySet(),
            isolatedNodeIds = emptySet(),
            nodes = emptyList(),
            edges = emptyList(),
        )
    }

    val visibleIds = selectedNodes.map { it.threadKey }.toSet()
    val selectedEdges = when {
        presentation.edges.isNotEmpty() -> presentation.edges
            .filter { it.fromThreadKey in visibleIds && it.toThreadKey in visibleIds }
            .map { edge ->
                SelectedGraphEdge(
                    fromThreadKey = edge.fromThreadKey,
                    toThreadKey = edge.toThreadKey,
                    strength = edge.strength,
                    reasonLine = edge.reasonLine,
                )
            }
        else -> snapshot.graph.edges
            .filter { it.fromThreadKey in visibleIds && it.toThreadKey in visibleIds }
            .sortedWith(compareByDescending<com.mindflow.app.data.wiki.DirectionWikiGraphEdge> { it.strength }.thenByDescending { it.confidence })
            .take(4)
            .map { edge ->
                SelectedGraphEdge(
                    fromThreadKey = edge.fromThreadKey,
                    toThreadKey = edge.toThreadKey,
                    strength = edge.strength,
                    reasonLine = edge.reasonLine,
                )
            }
    }

    return SelectedGraphData(
        headline = presentation.headline.ifBlank {
            buildString {
                append("${selectedNodes.size} 个主题")
                if (selectedEdges.isNotEmpty()) append(" · ${selectedEdges.size} 条关系")
            }
        },
        summaryLine = presentation.summaryLine.ifBlank { snapshot.graph.overview.summaryLine },
        hubNodeIds = snapshot.graph.overview.hubThreadKeys.filter { it in visibleIds }.toSet(),
        isolatedNodeIds = snapshot.graph.overview.isolatedThreadKeys.filter { it in visibleIds }.toSet(),
        nodes = selectedNodes,
        edges = selectedEdges,
    )
}

internal fun projectPureGraphInfo(
    snapshot: DirectionWikiSnapshot,
    selection: SelectedGraphData,
): GraphProjection {
    if (selection.nodes.isEmpty()) {
        return GraphProjection(
            headline = selection.headline,
            summaryLine = selection.summaryLine,
            hubNodeIds = emptySet(),
            isolatedNodeIds = emptySet(),
            nodes = emptyList(),
            edges = emptyList(),
        )
    }

    val relationCountByThread = buildMap {
        selection.edges.forEach { edge ->
            put(edge.fromThreadKey, getOrDefault(edge.fromThreadKey, 0) + 1)
            put(edge.toThreadKey, getOrDefault(edge.toThreadKey, 0) + 1)
        }
    }
    val projectionNodes = selection.nodes
        .map { node ->
            val direction = snapshot.directions[node.threadKey]
            GraphProjectionNode(
                threadKey = node.threadKey,
                label = node.label,
                summaryLine = node.summaryLine
                    .ifBlank { direction?.let(::graphSummaryLine).orEmpty() }
                    .ifBlank { "这条主题正在继续长。" },
                densityScore = node.densityScore,
                maturity = node.maturity,
                noteCount = node.noteCount,
                relationCount = relationCountByThread[node.threadKey] ?: 0,
            )
        }
        .sortedWith(
            compareByDescending<GraphProjectionNode> { it.relationCount }
                .thenByDescending { it.densityScore }
                .thenBy { it.label.length },
        )
    val nodeIds = projectionNodes.map { it.threadKey }.toSet()
    val projectionEdges = selection.edges
        .filter { it.fromThreadKey in nodeIds && it.toThreadKey in nodeIds }
        .map { edge ->
            GraphProjectionEdge(
                fromThreadKey = edge.fromThreadKey,
                toThreadKey = edge.toThreadKey,
                strength = edge.strength.coerceIn(1, 5),
                reasonLine = edge.reasonLine,
            )
        }

    return GraphProjection(
        headline = selection.headline,
        summaryLine = selection.summaryLine,
        hubNodeIds = selection.hubNodeIds.filter { it in nodeIds }.toSet(),
        isolatedNodeIds = selection.isolatedNodeIds.filter { it in nodeIds }.toSet(),
        nodes = projectionNodes,
        edges = projectionEdges,
    )
}

internal fun buildGraphVisualState(
    directions: Map<String, DirectionWikiDirectionSummary>,
    projection: GraphProjection,
): GraphOverviewUi {
    if (projection.nodes.isEmpty()) {
        return GraphOverviewUi(
            headline = "还没有足够结构",
            verdictLine = "",
            hubNodeIds = emptySet(),
            isolatedNodeIds = emptySet(),
            nodes = emptyList(),
            edges = emptyList(),
        )
    }

    val nodes = projection.nodes
        .map { node ->
            val direction = directions[node.threadKey]
            val structureStatus = graphStructureStatus(
                threadKey = node.threadKey,
                relationCount = node.relationCount,
                hubNodeIds = projection.hubNodeIds,
                isolatedNodeIds = projection.isolatedNodeIds,
            )
            GraphNodeUi(
                id = node.threadKey,
                label = node.label,
                summaryLine = node.summaryLine,
                threadKey = node.threadKey,
                structureStatus = structureStatus,
                accent = direction?.let(::graphDirectionAccent)
                    ?: graphNodeAccent(node.label, node.summaryLine),
                densityScore = node.densityScore,
                maturity = node.maturity,
                noteCount = node.noteCount,
                relationCount = node.relationCount,
                priority = graphNodePriority(
                    densityScore = node.densityScore,
                    maturity = node.maturity,
                    relationCount = node.relationCount,
                ),
            )
        }
        .sortedWith(
            compareByDescending<GraphNodeUi> { it.priority }
                .thenByDescending { it.relationCount }
                .thenByDescending { it.densityScore }
                .thenBy { it.label.length },
        )
    val nodeIds = nodes.map { it.id }.toSet()
    val edges = projection.edges
        .filter { it.fromThreadKey in nodeIds && it.toThreadKey in nodeIds }
        .map { edge ->
            GraphEdgeUi(
                fromId = edge.fromThreadKey,
                toId = edge.toThreadKey,
                weight = edge.strength,
                reasonLine = edge.reasonLine,
            )
        }
    val verdictLine = buildGraphVerdict(
        nodes = nodes,
        edges = edges,
        hubNodeIds = projection.hubNodeIds,
        isolatedNodeIds = projection.isolatedNodeIds,
    ).ifBlank {
        projection.summaryLine.ifBlank { "几条主题正在慢慢长出结构。" }
    }

    return GraphOverviewUi(
        headline = projection.headline,
        verdictLine = verdictLine,
        hubNodeIds = projection.hubNodeIds,
        isolatedNodeIds = projection.isolatedNodeIds,
        nodes = nodes,
        edges = edges,
    )
}

private fun graphStructureStatus(
    threadKey: String,
    relationCount: Int,
    hubNodeIds: Set<String>,
    isolatedNodeIds: Set<String>,
): GraphStructureStatus =
    when {
        threadKey in hubNodeIds -> GraphStructureStatus.HUB
        threadKey in isolatedNodeIds -> GraphStructureStatus.ISOLATED
        relationCount > 0 -> GraphStructureStatus.LINKED
        else -> GraphStructureStatus.EMERGING
    }

internal fun buildActivatedGraphNodes(
    notes: List<NoteEntity>,
    graphNodes: List<GraphNodeUi>,
): List<GraphNodeUi> {
    if (notes.isEmpty() || graphNodes.isEmpty()) return emptyList()
    val nodeById = graphNodes.associateBy { it.id }
    val counts = linkedMapOf<String, Int>()
    notes.forEach { note ->
        note.folderKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { key ->
                counts["folder:$key"] = counts.getOrDefault("folder:$key", 0) + 1
            }
        note.tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { tag ->
                val threadKey = "tag:$tag"
                counts[threadKey] = counts.getOrDefault(threadKey, 0) + 1
            }
    }
    val directMatches = counts.entries
        .mapNotNull { (threadKey, count) ->
            nodeById[threadKey]?.let { node -> node to count }
        }
        .sortedWith(compareByDescending<Pair<GraphNodeUi, Int>> { it.second }.thenByDescending { it.first.relationCount })
        .map { it.first }
        .distinctBy { it.id }
    return directMatches.take(3)
}

internal fun buildGraphVerdict(
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
                "几条主题正在慢慢长出结构。"
            }
        } ?: "几条主题正在慢慢长出结构。"
    } + "。"
}

private fun compactDirectionLabel(raw: String): String {
    val cleaned = raw
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    if (cleaned.isBlank()) return "未命名"
    val firstSegment = cleaned.split('｜', '|', '·', ':', '：', '，', ',', '。', '、')
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    val withoutPrefix = firstSegment.replace(
        Regex("^(方向|主线|结论|证据|方法|问题|概念|实验|项目|主题)\\s*"),
        "",
    ).trim().ifBlank { firstSegment }
    return if (withoutPrefix.any { it.isWhitespace() }) {
        withoutPrefix.split(Regex("\\s+")).take(2).joinToString(" ")
    } else {
        withoutPrefix.take(8)
    }
}

private fun classifyGraphBucket(texts: List<String>): GraphDomainBucket {
    val haystack = texts.joinToString(" ").lowercase()
    val scoredBuckets = GraphDomainBucket.values()
        .filter { it != GraphDomainBucket.OTHER }
        .associateWith { bucket ->
            bucket.keywords.count { keyword -> haystack.contains(keyword.lowercase()) }
        }
    val bestMatch = scoredBuckets.maxByOrNull { it.value }
    return if (bestMatch != null && bestMatch.value > 0) bestMatch.key else GraphDomainBucket.OTHER
}

private fun graphSummaryLine(direction: DirectionWikiDirectionSummary): String =
    direction.conclusionLine
        .ifBlank { direction.assetSummary }
        .ifBlank { direction.continuityLine }
        .ifBlank { direction.healthLine }
        .ifBlank { "这条主题还在慢慢成形。" }

private fun DirectionStage.toGraphMaturity(): DirectionWikiGraphMaturity =
    when (this) {
        DirectionStage.FORMING -> DirectionWikiGraphMaturity.FORMING
        DirectionStage.VALIDATING,
        DirectionStage.ADVANCING,
        -> DirectionWikiGraphMaturity.STRENGTHENING
        DirectionStage.SETTLING -> DirectionWikiGraphMaturity.STABLE
    }

private fun graphDirectionAccent(
    direction: DirectionWikiDirectionSummary,
): Color {
    val folderAccent = direction.threadKey
        .takeIf { it.startsWith("folder:") }
        ?.removePrefix("folder:")
        ?.trim()
        ?.let(MindFolderCatalog::fromKey)
        ?.let { Color(android.graphics.Color.parseColor(it.colorHex)) }
    if (folderAccent != null) return folderAccent
    return classifyGraphBucket(
        listOf(
            direction.title,
            direction.assetSummary,
            direction.conclusionLine,
            direction.knowledgeObjectLine,
        ),
    ).accent
}

private fun graphNodeAccent(
    label: String,
    summaryLine: String,
): Color = classifyGraphBucket(
    listOf(label, summaryLine),
).accent

private fun graphNodePriority(
    densityScore: Double,
    maturity: DirectionWikiGraphMaturity,
    relationCount: Int,
): Int {
    val maturityScore = when (maturity) {
        DirectionWikiGraphMaturity.STABLE -> 5
        DirectionWikiGraphMaturity.STRENGTHENING -> 4
        DirectionWikiGraphMaturity.FORMING -> 3
    }
    val densityBoost = when {
        densityScore >= 0.8 -> 2
        densityScore >= 0.55 -> 1
        else -> 0
    }
    return (maturityScore + densityBoost + relationCount.coerceAtMost(2)).coerceIn(1, 5)
}

private fun graphNodeWidth(
    node: GraphNodeUi,
    selected: Boolean,
): androidx.compose.ui.unit.Dp =
    when {
        selected -> 116.dp
        node.priority >= 4 -> 104.dp
        node.relationCount >= 2 || node.noteCount >= 5 -> 100.dp
        else -> 88.dp
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
    val ordered = nodes.sortedWith(
        compareByDescending<GraphNodeUi> { it.priority }
            .thenByDescending { it.relationCount }
            .thenByDescending { it.noteCount }
            .thenBy { it.label },
    )
    val degreeByNode = buildMap {
        edges.forEach { edge ->
            put(edge.fromId, getOrDefault(edge.fromId, 0) + 1)
            put(edge.toId, getOrDefault(edge.toId, 0) + 1)
        }
    }
    val centerX = widthPx / 2f
    val centerY = heightPx / 2f
    if (ordered.size == 1) {
        return listOf(GraphNodeLayout(ordered.first(), centerX to centerY))
    }
    val primaryHub = ordered
        .firstOrNull { it.id in hubNodeIds }
        ?: ordered.firstOrNull { (degreeByNode[it.id] ?: 0) > 0 }
        ?: ordered.first()
    val strongestEdge = edges.maxByOrNull { it.weight }
    val secondaryHub = ordered.firstOrNull { node ->
        node.id != primaryHub.id && (
            node.id in hubNodeIds ||
                node.id == strongestEdge?.fromId ||
                node.id == strongestEdge?.toId
            )
    }

    val positions = linkedMapOf<String, Pair<Float, Float>>()
    val centerGap = min(widthPx, heightPx) * 0.16f
    val primaryPosition = if (secondaryHub != null) (centerX - centerGap) to centerY else centerX to centerY
    positions[primaryHub.id] = primaryPosition
    secondaryHub?.let {
        positions[it.id] = (centerX + centerGap) to (centerY - 8f)
    }

    val isolated = ordered.filter { it.id in isolatedNodeIds && it.id !in positions.keys }
    val connected = ordered.filter { it.id !in positions.keys && it.id !in isolated.map { item -> item.id }.toSet() }

    val leftOffsets = listOf(
        (-138f to -84f),
        (-132f to 82f),
    )
    val rightOffsets = listOf(
        (138f to -74f),
        (132f to 88f),
    )
    val centerOffsets = listOf(
        (0f to -128f),
        (0f to 128f),
    )

    fun anchorFor(node: GraphNodeUi): Pair<Float, Float> {
        if (secondaryHub == null) return primaryPosition
        val leftStrength = edges.firstOrNull {
            (it.fromId == primaryHub.id && it.toId == node.id) || (it.toId == primaryHub.id && it.fromId == node.id)
        }?.weight ?: 0
        val rightStrength = edges.firstOrNull {
            (it.fromId == secondaryHub.id && it.toId == node.id) || (it.toId == secondaryHub.id && it.fromId == node.id)
        }?.weight ?: 0
        return if (leftStrength >= rightStrength) primaryPosition else positions[secondaryHub.id] ?: primaryPosition
    }

    connected.forEachIndexed { index, node ->
        val anchor = anchorFor(node)
        val offset = when {
            secondaryHub == null -> centerOffsets.getOrElse(index % centerOffsets.size) { 0f to 0f }
            anchor == primaryPosition -> leftOffsets.getOrElse(index % leftOffsets.size) { -126f to 0f }
            else -> rightOffsets.getOrElse(index % rightOffsets.size) { 126f to 0f }
        }
        positions[node.id] = clamp(anchor.first + offset.first, 54f, widthPx - 54f) to
            clamp(anchor.second + offset.second, 48f, heightPx - 48f)
    }

    val isolatedOffsets = listOf(
        (0f to -144f),
        (0f to 144f),
        (-156f to 0f),
        (156f to 0f),
    )
    isolated.forEachIndexed { index, node ->
        val offset = isolatedOffsets.getOrElse(index % isolatedOffsets.size) { 0f to 0f }
        positions[node.id] = clamp(centerX + offset.first, 54f, widthPx - 54f) to
            clamp(centerY + offset.second, 48f, heightPx - 48f)
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
