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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import com.mindflow.app.data.wiki.KnowledgeLayerSearchType
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
    val threadKey: String? = null,
    val noteId: Long? = null,
    val accent: Color = AccentBlue,
)

private data class GraphClusterUi(
    val direction: GraphNodeUi,
    val children: List<GraphNodeUi>,
)

private data class GraphOverviewUi(
    val totalNodeCount: Int,
    val visibleNodeCount: Int,
    val clusters: List<GraphClusterUi>,
)

private data class GraphClusterLayout(
    val cluster: GraphClusterUi,
    val directionPosition: Pair<Float, Float>,
    val childPositions: List<Pair<GraphNodeUi, Pair<Float, Float>>>,
)

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
    val graphOverview = remember(snapshot) { buildGraphOverview(snapshot) }
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
                            text = "先看热度，再看它们怎么连。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "记忆记录热力",
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
                            title = "完整图谱",
                            headline = if (graphOverview.clusters.isNotEmpty()) {
                                "先看 ${graphOverview.visibleNodeCount} 个关键词"
                            } else {
                                "还没有足够节点"
                            },
                        )
                        if (graphOverview.clusters.isEmpty()) {
                            Text(
                                text = "先让 Flow 继续维护一段时间，这里才会长出可读的方向、概念、结论和证据网络。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSoft,
                            )
                        } else {
                            KnowledgeGraphCanvas(
                                overview = graphOverview,
                                onOpenThread = onOpenThread,
                                onOpenNote = onOpenNote,
                            )
                            Text(
                                text = "点一个关键词，继续看对应记录或主题。",
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
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val clusters = overview.clusters
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(560.dp)
            .background(Color.Transparent),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centerX = widthPx / 2f
        val centerY = heightPx * 0.48f
        val ringBase = widthPx.coerceAtMost(heightPx)
        val directionRadius = ringBase * 0.24f
        val childRadius = ringBase * 0.40f
        val horizontalPadding = with(density) { 56.dp.toPx() }
        val verticalPadding = with(density) { 64.dp.toPx() }
        val layouts = clusters.mapIndexed { index, cluster ->
            val sectorSize = (2 * PI) / clusters.size.coerceAtLeast(1)
            val angle = (-PI / 2.0) + (sectorSize * index)
            val directionX = clamp(
                centerX + cos(angle).toFloat() * directionRadius,
                horizontalPadding,
                widthPx - horizontalPadding,
            )
            val directionY = clamp(
                centerY + sin(angle).toFloat() * directionRadius,
                verticalPadding,
                heightPx - verticalPadding,
            )
            val childPositions = cluster.children.mapIndexed { childIndex, child ->
                val childSpread = when (cluster.children.size) {
                    0, 1 -> 0.0
                    else -> min(sectorSize * 0.36, 0.72)
                }
                val childAngle = if (cluster.children.size <= 1) {
                    angle
                } else {
                    angle - (childSpread / 2.0) +
                        (childSpread * childIndex / (cluster.children.size - 1).coerceAtLeast(1))
                }
                val childX = clamp(
                    centerX + cos(childAngle).toFloat() * childRadius,
                    horizontalPadding,
                    widthPx - horizontalPadding,
                )
                val childY = clamp(
                    centerY + sin(childAngle).toFloat() * childRadius,
                    verticalPadding,
                    heightPx - verticalPadding,
                )
                child to Pair(childX, childY)
            }
            GraphClusterLayout(
                cluster = cluster,
                directionPosition = Pair(directionX, directionY),
                childPositions = childPositions,
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Accent.copy(alpha = 0.16f),
                radius = size.minDimension * 0.24f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 2.4f),
            )
            drawCircle(
                color = AccentBlue.copy(alpha = 0.12f),
                radius = size.minDimension * 0.40f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 2f),
            )
            layouts.forEach { layout ->
                layout.childPositions.forEach { (_, childPos) ->
                    drawLine(
                        color = layout.cluster.direction.accent.copy(alpha = 0.24f),
                        start = androidx.compose.ui.geometry.Offset(
                            layout.directionPosition.first,
                            layout.directionPosition.second,
                        ),
                        end = androidx.compose.ui.geometry.Offset(childPos.first, childPos.second),
                        strokeWidth = 1.8f,
                    )
                }
            }
        }

        layouts.forEach { layout ->
            GraphNodeBubble(
                node = layout.cluster.direction,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (layout.directionPosition.first - 56.dpToPx(density)).roundToInt(),
                        y = (layout.directionPosition.second - 22.dpToPx(density)).roundToInt(),
                    )
                },
                onOpenThread = onOpenThread,
                onOpenNote = onOpenNote,
                width = 112.dp,
            )
            layout.childPositions.forEach { (node, pos) ->
                GraphNodeBubble(
                    node = node,
                    modifier = Modifier.offset {
                        IntOffset(
                            x = (pos.first - 44.dpToPx(density)).roundToInt(),
                            y = (pos.second - 20.dpToPx(density)).roundToInt(),
                        )
                    },
                    onOpenThread = onOpenThread,
                    onOpenNote = onOpenNote,
                    compact = true,
                    width = 88.dp,
                )
            }
        }
    }
}

private fun Int.dpToPx(density: Density): Float = with(density) { this@dpToPx.dp.toPx() }

@androidx.compose.runtime.Composable
private fun GraphNodeBubble(
    node: GraphNodeUi,
    modifier: Modifier,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    compact: Boolean = false,
    width: androidx.compose.ui.unit.Dp,
) {
    Surface(
        modifier = modifier.clickable {
            node.noteId?.let(onOpenNote) ?: node.threadKey?.let(onOpenThread)
        },
        color = WhiteGlass.copy(alpha = if (compact) 0.86f else 0.92f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, node.accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .width(width)
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 8.dp else 10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(node.accent.copy(alpha = 0.88f)),
            )
            Text(
                text = node.label,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildGraphOverview(snapshot: DirectionWikiSnapshot): GraphOverviewUi {
    val directions = snapshot.directions.values
        .sortedByDescending { it.updatedAt }
        .take(4)
    if (directions.isEmpty()) return GraphOverviewUi(
        totalNodeCount = 0,
        visibleNodeCount = 0,
        clusters = emptyList(),
    )

    val allDistinctNodeCount = snapshot.directions.size +
        snapshot.knowledgeItems
            .filter { it.type != KnowledgeLayerSearchType.DIRECTION }
            .distinctBy { it.id }
            .size
    val clusters = mutableListOf<GraphClusterUi>()
    var visibleNodeCount = 0
    directions.forEach { direction ->
        val children = snapshot.knowledgeItems
            .filter { it.threadKey == direction.threadKey }
            .filter { it.type != KnowledgeLayerSearchType.DIRECTION }
            .sortedWith(
                compareBy<KnowledgeLayerSearchItem> { graphTypePriority(it.type) }
                    .thenByDescending { it.updatedAt },
            )
            .take(3)
            .map { item ->
                GraphNodeUi(
                    id = item.id,
                    label = compactGraphLabel(item.title.ifBlank { item.summary }),
                    threadKey = item.threadKey.takeIf { it.isNotBlank() },
                    noteId = item.noteId,
                    accent = when (item.type) {
                        KnowledgeLayerSearchType.CONCLUSION -> Accent
                        KnowledgeLayerSearchType.EVIDENCE -> AccentBlue
                        else -> Accent.copy(alpha = 0.9f)
                    },
                )
            }
        clusters += GraphClusterUi(
            direction = GraphNodeUi(
                id = "direction:${direction.threadKey}",
                label = compactGraphLabel(direction.title),
                threadKey = direction.threadKey,
                accent = AccentBlue,
            ),
            children = children,
        )
        visibleNodeCount += 1 + children.size
    }
    return GraphOverviewUi(
        totalNodeCount = allDistinctNodeCount,
        visibleNodeCount = visibleNodeCount,
        clusters = clusters,
    )
}

private fun graphTypePriority(type: KnowledgeLayerSearchType): Int =
    when (type) {
        KnowledgeLayerSearchType.CONCLUSION -> 0
        KnowledgeLayerSearchType.EVIDENCE -> 1
        KnowledgeLayerSearchType.CONCEPT -> 2
        KnowledgeLayerSearchType.QUESTION -> 3
        KnowledgeLayerSearchType.METHOD -> 4
        KnowledgeLayerSearchType.EXPERIMENT -> 5
        KnowledgeLayerSearchType.DIRECTION -> 6
    }

private fun compactGraphLabel(raw: String): String {
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
