package com.mindflow.app.data.reviewchat

import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.topic.AiChatResult

internal object ReviewChatCategorySummarizer {
    private const val BATCH_SIZE = 24
    private const val MAX_BATCHES = 6

    suspend fun summarize(
        question: String,
        notes: List<NoteEntity>,
        runCloud: suspend (String) -> AiChatResult,
    ): List<String> {
        if (notes.isEmpty()) return emptyList()

        val batches = notes
            .sortedBy(NoteEntity::createdAt)
            .chunked(BATCH_SIZE)
            .take(MAX_BATCHES)

        val batchCandidates = batches.flatMapIndexed { index, batch ->
            when (val result = runCloud(buildBatchPrompt(question, index + 1, batches.size, batch))) {
                is AiChatResult.Success -> parseCategoryCandidates(result.content)
                is AiChatResult.Failure -> emptyList()
            }
        }.distinct()

        if (batchCandidates.isEmpty()) return emptyList()

        val mergedCandidates = when (
            val result = runCloud(buildMergePrompt(question, batchCandidates))
        ) {
            is AiChatResult.Success -> parseCategoryCandidates(result.content)
            is AiChatResult.Failure -> emptyList()
        }

        return (mergedCandidates.ifEmpty { batchCandidates })
            .distinct()
            .take(8)
    }

    private fun buildBatchPrompt(
        question: String,
        batchIndex: Int,
        totalBatches: Int,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("你在归纳一批个人历史记录的高层类别。")
        appendLine("用户问题：$question")
        appendLine("当前批次：$batchIndex/$totalBatches")
        appendLine("输出要求：")
        appendLine("1. 只输出 2 到 5 行。")
        appendLine("2. 每行格式固定为 `类别名称：包含的信息`。")
        appendLine("3. 不要编号，不要项目符号，不要写“类别：类别名称”。")
        appendLine("4. 不要把时间范围、统计信息、历史记录、查询结果当成类别。")
        appendLine("5. 类别要尽量上层、可合并，避免太细。")
        appendLine("记录：")
        notes.forEach { note ->
            appendLine(
                "${note.createdLocalDate().format(reviewChatDateFormatter)}《${note.topic.ifBlank { "未命名记录" }}》：" +
                    note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(80)
            )
        }
    }

    private fun buildMergePrompt(
        question: String,
        candidates: List<String>,
    ): String = buildString {
        appendLine("你在合并多批个人历史记录的类别候选。")
        appendLine("用户问题：$question")
        appendLine("输出要求：")
        appendLine("1. 只输出 4 到 8 行。")
        appendLine("2. 每行格式固定为 `类别名称：包含的信息`。")
        appendLine("3. 合并同类项，保留高层类别。")
        appendLine("4. 不要编号，不要项目符号，不要写“类别：类别名称”。")
        appendLine("5. 不要把时间范围、统计信息、历史记录、查询结果当成类别。")
        appendLine("候选：")
        candidates.forEach(::appendLine)
    }

    internal fun parseCategoryCandidates(content: String): List<String> =
        content
            .lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
                    .removePrefix("类别：")
                    .removePrefix("类别:")
                    .trim()
            }
            .filter { line ->
                line.isNotBlank() &&
                    '：' in line &&
                    !line.startsWith("统计") &&
                    !line.startsWith("时间范围") &&
                    !line.startsWith("查询结果") &&
                    !line.startsWith("历史记录")
            }
            .distinct()
}
