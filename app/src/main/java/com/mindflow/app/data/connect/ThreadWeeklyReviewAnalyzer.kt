package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ThreadWeeklyReviewSummary(
    val statsLine: String,
    val lines: List<String>,
) {
    val mainLine: String
        get() = lines.firstOrNull().orEmpty()
}

object ThreadWeeklyReviewAnalyzer {
    fun build(notes: List<NoteEntity>): ThreadWeeklyReviewSummary {
        val weeklyNotes = notes.currentWeekNotes()
        if (weeklyNotes.isEmpty()) {
            return ThreadWeeklyReviewSummary(
                statsLine = "这周还没有新的推进",
                lines = listOf("先补一条真实记录，让这个方向重新开始流动起来。"),
            )
        }
        val latest = weeklyNotes.maxByOrNull { it.updatedAt }
        val repeatedTag = weeklyNotes
            .flatMap { it.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val lines = buildList {
            if (repeatedTag != null) {
                add("这周主线主要围绕「$repeatedTag」，可以继续把它压成更明确的问题定义。")
            } else {
                add("这周这个方向还在继续聚焦，别再往外摊更多分支。")
            }
            latest?.let {
                add("下周先接着推进「${it.topic.ifBlank { "未命名记录" }}」，别让最近这条又沉下去。")
            }
        }.take(2)
        return ThreadWeeklyReviewSummary(
            statsLine = buildList {
                add("本周 ${weeklyNotes.size} 条")
                val progressCount = weeklyNotes.count { it.status == NoteStatus.IN_PROGRESS }
                val doneCount = weeklyNotes.count { it.status == NoteStatus.DONE }
                if (progressCount > 0) add("推进 ${progressCount} 条")
                if (doneCount > 0) add("完成 ${doneCount} 条")
            }.joinToString(" · "),
            lines = lines,
        )
    }

    private fun List<NoteEntity>.currentWeekNotes(): List<NoteEntity> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val weekStart = today.with(DayOfWeek.MONDAY)
        return filter { note ->
            val noteDate = Instant.ofEpochMilli(note.updatedAt).atZone(zoneId).toLocalDate()
            !noteDate.isBefore(weekStart)
        }
    }
}
