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
