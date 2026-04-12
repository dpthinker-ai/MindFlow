package com.mindflow.app.data.localmodel

import android.content.Context
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.MindFolderCatalog
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import com.mindflow.app.data.wiki.KnowledgeLayerSearchType
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalKnowledgeMaintenancePlanner(
    context: Context,
    private val noteRepository: NoteRepository,
    private val directionWikiCoordinator: DirectionWikiCoordinator,
    private val onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository,
    private val onDeviceAiClient: OnDeviceAiClient,
    private val applicationScope: CoroutineScope,
) {
    val logDirectoryPath: String
        get() = externalLogDir.absolutePath

    private data class MaintainerSnippet(
        val title: String,
        val summary: String,
        val relativePath: String,
        val updatedAt: Long,
    )

    private data class MaintainerCorpus(
        val rawIndexEntries: List<String>,
        val wikiIndexEntries: List<String>,
        val recentSources: List<MaintainerSnippet>,
        val folderSlices: List<MaintainerSnippet>,
        val knowledgeItems: List<KnowledgeLayerSearchItem>,
        val directions: List<MaintainerSnippet>,
        val conclusions: List<MaintainerSnippet>,
        val concepts: List<MaintainerSnippet>,
        val evidence: List<MaintainerSnippet>,
        val questions: List<MaintainerSnippet>,
        val methods: List<MaintainerSnippet>,
        val experiments: List<MaintainerSnippet>,
        val recentLogEntries: List<String>,
        val inventoryLine: String,
        val pointLabels: List<String>,
        val rawSourceCount: Int,
        val directionCount: Int,
        val knowledgeObjectCount: Int,
        val newSourceCount: Int,
        val newNodeCount: Int,
        val newEdgeCount: Int,
        val activeHubLabel: String,
        val missingLinkLabel: String,
        val updatedDirectionTitle: String,
        val preferredSettledKnowledge: MaintainerSnippet?,
        val preferredConnectionKnowledge: MaintainerSnippet?,
        val preferredSettledDirection: DirectionWikiDirectionSummary?,
        val preferredConnectionDirection: DirectionWikiDirectionSummary?,
    )

    private val knowledgeLayerRoot = File(context.filesDir, "knowledge-layer")
    private val wikiRootDir = File(knowledgeLayerRoot, "wiki")
    private val rawRootDir = File(knowledgeLayerRoot, "raw")
    private val rootDir = File(wikiRootDir, "local-maintainer")
    private val statusFile = File(rootDir, "status.json")
    private val latestErrorFile = File(rootDir, "latest-error.md")
    private val externalLogDir = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "mindflow-logs",
    )
    private val externalStatusFile = File(externalLogDir, "local-maintainer-status.json")
    private val externalLatestErrorFile = File(externalLogDir, "latest-local-maintainer-error.md")
    private val refreshIntervalMs = 18L * 60L * 60L * 1000L
    private val displayDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val _snapshot = MutableStateFlow(loadSnapshotFromDisk())
    val snapshot: StateFlow<LocalKnowledgeMaintenanceSnapshot> = _snapshot
    private val _maintenanceStatus = MutableStateFlow(loadStatusFromDisk())
    val maintenanceStatus: StateFlow<LocalKnowledgeMaintenanceStatus> = _maintenanceStatus

    fun maintainInBackgroundIfNeeded() {
        applicationScope.launch {
            val latest = File(rootDir, "latest.md")
            val now = System.currentTimeMillis()
            val currentSnapshot = _snapshot.value
            val wikiSnapshot = directionWikiCoordinator.snapshot.value
            val isFreshEnough = latest.exists() && now - latest.lastModified() < refreshIntervalMs
            val isAlignedWithWiki = currentSnapshot.generatedAt >= wikiSnapshot.lastGeneratedAt &&
                currentSnapshot.activeDirectionCount >= wikiSnapshot.directions.size
            if (isFreshEnough && isAlignedWithWiki) return@launch
            runCatching { refreshNow() }
        }
    }

    suspend fun refreshNow(): Result<Unit> = withContext(Dispatchers.Default) {
        var currentStep = "准备本地维护"
        val startedAt = System.currentTimeMillis()
        try {
            markMaintenanceStarted(startedAt)
            currentStep = "检查本地模型状态"
            updateMaintenanceProgress(currentStep, 0.06f)
            val settings = onDeviceModelSettingsRepository.getCurrent()
            if (!settings.preferOnDevice || !settings.isReady) {
                val error = IllegalStateException("本地模型尚未就绪或没有开启本地优先。")
                markMaintenanceFailure(error.message.orEmpty(), currentStep, "")
                return@withContext Result.failure(error)
            }

            currentStep = "刷新知识层切片"
            updateMaintenanceProgress(currentStep, 0.14f)
            runCatching { directionWikiCoordinator.refreshNow() }

            currentStep = "收集本地材料"
            updateMaintenanceProgress(currentStep, 0.28f)
            val notes = noteRepository.observeAllNotes().first().filter { !it.isArchived }
            val snapshot = directionWikiCoordinator.snapshot.value
            val zoneId = ZoneId.systemDefault()
            val generatedAt = System.currentTimeMillis()
            val today = LocalDate.now(zoneId)
            val corpus = buildCorpus(
                notes = notes,
                snapshot = snapshot,
                generatedAt = generatedAt,
                zoneId = zoneId,
            )
            if (
                corpus.directionCount == 0 &&
                corpus.recentSources.isEmpty() &&
                corpus.knowledgeObjectCount == 0
            ) {
                markMaintenanceSuccess("当前还没有足够的本地材料可维护。", generatedAt)
                return@withContext Result.success(Unit)
            }

            currentStep = "生成当前知识长相"
            updateMaintenanceProgress(currentStep, 0.42f)
            val knowledgeShape = onDeviceAiClient.generateLocalKnowledgeShape(
                settings = settings,
                contextSummary = buildKnowledgeShapeContext(corpus),
            )
            currentStep = "生成当前综合判断"
            updateMaintenanceProgress(currentStep, 0.54f)
            val mainline = onDeviceAiClient.generateFlowMainline(
                settings = settings,
                contextSummary = buildCurrentJudgementContext(corpus),
            )
            currentStep = "生成最近吸收结果"
            updateMaintenanceProgress(currentStep, 0.66f)
            val absorption = onDeviceAiClient.generateFlowSettledKnowledge(
                settings = settings,
                contextSummary = buildRecentAbsorptionContext(corpus),
            )
            currentStep = "生成今天新连接"
            updateMaintenanceProgress(currentStep, 0.78f)
            val connection = onDeviceAiClient.generateFlowBreakthroughGap(
                settings = settings,
                contextSummary = buildNewConnectionContext(corpus),
            )
            currentStep = "生成待厘清问题"
            updateMaintenanceProgress(currentStep, 0.88f)
            val openQuestion = onDeviceAiClient.generateLocalOpenQuestion(
                settings = settings,
                contextSummary = buildOpenQuestionContext(corpus),
            )

            val localSnapshot = LocalKnowledgeMaintenanceSnapshot(
                rootPath = rootDir.absolutePath,
                generatedAt = generatedAt,
                date = today.toString(),
                knowledgeShape = buildCard(
                    result = knowledgeShape,
                    fallbackLine = fallbackKnowledgeShapeLine(corpus),
                    fallbackSupport = fallbackKnowledgeShapeSupport(corpus),
                    anchorLabel = corpus.preferredSettledKnowledge?.title
                        ?: corpus.updatedDirectionTitle,
                    threadKey = corpus.preferredSettledDirection?.threadKey.orEmpty(),
                ),
                knowledgeInventoryLine = corpus.inventoryLine,
                knowledgePointLabels = corpus.pointLabels,
                lastLogLine = corpus.recentLogEntries.firstOrNull().orEmpty(),
                recentAbsorption = buildCard(
                    result = absorption,
                    fallbackLine = corpus.preferredSettledKnowledge?.summary
                        ?: corpus.conclusions.firstOrNull()?.summary
                        ?: corpus.evidence.firstOrNull()?.summary
                        ?: corpus.methods.firstOrNull()?.summary
                        ?: corpus.experiments.firstOrNull()?.summary
                        ?: corpus.recentSources.firstOrNull()?.summary
                        ?: "今天还没有真正被吸收进知识层的新结果。",
                    fallbackSupport = corpus.evidence.firstOrNull()?.summary
                        ?: corpus.methods.firstOrNull()?.summary
                        ?: corpus.experiments.firstOrNull()?.summary
                        ?: corpus.folderSlices.firstOrNull()?.summary
                        ?: "再喂一点更稳定的材料，这里会开始留下更可复用的结果。",
                    anchorLabel = corpus.preferredSettledKnowledge?.title
                        ?: corpus.conclusions.firstOrNull()?.title
                        ?: corpus.updatedDirectionTitle,
                    threadKey = corpus.preferredSettledDirection?.threadKey.orEmpty(),
                ),
                currentJudgement = buildCard(
                    result = mainline,
                    fallbackLine = corpus.preferredSettledKnowledge?.summary
                        ?: corpus.conclusions.firstOrNull()?.summary
                        ?: corpus.methods.firstOrNull()?.summary
                        ?: corpus.directions.firstOrNull()?.summary
                        ?: "今天还没有被压成真正可用的综合判断。",
                    fallbackSupport = corpus.evidence.firstOrNull()?.summary
                        ?: corpus.methods.firstOrNull()?.summary
                        ?: corpus.folderSlices.firstOrNull()?.summary
                        ?: corpus.questions.firstOrNull()?.summary
                        ?: "先让本地维护把今天进来的材料和已有积累压成更稳的一条判断。",
                    anchorLabel = corpus.preferredSettledKnowledge?.title
                        ?: corpus.updatedDirectionTitle,
                    threadKey = corpus.preferredSettledDirection?.threadKey.orEmpty(),
                ),
                newConnection = buildCard(
                    result = connection,
                    fallbackLine = corpus.preferredConnectionKnowledge?.summary
                        ?: corpus.preferredConnectionKnowledge?.title
                        ?: fallbackNewConnectionLine(corpus)
                        ?: "今天还没有长出真正值得试的新连接。",
                    fallbackSupport = corpus.questions.firstOrNull()?.summary
                        ?: corpus.methods.firstOrNull()?.summary
                        ?: corpus.experiments.firstOrNull()?.summary
                        ?: fallbackNewConnectionSupport(corpus)
                        ?: "再喂一点跨项目、跨方向的材料，这里会更容易长出新连接。",
                    anchorLabel = corpus.preferredConnectionKnowledge?.title
                        ?: corpus.concepts.firstOrNull()?.title
                        ?: corpus.updatedDirectionTitle,
                    threadKey = corpus.preferredConnectionDirection?.threadKey.orEmpty(),
                ),
                openQuestion = buildCard(
                    result = openQuestion,
                    fallbackLine = corpus.preferredConnectionKnowledge?.summary
                        ?: corpus.preferredConnectionKnowledge?.title
                        ?: corpus.questions.firstOrNull()?.summary
                        ?: corpus.missingLinkLabel,
                    fallbackSupport = corpus.questions.getOrNull(1)?.summary
                        ?: corpus.folderSlices.firstOrNull()?.summary
                        ?: corpus.methods.firstOrNull()?.summary
                        ?: "",
                    anchorLabel = corpus.preferredConnectionKnowledge?.title
                        ?: corpus.questions.firstOrNull()?.title.orEmpty(),
                    threadKey = corpus.preferredConnectionDirection?.threadKey.orEmpty(),
                ),
                graphPulse = LocalKnowledgeGraphPulse(
                    newSourceCount = corpus.newSourceCount,
                    newNodeCount = corpus.newNodeCount,
                    newEdgeCount = corpus.newEdgeCount,
                    activeHubLabel = corpus.activeHubLabel,
                    missingLinkLabel = corpus.missingLinkLabel,
                ),
                activeDirectionCount = corpus.directionCount,
                updatedDirectionTitle = corpus.updatedDirectionTitle,
            )

            currentStep = "写回本地知识层文件"
            updateMaintenanceProgress(currentStep, 0.96f)
            withContext(Dispatchers.IO) {
                writeMaintainerFiles(snapshot = localSnapshot, corpus = corpus)
            }
            _snapshot.value = localSnapshot
            markMaintenanceSuccess("已完成本地知识层维护。", generatedAt)
            Result.success(Unit)
        } catch (throwable: Throwable) {
            val tracePath = withContext(Dispatchers.IO) {
                writeFailureTrace(step = currentStep, throwable = throwable)
            }
            markMaintenanceFailure(
                message = throwable.message?.takeIf { it.isNotBlank() }
                    ?: throwable::class.java.simpleName,
                step = currentStep,
                tracePath = tracePath,
            )
            Result.failure(throwable)
        }
    }

    private fun buildCorpus(
        notes: List<NoteEntity>,
        snapshot: DirectionWikiSnapshot,
        generatedAt: Long,
        zoneId: ZoneId,
    ): MaintainerCorpus {
        val rawNotes = readMarkdownSnippets(File(rawRootDir, "notes"), rawRootDir, limit = 8)
        val rawResearch = readMarkdownSnippets(File(rawRootDir, "research"), rawRootDir, limit = 6)
        val rawValidations = readMarkdownSnippets(File(rawRootDir, "validations"), rawRootDir, limit = 6)
        val rawReflections = readMarkdownSnippets(File(rawRootDir, "reflections"), rawRootDir, limit = 4)
        val rawReviews = readMarkdownSnippets(File(rawRootDir, "reviews"), rawRootDir, limit = 6)
        val rawIndexEntries = readIndexEntries(File(rawRootDir, "index.md"), limit = 8)
        val wikiIndexEntries = readIndexEntries(File(wikiRootDir, "index.md"), limit = 10)
        val recentSources = listOf(rawNotes, rawResearch, rawValidations, rawReflections, rawReviews)
            .flatten()
            .sortedByDescending { it.updatedAt }
            .take(10)
        val folderSlices = buildFolderSlices(notes)

        val directions = readMarkdownSnippets(File(wikiRootDir, "directions"), wikiRootDir, limit = 6)
        val conclusions = readMarkdownSnippets(File(wikiRootDir, "conclusions"), wikiRootDir, limit = 6)
        val concepts = readMarkdownSnippets(File(wikiRootDir, "concepts"), wikiRootDir, limit = 6)
        val evidence = readMarkdownSnippets(File(wikiRootDir, "evidence"), wikiRootDir, limit = 6)
        val questions = readMarkdownSnippets(File(wikiRootDir, "questions"), wikiRootDir, limit = 6)
        val methods = readMarkdownSnippets(File(wikiRootDir, "methods"), wikiRootDir, limit = 6)
        val experiments = readMarkdownSnippets(File(wikiRootDir, "experiments"), wikiRootDir, limit = 6)
        val knowledgeItems = snapshot.knowledgeItems
            .sortedWith(
                compareBy<KnowledgeLayerSearchItem> { graphTypePriority(it.type) }
                    .thenByDescending { it.updatedAt },
            )
            .take(12)
        val recentLogEntries = readLogEntries(File(wikiRootDir, "log.md"), limit = 6)

        val directionCount = snapshot.directions.size
        val conclusionCount = countMarkdownPages(File(wikiRootDir, "conclusions"))
        val conceptCount = countMarkdownPages(File(wikiRootDir, "concepts"))
        val questionCount = countMarkdownPages(File(wikiRootDir, "questions"))
        val methodCount = countMarkdownPages(File(wikiRootDir, "methods"))
        val experimentCount = countMarkdownPages(File(wikiRootDir, "experiments"))
        val rawSourceCount = countMarkdownPages(File(rawRootDir, "notes")) +
            countMarkdownPages(File(rawRootDir, "research")) +
            countMarkdownPages(File(rawRootDir, "validations")) +
            countMarkdownPages(File(rawRootDir, "reflections")) +
            countMarkdownPages(File(rawRootDir, "reviews"))

        val updatedThreshold = generatedAt - 36L * 60L * 60L * 1000L
        val newSourceCount = notes.count { Instant.ofEpochMilli(it.updatedAt).atZone(zoneId).toLocalDate() == LocalDate.now(zoneId) }
        val newNodeCount = snapshot.knowledgeItems.count { it.updatedAt > updatedThreshold } +
            snapshot.directions.values.count { it.updatedAt > updatedThreshold }
        val newEdgeCount = snapshot.directions.values.count {
            it.knowledgeObjectLine.isNotBlank() ||
                it.groundingLine.isNotBlank() ||
                it.trustLine.isNotBlank()
        }
        val preferredSettledDirection = snapshot.directions.values
            .sortedByDescending { it.updatedAt }
            .firstOrNull {
                it.validatedPoints.isNotEmpty() ||
                    it.verifiedPoints.isNotEmpty() ||
                    it.conclusionLine.isNotBlank() ||
                    it.assetSummary.isNotBlank()
            } ?: snapshot.directions.values.maxByOrNull { it.updatedAt }
        val preferredConnectionDirection = snapshot.directions.values
            .sortedByDescending { it.updatedAt }
            .firstOrNull {
                it.openQuestions.isNotEmpty() ||
                    it.maintenanceFocusLine.isNotBlank() ||
                    it.maintenanceLine.isNotBlank() ||
                    it.hypothesisPoints.isNotEmpty()
            } ?: snapshot.directions.values.maxByOrNull { it.updatedAt }
        val preferredSettledKnowledge = (
            conclusions +
                evidence +
                methods +
                experiments +
                folderSlices +
                recentSources
            )
            .sortedByDescending { it.updatedAt }
            .firstOrNull { it.summary.isNotBlank() || it.title.isNotBlank() }
        val preferredConnectionKnowledge = (
            questions +
                concepts +
                methods +
                experiments +
                folderSlices +
                recentSources
            )
            .sortedByDescending { it.updatedAt }
            .firstOrNull { it.summary.isNotBlank() || it.title.isNotBlank() }

        val inventoryLine = buildString {
            append("${directionCount} 条方向")
            append(" · ${conclusionCount} 条结论")
            append(" · ${conceptCount} 个概念")
            append(" · ${questionCount} 个问题")
            append(" · ${methodCount} 个方法")
            append(" · ${experimentCount} 个实验")
        }
        val pointLabels = buildPointLabels(snapshot.knowledgeItems, snapshot.directions.values.toList())

        return MaintainerCorpus(
            rawIndexEntries = rawIndexEntries,
            wikiIndexEntries = wikiIndexEntries,
            recentSources = recentSources,
            folderSlices = folderSlices,
            knowledgeItems = knowledgeItems,
            directions = directions,
            conclusions = conclusions,
            concepts = concepts,
            evidence = evidence,
            questions = questions,
            methods = methods,
            experiments = experiments,
            recentLogEntries = recentLogEntries,
            inventoryLine = inventoryLine,
            pointLabels = pointLabels,
            rawSourceCount = rawSourceCount,
            directionCount = directionCount,
            knowledgeObjectCount = snapshot.knowledgeItems.size,
            newSourceCount = newSourceCount,
            newNodeCount = newNodeCount,
            newEdgeCount = newEdgeCount,
            activeHubLabel = preferredSettledKnowledge?.title
                ?.takeIf { it.isNotBlank() }
                ?: knowledgeItems.firstOrNull()?.title.orEmpty(),
            missingLinkLabel = preferredConnectionKnowledge?.summary
                .takeIf { !it.isNullOrBlank() }
                ?: preferredConnectionKnowledge?.title
                ?: preferredConnectionDirection?.maintenanceFocusLine
                    ?.takeIf { it.isNotBlank() }
                ?: preferredConnectionDirection?.openQuestions?.firstOrNull().orEmpty(),
            updatedDirectionTitle = preferredSettledKnowledge?.title
                ?.takeIf { it.isNotBlank() }
                ?: preferredSettledDirection?.title.orEmpty(),
            preferredSettledKnowledge = preferredSettledKnowledge,
            preferredConnectionKnowledge = preferredConnectionKnowledge,
            preferredSettledDirection = preferredSettledDirection,
            preferredConnectionDirection = preferredConnectionDirection,
        )
    }

    private fun buildKnowledgeShapeContext(corpus: MaintainerCorpus): String = buildString {
        appendLine("你正在维护一套本地 llm-wiki。下面是当前 wiki 的索引、最新 source 和知识对象分布。")
        appendLine("知识库存量：${corpus.inventoryLine}")
        if (corpus.rawIndexEntries.isNotEmpty()) {
            appendLine("raw index：")
            corpus.rawIndexEntries.forEach { appendLine("- $it") }
        }
        if (corpus.wikiIndexEntries.isNotEmpty()) {
            appendLine("wiki index：")
            corpus.wikiIndexEntries.forEach { appendLine("- $it") }
        }
        appendLine("最近维护日志：")
        if (corpus.recentLogEntries.isEmpty()) {
            appendLine("- 还没有维护日志")
        } else {
            corpus.recentLogEntries.forEach { appendLine("- $it") }
        }
        appendSnippetSection("最近 source", corpus.recentSources.take(6))
        appendSnippetSection("跨文件夹最近材料", corpus.folderSlices.take(5))
        appendSnippetSection("结论页面", corpus.conclusions.take(5))
        appendSnippetSection("概念页面", corpus.concepts.take(5))
        appendSnippetSection("问题页面", corpus.questions.take(4))
        appendSnippetSection("方法页面", corpus.methods.take(4))
        appendSnippetSection("实验页面", corpus.experiments.take(4))
        appendSnippetSection("方向页面", corpus.directions.take(4))
        if (corpus.pointLabels.isNotEmpty()) {
            appendLine("当前高频知识点：${corpus.pointLabels.joinToString(" / ")}")
        }
        appendLine("请说明当前知识长成什么样，以及它现在主要包含哪些点。")
    }

    private fun buildCurrentJudgementContext(corpus: MaintainerCorpus): String = buildString {
        appendLine("你在做本地 llm-wiki 的 maintainer pass。请基于最近 source、结论页、概念页、方法页、实验页和日志，压出一个当前综合判断。")
        appendLine("不要做即时摘要，要像已经维护过 wiki 之后读出来的当前结论。")
        appendLine("不要因为最近最活跃的是同一个项目，就默认围着它写；如果其他主题里有更通用的结论或方法，优先把它提出来。")
        appendLine("知识库存量：${corpus.inventoryLine}")
        if (corpus.wikiIndexEntries.isNotEmpty()) {
            appendLine("wiki index：")
            corpus.wikiIndexEntries.take(8).forEach { appendLine("- $it") }
        }
        corpus.preferredSettledKnowledge?.let { item ->
            appendLine("当前最稳知识：${item.title}")
            item.summary.takeIf { it.isNotBlank() }?.let { appendLine("稳定结果：$it") }
        }
        appendSnippetSection("跨文件夹最近材料", corpus.folderSlices.take(4))
        appendSnippetSection("最近 source", corpus.recentSources.take(6))
        appendSnippetSection("最近结论页", corpus.conclusions.take(4))
        appendSnippetSection("最近方法页", corpus.methods.take(3))
        appendSnippetSection("最近实验页", corpus.experiments.take(3))
        appendSnippetSection("最近证据页", corpus.evidence.take(4))
        appendSnippetSection("相关方向页", corpus.directions.take(3))
        appendSnippetSection("最近维护日志", corpus.recentLogEntries.map { MaintainerSnippet(it, "", "wiki/log.md", 0L) })
        appendLine("目标：输出一条会改变优先级的判断。")
    }

    private fun buildRecentAbsorptionContext(corpus: MaintainerCorpus): String = buildString {
        appendLine("请挑出最近真正被吸收进本地知识层的一条结果。它应该像被写进 wiki 的结论、方法或稳定判断。")
        appendLine("不要默认选择最近最热的项目记录；如果其他主题刚长出更可复用的结果，优先选那个。")
        appendLine("知识库存量：${corpus.inventoryLine}")
        if (corpus.rawIndexEntries.isNotEmpty()) {
            appendLine("raw index：")
            corpus.rawIndexEntries.take(6).forEach { appendLine("- $it") }
        }
        corpus.preferredSettledKnowledge?.let { item ->
            appendLine("优先结果：${item.title}")
            item.summary.takeIf { it.isNotBlank() }?.let { appendLine("结果摘要：$it") }
        }
        appendSnippetSection("跨文件夹最近材料", corpus.folderSlices.take(4))
        appendSnippetSection("最近 source", corpus.recentSources.take(6))
        appendSnippetSection("结论页", corpus.conclusions.take(5))
        appendSnippetSection("证据页", corpus.evidence.take(5))
        appendSnippetSection("方法页", corpus.methods.take(4))
        appendSnippetSection("实验页", corpus.experiments.take(4))
        appendSnippetSection("相关方向页", corpus.directions.take(3))
        appendLine("目标：输出最近吸收进知识层的一条结果，不要写趋势总结。")
    }

    private fun buildNewConnectionContext(corpus: MaintainerCorpus): String = buildString {
        appendLine("请像本地 llm-wiki 维护员一样，找出今天知识层里最值得长出来的一条新连接。")
        appendLine("不要复述当前判断，要优先连接不同方向、不同概念、不同方法或不同经验。")
        appendLine("如果某个项目最近特别活跃，不要继续在它内部兜圈，优先寻找跨主题、跨文件夹的连接。")
        appendLine("知识库存量：${corpus.inventoryLine}")
        if (corpus.wikiIndexEntries.isNotEmpty()) {
            appendLine("wiki index：")
            corpus.wikiIndexEntries.take(8).forEach { appendLine("- $it") }
        }
        corpus.preferredConnectionKnowledge?.let { item ->
            appendLine("当前主要张力：${item.title}")
            item.summary.takeIf { it.isNotBlank() }?.let { appendLine("张力摘要：$it") }
        }
        appendSnippetSection("跨文件夹最近材料", corpus.folderSlices.take(5))
        appendSnippetSection("概念页", corpus.concepts.take(5))
        appendSnippetSection("问题页", corpus.questions.take(5))
        appendSnippetSection("方法页", corpus.methods.take(4))
        appendSnippetSection("实验页", corpus.experiments.take(4))
        appendSnippetSection("方向页", corpus.directions.take(3))
        appendLine("高频知识点：${corpus.pointLabels.joinToString(" / ")}")
        appendLine("目标：输出一条新连接，以及下一次该摄入什么材料。")
    }

    private fun buildOpenQuestionContext(corpus: MaintainerCorpus): String = buildString {
        appendLine("请像本地知识维护员一样，挑一个当前最值得继续追问的问题。")
        appendLine("这个问题应该来自真实知识张力，而不是普通待办。")
        appendLine("知识库存量：${corpus.inventoryLine}")
        if (corpus.wikiIndexEntries.isNotEmpty()) {
            appendLine("wiki index：")
            corpus.wikiIndexEntries.take(8).forEach { appendLine("- $it") }
        }
        appendSnippetSection("跨文件夹最近材料", corpus.folderSlices.take(4))
        appendSnippetSection("问题页", corpus.questions.take(6))
        appendSnippetSection("证据页", corpus.evidence.take(4))
        appendSnippetSection("最近日志", corpus.recentLogEntries.map { MaintainerSnippet(it, "", "wiki/log.md", 0L) })
        corpus.preferredConnectionKnowledge?.let { item ->
            item.summary.takeIf { it.isNotBlank() }?.let { appendLine("- ${item.title}：$it") }
        }
        appendLine("目标：输出一个最值得追的问题，以及为什么现在要追它。")
    }

    private fun buildMaintainerMarkdown(snapshot: LocalKnowledgeMaintenanceSnapshot): String = buildString {
        appendLine("# Local Maintainer")
        appendLine()
        appendLine("- date: ${snapshot.date}")
        appendLine("- model: Gemma 4 E4B")
        appendLine("- generated_at: ${displayTime(snapshot.generatedAt)}")
        appendLine("- active_directions: ${snapshot.activeDirectionCount}")
        appendLine("- inventory: ${snapshot.knowledgeInventoryLine}")
        appendLine()
        appendSection("当前知识长相", snapshot.knowledgeShape)
        if (snapshot.knowledgePointLabels.isNotEmpty()) {
            appendLine("## 当前包含的点")
            snapshot.knowledgePointLabels.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("## 今天图谱变化")
        appendLine("- 活跃枢纽：${snapshot.graphPulse.activeHubLabel.ifBlank { "还没形成稳定枢纽" }}")
        appendLine("- 新材料：${snapshot.graphPulse.newSourceCount}")
        appendLine("- 新节点：${snapshot.graphPulse.newNodeCount}")
        appendLine("- 新连接：${snapshot.graphPulse.newEdgeCount}")
        snapshot.graphPulse.missingLinkLabel.takeIf { it.isNotBlank() }?.let {
            appendLine("- 待补连接：$it")
        }
        appendLine()
        appendSection("今天新吸收", snapshot.recentAbsorption)
        appendSection("当前成立", snapshot.currentJudgement)
        appendSection("今天新连接", snapshot.newConnection)
        appendSection("待厘清", snapshot.openQuestion)
    }

    private fun writeMaintainerFiles(
        snapshot: LocalKnowledgeMaintenanceSnapshot,
        corpus: MaintainerCorpus,
    ) {
        rootDir.mkdirs()
        val latestFile = File(rootDir, "latest.md")
        val latestJsonFile = File(rootDir, "latest.json")
        val dailyFile = File(rootDir, "${snapshot.date}.md")
        val shapeFile = File(rootDir, "knowledge-shape.md")
        val judgementFile = File(rootDir, "current-judgement.md")
        val absorptionFile = File(rootDir, "recent-absorption.md")
        val connectionFile = File(rootDir, "new-connection.md")
        val questionFile = File(rootDir, "open-question.md")
        val indexFile = File(rootDir, "index.md")
        val logFile = File(rootDir, "log.md")

        val latestContent = buildMaintainerMarkdown(snapshot)
        latestFile.writeText(latestContent)
        dailyFile.writeText(latestContent)
        latestJsonFile.writeText(snapshot.toJson().toString(2))

        shapeFile.writeText(
            buildString {
                appendLine("# 当前知识长相")
                appendLine()
                appendLine(snapshot.knowledgeShape.line.ifBlank { fallbackKnowledgeShapeLine(corpus) })
                appendLine()
                appendLine(snapshot.knowledgeShape.support.ifBlank { fallbackKnowledgeShapeSupport(corpus) })
                appendLine()
                appendLine("## 当前库存")
                appendLine(snapshot.knowledgeInventoryLine)
                appendLine()
                if (snapshot.knowledgePointLabels.isNotEmpty()) {
                    appendLine("## 当前包含的点")
                    snapshot.knowledgePointLabels.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendSnippetSection("最近方向页", corpus.directions.take(4))
                appendSnippetSection("最近结论页", corpus.conclusions.take(4))
                appendSnippetSection("最近概念页", corpus.concepts.take(4))
            },
        )
        judgementFile.writeText(buildCardMarkdown("当前成立", snapshot.currentJudgement, corpus.conclusions, corpus.evidence))
        absorptionFile.writeText(buildCardMarkdown("今天新吸收", snapshot.recentAbsorption, corpus.recentSources, corpus.conclusions))
        connectionFile.writeText(buildCardMarkdown("今天新连接", snapshot.newConnection, corpus.concepts + corpus.questions, corpus.methods + corpus.experiments))
        questionFile.writeText(buildCardMarkdown("待厘清", snapshot.openQuestion, corpus.questions, corpus.evidence))
        indexFile.writeText(
            buildString {
                appendLine("# Local Maintainer Index")
                appendLine()
                appendLine("更新时间：${displayTime(snapshot.generatedAt)}")
                appendLine()
                appendLine("- [当前知识长相](knowledge-shape.md) · ${snapshot.knowledgeShape.line}")
                appendLine("- [当前成立](current-judgement.md) · ${snapshot.currentJudgement.line}")
                appendLine("- [今天新吸收](recent-absorption.md) · ${snapshot.recentAbsorption.line}")
                appendLine("- [今天新连接](new-connection.md) · ${snapshot.newConnection.line}")
                appendLine("- [待厘清](open-question.md) · ${snapshot.openQuestion.line}")
            },
        )
        logFile.appendText(
            buildString {
                appendLine("## [${displayDate(snapshot.generatedAt)}] maintain | ${snapshot.activeDirectionCount} directions · ${corpus.rawSourceCount} raw sources")
                appendLine("- 当前知识：${snapshot.knowledgeShape.line}")
                appendLine("- 当前成立：${snapshot.currentJudgement.line}")
                appendLine("- 今天新吸收：${snapshot.recentAbsorption.line}")
                appendLine("- 今天新连接：${snapshot.newConnection.line}")
                snapshot.openQuestion.line.takeIf { it.isNotBlank() }?.let { appendLine("- 待厘清：$it") }
                appendLine()
            },
        )
    }

    private fun buildCardMarkdown(
        title: String,
        card: LocalMaintainerCard,
        primaryReferences: List<MaintainerSnippet>,
        secondaryReferences: List<MaintainerSnippet>,
    ): String = buildString {
        appendLine("# $title")
        appendLine()
        appendLine(card.line.ifBlank { "暂无内容" })
        appendLine()
        card.support.takeIf { it.isNotBlank() }?.let {
            appendLine(it)
            appendLine()
        }
        card.anchorLabel.takeIf { it.isNotBlank() }?.let {
            appendLine("主要来自：$it")
            appendLine()
        }
        appendSnippetSection("主要参考", primaryReferences.take(5))
        appendSnippetSection("次级参考", secondaryReferences.take(4))
    }

    private fun buildCard(
        result: AiChatResult,
        fallbackLine: String,
        fallbackSupport: String,
        anchorLabel: String,
        threadKey: String,
        noteId: Long? = null,
    ): LocalMaintainerCard {
        val lines = (result as? AiChatResult.Success)?.content?.let(::parseLines).orEmpty()
        return LocalMaintainerCard(
            line = lines.getOrElse(0) { fallbackLine }.ifBlank { fallbackLine },
            support = lines.getOrElse(1) { fallbackSupport }.ifBlank { fallbackSupport },
            anchorLabel = anchorLabel,
            threadKey = threadKey,
            noteId = noteId,
        )
    }

    private fun fallbackKnowledgeShapeLine(corpus: MaintainerCorpus): String {
        val primaryDirection = corpus.preferredSettledKnowledge?.title
            ?.takeIf { it.isNotBlank() }
            ?: corpus.updatedDirectionTitle.takeIf { it.isNotBlank() }
        val topPoint = corpus.pointLabels.firstOrNull()
        return when {
            primaryDirection != null && topPoint != null ->
                "当前知识开始围绕 $primaryDirection 这样的稳定结果收束，并开始把 $topPoint 这样的点写进更可复用的积累里。"
            primaryDirection != null ->
                "当前知识开始围绕 $primaryDirection 这样的稳定结果收束，最近的维护在把零散材料压成更可复用的结果。"
            corpus.inventoryLine.isNotBlank() ->
                "当前知识已经开始长出稳定骨架，不再只是零散记录。"
            else -> "当前知识还在形成中。"
        }
    }

    private fun fallbackKnowledgeShapeSupport(corpus: MaintainerCorpus): String {
        val pointPreview = corpus.pointLabels.take(4)
        return when {
            corpus.folderSlices.isNotEmpty() ->
                "最近跨文件夹被卷进来的材料包括：${corpus.folderSlices.take(3).joinToString("、") { it.title }}。"
            pointPreview.isNotEmpty() ->
                "现在最明显的点包括：${pointPreview.joinToString("、")}。"
            corpus.inventoryLine.isNotBlank() ->
                "当前库存：${corpus.inventoryLine}。"
            else -> "继续喂入真实材料，本地维护才会把知识层长起来。"
        }
    }

    private fun fallbackNewConnectionLine(corpus: MaintainerCorpus): String? {
        val first = corpus.pointLabels.firstOrNull() ?: return null
        val second = corpus.pointLabels.getOrNull(1) ?: return null
        return "把 $first 和 $second 连起来，可能会打开一条更具体的新方向。"
    }

    private fun fallbackNewConnectionSupport(corpus: MaintainerCorpus): String? {
        val question = corpus.questions.firstOrNull()?.title
        val method = corpus.methods.firstOrNull()?.title
        return when {
            corpus.folderSlices.size >= 2 ->
                "下一次可以先拿“${corpus.folderSlices[0].title}”和“${corpus.folderSlices[1].title}”试着对照，看它们能不能长成同一条方法线。"
            question != null && method != null -> "下一次可以围绕“$question”补材料，再试着借用“$method”的做法。"
            question != null -> "下一次可以先围绕“$question”继续补来源。"
            method != null -> "下一次可以先试着把“$method”迁到另一个方向。"
            else -> null
        }
    }

    private fun markMaintenanceStarted(startedAt: Long) {
        updateMaintenanceStatus(
            LocalKnowledgeMaintenanceStatus(
                isRunning = true,
                progress = 0f,
                step = "准备本地维护",
                lastStartedAt = startedAt,
                lastFinishedAt = _maintenanceStatus.value.lastFinishedAt,
                lastSucceededAt = _maintenanceStatus.value.lastSucceededAt,
                lastError = "",
                lastTracePath = "",
            ),
        )
    }

    private fun updateMaintenanceProgress(step: String, progress: Float) {
        updateMaintenanceStatus(
            _maintenanceStatus.value.copy(
                isRunning = true,
                progress = progress.coerceIn(0f, 1f),
                step = step,
                lastError = "",
                lastTracePath = "",
            ),
        )
    }

    private fun markMaintenanceSuccess(message: String, finishedAt: Long) {
        updateMaintenanceStatus(
            _maintenanceStatus.value.copy(
                isRunning = false,
                progress = 1f,
                step = message,
                lastFinishedAt = finishedAt,
                lastSucceededAt = finishedAt,
                lastError = "",
                lastTracePath = "",
            ),
        )
    }

    private fun markMaintenanceFailure(
        message: String,
        step: String,
        tracePath: String,
    ) {
        updateMaintenanceStatus(
            _maintenanceStatus.value.copy(
                isRunning = false,
                progress = _maintenanceStatus.value.progress.coerceAtLeast(0.01f),
                step = step,
                lastFinishedAt = System.currentTimeMillis(),
                lastError = if (step.isNotBlank()) "$message（停在：$step）" else message,
                lastTracePath = tracePath,
            ),
        )
    }

    private fun updateMaintenanceStatus(status: LocalKnowledgeMaintenanceStatus) {
        _maintenanceStatus.value = status
        rootDir.mkdirs()
        externalLogDir.mkdirs()
        val json = statusToJson(status).toString(2)
        statusFile.writeText(json)
        externalStatusFile.writeText(json)
    }

    private fun loadStatusFromDisk(): LocalKnowledgeMaintenanceStatus {
        val sourceFile = when {
            statusFile.exists() -> statusFile
            externalStatusFile.exists() -> externalStatusFile
            else -> return LocalKnowledgeMaintenanceStatus()
        }
        val persisted = runCatching {
            val json = JSONObject(sourceFile.readText())
            LocalKnowledgeMaintenanceStatus(
                isRunning = json.optBoolean("isRunning"),
                progress = json.optDouble("progress").toFloat(),
                step = json.optString("step"),
                lastStartedAt = json.optLong("lastStartedAt"),
                lastFinishedAt = json.optLong("lastFinishedAt"),
                lastSucceededAt = json.optLong("lastSucceededAt"),
                lastError = json.optString("lastError"),
                lastTracePath = json.optString("lastTracePath"),
            )
        }.getOrNull() ?: return LocalKnowledgeMaintenanceStatus()
        return if (persisted.isRunning) {
            persisted.copy(
                isRunning = false,
                progress = persisted.progress.coerceAtLeast(0.01f),
                lastFinishedAt = System.currentTimeMillis(),
                lastError = persisted.lastError.ifBlank {
                    if (persisted.step.isNotBlank()) {
                        "上次维护意外中断，停在：${persisted.step}"
                    } else {
                        "上次维护意外中断。"
                    }
                },
            )
        } else {
            persisted
        }
    }

    private fun statusToJson(status: LocalKnowledgeMaintenanceStatus): JSONObject = JSONObject().apply {
        put("isRunning", status.isRunning)
        put("progress", status.progress.toDouble())
        put("step", status.step)
        put("lastStartedAt", status.lastStartedAt)
        put("lastFinishedAt", status.lastFinishedAt)
        put("lastSucceededAt", status.lastSucceededAt)
        put("lastError", status.lastError)
        put("lastTracePath", status.lastTracePath)
    }

    private fun writeFailureTrace(step: String, throwable: Throwable): String {
        rootDir.mkdirs()
        externalLogDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val file = File(rootDir, "error-$timestamp.md")
        val externalFile = File(externalLogDir, "local-maintainer-error-$timestamp.md")
        val stackTrace = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()
        val content = buildString {
            appendLine("# Local Maintainer Error")
            appendLine()
            appendLine("- at: ${displayTime(timestamp)}")
            appendLine("- step: ${step.ifBlank { "未知步骤" }}")
            appendLine("- type: ${throwable::class.java.name}")
            appendLine("- message: ${throwable.message.orEmpty()}")
            appendLine()
            appendLine("```")
            appendLine(stackTrace.trim())
            appendLine("```")
        }
        file.writeText(content)
        latestErrorFile.writeText(content)
        externalFile.writeText(content)
        externalLatestErrorFile.writeText(content)
        return externalFile.absolutePath
    }

    private fun buildPointLabels(
        knowledgeItems: List<KnowledgeLayerSearchItem>,
        directions: List<DirectionWikiDirectionSummary>,
    ): List<String> {
        val labels = knowledgeItems
            .sortedWith(
                compareBy<KnowledgeLayerSearchItem> { graphTypePriority(it.type) }
                    .thenByDescending { it.updatedAt },
            )
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
            .toMutableList()
        if (labels.size < 4) {
            directions.sortedByDescending { it.updatedAt }
                .map { it.title.trim() }
                .filter { it.isNotBlank() }
                .forEach { label ->
                    if (label !in labels && labels.size < 6) labels += label
                }
        }
        return labels
    }

    private fun graphTypePriority(type: KnowledgeLayerSearchType): Int =
        when (type) {
            KnowledgeLayerSearchType.CONCLUSION -> 0
            KnowledgeLayerSearchType.EVIDENCE -> 1
            KnowledgeLayerSearchType.CONCEPT -> 2
            KnowledgeLayerSearchType.QUESTION -> 3
            KnowledgeLayerSearchType.METHOD -> 4
            KnowledgeLayerSearchType.EXPERIMENT -> 5
            KnowledgeLayerSearchType.DIRECTION -> 6
        }

    private fun buildFolderSlices(notes: List<NoteEntity>): List<MaintainerSnippet> =
        notes
            .filter { !it.isArchived }
            .groupBy { MindFolderCatalog.normalizedKey(it.folderKey) ?: "uncategorized" }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<NoteEntity>>> { (_, grouped) ->
                    grouped.maxOfOrNull { it.updatedAt } ?: 0L
                }.thenByDescending { (_, grouped) ->
                    grouped.count { it.status == NoteStatus.IN_PROGRESS }
                },
            )
            .mapNotNull { (folderKey, grouped) ->
                val note = grouped.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                val folderName = MindFolderCatalog.fromKey(folderKey)?.name ?: "其他"
                val summary = note.topic.ifBlank {
                    note.content
                        .lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty()
                }.take(120)
                MaintainerSnippet(
                    title = "$folderName · ${note.topic.ifBlank { "最近材料" }}",
                    summary = summary.ifBlank { "这个文件夹最近也有值得被吸进知识层的材料。" },
                    relativePath = "notes/${note.id}.md",
                    updatedAt = note.updatedAt,
                )
            }
            .take(6)

    private fun readMarkdownSnippets(
        directory: File,
        relativeBase: File,
        limit: Int,
    ): List<MaintainerSnippet> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "md" && it.name != "index.md" }
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .mapNotNull { readSnippet(it, relativeBase) }
    }

    private fun countMarkdownPages(directory: File): Int =
        directory.listFiles()
            .orEmpty()
            .count { it.isFile && it.extension == "md" && it.name != "index.md" }

    private fun readSnippet(
        file: File,
        relativeBase: File,
    ): MaintainerSnippet? {
        val lines = runCatching { file.readLines() }.getOrNull() ?: return null
        val title = lines.firstOrNull { it.trim().startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()
            .ifBlank { file.nameWithoutExtension }
        val summary = extractMarkdownSummary(lines)
        val relativePath = runCatching { file.relativeTo(relativeBase).path }.getOrElse { file.name }
        return MaintainerSnippet(
            title = title,
            summary = summary,
            relativePath = relativePath,
            updatedAt = file.lastModified(),
        )
    }

    private fun extractMarkdownSummary(lines: List<String>): String {
        val cleaned = lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }
            .filterNot { it.startsWith("- 更新时间") }
            .filterNot { it.startsWith("- 最近更新") }
            .filterNot { it.startsWith("- 类型") }
            .filterNot { it.startsWith("- 相关方向") }
            .filterNot { it.startsWith("- 相关记录") }
            .filterNot { it.startsWith("- 当前条目数") }
            .filterNot { it.startsWith("- date:") }
            .filterNot { it.startsWith("- model:") }
            .filterNot { it.startsWith("- generated_at:") }
            .filterNot { it.startsWith("- active_directions:") }
            .filterNot { it.startsWith("- inventory:") }
            .map {
                if (it.startsWith("- ")) it.removePrefix("- ").trim() else it
            }
            .filter { it.isNotBlank() }
            .toList()
        return cleaned.firstOrNull()?.take(140).orEmpty()
    }

    private fun readLogEntries(
        file: File,
        limit: Int,
    ): List<String> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("## [") }
                .takeLast(limit)
                .reversed()
        }.getOrElse { emptyList() }
    }

    private fun readIndexEntries(
        file: File,
        limit: Int,
    ): List<String> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("- ") }
                .map { it.removePrefix("- ").trim() }
                .filter { it.isNotBlank() }
                .take(limit)
        }.getOrElse { emptyList() }
    }

    private fun StringBuilder.appendSection(
        title: String,
        card: LocalMaintainerCard,
    ) {
        if (!card.hasContent) return
        appendLine("## $title")
        appendLine(card.line)
        card.support.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        card.anchorLabel.takeIf { it.isNotBlank() }?.let { appendLine("来自：$it") }
        appendLine()
    }

    private fun StringBuilder.appendSnippetSection(
        title: String,
        items: List<MaintainerSnippet>,
    ) {
        if (items.isEmpty()) return
        appendLine("## $title")
        items.forEach { item ->
            appendLine("- ${item.title}｜${item.summary.ifBlank { item.relativePath }}")
        }
        appendLine()
    }

    private fun parseLines(raw: String): List<String> =
        raw.replace("\r", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                it.removePrefix("-")
                    .removePrefix("•")
                    .removePrefix("1.")
                    .removePrefix("2.")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(4)
            .toList()

    private fun loadSnapshotFromDisk(): LocalKnowledgeMaintenanceSnapshot {
        val latestJson = File(rootDir, "latest.json")
        if (!latestJson.exists()) return LocalKnowledgeMaintenanceSnapshot(rootPath = rootDir.absolutePath)
        return runCatching {
            JSONObject(latestJson.readText()).toSnapshot(rootDir.absolutePath)
        }.getOrElse {
            LocalKnowledgeMaintenanceSnapshot(rootPath = rootDir.absolutePath)
        }
    }

    private fun LocalKnowledgeMaintenanceSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("generatedAt", generatedAt)
        put("rootPath", rootPath)
        put("knowledgeShape", knowledgeShape.toJson())
        put("knowledgeInventoryLine", knowledgeInventoryLine)
        put("knowledgePointLabels", JSONArray(knowledgePointLabels))
        put("lastLogLine", lastLogLine)
        put("activeDirectionCount", activeDirectionCount)
        put("updatedDirectionTitle", updatedDirectionTitle)
        put("recentAbsorption", recentAbsorption.toJson())
        put("currentJudgement", currentJudgement.toJson())
        put("newConnection", newConnection.toJson())
        put("openQuestion", openQuestion.toJson())
        put("graphPulse", graphPulse.toJson())
    }

    private fun LocalMaintainerCard.toJson(): JSONObject = JSONObject().apply {
        put("line", line)
        put("support", support)
        put("anchorLabel", anchorLabel)
        put("threadKey", threadKey)
        put("noteId", noteId ?: JSONObject.NULL)
    }

    private fun LocalKnowledgeGraphPulse.toJson(): JSONObject = JSONObject().apply {
        put("newSourceCount", newSourceCount)
        put("newNodeCount", newNodeCount)
        put("newEdgeCount", newEdgeCount)
        put("activeHubLabel", activeHubLabel)
        put("missingLinkLabel", missingLinkLabel)
    }

    private fun JSONObject.toSnapshot(rootPath: String): LocalKnowledgeMaintenanceSnapshot =
        LocalKnowledgeMaintenanceSnapshot(
            rootPath = optString("rootPath", rootPath).ifBlank { rootPath },
            generatedAt = optLong("generatedAt"),
            date = optString("date"),
            knowledgeShape = optJSONObject("knowledgeShape").toCard(),
            knowledgeInventoryLine = optString("knowledgeInventoryLine"),
            knowledgePointLabels = optJSONArray("knowledgePointLabels").toStringList(),
            lastLogLine = optString("lastLogLine"),
            recentAbsorption = optJSONObject("recentAbsorption").toCard(),
            currentJudgement = optJSONObject("currentJudgement").toCard(),
            newConnection = optJSONObject("newConnection").toCard(),
            openQuestion = optJSONObject("openQuestion").toCard(),
            graphPulse = optJSONObject("graphPulse").toGraphPulse(),
            activeDirectionCount = optInt("activeDirectionCount"),
            updatedDirectionTitle = optString("updatedDirectionTitle"),
        )

    private fun JSONObject?.toCard(): LocalMaintainerCard =
        if (this == null) {
            LocalMaintainerCard()
        } else {
            LocalMaintainerCard(
                line = optString("line"),
                support = optString("support"),
                anchorLabel = optString("anchorLabel"),
                threadKey = optString("threadKey"),
                noteId = optLong("noteId").takeIf { !isNull("noteId") },
            )
        }

    private fun JSONObject?.toGraphPulse(): LocalKnowledgeGraphPulse =
        if (this == null) {
            LocalKnowledgeGraphPulse()
        } else {
            LocalKnowledgeGraphPulse(
                newSourceCount = optInt("newSourceCount"),
                newNodeCount = optInt("newNodeCount"),
                newEdgeCount = optInt("newEdgeCount"),
                activeHubLabel = optString("activeHubLabel"),
                missingLinkLabel = optString("missingLinkLabel"),
            )
        }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) {
            emptyList()
        } else {
            buildList {
                for (index in 0 until length()) {
                    optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }

    private fun displayDate(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(displayDateFormatter)

    private fun displayTime(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(displayTimeFormatter)
}
