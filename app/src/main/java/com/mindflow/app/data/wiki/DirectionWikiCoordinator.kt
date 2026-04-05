package com.mindflow.app.data.wiki

import android.content.Context
import com.mindflow.app.data.connect.DirectionAssetAnalyzer
import com.mindflow.app.data.connect.DirectionContinuityAnalyzer
import com.mindflow.app.data.connect.DirectionStage
import com.mindflow.app.data.connect.DirectionStageHistoryAnalyzer
import com.mindflow.app.data.connect.ExternalResearchPlanner
import com.mindflow.app.data.connect.NoteConnectionAnalyzer
import com.mindflow.app.data.connect.NoteInsightSummaryExtractor
import com.mindflow.app.data.connect.ResearchGroundingSnapshot
import com.mindflow.app.data.connect.ResearchEvidenceAnalyzer
import com.mindflow.app.data.connect.ResearchEvidenceType
import com.mindflow.app.data.connect.ThreadExecutionPlanner
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.ThreadPreferencesRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DirectionWikiCoordinator(
    context: Context,
    private val noteRepository: NoteRepository,
    private val threadPreferencesRepository: ThreadPreferencesRepository,
    private val threadExecutionPlanner: ThreadExecutionPlanner,
    private val externalResearchPlanner: ExternalResearchPlanner,
    private val applicationScope: CoroutineScope,
) {
    private val rootDir = File(context.filesDir, "direction-wiki")
    private val refreshIntervalMs = 18L * 60L * 60L * 1000L
    private val _snapshot = MutableStateFlow(loadSnapshotFromDisk())
    val snapshot: StateFlow<DirectionWikiSnapshot> = _snapshot

    fun refreshInBackgroundIfNeeded() {
        val now = System.currentTimeMillis()
        val current = _snapshot.value
        if (current.lastGeneratedAt > 0L && now - current.lastGeneratedAt < refreshIntervalMs) return
        applicationScope.launch {
            runCatching { refreshNow() }
        }
    }

    suspend fun refreshNow(): DirectionWikiRefreshResult = withContext(Dispatchers.IO) {
        val allNotes = noteRepository.observeAllNotes().first()
        val activeNotes = allNotes.filter { !it.isArchived }
        val followed = threadPreferencesRepository.getCurrent().followedThreadKeys.sorted()
        ensureDirectories()

        val generatedAt = System.currentTimeMillis()
        val summaries = followed.mapNotNull { threadKey ->
            buildDirectionSummary(
                threadKey = threadKey,
                notes = NoteConnectionAnalyzer.notesForThread(threadKey, activeNotes),
            )
        }

        writeDirectionFiles(generatedAt, summaries, activeNotes)
        writeExport(generatedAt, summaries)

        val snapshot = DirectionWikiSnapshot(
            rootPath = rootDir.absolutePath,
            lastGeneratedAt = generatedAt,
            directions = summaries.associateBy { it.threadKey },
        )
        _snapshot.value = snapshot
        DirectionWikiRefreshResult(
            generatedDirectionCount = summaries.size,
            generatedAt = generatedAt,
        )
    }

    private suspend fun buildDirectionSummary(
        threadKey: String,
        notes: List<NoteEntity>,
    ): DirectionWikiDirectionSummary? {
        if (notes.isEmpty()) return null

        val thread = NoteConnectionAnalyzer.threadFromKey(threadKey, notes)
        val execution = threadExecutionPlanner.summarize(threadKey, notes)
        val research = externalResearchPlanner.summarize(threadKey, notes)
        val directionAssets = DirectionAssetAnalyzer.build(notes)
        val stageHistory = DirectionStageHistoryAnalyzer.build(notes)
        val continuity = DirectionContinuityAnalyzer.summarize(notes)
        val grounding = ResearchEvidenceAnalyzer.buildGrounding(notes)
        val slug = slugFor(thread.title, threadKey)
        val snapshotHistory = DirectionSnapshotHistoryAnalyzer.summarize(
            snapshotsDir = File(rootDir, "wiki/snapshots"),
            slug = slug,
            currentStage = execution.stage,
            currentTimestamp = System.currentTimeMillis(),
        )

        val verifiedPoints = grounding.verifiedItems.map { it.summary }
        val validatedPoints = grounding.validatedItems.map { it.summary }
        val signalPoints = grounding.signalItems.map { it.summary }
        val hypothesisPoints = grounding.hypothesisItems.map { it.summary }

        val openQuestions = buildList {
            research.contrarianQuestion.takeIf { it.isNotBlank() }?.let(::add)
            hypothesisPoints.forEach(::add)
            execution.blocker.takeIf { it.isNotBlank() }?.let(::add)
            if (verifiedPoints.isEmpty() && validatedPoints.isEmpty()) {
                add("还需要一条更硬的查证或验证结果，才能把这个方向真正压实。")
            }
        }.distinct().take(3)

        val stageHistorySummary = stageHistory
            .joinToString(" -> ") { "${it.label}${it.stage.label}" }

        val assetSummary = validatedPoints.firstOrNull()
            ?: verifiedPoints.firstOrNull()
            ?: directionAssets.firstOrNull()?.summary
            ?: execution.summary.ifBlank { research.outsideAngle }

        return DirectionWikiDirectionSummary(
            threadKey = threadKey,
            slug = slug,
            title = thread.title,
            stage = execution.stage,
            assetSummary = assetSummary,
            groundingLine = grounding.summary.summaryLine,
            signalPoints = signalPoints,
            hypothesisPoints = hypothesisPoints,
            verifiedPoints = verifiedPoints,
            validatedPoints = validatedPoints,
            openQuestions = openQuestions,
            continuityLine = continuity.continuityLine,
            trajectoryLine = continuity.trajectoryLine,
            stageHistorySummary = stageHistorySummary,
            snapshotStageLine = snapshotHistory.snapshotStageLine,
            snapshotCadenceLine = snapshotHistory.snapshotCadenceLine,
            updatedAt = notes.maxOfOrNull { it.updatedAt } ?: 0L,
        )
    }

    private suspend fun writeDirectionFiles(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
        allNotes: List<NoteEntity>,
    ) {
        val rawNotesDir = File(rootDir, "raw/notes")
        val rawResearchDir = File(rootDir, "raw/research")
        val rawValidationDir = File(rootDir, "raw/validations")
        val rawReflectionDir = File(rootDir, "raw/reflections")
        val directionsDir = File(rootDir, "wiki/directions")
        val evidenceDir = File(rootDir, "wiki/evidence")
        val snapshotsDir = File(rootDir, "wiki/snapshots")
        val conceptsDir = File(rootDir, "wiki/concepts")
        val questionsDir = File(rootDir, "wiki/questions")
        val methodsDir = File(rootDir, "wiki/methods")
        val experimentsDir = File(rootDir, "wiki/experiments")
        val timestamp = fileTimestamp(generatedAt)
        val rawIndexLines = mutableListOf<String>()
        val objectCandidates = mutableListOf<KnowledgeObjectCandidate>()

        summaries.forEach { summary ->
            val notes = NoteConnectionAnalyzer.notesForThread(summary.threadKey, allNotes)
            val researchNotes = notes.filter { ResearchEvidenceAnalyzer.classify(it).label in setOf("外部视角", "待验证", "已查证", "已验证") }
            val validationNotes = notes.filter {
                val type = ResearchEvidenceAnalyzer.classify(it)
                type == ResearchEvidenceType.HYPOTHESIS || type == ResearchEvidenceType.VALIDATED
            }
            val reflectionNotes = notes.filter { note ->
                note.status == com.mindflow.app.data.model.NoteStatus.DONE ||
                    note.content.contains("回看") ||
                    note.content.contains("复盘") ||
                    note.content.contains("经验") ||
                    note.content.contains("教训")
            }
            val execution = threadExecutionPlanner.summarize(summary.threadKey, notes)
            val research = externalResearchPlanner.summarize(summary.threadKey, notes)
            val grounding = ResearchEvidenceAnalyzer.buildGrounding(researchNotes)

            File(rawNotesDir, "${summary.slug}-$timestamp.md").writeText(buildRawNotesMarkdown(summary, notes))
            if (researchNotes.isNotEmpty()) {
                File(rawResearchDir, "${summary.slug}-$timestamp.md").writeText(buildRawResearchMarkdown(summary, researchNotes))
            }
            if (validationNotes.isNotEmpty()) {
                File(rawValidationDir, "${summary.slug}-$timestamp.md").writeText(buildRawValidationMarkdown(summary, validationNotes))
            }
            if (reflectionNotes.isNotEmpty()) {
                File(rawReflectionDir, "${summary.slug}-$timestamp.md").writeText(buildRawReflectionMarkdown(summary, reflectionNotes))
            }
            File(directionsDir, "${summary.slug}.md").writeText(buildDirectionMarkdown(summary, execution, research))
            File(evidenceDir, "${summary.slug}.md").writeText(buildEvidenceMarkdown(summary, grounding))
            File(snapshotsDir, "${summary.slug}-$timestamp.md").writeText(buildSnapshotMarkdown(summary, execution))
            val snapshotHistory = DirectionSnapshotHistoryAnalyzer.summarize(
                snapshotsDir = snapshotsDir,
                slug = summary.slug,
                currentStage = summary.stage,
                currentTimestamp = generatedAt,
            )
            File(snapshotsDir, "${summary.slug}-timeline.md").writeText(
                buildSnapshotTimelineMarkdown(summary, snapshotHistory),
            )
            rawIndexLines += buildList {
                add("- [${summary.title} raw notes](notes/${summary.slug}-$timestamp.md)")
                if (researchNotes.isNotEmpty()) add("- [${summary.title} raw research](research/${summary.slug}-$timestamp.md)")
                if (validationNotes.isNotEmpty()) add("- [${summary.title} validations](validations/${summary.slug}-$timestamp.md)")
                if (reflectionNotes.isNotEmpty()) add("- [${summary.title} reflections](reflections/${summary.slug}-$timestamp.md)")
            }
            notes.forEach { note ->
                objectCandidates += KnowledgeObjectClassifier.classify(note, summary.title)
            }
        }

        File(rootDir, "raw/index.md").writeText(
            buildString {
                appendLine("# Knowledge Layer Raw Sources")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                if (rawIndexLines.isEmpty()) {
                    appendLine("还没有导出的 source。")
                } else {
                    rawIndexLines.forEach(::appendLine)
                }
            },
        )

        writeConceptFiles(
            generatedAt = generatedAt,
            summaries = summaries,
            allNotes = allNotes,
            conceptsDir = conceptsDir,
        )
        writeKnowledgeObjectFiles(
            generatedAt = generatedAt,
            candidates = objectCandidates,
            directories = mapOf(
                KnowledgeObjectType.QUESTION to questionsDir,
                KnowledgeObjectType.METHOD to methodsDir,
                KnowledgeObjectType.EXPERIMENT to experimentsDir,
            ),
        )

        File(rootDir, "wiki/index.md").writeText(
            buildString {
                appendLine("# MindFlow Knowledge Layer")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                summaries.sortedBy { it.title }.forEach { summary ->
                    appendLine("- [${summary.title}](directions/${summary.slug}.md) · ${summary.stage.label}")
                }
                appendLine()
                appendLine("- [概念索引](concepts/index.md)")
                appendLine("- [问题索引](questions/index.md)")
                appendLine("- [方法索引](methods/index.md)")
                appendLine("- [实验索引](experiments/index.md)")
            },
        )

        File(rootDir, "wiki/log.md").appendText(
            buildString {
                appendLine("## [${displayDate(generatedAt)}] update | ${summaries.size} directions")
                summaries.forEach { summary ->
                    appendLine("- ${summary.title} · ${summary.stage.label}")
                }
                appendLine()
            },
        )
    }

    private fun writeConceptFiles(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
        allNotes: List<NoteEntity>,
        conceptsDir: File,
    ) {
        val conceptBuckets = buildMap<String, MutableList<Pair<DirectionWikiDirectionSummary, NoteEntity>>> {
            summaries.forEach { summary ->
                NoteConnectionAnalyzer.notesForThread(summary.threadKey, allNotes)
                    .forEach { note ->
                        note.tags
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .forEach { tag ->
                                getOrPut(tag) { mutableListOf() }.add(summary to note)
                            }
                    }
            }
        }
            .filterValues { pairs -> pairs.size >= 2 }
            .toSortedMap()

        File(conceptsDir, "index.md").writeText(
            buildString {
                appendLine("# Concepts")
                appendLine()
                appendLine("更新时间：${displayTime(generatedAt)}")
                appendLine()
                if (conceptBuckets.isEmpty()) {
                    appendLine("还没有形成跨方向复用的概念。")
                } else {
                    conceptBuckets.forEach { (tag, pairs) ->
                        val slug = slugFor(tag, tag)
                        val directionCount = pairs.map { it.first.threadKey }.distinct().size
                        appendLine("- [$tag]($slug.md) · ${pairs.size} 条记录 · ${directionCount} 条方向")
                    }
                }
            },
        )

        conceptBuckets.forEach { (tag, pairs) ->
            val slug = slugFor(tag, tag)
            val uniqueDirections = pairs
                .map { it.first }
                .distinctBy { it.threadKey }
                .sortedBy { it.title }
            val uniqueNotes = pairs
                .distinctBy { it.second.id }
                .sortedByDescending { it.second.updatedAt }
                .take(6)
            File(conceptsDir, "$slug.md").writeText(
                buildString {
                    appendLine("# $tag")
                    appendLine()
                    appendLine("- 最近更新：${displayTime(generatedAt)}")
                    appendLine("- 相关方向：${uniqueDirections.size} 条")
                    appendLine("- 相关记录：${uniqueNotes.size} 条")
                    appendLine()
                    appendLine("## 关联方向")
                    uniqueDirections.forEach { direction ->
                        appendLine("- [${direction.title}](../directions/${direction.slug}.md) · ${direction.stage.label}")
                    }
                    appendLine()
                    appendLine("## 最近记录")
                    uniqueNotes.forEach { (_, note) ->
                        val summary = NoteInsightSummaryExtractor.extract(note)
                        appendLine("- ${note.topic.ifBlank { "未命名记录" }}")
                        appendLine("  - ${summary.ifBlank { note.content.trim().take(72) }}")
                    }
                },
            )
        }
    }

    private fun writeKnowledgeObjectFiles(
        generatedAt: Long,
        candidates: List<KnowledgeObjectCandidate>,
        directories: Map<KnowledgeObjectType, File>,
    ) {
        val grouped = candidates
            .groupBy { it.type }
            .mapValues { (_, items) ->
                items
                    .groupBy { slugFor(it.title, "${it.type.folderName}-${it.noteId}") }
                    .mapNotNull { (slug, bucket) ->
                        val first = bucket.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                        Triple(slug, first, bucket.sortedByDescending { it.updatedAt })
                    }
                    .sortedByDescending { (_, first, _) -> first.updatedAt }
            }

        KnowledgeObjectType.entries.forEach { type ->
            val directory = directories.getValue(type)
            val items = grouped[type].orEmpty()
            File(directory, "index.md").writeText(
                buildString {
                    appendLine("# ${type.displayName}")
                    appendLine()
                    appendLine("更新时间：${displayTime(generatedAt)}")
                    appendLine()
                    if (items.isEmpty()) {
                        appendLine("还没有形成可复用的${type.displayName}对象。")
                    } else {
                        items.forEach { (slug, first, bucket) ->
                            appendLine("- [${first.title}]($slug.md) · ${bucket.size} 条来源 · ${bucket.map { it.threadTitle }.distinct().size} 条方向")
                        }
                    }
                },
            )
            items.forEach { (slug, first, bucket) ->
                File(directory, "$slug.md").writeText(
                    buildString {
                        appendLine("# ${first.title}")
                        appendLine()
                        appendLine("- 类型：${type.displayName}")
                        appendLine("- 最近更新：${displayTime(first.updatedAt)}")
                        appendLine("- 相关方向：${bucket.map { it.threadTitle }.distinct().joinToString("、")}")
                        appendLine()
                        appendLine("## 当前提炼")
                        appendLine(first.summary)
                        appendLine()
                        appendLine("## 来源记录")
                        bucket.take(8).forEach { item ->
                            appendLine("- [${item.threadTitle}](../directions/${slugFor(item.threadTitle, item.threadTitle)}.md) · ${item.summary}")
                        }
                    },
                )
            }
        }
    }

    private fun buildRawNotesMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw notes")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名记录" }}")
            appendLine("- id: ${note.id}")
            appendLine("- status: ${note.status.label}")
            appendLine("- horizon: ${note.horizon.label}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildRawValidationMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw validations")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名验证记录" }}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine("- status: ${note.status.label}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildRawReflectionMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw reflections")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名反思记录" }}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine("- status: ${note.status.label}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildRawResearchMarkdown(
        summary: DirectionWikiDirectionSummary,
        notes: List<NoteEntity>,
    ): String = buildString {
        appendLine("# ${summary.title} raw research")
        appendLine()
        notes.sortedByDescending { it.updatedAt }.forEach { note ->
            appendLine("## ${note.topic.ifBlank { "未命名研究记录" }}")
            appendLine("- evidence: ${ResearchEvidenceAnalyzer.classify(note).label}")
            appendLine("- updated: ${displayTime(note.updatedAt)}")
            appendLine()
            appendLine(note.content.trim())
            appendLine()
        }
    }

    private fun buildDirectionMarkdown(
        summary: DirectionWikiDirectionSummary,
        execution: com.mindflow.app.data.connect.ThreadExecutionSummary,
        research: com.mindflow.app.data.connect.ExternalResearchSnapshot,
    ): String = buildString {
        appendLine("# ${summary.title}")
        appendLine()
        appendLine("- 当前阶段：${summary.stage.label}")
        appendLine("- 最近更新：${displayTime(summary.updatedAt)}")
        appendLine()
        appendLine("## 当前判断")
        appendLine(summary.assetSummary.ifBlank { execution.summary.ifBlank { "这条方向还在继续形成。" } })
        appendLine()
        summary.groundingLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 证据基础")
            appendLine(it)
            appendLine()
        }
        summary.continuityLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 节奏")
            appendLine(it)
            appendLine()
        }
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 长期阶段")
            appendLine(it)
            appendLine()
        }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 快照节奏")
            appendLine(it)
            appendLine()
        }
        execution.blocker.takeIf { it.isNotBlank() }?.let {
            appendLine("## 当前卡点")
            appendLine(it)
            appendLine()
        }
        appendLine("## 执行")
        execution.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("- 为什么现在：$it") }
        execution.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("- 当前最小动作：$it") }
        execution.validationStep.takeIf { it.isNotBlank() }?.let { appendLine("- 当前验证动作：$it") }
        execution.postValidationAction.takeIf { it.isNotBlank() }?.let { appendLine("- 如果成立，下一步：$it") }
        appendLine()
        appendLine("## 研究")
        research.outsideAngle.takeIf { it.isNotBlank() }?.let { appendLine("- 外部视角：$it") }
        research.opportunityGap.takeIf { it.isNotBlank() }?.let { appendLine("- 机会缺口：$it") }
        research.contrarianQuestion.takeIf { it.isNotBlank() }?.let { appendLine("- 值得追问：$it") }
        research.externalHypothesis.takeIf { it.isNotBlank() }?.let { appendLine("- 外部假设：$it") }
        appendLine()
        if (summary.signalPoints.isNotEmpty()) {
            appendLine("## 外部视角沉淀")
            summary.signalPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.hypothesisPoints.isNotEmpty()) {
            appendLine("## 待验证")
            summary.hypothesisPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.verifiedPoints.isNotEmpty()) {
            appendLine("## 已查证")
            summary.verifiedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.validatedPoints.isNotEmpty()) {
            appendLine("## 已验证")
            summary.validatedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.openQuestions.isNotEmpty()) {
            appendLine("## 开放问题")
            summary.openQuestions.forEach { appendLine("- $it") }
            appendLine()
        }
        summary.stageHistorySummary.takeIf { it.isNotBlank() }?.let {
            appendLine("## 阶段历史")
            appendLine(it)
            appendLine()
        }
        summary.trajectoryLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 长期走势")
            appendLine(it)
            appendLine()
        }
    }

    private fun buildEvidenceMarkdown(
        summary: DirectionWikiDirectionSummary,
        grounding: ResearchGroundingSnapshot,
    ): String = buildString {
        appendLine("# ${summary.title} evidence")
        appendLine()
        appendLine("- ${grounding.summary.summaryLine.ifBlank { "暂无结构化研究证据" }}")
        appendLine()
        if (summary.signalPoints.isNotEmpty()) {
            appendLine("## 外部视角")
            summary.signalPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.hypothesisPoints.isNotEmpty()) {
            appendLine("## 待验证")
            summary.hypothesisPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.verifiedPoints.isNotEmpty()) {
            appendLine("## 已查证")
            summary.verifiedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (summary.validatedPoints.isNotEmpty()) {
            appendLine("## 已验证")
            summary.validatedPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        summary.continuityLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 节奏")
            appendLine(it)
            appendLine()
        }
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 长期阶段")
            appendLine(it)
            appendLine()
        }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let {
            appendLine("## 快照节奏")
            appendLine(it)
            appendLine()
        }
    }

    private fun buildSnapshotMarkdown(
        summary: DirectionWikiDirectionSummary,
        execution: com.mindflow.app.data.connect.ThreadExecutionSummary,
    ): String = buildString {
        appendLine("# ${summary.title} snapshot")
        appendLine()
        appendLine("- 阶段：${summary.stage.label}")
        appendLine("- 最近更新：${displayTime(summary.updatedAt)}")
        summary.groundingLine.takeIf { it.isNotBlank() }?.let { appendLine("- 证据基础：$it") }
        summary.continuityLine.takeIf { it.isNotBlank() }?.let { appendLine("- 节奏：$it") }
        execution.whyNow.takeIf { it.isNotBlank() }?.let { appendLine("- 为什么现在：$it") }
        execution.nextStep.takeIf { it.isNotBlank() }?.let { appendLine("- 下一步：$it") }
        execution.validationStep.takeIf { it.isNotBlank() }?.let { appendLine("- 先验证：$it") }
        summary.trajectoryLine.takeIf { it.isNotBlank() }?.let { appendLine("- 长期走势：$it") }
        summary.stageHistorySummary.takeIf { it.isNotBlank() }?.let { appendLine("- 历史：$it") }
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let { appendLine("- 长期阶段：$it") }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let { appendLine("- 快照节奏：$it") }
    }

    private fun buildSnapshotTimelineMarkdown(
        summary: DirectionWikiDirectionSummary,
        history: DirectionSnapshotHistorySummary,
    ): String = buildString {
        appendLine("# ${summary.title} stage timeline")
        appendLine()
        summary.snapshotStageLine.takeIf { it.isNotBlank() }?.let { appendLine("- $it") }
        summary.snapshotCadenceLine.takeIf { it.isNotBlank() }?.let { appendLine("- $it") }
        appendLine()
        appendLine("## Recent snapshots")
        if (history.snapshotEntries.isEmpty()) {
            appendLine("还没有可追踪的阶段快照。")
        } else {
            history.snapshotEntries
                .sortedByDescending { it.timestamp }
                .forEach { entry ->
                    appendLine("- ${displayTime(entry.timestamp)} · ${entry.stage.label}")
                }
        }
    }

    private fun writeExport(
        generatedAt: Long,
        summaries: List<DirectionWikiDirectionSummary>,
    ) {
        val root = JSONObject()
            .put("generatedAt", generatedAt)
            .put("rootPath", rootDir.absolutePath)
            .put(
                "directions",
                JSONArray().apply {
                    summaries.forEach { summary ->
                        put(
                            JSONObject()
                                .put("threadKey", summary.threadKey)
                                .put("slug", summary.slug)
                                .put("title", summary.title)
                                .put("stage", summary.stage.name)
                                .put("assetSummary", summary.assetSummary)
                                .put("groundingLine", summary.groundingLine)
                                .put("continuityLine", summary.continuityLine)
                                .put("trajectoryLine", summary.trajectoryLine)
                                .put("stageHistorySummary", summary.stageHistorySummary)
                                .put("snapshotStageLine", summary.snapshotStageLine)
                                .put("snapshotCadenceLine", summary.snapshotCadenceLine)
                                .put("updatedAt", summary.updatedAt)
                                .put("signalPoints", JSONArray(summary.signalPoints))
                                .put("hypothesisPoints", JSONArray(summary.hypothesisPoints))
                                .put("verifiedPoints", JSONArray(summary.verifiedPoints))
                                .put("validatedPoints", JSONArray(summary.validatedPoints))
                                .put("openQuestions", JSONArray(summary.openQuestions)),
                        )
                    }
                },
            )
        File(rootDir, "wiki/export/direction-assets.json").writeText(root.toString(2))
    }

    private fun loadSnapshotFromDisk(): DirectionWikiSnapshot {
        val exportFile = File(rootDir, "wiki/export/direction-assets.json")
        if (!exportFile.exists()) {
            return DirectionWikiSnapshot(rootPath = rootDir.absolutePath)
        }
        return runCatching {
            val json = JSONObject(exportFile.readText())
            val directionsArray = json.optJSONArray("directions") ?: JSONArray()
            val directions = buildMap {
                for (index in 0 until directionsArray.length()) {
                    val item = directionsArray.optJSONObject(index) ?: continue
                    val threadKey = item.optString("threadKey")
                    if (threadKey.isBlank()) continue
                    put(
                        threadKey,
                        DirectionWikiDirectionSummary(
                            threadKey = threadKey,
                            slug = item.optString("slug"),
                            title = item.optString("title"),
                            stage = item.optString("stage").toDirectionStage(),
                            assetSummary = item.optString("assetSummary"),
                            groundingLine = item.optString("groundingLine"),
                            continuityLine = item.optString("continuityLine"),
                            trajectoryLine = item.optString("trajectoryLine"),
                            snapshotStageLine = item.optString("snapshotStageLine"),
                            snapshotCadenceLine = item.optString("snapshotCadenceLine"),
                            signalPoints = item.optJSONArray("signalPoints").toStringList(),
                            hypothesisPoints = item.optJSONArray("hypothesisPoints").toStringList(),
                            verifiedPoints = item.optJSONArray("verifiedPoints").toStringList(),
                            validatedPoints = item.optJSONArray("validatedPoints").toStringList(),
                            openQuestions = item.optJSONArray("openQuestions").toStringList(),
                            stageHistorySummary = item.optString("stageHistorySummary"),
                            updatedAt = item.optLong("updatedAt"),
                        ),
                    )
                }
            }
            DirectionWikiSnapshot(
                rootPath = json.optString("rootPath", rootDir.absolutePath),
                lastGeneratedAt = json.optLong("generatedAt"),
                directions = directions,
            )
        }.getOrElse {
            DirectionWikiSnapshot(rootPath = rootDir.absolutePath)
        }
    }

    private fun ensureDirectories() {
        listOf(
            File(rootDir, "raw/notes"),
            File(rootDir, "raw/research"),
            File(rootDir, "raw/validations"),
            File(rootDir, "raw/reflections"),
            File(rootDir, "wiki/directions"),
            File(rootDir, "wiki/concepts"),
            File(rootDir, "wiki/evidence"),
            File(rootDir, "wiki/questions"),
            File(rootDir, "wiki/methods"),
            File(rootDir, "wiki/experiments"),
            File(rootDir, "wiki/snapshots"),
            File(rootDir, "wiki/export"),
        ).forEach { it.mkdirs() }
        File(rootDir, "AGENTS.md").takeIf { !it.exists() }?.writeText(
            """
            # MindFlow Knowledge Layer Agent Rules

            - Treat `raw/` as append-only sources.
            - Update `wiki/directions/`, `wiki/concepts/`, `wiki/evidence/`, `wiki/questions/`, `wiki/methods/`, `wiki/experiments/`, `wiki/snapshots/`, `wiki/index.md`, and `wiki/log.md`.
            - Distinguish clearly between:
              - AI external perspective
              - pending validation
              - verified findings
              - validated outcomes
            - Treat directions as one view of the knowledge layer, not the whole knowledge layer.
            - Prefer concise markdown pages over verbose chat transcripts.
            """.trimIndent(),
        )
    }
    private fun slugFor(title: String, fallback: String): String {
        val base = title
            .lowercase()
            .replace("#", "")
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
            .trim('-')
        return if (base.isNotBlank()) base else fallback.replace(':', '-').replace('/', '-')
    }

    private fun fileTimestamp(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    private fun displayTime(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun displayDate(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    private fun String.toDirectionStage(): DirectionStage =
        runCatching { DirectionStage.valueOf(this) }.getOrDefault(DirectionStage.FORMING)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
