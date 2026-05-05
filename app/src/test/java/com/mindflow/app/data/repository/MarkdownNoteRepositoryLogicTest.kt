package com.mindflow.app.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownNoteRepositoryLogicTest {
    @Test
    fun `should apply ai refresh result rejects stale extraction when content changed`() {
        assertThat(
            shouldApplyAiRefreshResult(
                extractedFromContent = "旧内容",
                latestContent = "新内容",
                latestSourceIsManual = false,
                force = true,
            ),
        ).isFalse()
    }

    @Test
    fun `should apply ai refresh result rejects manual latest source when not forced`() {
        assertThat(
            shouldApplyAiRefreshResult(
                extractedFromContent = "同一份内容",
                latestContent = "同一份内容",
                latestSourceIsManual = true,
                force = false,
            ),
        ).isFalse()
    }

    @Test
    fun `should apply ai refresh result allows forced refresh on unchanged content`() {
        assertThat(
            shouldApplyAiRefreshResult(
                extractedFromContent = "同一份内容",
                latestContent = "同一份内容",
                latestSourceIsManual = true,
                force = true,
            ),
        ).isTrue()
    }

    @Test
    fun `article insight auto generation accepts links and long captures`() {
        assertThat(shouldAutoGenerateArticleInsight("链接：https://example.com/post/123")).isTrue()
        assertThat(shouldAutoGenerateArticleInsight("长文正文".repeat(180))).isTrue()
    }

    @Test
    fun `article insight auto generation ignores short plain notes`() {
        assertThat(shouldAutoGenerateArticleInsight("今天想到一个很短的捕捉。")).isFalse()
    }
}
