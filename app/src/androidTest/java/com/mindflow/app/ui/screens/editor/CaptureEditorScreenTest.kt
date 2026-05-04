package com.mindflow.app.ui.screens.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mindflow.app.ui.theme.MindFlowTheme
import org.junit.Rule
import org.junit.Test

class CaptureEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureScreen_keepsInputFlowClean() {
        composeRule.setContent {
            MindFlowTheme {
                CaptureEditorScreen(
                    uiState = NoteEditorUiState(
                        isNew = true,
                        content = "把这个想法先记下来",
                        hasUnsavedChanges = true,
                    ),
                    onBack = {},
                    onContentChange = {},
                    onSave = {},
                    onSaveAndExit = {},
                )
            }
        }

        composeRule.onNodeWithTag(CaptureContentFieldTestTag).assertExists()
        composeRule.onNodeWithText("纯文本输入").assertExists()
        composeRule.onNodeWithText("内容（可编辑）").assertExists()
        composeRule.onNodeWithText("AI 建议标题").assertExists()
        composeRule.onNodeWithText("类型识别").assertExists()
        composeRule.onNodeWithText("附件").assertExists()
        composeRule.onNodeWithText("更多选项").assertExists()
        composeRule.onNodeWithText("添加图片").assertExists()
        composeRule.onNodeWithText("添加链接").assertExists()
        composeRule.onNodeWithText("存为草稿").assertExists()
        composeRule.onNodeWithText("AI 建议").assertExists()
        composeRule.onNodeWithText("完成记录").assertExists()
        composeRule.onNodeWithContentDescription("保存").assertExists()
        composeRule.onNodeWithContentDescription("语音输入").assertDoesNotExist()
        composeRule.onNodeWithText("AI 整理").assertDoesNotExist()
        composeRule.onNodeWithText("补充信息").assertDoesNotExist()
        composeRule.onNodeWithText("方向提示").assertDoesNotExist()
        composeRule.onNodeWithText("旧知识召回").assertDoesNotExist()
        composeRule.onNodeWithText("输入时只做输入，整理和归类放到保存后。").assertDoesNotExist()
        composeRule.onNodeWithText("写下想法").assertDoesNotExist()
        composeRule.onNodeWithText("这里不做方向判断，不做旧知识召回，也不要求你先补结构。").assertDoesNotExist()
        composeRule.onNodeWithText("先记下来，别让整理动作打断输入。").assertDoesNotExist()
    }
}
