package com.mindflow.app

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.export.MarkdownExporter
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.local.entity.NoteStatusHistoryEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TopicSource
import org.junit.Test

class MarkdownExporterTest {
    private val exporter = MarkdownExporter()

    @Test
    fun `export includes note metadata and history`() {
        val notes = listOf(
            NoteEntity(
                id = 1,
                content = "做一个能记录家里灵感的小工具",
                topic = "家庭灵感池",
                topicSource = TopicSource.MANUAL,
                status = NoteStatus.IN_PROGRESS,
                isArchived = false,
                createdAt = 1_000,
                updatedAt = 2_000,
            )
        )
        val history = listOf(
            NoteStatusHistoryEntity(
                noteId = 1,
                fromStatus = null,
                toStatus = NoteStatus.IDEA,
                changedAt = 1_000,
            ),
            NoteStatusHistoryEntity(
                noteId = 1,
                fromStatus = NoteStatus.IDEA,
                toStatus = NoteStatus.IN_PROGRESS,
                changedAt = 2_000,
            )
        )

        val markdown = exporter.export(notes, history, generatedAt = 3_000)

        assertThat(markdown).contains("# MindFlow Export")
        assertThat(markdown).contains("## 1. 家庭灵感池")
        assertThat(markdown).contains("- 状态: 进行中")
        assertThat(markdown).contains("### 状态历史")
        assertThat(markdown).contains("初始 -> 想法")
        assertThat(markdown).contains("想法 -> 进行中")
    }
}
