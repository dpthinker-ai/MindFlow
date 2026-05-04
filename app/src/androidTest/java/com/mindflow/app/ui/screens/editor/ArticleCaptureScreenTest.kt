package com.mindflow.app.ui.screens.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mindflow.app.ui.theme.MindFlowTheme
import org.junit.Rule
import org.junit.Test

class ArticleCaptureScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun articleCaptureScreen_showsParserSections() {
        composeRule.setContent {
            MindFlowTheme {
                ArticleCaptureScreen(
                    uiState = NoteEditorUiState(
                        isNew = true,
                        topic = "文章收藏",
                        content = "链接：https://uxdesign.cc/design-system-value\n补充说明：设计系统价值",
                        tags = listOf("文章"),
                    ),
                    onBack = {},
                    onContentChange = {},
                    onSave = {},
                    onSaveAndExit = {},
                    onCaptureAction = {},
                )
            }
        }

        composeRule.onNodeWithText("文章收藏解析").assertExists()
        composeRule.onNodeWithText("AI 生成摘要").assertExists()
        composeRule.onNodeWithText("关键要点").assertExists()
        composeRule.onNodeWithText("相关主题").assertExists()
        composeRule.onNodeWithText("插入今天").assertExists()
        composeRule.onNodeWithText("链接任务").assertExists()
        composeRule.onNodeWithText("导入项目").assertExists()
        composeRule.onNodeWithText("查看原文").assertExists()
    }
}
