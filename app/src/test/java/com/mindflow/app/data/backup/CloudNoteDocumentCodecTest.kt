package com.mindflow.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TopicSource
import org.junit.Test

class CloudNoteDocumentCodecTest {
    private val codec = CloudNoteDocumentCodec()

    @Test
    fun `codec persists ai insight without merging it into content`() {
        val markdown = codec.encode(
            note = NoteEntity(
                id = 7,
                content = "正文原文",
                topic = "阅读体验",
                topicSource = TopicSource.MANUAL,
                status = NoteStatus.IDEA,
                isArchived = false,
                aiSummary = "正文需要完整阅读。",
                aiKeyPoints = listOf("不要截断正文", "洞察后台生成"),
                aiInsightContentHash = "hash-7",
                aiInsightUpdatedAt = 1_000,
                createdAt = 1_000,
                updatedAt = 1_000,
            ),
            history = emptyList(),
        )

        val restored = codec.decode("note-7.md", markdown).note

        assertThat(restored.content).isEqualTo("正文原文")
        assertThat(restored.aiSummary).isEqualTo("正文需要完整阅读。")
        assertThat(restored.aiKeyPoints).containsExactly("不要截断正文", "洞察后台生成").inOrder()
        assertThat(restored.aiInsightContentHash).isEqualTo("hash-7")
    }
}
