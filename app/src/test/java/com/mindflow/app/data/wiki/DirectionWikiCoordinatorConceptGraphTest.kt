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

class DirectionWikiCoordinatorConceptGraphTest {
    @Test
    fun `buildConceptGraphCandidates combines recent concept buckets with long-term object concepts`() {
        val candidates = buildConceptGraphCandidates(
            conceptBuckets = mapOf(
                "复盘" to listOf(
                    directionSummary("tag:health", "健康") to note(1L, "最近记录", tags = listOf("复盘")),
                ),
                "睡眠规律" to listOf(
                    directionSummary("tag:sleep", "睡眠") to note(2L, "规律记录", tags = listOf("睡眠规律")),
                ),
            ),
            objectCandidates = listOf(
                objectCandidate(
                    noteId = 3L,
                    relatedConcepts = listOf("复盘", "结构化"),
                ),
            ),
        )

        assertThat(candidates.map { it.title }).containsAtLeast("复盘", "睡眠规律", "结构化")
        assertThat(candidates).hasSize(3)
        assertThat(candidates.maxOf { it.updatedAt }).isEqualTo(3_000L)

        val retrospective = candidates.first { it.title == "复盘" }
        assertThat(retrospective.sourceIds).containsAtLeast("note:1", "methods:3")
        assertThat(retrospective.updatedAt).isEqualTo(3_000L)
    }

    @Test
    fun `buildConceptGraphCandidates keeps punctuation-heavy concepts distinct`() {
        val candidates = buildConceptGraphCandidates(
            conceptBuckets = mapOf(
                "C#" to listOf(
                    directionSummary("tag:dotnet", "Dotnet") to note(11L, "CSharp", tags = listOf("C#")),
                ),
                "C++" to listOf(
                    directionSummary("tag:cpp", "Cpp") to note(12L, "Cpp", tags = listOf("C++")),
                ),
            ),
            objectCandidates = emptyList(),
        )

        assertThat(candidates.map { it.title }).containsExactly("C++", "C#")
        assertThat(candidates.map { it.conceptId }.distinct()).hasSize(2)
    }

    @Test
    fun `concept graph json round-trip preserves default center and edges`() {
        val snapshot = ConceptGraphSnapshot(
            defaultCenterNodeId = "concept:a",
            nodes = listOf(
                ConceptGraphNode(
                    conceptId = "concept:a",
                    label = "A",
                    aliases = listOf("Alpha"),
                    summary = "中心节点",
                    hotnessScore = 0.9,
                    updatedAt = 4_000L,
                    sourceIds = listOf("note:1"),
                ),
                ConceptGraphNode(
                    conceptId = "concept:b",
                    label = "B",
                    aliases = listOf("Beta"),
                    summary = "邻接节点",
                    hotnessScore = 0.6,
                    updatedAt = 2_000L,
                    sourceIds = listOf("methods:3"),
                ),
            ),
            edges = listOf(
                ConceptGraphEdge(
                    fromConceptId = "concept:a",
                    toConceptId = "concept:b",
                    relationType = ConceptGraphRelationType.SUPPORTS,
                    reasonLine = "A 支撑 B。",
                    supportIds = listOf("note:1", "methods:3"),
                    confidence = 0.7,
                ),
            ),
            source = "llm",
            generatedAt = 5_000L,
        )

        val restored = snapshot.toConceptGraphJsonString().toConceptGraphSnapshot()

        assertThat(restored.defaultCenterNodeId).isEqualTo("concept:a")
        assertThat(restored.nodes.map { it.conceptId }).containsExactly("concept:a", "concept:b").inOrder()
        assertThat(restored.edges).hasSize(1)
        assertThat(restored.edges.single().fromConceptId).isEqualTo("concept:a")
        assertThat(restored.edges.single().toConceptId).isEqualTo("concept:b")
        assertThat(restored.edges.single().relationType).isEqualTo(ConceptGraphRelationType.SUPPORTS)
    }

    @Test
    fun `safe concept graph parser falls back to default snapshot on malformed json`() {
        val restored = parseConceptGraphSnapshotOrDefault("{not-valid")

        assertThat(restored).isEqualTo(ConceptGraphSnapshot())
    }

    private fun objectCandidate(
        noteId: Long,
        relatedConcepts: List<String>,
    ) = KnowledgeObjectCandidate(
        type = KnowledgeObjectType.METHOD,
        title = "固定复盘流程",
        summary = "每周固定复盘一次。",
        noteId = noteId,
        updatedAt = 3_000L,
        threadKey = "tag:learning",
        threadTitle = "学习",
        relatedConcepts = relatedConcepts,
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
