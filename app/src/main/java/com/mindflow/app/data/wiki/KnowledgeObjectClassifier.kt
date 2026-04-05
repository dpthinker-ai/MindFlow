package com.mindflow.app.data.wiki

import com.mindflow.app.data.connect.NoteInsightSummaryExtractor
import com.mindflow.app.data.connect.ResearchEvidenceAnalyzer
import com.mindflow.app.data.connect.ResearchEvidenceType
import com.mindflow.app.data.local.entity.NoteEntity
import java.util.Locale

enum class KnowledgeObjectType(
    val folderName: String,
    val displayName: String,
) {
    QUESTION("questions", "问题"),
    METHOD("methods", "方法"),
    EXPERIMENT("experiments", "实验"),
}

data class KnowledgeObjectCandidate(
    val type: KnowledgeObjectType,
    val title: String,
    val summary: String,
    val noteId: Long,
    val updatedAt: Long,
    val threadTitle: String,
)

object KnowledgeObjectClassifier {
    fun classify(note: NoteEntity, threadTitle: String): List<KnowledgeObjectCandidate> {
        val summary = NoteInsightSummaryExtractor.extract(note).ifBlank {
            note.content.trim().lineSequence().firstOrNull().orEmpty()
        }.trim()
        if (summary.isBlank()) return emptyList()

        val title = note.topic.ifBlank { summary.take(40) }.trim()
        val content = "${note.topic}\n${note.content}".lowercase(Locale.getDefault())
        val evidenceType = ResearchEvidenceAnalyzer.classify(note)
        val results = mutableListOf<KnowledgeObjectCandidate>()

        if (isQuestion(content)) {
            results += candidate(
                type = KnowledgeObjectType.QUESTION,
                title = title,
                summary = summary,
                note = note,
                threadTitle = threadTitle,
            )
        }

        if (isMethod(content)) {
            results += candidate(
                type = KnowledgeObjectType.METHOD,
                title = title,
                summary = summary,
                note = note,
                threadTitle = threadTitle,
            )
        }

        if (isExperiment(content, evidenceType)) {
            results += candidate(
                type = KnowledgeObjectType.EXPERIMENT,
                title = title,
                summary = summary,
                note = note,
                threadTitle = threadTitle,
            )
        }

        return results.distinctBy { "${it.type}:${it.title}" }
    }

    private fun candidate(
        type: KnowledgeObjectType,
        title: String,
        summary: String,
        note: NoteEntity,
        threadTitle: String,
    ) = KnowledgeObjectCandidate(
        type = type,
        title = title,
        summary = summary,
        noteId = note.id,
        updatedAt = note.updatedAt,
        threadTitle = threadTitle,
    )

    private fun isQuestion(content: String): Boolean =
        listOf("如何", "为什么", "是否", "怎么", "怎样", "能否", "问题", "?", "？")
            .any(content::contains)

    private fun isMethod(content: String): Boolean =
        listOf("方法", "流程", "步骤", "策略", "原则", "做法", "模板", "机制")
            .any(content::contains)

    private fun isExperiment(content: String, evidenceType: ResearchEvidenceType): Boolean =
        evidenceType == ResearchEvidenceType.HYPOTHESIS ||
            evidenceType == ResearchEvidenceType.VALIDATED ||
            listOf("验证", "实验", "测试", "试验", "假设", "试点", "看什么结果算成立")
                .any(content::contains)
}
