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
}
