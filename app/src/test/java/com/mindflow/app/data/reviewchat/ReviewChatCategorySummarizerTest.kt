package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewChatCategorySummarizerTest {
    @Test
    fun parseCategoryCandidates_stripsPrefixAndNoise() {
        val parsed = ReviewChatCategorySummarizer.parseCategoryCandidates(
            """
            - 类别：应用开发：启动页、功能迭代、应用市场
            * 技术优化：OCR、OpenCL、GC 调优
            统计信息：共 135 条记录
            历史记录：不要当成类别
            个人成长：注意力、原则、心理建设
            """.trimIndent()
        )

        assertThat(parsed).containsExactly(
            "应用开发：启动页、功能迭代、应用市场",
            "技术优化：OCR、OpenCL、GC 调优",
            "个人成长：注意力、原则、心理建设",
        ).inOrder()
    }
}
