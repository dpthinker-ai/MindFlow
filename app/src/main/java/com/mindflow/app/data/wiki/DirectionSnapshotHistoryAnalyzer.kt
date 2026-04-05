package com.mindflow.app.data.wiki

import com.mindflow.app.data.connect.DirectionStage
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class DirectionSnapshotHistorySummary(
    val snapshotStageLine: String = "",
    val snapshotCadenceLine: String = "",
    val snapshotEntries: List<DirectionSnapshotEntry> = emptyList(),
)

data class DirectionSnapshotEntry(
    val stage: DirectionStage,
    val timestamp: Long,
)

object DirectionSnapshotHistoryAnalyzer {
    private val filenameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

    fun summarize(
        snapshotsDir: File,
        slug: String,
        currentStage: DirectionStage,
        currentTimestamp: Long,
    ): DirectionSnapshotHistorySummary {
        val existing = snapshotsDir
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name.startsWith("$slug-") &&
                    file.name.endsWith(".md") &&
                    !file.name.endsWith("-timeline.md")
            }
            .mapNotNull { parseSnapshot(it) }

        val current = DirectionSnapshotEntry(
            stage = currentStage,
            timestamp = currentTimestamp,
        )
        val entries = (existing + current)
            .sortedBy { it.timestamp }
            .distinctBy { "${it.stage.name}:${it.timestamp}" }

        if (entries.isEmpty()) return DirectionSnapshotHistorySummary()

        val condensedStages = entries
            .map { it.stage.label }
            .fold(mutableListOf<String>()) { acc, label ->
                if (acc.lastOrNull() != label) acc.add(label)
                acc
            }

        val snapshotStageLine = when {
            condensedStages.size >= 2 -> "近 ${entries.size} 次快照：${condensedStages.takeLast(5).joinToString(" -> ")}"
            else -> "近 ${entries.size} 次快照：${entries.last().stage.label}"
        }

        val now = currentTimestamp
        val latestAt = entries.last().timestamp
        val daysSinceLatest = TimeUnit.MILLISECONDS.toDays(now - latestAt).coerceAtLeast(0)
        val averageGapDays = entries
            .zipWithNext()
            .map { (left, right) -> TimeUnit.MILLISECONDS.toDays(right.timestamp - left.timestamp).coerceAtLeast(0) }
            .takeLast(4)
            .average()

        val snapshotCadenceLine = when {
            entries.size <= 1 -> "阶段快照刚开始积累，再补几次更新后会更能看出长期节奏。"
            daysSinceLatest >= 21L -> "最近一次阶段快照已经是 $daysSinceLatest 天前，方向可能需要补一次新的阶段判断。"
            averageGapDays.isNaN() -> "阶段快照还不够稳定，先继续积累。"
            averageGapDays <= 4.0 -> "近几次阶段快照更新很紧，方向节奏保持得比较稳。"
            averageGapDays <= 10.0 -> "近几次阶段快照还算连续，方向在稳步往前。"
            else -> "阶段快照更新开始变慢，建议补一次阶段判断，避免方向只停在旧结论里。"
        }

        return DirectionSnapshotHistorySummary(
            snapshotStageLine = snapshotStageLine,
            snapshotCadenceLine = snapshotCadenceLine,
            snapshotEntries = entries.takeLast(8),
        )
    }

    private fun parseSnapshot(file: File): DirectionSnapshotEntry? {
        val timestamp = parseTimestamp(file.nameWithoutExtension) ?: file.lastModified()
        val stage = file.useLines { lines ->
            lines.firstOrNull { it.startsWith("- 阶段：") }
                ?.removePrefix("- 阶段：")
                ?.trim()
                ?.toDirectionStage()
        } ?: return null
        return DirectionSnapshotEntry(
            stage = stage,
            timestamp = timestamp,
        )
    }

    private fun parseTimestamp(raw: String): Long? {
        val timestampPart = Regex("(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})$")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        return runCatching {
            filenameFormatter.parse(timestampPart)
                .let { parsed -> java.time.LocalDateTime.from(parsed) }
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun String.toDirectionStage(): DirectionStage? =
        DirectionStage.entries.firstOrNull { entry ->
            entry.label == this || entry.name == this
        }
}
