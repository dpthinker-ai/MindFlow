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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiGraphNode
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private data class GraphNodeUi(
    val id: String,
    val label: String,
    val summaryLine: String,
    val gapLine: String,
    val threadKey: String,
    val accent: Color = AccentBlue,
    val noteCount: Int = 0,
    val relationCount: Int = 0,
    val priority: Int = 3,
)

private data class GraphEdgeUi(
    val fromId: String,
    val toId: String,
    val weight: Int,
)

private data class GraphOverviewUi(
    val totalNodeCount: Int,
    val visibleNodeCount: Int,
    val nodes: List<GraphNodeUi>,
    val edges: List<GraphEdgeUi>,
)

private data class GraphNodeLayout(
    val node: GraphNodeUi,
    val position: Pair<Float, Float>,
)

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
        onOpenThread = onOpenThread,
        onOpenNote = onOpenNote,
    )
}

@androidx.compose.runtime.Composable
private fun KnowledgeGraphScreen(
    snapshot: DirectionWikiSnapshot,
    notes: List<NoteEntity>,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember(zoneId) { LocalDate.now(zoneId) }
    val activeNotes = remember(notes) { notes.filterNot { it.isArchived } }
    val graphOverview = remember(snapshot, activeNotes) { buildGraphOverview(snapshot, activeNotes) }
    var selectedNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(graphOverview.nodes) {
        if (selectedNodeId !in graphOverview.nodes.map { it.id }.toSet()) {
            selectedNodeId = graphOverview.nodes.firstOrNull()?.id
        }
    }
    val selectedGraphNode = remember(graphOverview, selectedNodeId) {
        graphOverview.nodes.firstOrNull { it.id == selectedNodeId } ?: graphOverview.nodes.firstOrNull()
    }
    val relatedGraphNode = remember(graphOverview, selectedGraphNode) {
        selectedGraphNode?.let { node ->
            graphOverview.edges
                .filter { it.fromId == node.id || it.toId == node.id }
                .maxByOrNull { it.weight }
                ?.let { edge ->
                    val relatedId = if (edge.fromId == node.id) edge.toId else edge.fromId
                    graphOverview.nodes.firstOrNull { it.id == relatedId }
                }
        }
    }
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
                                        "点某一天，回看当天真正有变化的记录。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
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

                item {
                    PanelCard {
                        SectionHeader(
                            title = "信息图谱",
                            headline = if (graphOverview.nodes.isNotEmpty()) {
                                buildString {
                                    append("${graphOverview.visibleNodeCount} 个主题")
                                    if (graphOverview.edges.isNotEmpty()) {
                                        append(" · ${graphOverview.edges.size} 条关系")
                                    }
                                }
                            } else {
                                "还没有足够结构"
                            },
                        )
                        if (graphOverview.nodes.isEmpty()) {
                            Text(
                                text = "先让记录继续积累，这里会慢慢长出你的信息图谱。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSoft,
                            )
                        } else {
                            KnowledgeGraphCanvas(
                                overview = graphOverview,
                                selectedNodeId = selectedGraphNode?.id,
                                onSelectNode = { selectedNodeId = it },
                            )
                            selectedGraphNode?.let { node ->
                                GraphFocusCard(
                                    node = node,
                                    relatedNode = relatedGraphNode,
                                    onOpenThread = onOpenThread,
                                )
                            }
                            if (graphOverview.edges.isEmpty()) {
                                Text(
                                    text = "这些主题已经出现，但彼此关系还不够清楚，先继续记录，关系会慢慢长出来。",
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
            .background(Color.Transparent),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val layouts = buildGraphNodeLayouts(
            nodes = nodes,
            widthPx = widthPx,
            heightPx = heightPx,
        )
        val positionById = layouts.associate { it.node.id to it.position }

        Canvas(modifier = Modifier.fillMaxSize()) {
            overview.edges.forEach { edge ->
                val from = positionById[edge.fromId] ?: return@forEach
                val to = positionById[edge.toId] ?: return@forEach
                val highlighted = selectedNodeId != null && (edge.fromId == selectedNodeId || edge.toId == selectedNodeId)
                val edgeColor = if (highlighted) AccentBlue.copy(alpha = 0.34f) else BorderSoft.copy(alpha = 0.72f)
                drawLine(
                    color = edgeColor,
                    start = androidx.compose.ui.geometry.Offset(from.first, from.second),
                    end = androidx.compose.ui.geometry.Offset(to.first, to.second),
                    strokeWidth = if (highlighted) 4f else (1.6f + edge.weight.coerceAtMost(6) * 0.35f),
                )
            }
        }

        layouts.forEach { layout ->
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
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) node.accent.copy(alpha = 0.14f) else WhiteGlass.copy(alpha = 0.92f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, node.accent.copy(alpha = if (selected) 0.72f else 0.35f)),
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
private fun GraphFocusCard(
    node: GraphNodeUi,
    relatedNode: GraphNodeUi?,
    onOpenThread: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenThread(node.threadKey) },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
            )
            Text(
                text = node.summaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relatedNode?.let { "最相关：${it.label}" } ?: "这条主题目前还比较独立。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "下一步：${node.gapLine}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "打开这条主题",
                style = MaterialTheme.typography.labelLarge,
                color = AccentBlue,
            )
        }
    }
}

private fun buildGraphOverview(
    snapshot: DirectionWikiSnapshot,
    notes: List<NoteEntity>,
): GraphOverviewUi {
    if (snapshot.graph.nodes.isNotEmpty()) {
        return buildAiGraphOverview(snapshot, notes)
    }
    val directions = snapshot.directions.values
        .sortedByDescending { it.updatedAt }
        .take(6)
    if (directions.isEmpty()) return GraphOverviewUi(
        totalNodeCount = 0,
        visibleNodeCount = 0,
        nodes = emptyList(),
        edges = emptyList(),
    )
    val notesByThread = directions.associate { direction ->
        direction.threadKey to NoteConnectionAnalyzer.notesForThread(direction.threadKey, notes)
    }
    val edges = buildGraphEdges(
        directions = directions,
        notesByThread = notesByThread,
    )
    val relationCountByThread = buildMap {
        edges.forEach { edge ->
            put(edge.fromId, getOrDefault(edge.fromId, 0) + 1)
            put(edge.toId, getOrDefault(edge.toId, 0) + 1)
        }
    }
    val nodes = directions
        .map { direction ->
            val threadNotes = notesByThread[direction.threadKey].orEmpty()
            GraphNodeUi(
                id = direction.threadKey,
                label = compactDirectionLabel(direction.title),
                summaryLine = graphSummaryLine(direction),
                gapLine = graphGapLine(direction),
                threadKey = direction.threadKey,
                accent = graphDirectionAccent(direction, threadNotes),
                noteCount = threadNotes.size,
                relationCount = relationCountByThread[direction.threadKey] ?: 0,
                priority = ((relationCountByThread[direction.threadKey] ?: 0) + threadNotes.size.coerceAtMost(4)).coerceIn(1, 5),
            )
        }
        .sortedWith(
            compareByDescending<GraphNodeUi> { it.priority }
                .thenByDescending { it.relationCount }
                .thenByDescending { it.noteCount }
                .thenBy { it.label.length },
        )
    return GraphOverviewUi(
        totalNodeCount = snapshot.directions.size,
        visibleNodeCount = nodes.size,
        nodes = nodes,
        edges = edges,
    )
}

private fun buildAiGraphOverview(
    snapshot: DirectionWikiSnapshot,
    notes: List<NoteEntity>,
): GraphOverviewUi {
    val summariesByThread = snapshot.directions
    val graphNodes = snapshot.graph.nodes
        .distinctBy { it.threadKey }
        .take(6)
    if (graphNodes.isEmpty()) {
        return GraphOverviewUi(
            totalNodeCount = 0,
            visibleNodeCount = 0,
            nodes = emptyList(),
            edges = emptyList(),
        )
    }
    val relationCountByThread = buildMap {
        snapshot.graph.edges.forEach { edge ->
            put(edge.fromThreadKey, getOrDefault(edge.fromThreadKey, 0) + 1)
            put(edge.toThreadKey, getOrDefault(edge.toThreadKey, 0) + 1)
        }
    }
    val nodes = graphNodes
        .map { node ->
            val direction = summariesByThread[node.threadKey]
            val threadNotes = NoteConnectionAnalyzer.notesForThread(node.threadKey, notes)
            GraphNodeUi(
                id = node.threadKey,
                label = node.label,
                summaryLine = node.summaryLine.ifBlank {
                    direction?.let(::graphSummaryLine).orEmpty()
                }.ifBlank { "这条主题正在继续长。"},
                gapLine = node.gapLine.ifBlank {
                    direction?.let(::graphGapLine).orEmpty()
                }.ifBlank { "继续补一条更硬的新材料。"},
                threadKey = node.threadKey,
                accent = direction?.let { graphDirectionAccent(it, threadNotes) }
                    ?: graphNodeAccent(node, threadNotes),
                noteCount = threadNotes.size,
                relationCount = relationCountByThread[node.threadKey] ?: 0,
                priority = node.priority.coerceIn(1, 5),
            )
        }
        .sortedWith(
            compareByDescending<GraphNodeUi> { it.priority }
                .thenByDescending { it.relationCount }
                .thenByDescending { it.noteCount }
                .thenBy { it.label.length },
        )
    val visibleIds = nodes.map { it.id }.toSet()
    val edges = snapshot.graph.edges
        .filter { it.fromThreadKey in visibleIds && it.toThreadKey in visibleIds }
        .map { edge ->
            GraphEdgeUi(
                fromId = edge.fromThreadKey,
                toId = edge.toThreadKey,
                weight = edge.strength.coerceIn(1, 5),
            )
        }
    return GraphOverviewUi(
        totalNodeCount = snapshot.graph.nodes.size,
        visibleNodeCount = nodes.size,
        nodes = nodes,
        edges = edges,
    )
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

private fun buildGraphEdges(
    directions: List<DirectionWikiDirectionSummary>,
    notesByThread: Map<String, List<NoteEntity>>,
): List<GraphEdgeUi> {
    val edgeWeights = mutableMapOf<Pair<String, String>, Int>()
    directions.forEachIndexed { index, left ->
        directions.drop(index + 1).forEach { right ->
            val score = scoreGraphRelation(
                leftNotes = notesByThread[left.threadKey].orEmpty(),
                rightNotes = notesByThread[right.threadKey].orEmpty(),
            )
            if (score > 0) {
                edgeWeights[left.threadKey to right.threadKey] = score
            }
        }
    }
    return edgeWeights.entries
        .sortedByDescending { it.value }
        .take(8)
        .map { (key, score) ->
            GraphEdgeUi(
                fromId = key.first,
                toId = key.second,
                weight = score,
            )
        }
}

private fun scoreGraphRelation(
    leftNotes: List<NoteEntity>,
    rightNotes: List<NoteEntity>,
): Int {
    if (leftNotes.isEmpty() || rightNotes.isEmpty()) return 0
    val leftTags = leftNotes.flatMap { it.tags }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val rightTags = rightNotes.flatMap { it.tags }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val sharedTags = leftTags.intersect(rightTags).size

    val leftTokens = leftNotes
        .flatMap { note -> graphTokens(note.topic) + graphTokens(note.content.take(160)) }
        .toSet()
    val rightTokens = rightNotes
        .flatMap { note -> graphTokens(note.topic) + graphTokens(note.content.take(160)) }
        .toSet()
    val sharedTokens = leftTokens.intersect(rightTokens)
        .filterNot { token -> token.length <= 1 || token in graphStopTokens }
        .size

    return (sharedTags * 4) + sharedTokens.coerceAtMost(5)
}

private fun graphTokens(raw: String): Set<String> =
    Regex("[\\p{IsHan}]{2,}|[a-z0-9]{3,}")
        .findAll(raw.lowercase())
        .map { it.value.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun graphSummaryLine(direction: DirectionWikiDirectionSummary): String =
    direction.conclusionLine
        .ifBlank { direction.assetSummary }
        .ifBlank { direction.continuityLine }
        .ifBlank { direction.healthLine }
        .ifBlank { "这条主题还在慢慢成形。" }

private fun graphGapLine(direction: DirectionWikiDirectionSummary): String =
    direction.openQuestions.firstOrNull()
        .orEmpty()
        .ifBlank { direction.maintenanceFocusLine }
        .ifBlank { direction.maintenanceTargetLine }
        .ifBlank { "再补一条新记录，让这条主题继续长。" }

private fun graphDirectionAccent(
    direction: DirectionWikiDirectionSummary,
    threadNotes: List<NoteEntity>,
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
        ) + threadNotes.flatMap { note -> listOf(note.topic, note.content.take(140)) },
    ).accent
}

private fun graphNodeAccent(
    node: DirectionWikiGraphNode,
    threadNotes: List<NoteEntity>,
): Color = classifyGraphBucket(
    listOf(node.label, node.summaryLine, node.gapLine) + threadNotes.flatMap { note -> listOf(note.topic, note.content.take(140)) },
).accent

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
    val centerX = widthPx / 2f
    val centerY = heightPx / 2f
    if (ordered.size == 1) {
        return listOf(GraphNodeLayout(ordered.first(), centerX to centerY))
    }
    if (ordered.size == 2) {
        val spread = min(widthPx, heightPx) * 0.22f
        return listOf(
            GraphNodeLayout(ordered[0], (centerX - spread) to centerY),
            GraphNodeLayout(ordered[1], (centerX + spread) to centerY),
        )
    }

    val layouts = mutableListOf<GraphNodeLayout>()
    val centerNode = ordered.first()
    layouts += GraphNodeLayout(centerNode, centerX to centerY)
    val outerNodes = ordered.drop(1)
    val baseRadius = min(widthPx, heightPx) * 0.34f
    outerNodes.forEachIndexed { index, node ->
        val angle = (-PI / 2.0) + (2 * PI * index / outerNodes.size.coerceAtLeast(1))
        val inwardBias = (node.priority.coerceAtMost(5) * 8f) + (node.relationCount.coerceAtMost(3) * 10f)
        val radiusX = baseRadius - inwardBias
        val radiusY = (baseRadius * 0.78f) - inwardBias
        val x = clamp(centerX + cos(angle).toFloat() * radiusX, 48f, widthPx - 48f)
        val y = clamp(centerY + sin(angle).toFloat() * radiusY, 42f, heightPx - 42f)
        layouts += GraphNodeLayout(node, x to y)
    }
    return layouts
}

private val graphStopTokens = setOf(
    "这个",
    "那个",
    "因为",
    "所以",
    "我们",
    "自己",
    "已经",
    "现在",
    "需要",
    "继续",
    "主题",
    "记录",
    "问题",
    "方法",
    "实验",
)

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
