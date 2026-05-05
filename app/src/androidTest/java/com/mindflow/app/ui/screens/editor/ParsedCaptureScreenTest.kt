package com.mindflow.app.ui.screens.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mindflow.app.ui.theme.MindFlowTheme
import org.junit.Rule
import org.junit.Test

class ParsedCaptureScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun voiceCaptureScreen_showsVoiceParserSections() {
        composeRule.setContent {
            MindFlowTheme {
                VoiceCaptureScreen(
                    uiState = NoteEditorUiState(
                        isNew = true,
                        topic = "语音输入",
                        content = "原始内容：明天继续验证新增页面",
                        tags = listOf("语音"),
                    ),
                    onBack = {},
                    onContentChange = {},
                    onEnsureVoiceInsight = {},
                    onEnsureVoiceTranscription = {},
                    onSave = {},
                    onSaveAndExit = {},
                    onCaptureAction = {},
                )
            }
        }

        composeRule.onNodeWithText("语音输入").assertExists()
        composeRule.onNodeWithText("原始内容信息").assertExists()
        composeRule.onNodeWithText("开始录音").assertExists()
        composeRule.onNodeWithText("AI 洞察").assertExists()
        composeRule.onNodeWithText("语音转写（可编辑）").assertExists()
        composeRule.onNodeWithText("删除").assertExists()
        composeRule.onNodeWithText("继续录入").assertExists()
        composeRule.onNodeWithText("完成解析").assertExists()
    }

    @Test
    fun imageCaptureScreen_showsImageParserSections() {
        composeRule.setContent {
            MindFlowTheme {
                ImageCaptureScreen(
                    uiState = NoteEditorUiState(
                        isNew = true,
                        topic = "图片记录",
                        content = "图片：\n补充说明：看板截图",
                        tags = listOf("图片"),
                    ),
                    onBack = {},
                    onContentChange = {},
                    onEnsureImageUnderstanding = {},
                    onSave = {},
                    onSaveAndExit = {},
                    onCaptureAction = {},
                )
            }
        }

        composeRule.onNodeWithText("图片输入").assertExists()
        composeRule.onNodeWithText("图片预览").assertExists()
        composeRule.onNodeWithText("图像理解结果").assertExists()
        composeRule.onNodeWithText("关键信息提取").assertExists()
        composeRule.onNodeWithText("结构化识别").assertExists()
        composeRule.onNodeWithText("OCR 文本(可选)").assertExists()
        composeRule.onNodeWithText("重新拍摄").assertExists()
        composeRule.onNodeWithText("从相册导入").assertExists()
        composeRule.onNodeWithText("继续解析").assertExists()
        composeRule.onNodeWithText("OCR 全文识别").assertDoesNotExist()
    }
}
