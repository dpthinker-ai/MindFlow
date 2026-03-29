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
import com.mindflow.app.ui.theme.BorderSoft
import com.mindflow.app.ui.theme.Panel
import com.mindflow.app.ui.theme.TextSoft
import com.mindflow.app.ui.theme.WhiteGlass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        onDeleteNote = viewModel::deleteNote,
        onShareNote = { pendingShareNote = it },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsScreen(
    uiState: StatsUiState,
    onOpenNote: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
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
                                color = TextSoft,
                            )
                        } else if (selectedDateNotes.isEmpty()) {
                            Text(
                                text = "这一天没有新记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSoft,
                            )
                        } else {
                            selectedDateNotes.forEach { note ->
                                SwipeRevealNoteCard(
                                    note = note,
                                    onOpen = { onOpenNote(note.id) },
                                    onToggleArchive = null,
                                    onShare = { onShareNote(note) },
                                    onDelete = { onDeleteNote(note.id) },
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
