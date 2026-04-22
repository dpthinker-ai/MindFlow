package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewChatAnswerFormatterTest {
    @Test
    fun normalizeReviewChatAnswerForDisplay_insertsBreaksBeforeListItems() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "先说结论。1. 第一件事 2. 第二件事 另外还要补充一点。",
        )

        assertThat(formatted).contains("先说结论。\n\n1. 第一件事")
        assertThat(formatted).contains("\n2. 第二件事")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_handlesChineseOrderedItemsAndSemicolons() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "先说结论；1）第一件事；2）第二件事；另外再补充一点。",
        )

        assertThat(formatted).contains("先说结论；\n1）第一件事")
        assertThat(formatted).contains("；\n2）第二件事")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_stripsOrphanAsterisks() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "*\n**结论\n1. 第一条 *\n2. 第二条",
        )

        assertThat(formatted).doesNotContain("*\n")
        assertThat(formatted).contains("结论")
        assertThat(formatted).contains("1. 第一条")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_convertsMarkdownTableToBulletList() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            """
            | 日期 | 主题 |
            | --- | --- |
            | 2026-04-21 | 产品方向 |
            | 2026-04-22 | 推荐链路 |
            """.trimIndent()
        )

        assertThat(formatted).doesNotContain("| 日期 |")
        assertThat(formatted).contains("- 日期：2026-04-21；主题：产品方向")
        assertThat(formatted).contains("- 日期：2026-04-22；主题：推荐链路")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_rewritesInlineEvidenceEchoesIntoList() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "3月份共计有31 条记录，以下是命中的记录：-记录｜2026-03-28｜应用启动页设计｜帮忙给这个应用增加一个启动页面。-记录｜2026-03-28｜MindFlow还要增加一个功能｜MindFlow还要增加一个功能。",
        )

        assertThat(formatted).contains("3月份共计有31 条记录，以下是命中的记录：")
        assertThat(formatted).contains("- 2026-03-28《应用启动页设计》")
        assertThat(formatted).contains("摘要：帮忙给这个应用增加一个启动页面。")
        assertThat(formatted).contains("- 2026-03-28《MindFlow还要增加一个功能》")
        assertThat(formatted).doesNotContain("-记录｜2026-03-28｜")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_doesNotBreakDatesIntoFakeBullets() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "结论根据提供的材料，关于“FitEver训练记录数据异常”的记录（2026-03-30）描述了一个问题，但最终结论是“把这个问题关闭吧”。",
        )

        assertThat(formatted).contains("（2026-03-30）")
        assertThat(formatted).doesNotContain("2026-03\n-\n30）")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_splitsPlainSectionLabelsIntoSeparateParagraphs() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "结论：当前没有明确未完成任务。依据：已有记录大多是已发生事件。下一步：如需追踪待办，请单独记录待办状态。",
        )

        assertThat(formatted).contains("结论：当前没有明确未完成任务。")
        assertThat(formatted).contains("\n\n依据：已有记录大多是已发生事件。")
        assertThat(formatted).contains("\n\n下一步：如需追踪待办，请单独记录待办状态。")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_splitsBareSectionLabelsWithoutColon() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "结论根据提供的材料，没有明确未完成任务。依据原始记录中，这个问题已关闭。下一步如果需要追踪待办，请单独记录待办状态。",
        )

        assertThat(formatted).contains("结论：根据提供的材料，没有明确未完成任务。")
        assertThat(formatted).contains("\n\n依据：原始记录中，这个问题已关闭。")
        assertThat(formatted).contains("\n\n下一步：如果需要追踪待办，请单独记录待办状态。")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_convertsInlineStarDateBulletsIntoList() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "依据：近期记录包括：*2026-04-05《注意力决定人生》：强调注意力的重要性。*2026-04-16《面对失败的正确态度》：强调不要为失败懊恼。",
        )

        assertThat(formatted).contains("依据：近期记录包括：")
        assertThat(formatted).contains("\n- 2026-04-05《注意力决定人生》：强调注意力的重要性。")
        assertThat(formatted).contains("\n- 2026-04-16《面对失败的正确态度》：强调不要为失败懊恼。")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_rendersStructuredSectionTagsIntoMarkdown() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            """
            【答复】人生态度记录的时间轴跨度从2026-03-28到2026-04-21，共129条记录。
            【依据】
            - 时间范围：最早记录日期为2026-03-28，最近记录日期为2026-04-21。
            - 主题变化：近期更聚焦在注意力管理、未来规划和生存法则。
            【下一步】
            - 如果需要更细分类，可以继续按月份拆分。
            """.trimIndent()
        )

        assertThat(formatted).startsWith("人生态度记录的时间轴跨度从2026-03-28到2026-04-21，共129条记录。")
        assertThat(formatted).contains("\n\n依据：\n- 时间范围：最早记录日期为2026-03-28，最近记录日期为2026-04-21。")
        assertThat(formatted).contains("\n\n下一步：\n- 如果需要更细分类，可以继续按月份拆分。")
        assertThat(formatted).doesNotContain("【依据】")
    }

    @Test
    fun normalizeReviewChatAnswerForDisplay_handlesInlineStructuredTagsOnSingleLine() {
        val formatted = normalizeReviewChatAnswerForDisplay(
            "【答复】3月份共计有31条记录。【记录】- 2026-03-28《应用启动页设计》：帮忙给这个应用增加一个启动页面。- 2026-03-28《MindFlow还要增加一个功能》：MindFlow还要增加一个功能。",
        )

        assertThat(formatted).contains("3月份共计有31条记录。")
        assertThat(formatted).contains("\n\n记录：")
        assertThat(formatted).contains("\n- 2026-03-28《应用启动页设计》：帮忙给这个应用增加一个启动页面。")
        assertThat(formatted).contains("\n- 2026-03-28《MindFlow还要增加一个功能》：MindFlow还要增加一个功能。")
        assertThat(formatted).doesNotContain("【记录】")
    }
}
