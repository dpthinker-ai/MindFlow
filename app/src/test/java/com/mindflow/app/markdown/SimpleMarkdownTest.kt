package com.mindflow.app.markdown

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SimpleMarkdownTest {
    @Test
    fun normalizeForDisplay_collapsesExtraBreaksAndTrims() {
        val normalized = SimpleMarkdown.normalizeForDisplay(
            "\r\n### 标题\r\n\r\n\r\n- 第一项\r\n- 第二项\r\n\r\n"
        )

        assertThat(normalized).isEqualTo(
            """
            ### 标题

            - 第一项
            - 第二项
            """.trimIndent()
        )
    }

    @Test
    fun parse_supportsCommonChatMarkdownStructure() {
        val blocks = SimpleMarkdown.parse(
            """
            ### 1. 工作与工具
            - **Codex 远程连接**：你记录了通过 SSH 连接远程开发环境。
            - **iWhisper 体验反馈**：你提到了输入框的问题。
            """.trimIndent()
        )

        assertThat(blocks).hasSize(2)
        assertThat(blocks.first()).isInstanceOf(MarkdownHeading::class.java)
        assertThat(blocks.last()).isInstanceOf(MarkdownBulletList::class.java)
    }
}
