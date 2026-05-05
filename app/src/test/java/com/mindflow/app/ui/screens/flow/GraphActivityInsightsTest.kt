package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TopicSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Test

class GraphActivityInsightsTest {
    @Test
    fun `countGraphActivityBetween includes both boundaries and ignores outside dates`() {
        val start = LocalDate.parse("2026-05-01")
        val activityByDate = mapOf(
            start.minusDays(1) to 9,
            start to 1,
            start.plusDays(1) to 0,
            start.plusDays(6) to 3,
            start.plusDays(7) to 7,
        )

        val count = countGraphActivityBetween(
            activityByDate = activityByDate,
            startDate = start,
            endDate = start.plusDays(6),
        )

        assertThat(count).isEqualTo(4)
    }

    @Test
    fun `longestGraphActivityStreak treats zero count days as gaps`() {
        val firstDay = LocalDate.parse("2026-05-01")
        val activityByDate = mapOf(
            firstDay to 1,
            firstDay.plusDays(1) to 2,
            firstDay.plusDays(2) to 0,
            firstDay.plusDays(3) to 4,
            firstDay.plusDays(4) to 1,
            firstDay.plusDays(5) to 3,
            firstDay.plusDays(7) to 1,
        )

        assertThat(longestGraphActivityStreak(activityByDate)).isEqualTo(3)
    }

    @Test
    fun `buildGraphTrendBuckets groups the requested recent window from oldest to newest`() {
        val endDate = LocalDate.parse("2026-05-08")
        val activityByDate = (0L..7L).associate { offset ->
            val date = endDate.minusDays(7L - offset)
            date to (offset + 1).toInt()
        } + mapOf(
            endDate.minusDays(8) to 99,
            endDate.plusDays(1) to 99,
        )

        val buckets = buildGraphTrendBuckets(
            activityByDate = activityByDate,
            endDate = endDate,
            days = 8,
            bucketCount = 4,
        )

        assertThat(buckets).containsExactly(3, 7, 11, 15).inOrder()
    }

    @Test
    fun `buildGraphMonthCalendarDates pads the visible month from Monday to Sunday`() {
        val dates = buildGraphMonthCalendarDates(YearMonth.of(2026, 5))

        assertThat(dates.first()).isEqualTo(LocalDate.parse("2026-04-27"))
        assertThat(dates.last()).isEqualTo(LocalDate.parse("2026-05-31"))
        assertThat(dates).hasSize(35)
        assertThat(dates[4]).isEqualTo(LocalDate.parse("2026-05-01"))
    }

    @Test
    fun `buildGraphWeekdayActivityCounts returns Monday first counts for a selected week`() {
        val monday = LocalDate.parse("2026-05-04")
        val activityByDate = mapOf(
            monday.minusDays(1) to 99,
            monday to 2,
            monday.plusDays(2) to 4,
            monday.plusDays(6) to 1,
            monday.plusDays(7) to 99,
        )

        val counts = buildGraphWeekdayActivityCounts(
            activityByDate = activityByDate,
            weekStart = monday,
        )

        assertThat(counts).containsExactly(2, 0, 4, 0, 0, 0, 1).inOrder()
    }

    @Test
    fun `graphActivityPhaseForHour groups activity into morning afternoon and night`() {
        assertThat(graphActivityPhaseForHour(5)).isEqualTo(GraphActivityPhase.MORNING)
        assertThat(graphActivityPhaseForHour(11)).isEqualTo(GraphActivityPhase.MORNING)
        assertThat(graphActivityPhaseForHour(12)).isEqualTo(GraphActivityPhase.AFTERNOON)
        assertThat(graphActivityPhaseForHour(17)).isEqualTo(GraphActivityPhase.AFTERNOON)
        assertThat(graphActivityPhaseForHour(18)).isEqualTo(GraphActivityPhase.NIGHT)
        assertThat(graphActivityPhaseForHour(4)).isEqualTo(GraphActivityPhase.NIGHT)
    }

    @Test
    fun `buildGraphActivityBySlot keeps the same day split by phase`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.parse("2026-05-05")
        val notes = listOf(
            graphTopicNoteAt(id = 1, dateTime = date.atTime(7, 30), topic = "早间", zoneId = zoneId),
            graphTopicNoteAt(id = 2, dateTime = date.atTime(10, 15), topic = "早间追加", zoneId = zoneId),
            graphTopicNoteAt(id = 3, dateTime = date.atTime(15, 0), topic = "午后", zoneId = zoneId),
            graphTopicNoteAt(id = 4, dateTime = date.atTime(22, 20), topic = "夜间", zoneId = zoneId),
        )

        val activityBySlot = buildGraphActivityBySlot(notes, zoneId)

        assertThat(activityBySlot[GraphActivitySlot(date, GraphActivityPhase.MORNING)]).isEqualTo(2)
        assertThat(activityBySlot[GraphActivitySlot(date, GraphActivityPhase.AFTERNOON)]).isEqualTo(1)
        assertThat(activityBySlot[GraphActivitySlot(date, GraphActivityPhase.NIGHT)]).isEqualTo(1)
    }

    @Test
    fun `buildGraphSelectedDayHeadline avoids saying zero records changed`() {
        val date = LocalDate.parse("2026-05-19")

        assertThat(buildGraphSelectedDayHeadline(date, 0))
            .isEqualTo("5月19日 · 暂无记录变动")
        assertThat(buildGraphSelectedDayHeadline(date, 3))
            .isEqualTo("5月19日 · 3 条记录有变动")
        assertThat(buildGraphSelectedDayHeadline(null, 3))
            .isEqualTo("点某一天，查看当天点亮的主题。")
    }

    @Test
    fun `shouldShowUnmappedGraphActivityHint only appears when records exist`() {
        assertThat(shouldShowUnmappedGraphActivityHint(recordCount = 0, activatedNodeCount = 0))
            .isFalse()
        assertThat(shouldShowUnmappedGraphActivityHint(recordCount = 2, activatedNodeCount = 0))
            .isTrue()
        assertThat(shouldShowUnmappedGraphActivityHint(recordCount = 2, activatedNodeCount = 1))
            .isFalse()
    }

    @Test
    fun `buildGraphHotTopics ranks recent topic activity with rising percentages`() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.parse("2026-05-05")
        val nodes = listOf(
            GraphNodeUi(
                id = "voice",
                label = "语音",
                summaryLine = "",
                structureStatus = GraphStructureStatus.LINKED,
                noteCount = 4,
                relationCount = 2,
                aliases = listOf("转写"),
                sourceIds = listOf("note:1", "note:2", "note:3", "note:4"),
            ),
            GraphNodeUi(
                id = "ai",
                label = "AI",
                summaryLine = "",
                structureStatus = GraphStructureStatus.HUB,
                noteCount = 2,
                relationCount = 5,
                sourceIds = listOf("note:5", "note:6"),
            ),
            GraphNodeUi(
                id = "old",
                label = "旧主题",
                summaryLine = "",
                structureStatus = GraphStructureStatus.ISOLATED,
                noteCount = 1,
                relationCount = 1,
                sourceIds = listOf("note:7"),
            ),
        )
        val notes = listOf(
            graphTopicNote(id = 1, date = today, topic = "语音识别", tags = listOf("语音"), zoneId = zoneId),
            graphTopicNote(id = 2, date = today.minusDays(2), topic = "转写评估", tags = listOf("语音"), zoneId = zoneId),
            graphTopicNote(id = 3, date = today.minusDays(10), topic = "语音标题", tags = listOf("语音"), zoneId = zoneId),
            graphTopicNote(id = 4, date = today.minusDays(42), topic = "语音旧记录", tags = listOf("语音"), zoneId = zoneId),
            graphTopicNote(id = 5, date = today.minusDays(1), topic = "AI 规划", tags = listOf("AI"), zoneId = zoneId),
            graphTopicNote(id = 6, date = today.minusDays(35), topic = "AI 旧规划", tags = listOf("AI"), zoneId = zoneId),
            graphTopicNote(id = 7, date = today.minusDays(40), topic = "旧主题", tags = listOf("旧主题"), zoneId = zoneId),
        )

        val topics = buildGraphHotTopics(
            notes = notes,
            graphNodes = nodes,
            zoneId = zoneId,
            today = today,
            limit = 2,
        )

        assertThat(topics.map { it.label }).containsExactly("语音", "AI").inOrder()
        assertThat(topics.map { it.recentRecordCount }).containsExactly(3, 1).inOrder()
        assertThat(topics.map { it.trendPercent }).containsExactly(99, 0).inOrder()
    }
}

private fun graphTopicNote(
    id: Long,
    date: LocalDate,
    topic: String,
    tags: List<String>,
    zoneId: ZoneId,
): NoteEntity {
    val timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    return NoteEntity(
        id = id,
        content = "$topic 内容",
        topic = topic,
        topicSource = TopicSource.MANUAL,
        tags = tags,
        status = NoteStatus.IDEA,
        isArchived = false,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

private fun graphTopicNoteAt(
    id: Long,
    dateTime: LocalDateTime,
    topic: String,
    zoneId: ZoneId,
): NoteEntity {
    val timestamp = dateTime.atZone(zoneId).toInstant().toEpochMilli()
    return NoteEntity(
        id = id,
        content = "$topic 内容",
        topic = topic,
        topicSource = TopicSource.MANUAL,
        tags = emptyList(),
        status = NoteStatus.IDEA,
        isArchived = false,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}
