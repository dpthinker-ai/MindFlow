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
}
