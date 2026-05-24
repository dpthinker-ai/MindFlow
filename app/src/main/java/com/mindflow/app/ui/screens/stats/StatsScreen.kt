package com.mindflow.app.ui.screens.stats

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.share.NoteShareCardGenerator
import com.mindflow.app.share.NoteShareStyle
import com.mindflow.app.share.shareNoteCard
import com.mindflow.app.ui.components.BottomBarClearance
import com.mindflow.app.ui.components.PanelCard
import com.mindflow.app.ui.components.ScreenBackground
import com.mindflow.app.ui.components.ScreenHorizontalPadding
import com.mindflow.app.ui.components.SectionHeader
import com.mindflow.app.ui.components.ShareStyleDialog
import com.mindflow.app.ui.components.SwipeRevealNoteCard
import com.mindflow.app.ui.theme.Accent
import com.mindflow.app.ui.theme.Panel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal const val StatsDeleteUsesDeferredSnackbarUndo = false

@Composable
fun StatsRoute(
    noteRepository: NoteRepository,
    onOpenNote: (Long) -> Unit,
) {
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(noteRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareCardGenerator = remember(context) { NoteShareCardGenerator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pendingShareNote by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is StatsEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    pendingShareNote?.let { note ->
        ShareStyleDialog(
            onDismiss = { pendingShareNote = null },
            onLight = {
                pendingShareNote = null
                scope.launch {
                    runCatching { shareNoteCard(context, shareCardGenerator, note, NoteShareStyle.LIGHT) }
                        .onFailure { Toast.makeText(context, "生成分享图失败", Toast.LENGTH_SHORT).show() }
                }
            },
            onDark = {
                pendingShareNote = null
                scope.launch {
                    runCatching { shareNoteCard(context, shareCardGenerator, note, NoteShareStyle.DARK) }
                        .onFailure { Toast.makeText(context, "生成分享图失败", Toast.LENGTH_SHORT).show() }
                }
            },
        )
    }
    StatsScreen(
        uiState = uiState,
        onOpenNote = onOpenNote,
        onDeleteNote = { note -> viewModel.deleteNote(note.id) },
        onShareNote = { pendingShareNote = it },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsScreen(
    uiState: StatsUiState,
    onOpenNote: (Long) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onShareNote: (NoteEntity) -> Unit,
) {
    val stats = uiState.stats
    val today = remember { LocalDate.now() }
    val zoneId = remember { ZoneId.systemDefault() }
    val availableYears = remember(stats.availableYears, today) {
        (stats.availableYears + today.year).distinct().sortedDescending()
    }
    val defaultSelectedYear = remember(availableYears, today) {
        if (today.year in availableYears) today.year else availableYears.first()
    }
    val activityByDate = remember(stats.activityDays) {
        stats.activityDays.associate { it.date to it.count }
    }

    var selectedYear by rememberSaveable { mutableIntStateOf(defaultSelectedYear) }
    var selectedDateKey by rememberSaveable { mutableStateOf<String?>(today.toString()) }

    LaunchedEffect(availableYears, defaultSelectedYear) {
        if (selectedYear !in availableYears) {
            selectedYear = defaultSelectedYear
        }
    }

    val selectedDate = selectedDateKey?.let(LocalDate::parse)
    LaunchedEffect(selectedYear, selectedDateKey, today) {
        val date = selectedDateKey?.let(LocalDate::parse)
        if (date != null && date.year != selectedYear) {
            selectedDateKey = null
        } else if (selectedYear == today.year && date == null) {
            selectedDateKey = today.toString()
        }
    }
    val selectedYearSummary = stats.yearSummary(selectedYear)
    val selectedDateNotes = remember(uiState.notes, selectedDate, zoneId) {
        if (selectedDate == null) {
            emptyList()
        } else {
            uiState.notes
                .filter { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.createdAt), zoneId) == selectedDate }
                .sortedByDescending { it.createdAt }
        }
    }
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ScreenHorizontalPadding,
                    bottom = BottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    PanelCard {
                        if (availableYears.size > 1) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                availableYears.forEach { year ->
                                    FilterChip(
                                        selected = selectedYear == year,
                                        onClick = { selectedYear = year },
                                        label = { Text(year.toString(), maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {
                                ContributionHeatmap(
                                    year = selectedYear,
                                    activityByDate = activityByDate,
                                    peakCount = selectedYearSummary?.peakCount ?: 0,
                                    selectedDate = selectedDate,
                                    onSelectDate = { date ->
                                        selectedDateKey = date.toString()
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    PanelCard {
                        SectionHeader(
                            title = selectedDate?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: "当天记录",
                            headline = if (selectedDate == null) null else "${selectedDateNotes.size} 条",
                        )
                        if (selectedDate == null) {
                            Text(
                                text = "点某一天，查看记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (selectedDateNotes.isEmpty()) {
                            Text(
                                text = "这一天没有新记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            selectedDateNotes.forEach { note ->
                                SwipeRevealNoteCard(
                                    note = note,
                                    onOpen = { onOpenNote(note.id) },
                                    onToggleArchive = null,
                                    onShare = { onShareNote(note) },
                                    onDelete = { onDeleteNote(note) },
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
    val selectedAccent = MaterialTheme.colorScheme.primary
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
