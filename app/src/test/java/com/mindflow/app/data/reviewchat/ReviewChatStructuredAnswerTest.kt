package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewChatStructuredAnswerTest {
    @Test
    fun parseReviewChatStructuredAnswer_parsesJsonSchema() {
        val answer = parseReviewChatStructuredAnswer(
            """
            {
              "summary": "本周末主要记录了 3 类信息。",
              "sections": [
                {
                  "title": "类别",
                  "items": [
                    "产品设计：启动页、图标、名称",
                    "技术实现：OCR、OpenCL、GC 水线"
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(answer).isNotNull()
        assertThat(answer!!.sections).hasSize(2)
        assertThat(answer.sections.first().title).isEqualTo("答复")
        assertThat(answer.sections.first().body.single()).isEqualTo("本周末主要记录了 3 类信息。")
        assertThat(answer.sections[1].title).isEqualTo("类别")
        assertThat(answer.sections[1].items).containsExactly(
            "产品设计：启动页、图标、名称",
            "技术实现：OCR、OpenCL、GC 水线",
        ).inOrder()
    }

    @Test
    fun parseReviewChatStructuredAnswer_splitsInlineCategoryItemsOnSingleLine() {
        val answer = parseReviewChatStructuredAnswer(
            "【答复】下面是分类结果。【类别】- 应用设计与功能开发：应用启动页设计、图标及名称确定-技术实现与优化：前端OCR识别问题、利用RL优化GC触发水线-个人成长与效率提升：提高生产力的方法、拒绝内耗，保持精力充沛",
        )

        assertThat(answer).isNotNull()
        val sections = answer!!.sections
        assertThat(sections).hasSize(2)
        assertThat(sections[0].title).isEqualTo("答复")
        assertThat(sections[0].body.single()).isEqualTo("下面是分类结果。")
        assertThat(sections[1].title).isEqualTo("类别")
        assertThat(sections[1].items).containsExactly(
            "应用设计与功能开发：应用启动页设计、图标及名称确定",
            "技术实现与优化：前端OCR识别问题、利用RL优化GC触发水线",
            "个人成长与效率提升：提高生产力的方法、拒绝内耗，保持精力充沛",
        ).inOrder()
    }

    @Test
    fun renderReviewChatStructuredAnswerAsMarkdown_rendersSectionListsDeterministically() {
        val markdown = renderReviewChatStructuredAnswerAsMarkdown(
            ReviewChatStructuredAnswer(
                sections = listOf(
                    ReviewChatStructuredSection(
                        title = "答复",
                        body = listOf("本周末主要记录了 3 类信息。"),
                        items = emptyList(),
                    ),
                    ReviewChatStructuredSection(
                        title = "类别",
                        body = emptyList(),
                        items = listOf(
                            "产品设计：启动页、图标、名称",
                            "技术实现：OCR、OpenCL、GC 水线",
                        ),
                    ),
                ),
            ),
        )

        assertThat(markdown).contains("本周末主要记录了 3 类信息。")
        assertThat(markdown).contains("\n\n类别：\n- 产品设计：启动页、图标、名称")
        assertThat(markdown).contains("\n- 技术实现：OCR、OpenCL、GC 水线")
    }
}
