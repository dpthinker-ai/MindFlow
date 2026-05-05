package com.mindflow.app.ui.screens.flow

import com.mindflow.app.data.local.entity.NoteEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal enum class GraphActivityPhase(
    val label: String,
) {
    MORNING("早间"),
    AFTERNOON("午后"),
    NIGHT("夜间"),
}

internal data class GraphActivitySlot(
    val date: LocalDate,
    val phase: GraphActivityPhase,
)

internal fun graphActivityPhaseForHour(hour: Int): GraphActivityPhase {
    val normalizedHour = ((hour % 24) + 24) % 24
    return when (normalizedHour) {
        in 5..11 -> GraphActivityPhase.MORNING
        in 12..17 -> GraphActivityPhase.AFTERNOON
        else -> GraphActivityPhase.NIGHT
    }
}

internal fun graphActivitySlotForNote(
    note: NoteEntity,
    zoneId: ZoneId,
): GraphActivitySlot {
    val dateTime = Instant.ofEpochMilli(note.updatedAt.coerceAtLeast(note.createdAt))
        .atZone(zoneId)
    return GraphActivitySlot(
        date = dateTime.toLocalDate(),
        phase = graphActivityPhaseForHour(dateTime.hour),
    )
}

internal fun buildGraphActivityBySlot(
    notes: List<NoteEntity>,
    zoneId: ZoneId,
): Map<GraphActivitySlot, Int> =
    notes
        .filterNot { it.isArchived }
        .groupingBy { note -> graphActivitySlotForNote(note, zoneId) }
        .eachCount()

internal fun countGraphActivityBetween(
    activityByDate: Map<LocalDate, Int>,
    startDate: LocalDate,
    endDate: LocalDate,
): Int {
    if (endDate.isBefore(startDate)) return 0
    return activityByDate
        .filterKeys { date -> !date.isBefore(startDate) && !date.isAfter(endDate) }
        .values
        .sumOf { it.coerceAtLeast(0) }
}

internal fun longestGraphActivityStreak(
    activityByDate: Map<LocalDate, Int>,
): Int {
    val activeDates = activityByDate
        .filterValues { it > 0 }
        .keys
        .sorted()
    if (activeDates.isEmpty()) return 0

    var longest = 1
    var current = 1
    activeDates.zipWithNext().forEach { (previous, currentDate) ->
        if (previous.plusDays(1) == currentDate) {
            current += 1
        } else {
            current = 1
        }
        longest = maxOf(longest, current)
    }
    return longest
}

internal fun buildGraphTrendBuckets(
    activityByDate: Map<LocalDate, Int>,
    endDate: LocalDate,
    days: Int,
    bucketCount: Int,
): List<Int> {
    if (days <= 0 || bucketCount <= 0) return emptyList()
    val startDate = endDate.minusDays((days - 1).toLong())
    val buckets = MutableList(bucketCount) { 0 }
    repeat(days) { dayOffset ->
        val date = startDate.plusDays(dayOffset.toLong())
        val bucketIndex = ((dayOffset * bucketCount) / days).coerceIn(0, bucketCount - 1)
        buckets[bucketIndex] += activityByDate.getOrDefault(date, 0).coerceAtLeast(0)
    }
    return buckets
}

internal fun buildGraphMonthCalendarDates(
    month: YearMonth,
): List<LocalDate> {
    val firstVisibleDate = month
        .atDay(1)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastVisibleDate = month
        .atEndOfMonth()
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return buildList {
        var current = firstVisibleDate
        while (!current.isAfter(lastVisibleDate)) {
            add(current)
            current = current.plusDays(1)
        }
    }
}

internal fun buildGraphWeekdayActivityCounts(
    activityByDate: Map<LocalDate, Int>,
    weekStart: LocalDate,
): List<Int> =
    List(7) { dayOffset ->
        val date = weekStart.plusDays(dayOffset.toLong())
        activityByDate.getOrDefault(date, 0).coerceAtLeast(0)
    }

internal fun buildGraphSelectedDayHeadline(
    date: LocalDate?,
    recordCount: Int,
): String =
    when {
        date == null -> "点某一天，查看当天点亮的主题。"
        recordCount <= 0 -> "${date.monthValue}月${date.dayOfMonth}日 · 暂无记录变动"
        else -> "${date.monthValue}月${date.dayOfMonth}日 · $recordCount 条记录有变动"
    }

internal fun buildGraphSelectedSlotHeadline(
    slot: GraphActivitySlot?,
    recordCount: Int,
): String =
    when {
        slot == null -> "点某个时段，查看被点亮的主题。"
        recordCount <= 0 -> "${slot.date.monthValue}月${slot.date.dayOfMonth}日 · ${slot.phase.label} · 暂无记录变动"
        else -> "${slot.date.monthValue}月${slot.date.dayOfMonth}日 · ${slot.phase.label} · $recordCount 条记录有变动"
    }

internal fun shouldShowUnmappedGraphActivityHint(
    recordCount: Int,
    activatedNodeCount: Int,
): Boolean =
    recordCount > 0 && activatedNodeCount == 0

internal data class GraphHotTopicUi(
    val nodeId: String,
    val label: String,
    val recentRecordCount: Int,
    val trendPercent: Int,
    val relationCount: Int,
)

internal fun buildGraphHotTopics(
    notes: List<NoteEntity>,
    graphNodes: List<GraphNodeUi>,
    zoneId: ZoneId,
    today: LocalDate,
    limit: Int = 3,
): List<GraphHotTopicUi> {
    if (notes.isEmpty() || graphNodes.isEmpty() || limit <= 0) return emptyList()
    val activeNotes = notes.filterNot { it.isArchived }
    val recentStart = today.minusDays(29)
    val previousStart = today.minusDays(59)
    val previousEnd = today.minusDays(30)
    return graphNodes
        .mapNotNull { node ->
            var recentCount = 0
            var previousCount = 0
            activeNotes.forEach { note ->
                if (!noteMatchesGraphTopic(note, node)) return@forEach
                val date = Instant.ofEpochMilli(note.updatedAt.coerceAtLeast(note.createdAt))
                    .atZone(zoneId)
                    .toLocalDate()
                when {
                    !date.isBefore(recentStart) && !date.isAfter(today) -> recentCount += 1
                    !date.isBefore(previousStart) && !date.isAfter(previousEnd) -> previousCount += 1
                }
            }
            if (recentCount <= 0) {
                null
            } else {
                GraphHotTopicUi(
                    nodeId = node.id,
                    label = node.label,
                    recentRecordCount = recentCount,
                    trendPercent = buildGraphTopicTrendPercent(
                        recentCount = recentCount,
                        previousCount = previousCount,
                    ),
                    relationCount = node.relationCount,
                )
            }
        }
        .sortedWith(
            compareByDescending<GraphHotTopicUi> { it.recentRecordCount }
                .thenByDescending { it.trendPercent }
                .thenByDescending { it.relationCount },
        )
        .take(limit)
}

private fun buildGraphTopicTrendPercent(
    recentCount: Int,
    previousCount: Int,
): Int =
    when {
        recentCount <= 0 -> 0
        previousCount <= 0 -> (36 + recentCount * 12).coerceAtMost(96)
        else -> (((recentCount - previousCount).toDouble() / previousCount.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 99)
    }

private fun noteMatchesGraphTopic(
    note: NoteEntity,
    node: GraphNodeUi,
): Boolean {
    val sourceNoteIds = node.sourceIds
        .mapNotNull { sourceId -> sourceId.removePrefix("note:").toLongOrNull() }
        .toSet()
    if (note.id in sourceNoteIds) return true
    val terms = (listOf(node.label) + node.aliases)
        .map(::normalizeGraphTopicTerm)
        .filter { it.isNotBlank() }
        .distinct()
    if (terms.isEmpty()) return false
    val noteTerms = (note.tags + note.topic)
        .map(::normalizeGraphTopicTerm)
        .filter { it.isNotBlank() }
        .toSet()
    if (terms.any { it in noteTerms }) return true
    val searchable = normalizeGraphTopicTerm(note.content.take(500))
    return terms.any { term -> searchable.contains(term) }
}

private fun normalizeGraphTopicTerm(value: String): String =
    value.trim().lowercase()
