package com.mindflow.app.data.localmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GemmaTaskPromptFactoryTest {
    @Test
    fun `polish prompt forbids inventing new facts`() {
        val prompt = GemmaTaskPromptFactory.polish("原文")
        assertThat(prompt).contains("不新增原文没有的信息")
        assertThat(prompt).contains("polishedText")
        assertThat(prompt).contains("changeSummary")
    }

    @Test
    fun `topic prompt rejects generic labels`() {
        val prompt = GemmaTaskPromptFactory.extractTopic("原文")
        assertThat(prompt).contains("禁止使用“记录”“想法”“学习”“随想”")
        assertThat(prompt).contains("whyThisTopic")
    }

    @Test
    fun `note insight prompt asks for non overlapping durable summary`() {
        val prompt = GemmaTaskPromptFactory.summarizeNote("原文")
        assertThat(prompt).contains("可长期保存")
        assertThat(prompt).contains("关键要点必须彼此不重复")
        assertThat(prompt).contains("summary")
        assertThat(prompt).contains("keyPoints")
    }

    @Test
    fun `graph relation prompt stays local and layer scoped`() {
        val prompt = GemmaTaskPromptFactory.generateGraphRelations("sleep", listOf("recovery", "focus"))
        assertThat(prompt).contains("只判断中心知识点与候选邻居之间的关系")
        assertThat(prompt).doesNotContain("生成整张图")
    }

    @Test
    fun `media prompts keep capture processing local first`() {
        val audioPrompt = GemmaTaskPromptFactory.transcribeAudio(
            audioPath = "/private/voice.m4a",
            localeHint = "zh-CN",
        )
        assertThat(audioPrompt).contains("原始录音文件")
        assertThat(audioPrompt).contains("本地端侧")
        assertThat(audioPrompt).contains("transcript")
        assertThat(audioPrompt).contains("topic")

        val imagePrompt = GemmaTaskPromptFactory.understandImage(
            imagePath = "/private/image.jpg",
            userNote = "会议白板",
        )
        assertThat(imagePrompt).contains("图片已作为图像输入提供")
        assertThat(imagePrompt).doesNotContain("/private/image.jpg")
        assertThat(imagePrompt).contains("根据图片类型选择")
        assertThat(imagePrompt).contains("extractedText")
        assertThat(imagePrompt).contains("objects")
    }
}
