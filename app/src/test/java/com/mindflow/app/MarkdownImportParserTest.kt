package com.mindflow.app

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.importing.MarkdownImportParser
import com.mindflow.app.data.model.NoteStatus
import org.junit.Test

class MarkdownImportParserTest {
    private val parser = MarkdownImportParser()

    @Test
    fun `parse restores exported note metadata and history`() {
        val markdown = """
            # MindFlow Export
            
            - 导出时间: 2026-03-28 10:00
            - 记录数量: 1
            
            ## 1. 家庭灵感池
            
            - 状态: 已实现
            - 创建时间: 2026-03-20 08:00
            - 更新时间: 2026-03-24 09:30
            - 已归档: 是
            
            ### 内容
            
            做一个能记录家里灵感的小工具
            
            ### 状态历史
            
            - 2026-03-20 08:00: 初始 -> 想法
            - 2026-03-22 12:00: 想法 -> 进行中
            - 2026-03-24 09:30: 进行中 -> 已实现
            
            ---
        """.trimIndent()

        val parsed = parser.parse(markdown).single()

        assertThat(parsed.topic).isEqualTo("家庭灵感池")
        assertThat(parsed.status).isEqualTo(NoteStatus.DONE)
        assertThat(parsed.isArchived).isTrue()
        assertThat(parsed.content).contains("家里灵感")
        assertThat(parsed.history).hasSize(3)
        assertThat(parsed.history.last().toStatus).isEqualTo(NoteStatus.DONE)
    }
}
