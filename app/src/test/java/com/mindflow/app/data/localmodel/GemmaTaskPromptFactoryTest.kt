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
    fun `graph relation prompt stays local and layer scoped`() {
        val prompt = GemmaTaskPromptFactory.generateGraphRelations("sleep", listOf("recovery", "focus"))
        assertThat(prompt).contains("只判断中心知识点与候选邻居之间的关系")
        assertThat(prompt).doesNotContain("生成整张图")
    }
}
