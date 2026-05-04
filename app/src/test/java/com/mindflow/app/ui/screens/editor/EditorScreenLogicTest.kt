package com.mindflow.app.ui.screens.editor

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiProvider
import com.mindflow.app.data.ai.AiTaskType
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.ui.navigation.CaptureMode
import java.io.File
import org.junit.Test

class EditorScreenLogicTest {
    @Test
    fun shouldComputeEditorInsights_requiresLoadedExistingNoteAndExpandedPanels() {
        assertThat(
            shouldComputeEditorInsights(
                isLoading = false,
                noteId = 42L,
                metadataExpanded = true,
                extraInfoExpanded = true,
            ),
        ).isTrue()

        assertThat(
            shouldComputeEditorInsights(
                isLoading = false,
                noteId = null,
                metadataExpanded = true,
                extraInfoExpanded = true,
            ),
        ).isFalse()

        assertThat(
            shouldComputeEditorInsights(
                isLoading = true,
                noteId = 42L,
                metadataExpanded = true,
                extraInfoExpanded = true,
            ),
        ).isFalse()

        assertThat(
            shouldComputeEditorInsights(
                isLoading = false,
                noteId = 42L,
                metadataExpanded = false,
                extraInfoExpanded = true,
            ),
        ).isFalse()
    }

    @Test
    fun buildEditorDraftAnalysis_skipsThreadMatchingForNewDrafts() {
        val result = buildEditorDraftAnalysis(
            EditorDraftAnalysisInput(
                isLoading = false,
                noteId = null,
                topic = "新的火花",
                content = "先随手记下来，晚点再整理。",
                folderKey = "work",
                tags = listOf("产品"),
                allNotes = listOf(
                    sampleNote(
                        id = 1L,
                        topic = "已有方向",
                        content = "一条旧记录",
                        folderKey = "work",
                        tags = listOf("产品"),
                    ),
                    sampleNote(
                        id = 2L,
                        topic = "另一条记录",
                        content = "第二条旧记录",
                        folderKey = "work",
                        tags = listOf("产品"),
                    ),
                ),
            ),
        )

        assertThat(result.relatedNotes).isEmpty()
        assertThat(result.suggestedThread).isNull()
    }

    @Test
    fun shouldRequestEditorKnowledgeRecall_requiresManualTrigger() {
        assertThat(
            shouldRequestEditorKnowledgeRecall(
                isLoading = false,
                requestVersion = 0,
            ),
        ).isFalse()

        assertThat(
            shouldRequestEditorKnowledgeRecall(
                isLoading = true,
                requestVersion = 1,
            ),
        ).isFalse()

        assertThat(
            shouldRequestEditorKnowledgeRecall(
                isLoading = false,
                requestVersion = 1,
            ),
        ).isTrue()
    }

    @Test
    fun buildEditorAiModeSummary_explainsEffectiveStrategy() {
        assertThat(
            buildEditorAiModeSummary(
                mode = AiExecutionMode.AUTOMATIC,
                onDeviceReady = true,
            ),
        ).isEqualTo("当前策略：自动。编辑页会先云侧，失败后回退端侧。")

        assertThat(
            buildEditorAiModeSummary(
                mode = AiExecutionMode.AUTOMATIC,
                onDeviceReady = false,
            ),
        ).isEqualTo("当前策略：自动。端侧未就绪，这次会直接走云侧。")

        assertThat(
            buildEditorAiModeSummary(
                mode = AiExecutionMode.ON_DEVICE_ONLY,
                onDeviceReady = false,
            ),
        ).isEqualTo("当前策略：仅端侧。本地模型未就绪时，这类整理不会返回结果。")
    }

    @Test
    fun buildEditorAiRunFeedback_reportsProviderAndFallback() {
        assertThat(
            buildEditorAiRunFeedback(
                taskType = AiTaskType.POLISH_CONTENT,
                provider = AiProvider.ON_DEVICE,
                fallbackOccurred = false,
            ),
        ).isEqualTo("本次整理正文由端侧完成。")

        assertThat(
            buildEditorAiRunFeedback(
                taskType = AiTaskType.EXTRACT_TAGS,
                provider = AiProvider.CLOUD,
                fallbackOccurred = true,
            ),
        ).isEqualTo("本次整理标签由云侧完成，另一侧没有给出可用结果。")

        assertThat(
            buildEditorAiRunFeedback(
                taskType = AiTaskType.POLISH_TITLE,
                provider = AiProvider.CLOUD,
                fallbackOccurred = false,
            ),
        ).isEqualTo("本次润色标题由云侧完成。")
        assertThat(
            buildEditorAiRunFeedback(
                taskType = AiTaskType.SUMMARIZE_NOTE,
                provider = AiProvider.ON_DEVICE,
                fallbackOccurred = false,
            ),
        ).isEqualTo("本次整理洞察由端侧完成。")
    }

    @Test
    fun textContentEditorMinLines_keepsShortContentCompact() {
        assertThat(textContentEditorMinLines("一行短内容")).isEqualTo(3)
        assertThat(textContentEditorMinLines("第一行\n第二行\n第三行\n第四行")).isEqualTo(4)
        assertThat(textContentEditorMinLines("1\n2\n3\n4\n5\n6\n7")).isEqualTo(6)
    }

    @Test
    fun voiceTranscriptEditorMinLines_growsWithLongTranscript() {
        assertThat(voiceTranscriptEditorMinLines("短句")).isEqualTo(4)
        assertThat(voiceTranscriptEditorMinLines("一".repeat(260))).isAtLeast(6)
        assertThat(voiceTranscriptEditorMinLines("一".repeat(460))).isAtLeast(8)
    }

    @Test
    fun shouldAutoGenerateTextInsight_onlyRunsForPlainTextRecords() {
        assertThat(shouldAutoGenerateTextInsight("一段普通文本记录")).isTrue()
        assertThat(shouldAutoGenerateTextInsight("图片：/tmp/image.jpg\n说明：截图")).isFalse()
        assertThat(shouldAutoGenerateTextInsight("原始录音：/tmp/audio.m4a\n原始内容：转写")).isFalse()
    }

    @Test
    fun voiceTranscriptFromContent_ignoresAudioFilePath() {
        val content = """
            原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-1.m4a
            语音转写（可编辑）：今天先把语音输入体验调顺
            AI 快速提取：输入体验需要以转写文本为主
            关键信息：增加频谱动效；隐藏文件地址
        """.trimIndent()

        assertThat(voiceTranscriptFromContent(content)).isEqualTo("今天先把语音输入体验调顺")
        assertThat(voiceKeyPointsFromContent(content)).containsExactly("增加频谱动效", "隐藏文件地址")

        val emptyTranscript = """
            原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-2.m4a
            语音转写（可编辑）：
            识别信息：
        """.trimIndent()
        assertThat(voiceTranscriptFromContent(emptyTranscript)).isEmpty()
        assertThat(voiceTranscriptFromContent(emptyTranscript)).doesNotContain("语音转写")
    }

    @Test
    fun voiceTranscriptEmptyState_usesUserFacingStatusText() {
        assertThat(
            voiceTranscriptPlaceholder(
                hasRecording = true,
                recordingState = "SAVED",
                isTranscribing = true,
            ),
        ).isEqualTo("正在转写…")
        assertThat(
            voiceTranscriptPlaceholder(
                hasRecording = true,
                recordingState = "SAVED",
                isTranscribing = false,
            ),
        ).isEqualTo("转写暂未完成，可先手动补充内容。")
        assertThat(voiceTranscriptStatus(hasRecording = true, transcript = "", isTranscribing = true)).isEqualTo("转写中")
        assertThat(
            voiceTranscriptStatus(
                hasRecording = true,
                transcript = "",
                isTranscribing = false,
                recognitionStatus = "转写失败：Gemma 4 没有返回可用转写内容",
            ),
        ).isEqualTo("失败")
        assertThat(voiceTranscriptStatus(hasRecording = true, transcript = "", isTranscribing = false)).isEqualTo("待转写")
        assertThat(voiceTranscriptStatus(hasRecording = false, transcript = "")).isEqualTo("待录音")
        assertThat(voiceTranscriptStatus(hasRecording = true, transcript = "今天的语音内容")).isEqualTo("可编辑")
        assertThat(
            voiceTranscriptHelperText(
                recognitionStatus = "转写失败：Gemma 4 没有返回可用转写内容",
                isTranscribing = false,
                transcript = "",
            ),
        ).isEqualTo("转写失败：Gemma 4 没有返回可用转写内容")
        assertThat(
            voiceTranscriptHelperText(
                recognitionStatus = "转写失败：端侧转写没有返回可用文本，请确认 Gemma 4 模型已就绪",
                isTranscribing = false,
                transcript = "",
            ),
        ).isEqualTo("转写失败：旧版录音格式未被端侧音频输入识别；重新录音后会使用 16kHz WAV 转写")
    }

    @Test
    fun voiceTitleForDisplay_hidesGeneratedFallbackTitleUntilTranscriptExists() {
        assertThat(
            voiceTitleForDisplay(
                topic = "VoiceEmptyStateCheck",
                topicSource = TopicSource.RULE,
                transcript = "",
            ),
        ).isEmpty()
        assertThat(
            voiceTitleForDisplay(
                topic = "手动标题",
                topicSource = TopicSource.MANUAL,
                transcript = "",
            ),
        ).isEqualTo("手动标题")
        assertThat(
            voiceTitleForDisplay(
                topic = "语音输入验证",
                topicSource = TopicSource.RULE,
                transcript = "今天验证语音转写",
            ),
        ).isEqualTo("语音输入验证")
    }

    @Test
    fun voiceAiInsightPendingText_dependsOnTranscriptState() {
        assertThat(
            voiceAiInsightPendingText(
                transcript = "",
                recognitionStatus = "",
                isTranscribing = true,
            ),
        ).isEqualTo("转写完成后会自动整理关键信息")
        assertThat(
            voiceAiInsightPendingText(
                transcript = "今天验证语音转写",
                recognitionStatus = "",
                isTranscribing = false,
            ),
        ).isEqualTo("正在整理转写内容")
        assertThat(
            voiceAiInsightPendingText(
                transcript = "",
                recognitionStatus = "转写失败：端侧转写没有返回可用文本",
                isTranscribing = false,
            ),
        ).isEqualTo("当前没有转写内容，暂不生成 AI 洞察")
    }

    @Test
    fun shouldAttemptVoiceTranscription_requiresAudioAndSkipsFailedStatus() {
        val pending = """
            原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-2.m4a
            语音转写（可编辑）：
            识别信息：音频已保存，转写暂未完成，可先手动补充。
        """.trimIndent()
        val failed = pending.replace("音频已保存，转写暂未完成，可先手动补充。", "转写失败：端侧模型没有返回可用文本")

        assertThat(shouldAttemptVoiceTranscription(pending, isTranscribingVoice = false)).isTrue()
        assertThat(shouldAttemptVoiceTranscription(pending, isTranscribingVoice = true)).isFalse()
        assertThat(shouldAttemptVoiceTranscription(failed, isTranscribingVoice = false)).isFalse()
        assertThat(
            shouldAttemptVoiceTranscription(
                content = "语音转写（可编辑）：已经有转写",
                isTranscribingVoice = false,
                audioPathOverride = "/tmp/voice.m4a",
            ),
        ).isFalse()
    }

    @Test
    fun parseEditorAiTraceSnapshot_readsLatestTrace() {
        val parsed = parseEditorAiTraceSnapshot(
            """
            {"taskType":"POLISH_CONTENT","providerUsed":"CLOUD","fallbackOccurred":true,"fallbackReason":"empty_payload","latencyMs":812}
            """.trimIndent(),
        )

        assertThat(parsed).isNotNull()
        assertThat(parsed?.taskType).isEqualTo(AiTaskType.POLISH_CONTENT)
        assertThat(parsed?.providerUsed).isEqualTo(AiProvider.CLOUD)
        assertThat(parsed?.fallbackOccurred).isTrue()
    }

    @Test
    fun applyCaptureActionState_setsTodayTaskAndProjectFields() {
        val base = NoteEditorUiState(
            content = "把语音输入先接住",
            tags = listOf("语音"),
        )

        val today = applyCaptureActionState(base, CapturePostAction.ADD_TO_TODAY)
        assertThat(today.tags).contains("今天")
        assertThat(today.hasUnsavedChanges).isTrue()

        val task = applyCaptureActionState(base, CapturePostAction.CONVERT_TO_TASK)
        assertThat(task.status).isEqualTo(NoteStatus.IN_PROGRESS)
        assertThat(task.tags).contains("任务")
        assertThat(task.hasUnsavedChanges).isTrue()

        val project = applyCaptureActionState(base, CapturePostAction.ADD_TO_PROJECT)
        assertThat(project.folderKey).isEqualTo("project")
        assertThat(project.tags).contains("项目")
        assertThat(project.folderEdited).isTrue()
        assertThat(project.hasUnsavedChanges).isTrue()
    }

    @Test
    fun inputReferenceLabels_matchLatestDesign() {
        assertThat(textInputReferenceLabels()).containsExactly(
            "纯文本输入",
            "内容（可编辑）",
            "AI 建议标题",
            "类型识别",
            "标签",
            "附件",
            "完成记录",
        ).inOrder()

        assertThat(voiceInputReferenceLabels()).containsExactly(
            "语音输入",
            "录音动态效果",
            "原始内容信息",
            "AI 洞察",
            "删除",
            "继续录入",
            "完成解析",
        ).inOrder()

        assertThat(imageInputReferenceLabels()).containsExactly(
            "图片输入",
            "图片预览",
            "图像理解结果",
            "关键信息提取",
            "结构化识别",
            "OCR 文本(可选)",
            "重新拍摄",
            "从相册导入",
            "继续解析",
        ).inOrder()
    }

    @Test
    fun applyTextCaptureTypeSelection_updatesStatusAndTypeTag() {
        val initial = NoteEditorUiState(
            tags = listOf("产品", "灵感"),
            status = NoteStatus.IDEA,
        )

        val task = applyTextCaptureTypeSelection(initial, "任务")
        assertThat(task.status).isEqualTo(NoteStatus.IN_PROGRESS)
        assertThat(task.tags).containsExactly("任务", "产品").inOrder()
        assertThat(task.tagsEdited).isTrue()

        val document = applyTextCaptureTypeSelection(task, "文档")
        assertThat(document.status).isEqualTo(NoteStatus.IDEA)
        assertThat(document.tags).containsExactly("文档", "产品").inOrder()

        val idea = applyTextCaptureTypeSelection(document, "想法")
        assertThat(idea.status).isEqualTo(NoteStatus.IDEA)
        assertThat(idea.tags).containsExactly("想法", "产品").inOrder()
    }

    @Test
    fun contentReferenceLabels_matchLatestDesign() {
        assertThat(textContentReferenceLabels()).containsExactly(
            "文本记录",
            "标题",
            "重新生成标题",
            "正文",
            "润色正文",
            "记录类型",
            "相关主题",
            "AI 洞察",
            "附件",
            "插入今天",
            "链接任务",
            "导入项目",
        ).inOrder()
        assertThat(textContentReferenceExcludedLabels()).containsExactly(
            "AI 整理",
            "归档与时间",
            "标题（可编辑）",
            "生成标题",
            "润色标题",
            "正文内容（可全文编辑）",
            "AI 摘要",
            "关键要点",
            "重新生成摘要与要点",
            "Markdown 预览",
            "编辑正文",
            "已手动确认",
            "先存下这颗火花",
            "B",
            "I",
            "U",
            "•",
            "1.",
            "☑",
        ).inOrder()
        assertThat(mediaContentReferenceExcludedLabels()).containsExactly(
            "归档与时间",
            "Markdown 预览",
            "编辑正文",
            "已手动确认",
        ).inOrder()

        assertThat(voiceContentReferenceLabels()).containsExactly(
            "语音记录",
            "语音暂存音频（可回放）",
            "播放音频",
            "标题",
            "语音转写（可编辑）",
            "AI 洞察",
            "关键信息",
            "记录信息",
            "插入今天",
            "链接任务",
            "导入项目",
        ).inOrder()

        assertThat(imageContentReferenceLabels()).containsExactly(
            "图片记录",
            "图片预览",
            "图片理解摘要（可编辑）",
            "关键信息（可编辑）",
            "视觉识别结果",
            "OCR 全文（可选）",
            "记录信息（可修改）",
            "插入今天",
            "链接任务",
            "导入项目",
        ).inOrder()
    }

    @Test
    fun inlineVoiceCapture_entersVoiceScreenAndWaitsForManualStart() {
        assertThat(
            shouldShowVoiceCaptureScreen(
                isNew = true,
                launchCaptureMode = CaptureMode.TEXT,
                inlineVoiceRequested = true,
            ),
        ).isTrue()
        assertThat(
            shouldShowVoiceCaptureScreen(
                isNew = false,
                launchCaptureMode = CaptureMode.TEXT,
                inlineVoiceRequested = true,
            ),
        ).isTrue()
        assertThat(
            shouldShowVoiceCaptureScreen(
                isNew = true,
                launchCaptureMode = CaptureMode.TEXT,
                inlineVoiceRequested = false,
            ),
        ).isFalse()
        assertThat(
            shouldAutoStartVoiceCapture(
                launchAutoStart = false,
                inlineVoiceRequested = true,
            ),
        ).isFalse()
    }

    @Test
    fun normalizeVoiceAmplitude_keepsQuietInputStillAndScalesRealVoice() {
        assertThat(normalizeVoiceAmplitude(0f)).isEqualTo(0f)
        assertThat(normalizeVoiceAmplitude(0.03f)).isEqualTo(0f)
        assertThat(normalizeVoiceAmplitude(0.20f)).isGreaterThan(0f)
        assertThat(normalizeVoiceAmplitude(0.80f)).isGreaterThan(normalizeVoiceAmplitude(0.20f))
    }

    @Test
    fun capturePrivateFilesForDiscard_keepsCleanupInsideCaptureStorage() {
        val filesDir = File("/data/user/0/com.mindflow.app/files")
        val files = capturePrivateFilesForDiscard(
            content = """
                原始录音：/data/user/0/com.mindflow.app/files/captures/voice/voice-1.m4a
                图片：/data/user/0/com.mindflow.app/files/captures/images/image-1.jpg
                外部文件：/sdcard/Download/source.jpg
                其他文件：/data/user/0/com.other/files/captures/voice/voice-2.m4a
            """.trimIndent(),
            appFilesDir = filesDir,
        )

        assertThat(files.map(File::getPath)).isEqualTo(
            listOf(
                "/data/user/0/com.mindflow.app/files/captures/voice/voice-1.m4a",
                "/data/user/0/com.mindflow.app/files/captures/images/image-1.jpg",
            ),
        )
    }

    private fun sampleNote(
        id: Long,
        topic: String,
        content: String,
        folderKey: String,
        tags: List<String>,
    ): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = folderKey,
        folderSource = FolderSource.MANUAL,
        tags = tags,
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L + id,
        updatedAt = 2_000L + id,
    )
}
