package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.NoteStatus

enum class ResearchEvidenceType(
    val label: String,
) {
    SIGNAL("外部视角"),
    HYPOTHESIS("待验证"),
    VERIFIED("已查证"),
    VALIDATED("已验证"),
}

data class ResearchEvidenceSummary(
    val signalCount: Int = 0,
    val hypothesisCount: Int = 0,
    val verifiedCount: Int = 0,
    val validatedCount: Int = 0,
) {
    val summaryLine: String
        get() = buildList {
            if (signalCount > 0) add("外部视角 $signalCount")
            if (hypothesisCount > 0) add("待验证 $hypothesisCount")
            if (verifiedCount > 0) add("已查证 $verifiedCount")
            if (validatedCount > 0) add("已验证 $validatedCount")
        }.joinToString(" · ")
}

object ResearchEvidenceAnalyzer {
    fun summarize(notes: List<NoteEntity>): ResearchEvidenceSummary {
        val counts = notes
            .map(::classify)
            .groupingBy { it }
            .eachCount()

        return ResearchEvidenceSummary(
            signalCount = counts[ResearchEvidenceType.SIGNAL] ?: 0,
            hypothesisCount = counts[ResearchEvidenceType.HYPOTHESIS] ?: 0,
            verifiedCount = counts[ResearchEvidenceType.VERIFIED] ?: 0,
            validatedCount = counts[ResearchEvidenceType.VALIDATED] ?: 0,
        )
    }

    fun classify(note: NoteEntity): ResearchEvidenceType {
        val content = note.content
        return when {
            note.status == NoteStatus.DONE ||
                content.contains("验证成立") ||
                content.contains("已验证") ||
                content.contains("查证完成") ||
                content.contains("结果表明") ->
                ResearchEvidenceType.VALIDATED

            content.contains("我查到的内容") ||
                content.contains("查证结果") ||
                content.contains("对标") ||
                content.contains("资料来源") ->
                ResearchEvidenceType.VERIFIED

            content.contains("先验证") ||
                content.contains("看什么结果算成立") ||
                content.contains("外部假设") ||
                content.contains("我准备怎么验证") ->
                ResearchEvidenceType.HYPOTHESIS

            else -> ResearchEvidenceType.SIGNAL
        }
    }
}
