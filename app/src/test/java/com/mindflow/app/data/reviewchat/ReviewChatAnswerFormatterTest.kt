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
}
