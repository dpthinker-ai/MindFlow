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
                    onVoiceCapture = {},
                )
            }
        }

        composeRule.onNodeWithTag(CaptureContentFieldTestTag).assertExists()
        composeRule.onNodeWithText("语音输入").assertExists()
        composeRule.onNodeWithText("先存下这颗火花").assertExists()
        composeRule.onNodeWithText("AI 整理").assertDoesNotExist()
        composeRule.onNodeWithText("补充信息").assertDoesNotExist()
        composeRule.onNodeWithText("方向提示").assertDoesNotExist()
        composeRule.onNodeWithText("旧知识召回").assertDoesNotExist()
    }
}
