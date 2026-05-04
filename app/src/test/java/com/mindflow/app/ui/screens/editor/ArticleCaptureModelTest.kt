package com.mindflow.app.ui.screens.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArticleCaptureModelTest {
    @Test
    fun buildArticleCaptureModel_extractsUrlAndStableLocalSummary() {
        val model = buildArticleCaptureModel(
            topic = "文章收藏",
            content = "链接：https://uxdesign.cc/design-system-value\n补充说明：设计系统价值",
            tags = listOf("文章"),
            updatedAt = 1_714_000_000_000L,
        )

        assertThat(model.title).contains("设计系统价值")
        assertThat(model.host).isEqualTo("uxdesign.cc")
        assertThat(model.url).isEqualTo("https://uxdesign.cc/design-system-value")
        assertThat(model.summary).contains("本地")
        assertThat(model.keyPoints).hasSize(3)
        assertThat(model.topics).contains("文章")
    }

    @Test
    fun buildVoiceCaptureModel_keepsTranscriptAndVoiceSummaryStable() {
        val model = buildVoiceCaptureModel(
            topic = "语音输入",
            content = "原始内容：今天需要梳理新输入页的稳定性风险\n补充说明：睡前先记下来",
            tags = listOf("语音"),
            updatedAt = null,
        )

        assertThat(model.title).isEqualTo("语音输入")
        assertThat(model.sourceTitle).isEqualTo("原始内容信息")
        assertThat(model.sourceLabel).contains("继续录入")
        assertThat(model.summary).contains("语音")
        assertThat(model.keyPoints).hasSize(3)
        assertThat(model.topics).contains("语音")
    }

    @Test
    fun buildImageCaptureModel_keepsImageUnderstandingSummaryStable() {
        val model = buildImageCaptureModel(
            topic = "图片记录",
            content = "图片：meeting-whiteboard.png\n补充说明：白板上的项目拆解",
            tags = listOf("图片"),
            updatedAt = 1_714_000_000_000L,
        )

        assertThat(model.title).isEqualTo("图片输入")
        assertThat(model.sourceTitle).isEqualTo("图片预览")
        assertThat(model.sourceLabel).contains("从相册导入")
        assertThat(model.summary).contains("图片")
        assertThat(model.keyPoints).hasSize(3)
        assertThat(model.topics).contains("图片")
    }
}
