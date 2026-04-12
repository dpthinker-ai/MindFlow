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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindflow.app.data.model.NoteStats
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenancePlanner
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class GraphNodeUi(
    val id: String,
    val label: String,
    val subLabel: String,
    val threadKey: String? = null,
    val noteId: Long? = null,
    val accent: Color = AccentBlue,
)

private data class GraphDecisionCard(
    val title: String,
    val headline: String,
    val line: String,
    val support: String,
    val threadKey: String? = null,
    val noteId: Long? = null,
)

@androidx.compose.runtime.Composable
fun KnowledgeGraphRoute(
    noteRepository: NoteRepository,
    directionWikiCoordinator: DirectionWikiCoordinator,
    localKnowledgeMaintenancePlanner: LocalKnowledgeMaintenancePlanner,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val snapshot = directionWikiCoordinator.snapshot.collectAsStateWithLifecycle().value
    val maintainerSnapshot = localKnowledgeMaintenancePlanner.snapshot.collectAsStateWithLifecycle().value
    val noteStats = noteRepository.observeNoteStats().collectAsStateWithLifecycle(initialValue = NoteStats()).value
    KnowledgeGraphScreen(
        snapshot = snapshot,
        maintainerSnapshot = maintainerSnapshot,
        noteStats = noteStats,
        onBack = onBack,
        onOpenThread = onOpenThread,
        onOpenNote = onOpenNote,
    )
}

@androidx.compose.runtime.Composable
private fun KnowledgeGraphScreen(
    snapshot: DirectionWikiSnapshot,
    maintainerSnapshot: LocalKnowledgeMaintenanceSnapshot,
    noteStats: NoteStats,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val graphNodes = buildGraphNodes(snapshot)
    val decisionCards = rememberGraphDecisionCards(snapshot, maintainerSnapshot)
    val today = remember { LocalDate.now() }
    val availableYears = remember(noteStats.availableYears, today) {
        (noteStats.availableYears + today.year).distinct().sortedDescending()
    }
    var selectedYear by rememberSaveable { mutableIntStateOf(availableYears.firstOrNull() ?: today.year) }
    var selectedDateKey by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    val activityByDate = remember(noteStats.activityDays) {
        noteStats.activityDays.associate { it.date to it.count }
    }
    val selectedDate = selectedDateKey?.let(LocalDate::parse)
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
                            text = "先看哪里最密、哪里最孤、该补哪条边，再打开整张图。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                        Text(
                            text = "返回",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentBlue,
                            modifier = Modifier.clickable(onClick = onBack),
                        )
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "现在最值得看",
                            headline = if (decisionCards.isEmpty()) "等第一轮维护结果" else "先做判断，再看画布",
                        )
                        if (decisionCards.isEmpty()) {
                            Text(
                                text = "先让本地维护跑出一轮结果，这里会先回答当前枢纽、最孤的点和最缺的一条边。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSoft,
                            )
                        } else {
                            decisionCards.forEach { card ->
                                GraphDecisionSummaryCard(
                                    card = card,
                                    onOpenThread = onOpenThread,
                                    onOpenNote = onOpenNote,
                                )
                            }
                        }
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "记录热力",
                            headline = "${selectedYear} · ${noteStats.yearSummary(selectedYear)?.totalCount ?: 0} 条记录",
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
                                    peakCount = noteStats.yearSummary(selectedYear)?.peakCount ?: 0,
                                    selectedDate = selectedDate,
                                    onSelectDate = { date ->
                                        selectedDateKey = date.toString()
                                    },
                                )
                                Text(
                                    text = if (selectedDate != null) {
                                        "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · ${noteStats.activityCountOn(selectedDate)} 条记录"
                                    } else {
                                        "点某一天，回看当时你在高频记录什么。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSoft,
                                )
                            }
                        }
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "完整图谱",
                            headline = if (graphNodes.isNotEmpty()) "${graphNodes.size} 个节点" else "还没有足够节点",
                        )
                        if (graphNodes.isEmpty()) {
                            Text(
                                text = "先让 Flow 继续维护一段时间，这里才会长出可读的方向、概念、结论和证据网络。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSoft,
                            )
                        } else {
                            Text(
                                text = maintainerSnapshot.knowledgeShape.line.ifBlank {
                                    maintainerSnapshot.knowledgeInventoryLine.ifBlank {
                                        "当前图谱已经有 ${snapshot.directions.size} 条主题线和 ${snapshot.knowledgeItems.size} 个知识对象。"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSoft,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            KnowledgeGraphCanvas(
                                nodes = graphNodes,
                                onOpenThread = onOpenThread,
                                onOpenNote = onOpenNote,
                            )
                        }
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = "可继续打开的线",
                            headline = "${snapshot.directions.size} 条方向 · ${snapshot.knowledgeItems.size} 个知识对象",
                        )
                        snapshot.directions.values
                            .sortedByDescending { it.updatedAt }
                            .take(5)
                            .forEach { direction ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenThread(direction.threadKey) },
                                    color = WhiteGlass.copy(alpha = 0.78f),
                                    shape = CardShape,
                                    border = BorderStroke(1.dp, BorderSoft),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = direction.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = TextMain,
                                        )
                                        Text(
                                            text = direction.conclusionLine
                                                .ifBlank { direction.assetSummary }
                                                .ifBlank { direction.healthLine },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextMain,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = direction.trustLine
                                                .ifBlank { direction.groundingLine }
                                                .ifBlank { direction.maintenanceFocusLine },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSoft,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun GraphDecisionSummaryCard(
    card: GraphDecisionCard,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                card.noteId?.let(onOpenNote) ?: card.threadKey?.let(onOpenThread)
            },
        color = WhiteGlass.copy(alpha = 0.78f),
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.labelLarge,
                color = AccentBlue,
            )
            Text(
                text = card.headline,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.line,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMain,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            card.support.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun KnowledgeGraphCanvas(
    nodes: List<GraphNodeUi>,
    onOpenThread: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
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
        val centerY = heightPx / 2f
        val ringBase = widthPx.coerceAtMost(heightPx)
        val innerNodes = nodes.take(8)
        val middleNodes = nodes.drop(innerNodes.size).take(16)
        val outerNodes = nodes.drop(innerNodes.size + middleNodes.size).take(24)

        val innerPositions = innerNodes.mapIndexed { index, node ->
            val angle = (2 * PI * index / innerNodes.size.coerceAtLeast(1)).toFloat()
            val x = centerX + cos(angle) * ringBase * 0.20f
            val y = centerY + sin(angle) * ringBase * 0.18f
            node to Pair(x, y)
        }
        val middlePositions = middleNodes.mapIndexed { index, node ->
            val angle = (2 * PI * index / middleNodes.size.coerceAtLeast(1)).toFloat()
            val x = centerX + cos(angle) * ringBase * 0.34f
            val y = centerY + sin(angle) * ringBase * 0.31f
            node to Pair(x, y)
        }
        val outerPositions = outerNodes.mapIndexed { index, node ->
            val angle = (2 * PI * index / outerNodes.size.coerceAtLeast(1)).toFloat()
            val x = centerX + cos(angle) * ringBase * 0.47f
            val y = centerY + sin(angle) * ringBase * 0.43f
            node to Pair(x, y)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Accent.copy(alpha = 0.16f),
                radius = size.minDimension * 0.13f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 3f),
            )
            drawCircle(
                color = AccentBlue.copy(alpha = 0.12f),
                radius = size.minDimension * 0.26f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 2f),
            )
            drawCircle(
                color = Accent.copy(alpha = 0.10f),
                radius = size.minDimension * 0.39f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 2f),
            )
            innerPositions.forEach { (_, pos) ->
                drawLine(
                    color = AccentBlue.copy(alpha = 0.28f),
                    start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    end = androidx.compose.ui.geometry.Offset(pos.first, pos.second),
                    strokeWidth = 2.5f,
                )
            }
            middlePositions.forEachIndexed { index, (_, pos) ->
                val parent = innerPositions.getOrNull(index % innerPositions.size.coerceAtLeast(1))?.second
                    ?: Pair(centerX, centerY)
                drawLine(
                    color = Accent.copy(alpha = 0.22f),
                    start = androidx.compose.ui.geometry.Offset(parent.first, parent.second),
                    end = androidx.compose.ui.geometry.Offset(pos.first, pos.second),
                    strokeWidth = 2f,
                )
            }
            outerPositions.forEachIndexed { index, (_, pos) ->
                val parent = middlePositions.getOrNull(index % middlePositions.size.coerceAtLeast(1))?.second
                    ?: innerPositions.getOrNull(index % innerPositions.size.coerceAtLeast(1))?.second
                    ?: Pair(centerX, centerY)
                drawLine(
                    color = AccentBlue.copy(alpha = 0.16f),
                    start = androidx.compose.ui.geometry.Offset(parent.first, parent.second),
                    end = androidx.compose.ui.geometry.Offset(pos.first, pos.second),
                    strokeWidth = 1.6f,
                )
            }
        }

        Surface(
            color = WhiteGlass,
            shape = CardShape,
            border = BorderStroke(1.dp, BorderSoft),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("知识图谱", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextMain)
                Text("${nodes.size} 个节点", style = MaterialTheme.typography.labelSmall, color = TextSoft)
            }
        }

        innerPositions.forEach { (node, pos) ->
            GraphNodeBubble(
                node = node,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (pos.first - widthPx * 0.10f).roundToInt(),
                        y = (pos.second - 26.dpToPx(density)).roundToInt(),
                    )
                },
                onOpenThread = onOpenThread,
                onOpenNote = onOpenNote,
            )
        }
        middlePositions.forEach { (node, pos) ->
            GraphNodeBubble(
                node = node,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (pos.first - widthPx * 0.08f).roundToInt(),
                        y = (pos.second - 22.dpToPx(density)).roundToInt(),
                    )
                },
                onOpenThread = onOpenThread,
                onOpenNote = onOpenNote,
                compact = true,
            )
        }
        outerPositions.forEach { (node, pos) ->
            GraphNodeBubble(
                node = node,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (pos.first - widthPx * 0.07f).roundToInt(),
                        y = (pos.second - 18.dpToPx(density)).roundToInt(),
                    )
                },
                onOpenThread = onOpenThread,
                onOpenNote = onOpenNote,
                compact = true,
            )
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
) {
    Surface(
        modifier = modifier.clickable {
            node.noteId?.let(onOpenNote) ?: node.threadKey?.let(onOpenThread)
        },
        color = WhiteGlass.copy(alpha = if (compact) 0.86f else 0.92f),
        shape = CardShape,
        border = BorderStroke(1.dp, node.accent.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = node.label,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = node.subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildGraphNodes(snapshot: DirectionWikiSnapshot): List<GraphNodeUi> {
    val directions = snapshot.directions.values
        .sortedByDescending { it.updatedAt }
        .take(12)
    if (directions.isEmpty()) return emptyList()

    val nodes = mutableListOf<GraphNodeUi>()
    directions.forEach { direction ->
        nodes += GraphNodeUi(
            id = "direction:${direction.threadKey}",
            label = direction.title,
            subLabel = direction.stage.label,
            threadKey = direction.threadKey,
            accent = AccentBlue,
        )
        snapshot.knowledgeItems
            .filter { it.threadKey == direction.threadKey }
            .filter { it.type != KnowledgeLayerSearchType.DIRECTION }
            .sortedWith(
                compareBy<KnowledgeLayerSearchItem> { graphTypePriority(it.type) }
                    .thenByDescending { it.updatedAt },
            )
            .take(3)
            .forEach { item ->
                nodes += GraphNodeUi(
                    id = item.id,
                    label = item.title,
                    subLabel = item.type.label,
                    threadKey = item.threadKey.takeIf { it.isNotBlank() },
                    noteId = item.noteId,
                    accent = when (item.type) {
                        KnowledgeLayerSearchType.CONCLUSION -> Accent
                        KnowledgeLayerSearchType.EVIDENCE -> AccentBlue
                        else -> Accent.copy(alpha = 0.9f)
                    },
                )
            }
    }
    snapshot.knowledgeItems
        .filter { item ->
            item.type != KnowledgeLayerSearchType.DIRECTION &&
                nodes.none { existing -> existing.id == item.id }
        }
        .sortedWith(
            compareBy<KnowledgeLayerSearchItem> { graphTypePriority(it.type) }
                .thenByDescending { it.updatedAt },
        )
        .take(12)
        .forEach { item ->
            nodes += GraphNodeUi(
                id = item.id,
                label = item.title,
                subLabel = item.type.label,
                threadKey = item.threadKey.takeIf { it.isNotBlank() },
                noteId = item.noteId,
                accent = when (item.type) {
                    KnowledgeLayerSearchType.CONCLUSION -> Accent
                    KnowledgeLayerSearchType.EVIDENCE -> AccentBlue
                    else -> Accent.copy(alpha = 0.9f)
                },
            )
        }
    return nodes.distinctBy { it.id }.take(48)
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

private fun rememberGraphDecisionCards(
    snapshot: DirectionWikiSnapshot,
    maintainerSnapshot: LocalKnowledgeMaintenanceSnapshot,
): List<GraphDecisionCard> {
    val countsByThread = snapshot.knowledgeItems
        .filter { it.threadKey.isNotBlank() }
        .groupingBy { it.threadKey }
        .eachCount()
    val directions = snapshot.directions.values.sortedByDescending { it.updatedAt }
    val hubDirection = directions.maxByOrNull { direction ->
        countsByThread.getOrDefault(direction.threadKey, 0) +
            if (direction.title == maintainerSnapshot.graphPulse.activeHubLabel || direction.title == maintainerSnapshot.updatedDirectionTitle) 4 else 0
    }
    val denseDirection = directions.maxByOrNull { direction ->
        countsByThread.getOrDefault(direction.threadKey, 0)
    }
    val isolatedDirection = directions.minByOrNull { direction ->
        countsByThread.getOrDefault(direction.threadKey, 0)
    }
    val missingEdgeCard = GraphDecisionCard(
        title = "最缺的一条边",
        headline = maintainerSnapshot.graphPulse.missingLinkLabel.ifBlank { "还没形成明确缺口" },
        line = maintainerSnapshot.openQuestion.line.ifBlank {
            maintainerSnapshot.newConnection.line.ifBlank {
                "等更多记录进入之后，这里会优先提示最值得补的连接。"
            }
        },
        support = maintainerSnapshot.openQuestion.support.ifBlank {
            maintainerSnapshot.newConnection.support
        },
        threadKey = maintainerSnapshot.openQuestion.threadKey.takeIf { it.isNotBlank() },
        noteId = maintainerSnapshot.openQuestion.noteId,
    )
    return buildList {
        hubDirection?.let { direction ->
            add(
                GraphDecisionCard(
                    title = "当前枢纽",
                    headline = direction.title,
                    line = direction.conclusionLine
                        .ifBlank { direction.assetSummary }
                        .ifBlank { direction.healthLine }
                        .ifBlank { "${countsByThread.getOrDefault(direction.threadKey, 0)} 个知识对象围绕这条线聚集。" },
                    support = direction.trustLine
                        .ifBlank { direction.groundingLine }
                        .ifBlank { direction.maintenanceLine },
                    threadKey = direction.threadKey,
                ),
            )
        }
        denseDirection
            ?.takeIf { it.threadKey != hubDirection?.threadKey }
            ?.let { direction ->
                add(
                    GraphDecisionCard(
                        title = "最密的一团",
                        headline = "${direction.title} · ${countsByThread.getOrDefault(direction.threadKey, 0)} 个对象",
                        line = direction.knowledgeObjectLine
                            .ifBlank { direction.assetSummary }
                            .ifBlank { "这条线已经积累了最多知识对象，适合继续压成稳定资产。" },
                        support = direction.continuityLine
                            .ifBlank { direction.trajectoryLine }
                            .ifBlank { direction.healthLine },
                        threadKey = direction.threadKey,
                    ),
                )
            }
        isolatedDirection?.let { direction ->
            add(
                GraphDecisionCard(
                    title = "最孤的一点",
                    headline = direction.title,
                    line = direction.openQuestions.firstOrNull()
                        ?: direction.maintenanceLine
                            .ifBlank { "${countsByThread.getOrDefault(direction.threadKey, 0)} 个对象，还没连出稳定网络。" },
                    support = direction.maintenanceTargetLine
                        .ifBlank { direction.maintenanceSourceLine }
                        .ifBlank { direction.maintenanceDimensionLine },
                    threadKey = direction.threadKey,
                ),
            )
        }
        add(missingEdgeCard)
    }.take(4)
}

@Composable
private fun ContributionHeatmap(
    year: Int,
    activityByDate: Map<LocalDate, Int>,
    peakCount: Int,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
) {
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
