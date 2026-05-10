package com.mindflow.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import org.junit.Test

class RecordKindTest {
    @Test
    fun inferRecordKindDistinguishesVoiceImageLinkAndText() {
        assertThat(
            note(
                content = """
                    原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-1.wav
                    语音转写（可编辑）：今天需要复盘输入页
                """.trimIndent(),
            ).inferRecordKind(),
        ).isEqualTo(RecordKind.VOICE)

        assertThat(note(content = "图片：/data/user/0/com.mindflow.app/files/captures/images/one.jpg").inferRecordKind())
            .isEqualTo(RecordKind.IMAGE)

        assertThat(note(content = "链接：https://example.com/post").inferRecordKind())
            .isEqualTo(RecordKind.LINK)

        assertThat(note(content = "普通文本记录").inferRecordKind())
            .isEqualTo(RecordKind.TEXT)
    }

    @Test
    fun compactRecordPreviewTextUsesTranscriptInsteadOfAudioPath() {
        val preview = note(
            content = """
                原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-1.wav
                语音转写（可编辑）：今天先把语音记录预览改成转写内容
                识别信息：Gemma 4 已完成转写
            """.trimIndent(),
        ).compactRecordPreviewText()

        assertThat(preview).contains("语音记录预览")
        assertThat(preview).doesNotContain("/data/user/0")
    }

    @Test
    fun compactRecordTitleTextDoesNotExposeAudioPathTopic() {
        val title = note(
            topic = "原始录音：/data/user/0/com.mi",
            content = """
                原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-1.wav
                识别信息：
            """.trimIndent(),
        ).compactRecordTitleText()

        assertThat(title).isEqualTo("语音记录")
        assertThat(title).doesNotContain("/data/user/0")
    }

    @Test
    fun compactRecordPreviewTextUsesArticleBodyAndImageSummary() {
        val linkPreview = note(
            content = """
                链接：https://example.com/post
                正文：链接页保存后应该展示提取到的正文内容
                解析信息：正文已提取
            """.trimIndent(),
        ).compactRecordPreviewText()
        val imagePreview = note(
            content = """
                图片：/data/user/0/com.mindflow.app/files/captures/images/one.jpg
                图片理解摘要：白板上写了三个产品优化步骤
            """.trimIndent(),
        ).compactRecordPreviewText()

        assertThat(linkPreview).contains("提取到的正文内容")
        assertThat(linkPreview).doesNotContain("example.com")
        assertThat(imagePreview).contains("产品优化步骤")
        assertThat(imagePreview).doesNotContain("/data/user/0")
    }

    @Test
    fun compactRecordPreviewTextDoesNotShowEmptyImageFieldLabels() {
        val preview = note(
            content = """
                图片：/data/user/0/com.mindflow.app/files/captures/images/one.jpg
                补充说明：
                图像理解结果：
                关键信息提取：
                结构化识别：
                OCR 文本(可选)：
            """.trimIndent(),
        ).compactRecordPreviewText()

        assertThat(preview).isEqualTo("图片已保存，等待理解")
        assertThat(preview).doesNotContain("补充说明")
        assertThat(preview).doesNotContain("OCR 文本")
    }

    private fun note(
        content: String,
        topic: String = "测试记录",
    ): NoteEntity = NoteEntity(
        id = 1L,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = null,
        folderSource = FolderSource.MANUAL,
        tags = emptyList(),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.SHORT,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )
}
