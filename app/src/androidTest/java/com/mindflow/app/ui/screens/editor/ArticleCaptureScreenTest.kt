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
                    onEnsureArticleExtraction = {},
                    onSave = {},
                    onSaveAndExit = {},
                    onCaptureAction = {},
                )
            }
        }

        composeRule.onNodeWithText("链接输入").assertExists()
        composeRule.onNodeWithText("链接").assertExists()
        composeRule.onNodeWithText("正文内容").assertExists()
        composeRule.onNodeWithText("AI 洞察").assertExists()
        composeRule.onNodeWithText("相关主题").assertExists()
        composeRule.onNodeWithText("插入今天").assertExists()
        composeRule.onNodeWithText("链接任务").assertExists()
        composeRule.onNodeWithText("导入项目").assertExists()
        composeRule.onNodeWithText("解析正文").assertExists()
    }
}
