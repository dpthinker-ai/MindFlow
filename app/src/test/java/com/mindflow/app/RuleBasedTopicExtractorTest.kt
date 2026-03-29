package com.mindflow.app

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.topic.RuleBasedTopicExtractor
import org.junit.Test

class RuleBasedTopicExtractorTest {
    private val extractor = RuleBasedTopicExtractor()

    @Test
    fun `extract trims noisy prefixes and punctuation`() {
        val topic = extractor.extract("我想做一个新的家庭预算面板，先把固定支出归类。")

        assertThat(topic).isEqualTo("做一个新的家庭预算面板")
    }

    @Test
    fun `extract falls back when content is blank`() {
        val topic = extractor.extract("   ")

        assertThat(topic).isEqualTo("未命名想法")
    }
}
