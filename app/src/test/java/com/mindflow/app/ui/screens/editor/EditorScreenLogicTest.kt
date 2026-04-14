package com.mindflow.app.ui.screens.editor

import com.google.common.truth.Truth.assertThat
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
