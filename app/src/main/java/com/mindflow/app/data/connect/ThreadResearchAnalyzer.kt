package com.mindflow.app.data.connect

import com.mindflow.app.data.local.entity.NoteEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ResearchCluster(
    val label: String,
    val summary: String,
    val noteCount: Int,
    val validationStep: String = "",
    val followUpReason: String = "",
)

object ThreadResearchAnalyzer {
    fun isResearchMemoryNote(note: NoteEntity): Boolean {
        val topic = note.topic.trim()
        val content = note.content
        return topic.contains("研究", ignoreCase = true) ||
            content.contains("外部线索") ||
            content.contains("机会缺口") ||
            content.contains("我查到的内容")
    }

    fun buildResearchClusters(
        notes: List<NoteEntity>,
        threadTitle: String,
    ): List<ResearchCluster> {
        if (notes.isEmpty()) return emptyList()

        val grouped = notes
            .map { note -> pickResearchClusterKey(note, threadTitle) to note }
            .groupBy({ it.first }, { it.second })
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<NoteEntity>>> { it.value.size }
                    .thenBy { it.key.length },
            )
            .take(3)

        return grouped.map { (label, groupedNotes) ->
            val sampleTopics = groupedNotes
                .take(2)
                .joinToString(" · ") { note ->
                    note.topic
                        .substringBefore("·")
                        .trim()
                        .ifBlank { "未命名研究记录" }
                }
            val summary = if (groupedNotes.size >= 2) {
                "最近有 ${groupedNotes.size} 条研究都落在「$label」上，主线包括：$sampleTopics"
            } else {
                "最近新增的研究主要落在「$label」，适合先把它沉淀成一条更稳定的判断。"
            }
            ResearchCluster(
                label = label,
                summary = summary,
                noteCount = groupedNotes.size,
                validationStep = buildValidationStep(label, groupedNotes),
                followUpReason = buildFollowUpReason(label, groupedNotes),
            )
        }
    }

    private fun buildValidationStep(
        label: String,
        notes: List<NoteEntity>,
    ): String {
        val latest = notes.maxByOrNull { it.updatedAt }
        val extracted = latest
            ?.content
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { line ->
                line.startsWith("- 先验证这一步") ||
                    line.startsWith("先验证这一步") ||
                    line.startsWith("- 我准备怎么验证") ||
                    line.startsWith("我准备怎么验证")
            }
            ?.substringAfter("：", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (extracted != null) return extracted

        val latestTopic = latest
            ?.topic
            ?.substringBefore("·")
            ?.trim()
            ?.ifBlank { null }

        return when {
            notes.size >= 3 && latestTopic != null ->
                "围绕「$label」把「$latestTopic」压成一个对比验证，明确要看什么结果。"
            notes.size >= 2 ->
                "把「$label」先压成一个固定验证问题，再比较这 ${notes.size} 条研究里的共同判断。"
            latestTopic != null ->
                "先围绕「$latestTopic」补一条验证记录，写清楚预期、指标和结果判断。"
            else ->
                "先围绕「$label」补一条验证记录，明确要验证什么和怎么看结果。"
        }
    }

    private fun buildFollowUpReason(
        label: String,
        notes: List<NoteEntity>,
    ): String {
        val latest = notes.maxByOrNull { it.updatedAt }
        val zoneId = ZoneId.systemDefault()
        val latestDate = latest
            ?.let { Instant.ofEpochMilli(it.updatedAt).atZone(zoneId).toLocalDate() }
        val daysSinceLatest = latestDate
            ?.let { java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now(zoneId)).toInt() }
            ?: 0
        val latestTopic = latest
            ?.topic
            ?.substringBefore("·")
            ?.trim()
            ?.ifBlank { null }

        return when {
            notes.size >= 3 && daysSinceLatest <= 7 ->
                "这组研究最近反复出现，已经到了该把判断压成验证动作的时候。"
            notes.size >= 2 && latestTopic != null ->
                "最近的线索已经开始收敛到「$latestTopic」，现在最适合先做一次小验证。"
            daysSinceLatest <= 3 ->
                "这组研究刚被补充过，趁记忆和问题还新，先验证一小步最划算。"
            else ->
                "这组研究已经具备稳定主线，现在值得从线索往验证再推一步。"
        }
    }

    private fun pickResearchClusterKey(
        note: NoteEntity,
        threadTitle: String,
    ): String {
        val usefulTag = note.tags
            .map { it.trim() }
            .firstOrNull { candidate ->
                candidate.isNotBlank() &&
                    candidate !in genericResearchWords &&
                    candidate !in threadTitle.asResearchStopWords()
            }
        if (usefulTag != null) return usefulTag

        val token = researchTokens(note.topic, threadTitle)
            .plus(researchTokens(note.content.take(180), threadTitle))
            .firstOrNull()
        return token ?: "最近研究"
    }

    private fun researchTokens(
        raw: String,
        threadTitle: String,
    ): List<String> {
        val stopWords = genericResearchWords + threadTitle.asResearchStopWords()
        val regex = Regex("[\\p{IsHan}]{2,6}|[A-Za-z][A-Za-z0-9+-]{3,}")
        return regex.findAll(raw)
            .map { it.value.trim().lowercase() }
            .filter { it.isNotBlank() }
            .filterNot { token -> stopWords.any { stop -> token.contains(stop) || stop.contains(token) } }
            .distinct()
            .toList()
    }
}

private val genericResearchWords = setOf(
    "研究",
    "收获",
    "动作",
    "验证",
    "记录",
    "当前",
    "方向",
    "外部",
    "线索",
    "机会",
    "缺口",
    "最近",
    "继续",
    "结果",
    "主线",
    "主题",
    "问题",
)

private fun String.asResearchStopWords(): Set<String> =
    replace("#", " ")
        .replace("·", " ")
        .split(Regex("[\\s/、]+"))
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() && it.length >= 2 }
        .toSet()
