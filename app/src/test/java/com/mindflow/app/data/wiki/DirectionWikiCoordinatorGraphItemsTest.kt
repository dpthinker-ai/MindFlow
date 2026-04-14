package com.mindflow.app.data.wiki

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.connect.ResearchEvidenceType
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import org.junit.Test

class DirectionWikiCoordinatorGraphItemsTest {
    @Test
    fun `buildGraphConceptSearchItems keeps shared concept entries per thread`() {
        val conceptItems = buildGraphConceptSearchItems(
            conceptBuckets = mapOf(
                "睡眠" to listOf(
                    directionSummary("folder:work", "工作") to note(1, "记录 A", tags = listOf("睡眠")),
                    directionSummary("tag:health", "健康") to note(2, "记录 B", tags = listOf("睡眠")),
                ),
            ),
        )

        assertThat(conceptItems).hasSize(2)
        assertThat(conceptItems.map { it.threadKey }).containsExactly("folder:work", "tag:health")
        assertThat(conceptItems.map { it.title }.distinct()).containsExactly("睡眠")
    }

    @Test
    fun `buildGraphObjectSearchItems keeps shared object entries per thread`() {
        val objectItems = buildGraphObjectSearchItems(
            candidates = listOf(
                candidate(threadKey = "folder:work", threadTitle = "工作", noteId = 1L),
                candidate(threadKey = "tag:learning", threadTitle = "学习", noteId = 2L),
                candidate(threadKey = "folder:work", threadTitle = "工作", noteId = 3L),
            ),
        )

        assertThat(objectItems).hasSize(2)
        assertThat(objectItems.map { it.threadKey }).containsExactly("folder:work", "tag:learning")
        assertThat(objectItems.map { it.title }.distinct()).containsExactly("固定复盘流程")
        assertThat(objectItems.all { it.type == KnowledgeLayerSearchType.METHOD }).isTrue()
    }

    private fun candidate(
        threadKey: String,
        threadTitle: String,
        noteId: Long,
    ) = KnowledgeObjectCandidate(
        type = KnowledgeObjectType.METHOD,
        title = "固定复盘流程",
        summary = "每周固定复盘一次。",
        noteId = noteId,
        updatedAt = 1_000L + noteId,
        threadKey = threadKey,
        threadTitle = threadTitle,
        relatedConcepts = listOf("复盘"),
        evidenceType = ResearchEvidenceType.VERIFIED,
    )

    private fun directionSummary(
        threadKey: String,
        title: String,
    ) = DirectionWikiDirectionSummary(
        threadKey = threadKey,
        slug = threadKey,
        title = title,
        stage = DirectionStage.FORMING,
        updatedAt = 1_000L,
    )

    private fun note(
        id: Long,
        topic: String,
        tags: List<String>,
    ) = NoteEntity(
        id = id,
        content = "$topic 的内容",
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
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
