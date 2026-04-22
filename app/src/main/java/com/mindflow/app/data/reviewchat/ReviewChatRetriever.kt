package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.local.entity.NoteEntity

internal data class RankedReviewChatNote(
    val note: NoteEntity,
    val score: Int,
)

internal object ReviewChatRetriever {
    fun rank(
        query: ReviewChatParsedQuery,
        notes: List<NoteEntity>,
    ): List<RankedReviewChatNote> = notes
        .map { note ->
            RankedReviewChatNote(
                note = note,
                score = scoreNote(query, note),
            )
        }
        .sortedWith(
            compareByDescending<RankedReviewChatNote> { it.score }
                .thenByDescending { it.note.createdAt },
        )

    private fun scoreNote(
        query: ReviewChatParsedQuery,
        note: NoteEntity,
    ): Int {
        val title = note.topic.lowercase()
        val content = note.content.lowercase()
        val folder = note.folderKey.orEmpty().lowercase()
        val tags = note.tags.map(String::lowercase)

        var score = 0

        query.entityTerms.forEach { term ->
            val normalized = term.lowercase()
            score += weightedTermScore(
                term = normalized,
                title = title,
                tags = tags,
                folder = folder,
                content = content,
                titleWeight = 24,
                tagWeight = 18,
                folderWeight = 14,
                contentWeight = 8,
            )
        }

        query.keywords
            .map(String::lowercase)
            .distinct()
            .filterNot { keyword -> query.entityTerms.any { entity -> entity.lowercase() == keyword } }
            .forEach { keyword ->
                score += weightedTermScore(
                    term = keyword,
                    title = title,
                    tags = tags,
                    folder = folder,
                    content = content,
                    titleWeight = 10,
                    tagWeight = 8,
                    folderWeight = 6,
                    contentWeight = 3,
                )
            }

        if (query.entityTerms.isNotEmpty() && query.entityTerms.any { term -> title == term.lowercase() }) {
            score += 12
        }
        if (query.operation == ReviewChatQueryOperation.ANALYZE && query.entityTerms.isNotEmpty() && score > 0) {
            score += 4
        }
        return score
    }

    private fun weightedTermScore(
        term: String,
        title: String,
        tags: List<String>,
        folder: String,
        content: String,
        titleWeight: Int,
        tagWeight: Int,
        folderWeight: Int,
        contentWeight: Int,
    ): Int {
        if (term.isBlank()) return 0
        val negatedInContent = listOf(
            "${term}无关",
            "和${term}无关",
            "与${term}无关",
            "${term}没关系",
            "和${term}没关系",
            "与${term}没关系",
        ).any(content::contains)
        if (negatedInContent) return 0

        var score = 0
        if (title.contains(term)) score += titleWeight
        if (tags.any { it.contains(term) }) score += tagWeight
        if (folder.contains(term)) score += folderWeight
        if (content.contains(term)) score += contentWeight
        return score
    }
}
