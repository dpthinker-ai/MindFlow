package com.mindflow.app.data.localmodel

import android.content.Context
import com.mindflow.app.data.repository.NoteRepository
import com.mindflow.app.data.settings.OnDeviceModelSettingsRepository
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.wiki.DirectionWikiCoordinator
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalKnowledgeMaintenancePlanner(
    context: Context,
    private val noteRepository: NoteRepository,
    private val directionWikiCoordinator: DirectionWikiCoordinator,
    private val onDeviceModelSettingsRepository: OnDeviceModelSettingsRepository,
    private val onDeviceAiClient: OnDeviceAiClient,
    private val applicationScope: CoroutineScope,
) {
    private val rootDir = File(context.filesDir, "knowledge-layer/wiki/local-maintainer")
    private val refreshIntervalMs = 18L * 60L * 60L * 1000L
    private val _snapshot = MutableStateFlow(loadSnapshotFromDisk())
    val snapshot: StateFlow<LocalKnowledgeMaintenanceSnapshot> = _snapshot

    fun maintainInBackgroundIfNeeded() {
        val latest = File(rootDir, "latest.md")
        val now = System.currentTimeMillis()
        if (latest.exists() && now - latest.lastModified() < refreshIntervalMs) return
        applicationScope.launch {
            runCatching { refreshNow() }
        }
    }

    suspend fun refreshNow(): Result<Unit> = runCatching {
        val settings = onDeviceModelSettingsRepository.getCurrent()
        if (!settings.preferOnDevice || !settings.isReady) return@runCatching

        val notes = noteRepository.observeAllNotes().first().filter { !it.isArchived }
        val snapshot = directionWikiCoordinator.snapshot.value

        val recentNotes = notes.sortedByDescending { it.updatedAt }.take(6)
        val topDirections = snapshot.directions.values.sortedByDescending { it.updatedAt }.take(3)
        if (recentNotes.isEmpty() && topDirections.isEmpty()) return@runCatching
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        val settledDirection = topDirections.firstOrNull {
            it.validatedPoints.isNotEmpty() ||
                it.verifiedPoints.isNotEmpty() ||
                it.conclusionLine.isNotBlank() ||
                it.assetSummary.isNotBlank()
        } ?: topDirections.firstOrNull()
        val connectionDirection = topDirections.firstOrNull {
            it.openQuestions.isNotEmpty() ||
                it.maintenanceLine.isNotBlank() ||
                it.hypothesisPoints.isNotEmpty()
        } ?: topDirections.firstOrNull()

        val mainlineContext = buildString {
            appendLine("最近新材料：")
            recentNotes.forEachIndexed { index, note ->
                appendLine("${index + 1}. ${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}")
            }
            appendLine("已有方向：")
            topDirections.forEachIndexed { index, direction ->
                appendLine("${index + 1}. ${direction.title}｜${direction.conclusionLine.ifBlank { direction.assetSummary }.ifBlank { direction.healthLine }}")
            }
        }
        val settledContext = buildString {
            appendLine("已成立方向结果：")
            topDirections.forEachIndexed { index, direction ->
                appendLine("${index + 1}. ${direction.title}｜${direction.conclusionLine.ifBlank { direction.validatedPoints.firstOrNull().orEmpty() }.ifBlank { direction.verifiedPoints.firstOrNull().orEmpty() }.ifBlank { direction.assetSummary }}")
            }
            appendLine("最近稳定材料：")
            recentNotes.filter { it.knowledgeTrust.name == "VALIDATED" || it.knowledgeTrust.name == "VERIFIED" }
                .take(4)
                .forEachIndexed { index, note ->
                    appendLine("${index + 1}. ${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}")
                }
        }
        val gapContext = buildString {
            appendLine("当前张力：")
            topDirections.forEachIndexed { index, direction ->
                appendLine("${index + 1}. ${direction.title}｜${direction.openQuestions.firstOrNull().orEmpty().ifBlank { direction.maintenanceLine }.ifBlank { direction.healthLine }}")
            }
            appendLine("最近还没压实的新材料：")
            recentNotes.take(4).forEachIndexed { index, note ->
                appendLine("${index + 1}. ${note.topic.ifBlank { "未命名记录" }}｜${note.content.replace("\n", " ").replace(Regex("\\s+"), " ").trim().take(120)}")
            }
        }

        val mainline = onDeviceAiClient.generateFlowMainline(settings, mainlineContext)
        val settled = onDeviceAiClient.generateFlowSettledKnowledge(settings, settledContext)
        val gap = onDeviceAiClient.generateFlowBreakthroughGap(settings, gapContext)

        val date = today.toString()
        val generatedAt = System.currentTimeMillis()
        val localSnapshot = LocalKnowledgeMaintenanceSnapshot(
            rootPath = rootDir.absolutePath,
            generatedAt = generatedAt,
            date = date,
            recentAbsorption = buildCard(
                result = settled,
                fallbackLine = settledDirection?.validatedPoints?.firstOrNull()
                    ?: settledDirection?.verifiedPoints?.firstOrNull()
                    ?: settledDirection?.conclusionLine
                    ?: settledDirection?.assetSummary
                    ?: "今天还没有真正被吸收进知识层的新结果。",
                fallbackSupport = settledDirection?.groundingLine
                    ?: settledDirection?.trustLine
                    ?: settledDirection?.knowledgeObjectLine
                    ?: "再多给一点稳定材料，这里会开始留下更可复用的判断。",
                anchorLabel = settledDirection?.title.orEmpty(),
                threadKey = settledDirection?.threadKey.orEmpty(),
            ),
            currentJudgement = buildCard(
                result = mainline,
                fallbackLine = topDirections.firstOrNull()?.conclusionLine
                    ?: topDirections.firstOrNull()?.assetSummary
                    ?: "今天还没有被压成真正可用的综合判断。",
                fallbackSupport = topDirections.firstOrNull()?.nextShiftLine
                    ?: topDirections.firstOrNull()?.continuityLine
                    ?: "再沿着同一条线走一小步，本地维护会把它压成更稳的当前判断。",
                anchorLabel = topDirections.firstOrNull()?.title.orEmpty(),
                threadKey = topDirections.firstOrNull()?.threadKey.orEmpty(),
            ),
            newConnection = buildCard(
                result = gap,
                fallbackLine = connectionDirection?.openQuestions?.firstOrNull()
                    ?: connectionDirection?.maintenanceFocusLine
                    ?: connectionDirection?.maintenanceLine
                    ?: connectionDirection?.hypothesisPoints?.firstOrNull()
                    ?: "今天还没有长出真正值得试的新连接。",
                fallbackSupport = connectionDirection?.maintenanceSourceLine
                    ?: connectionDirection?.nextShiftLine
                    ?: connectionDirection?.groundingLine
                    ?: "再喂一点跨项目、跨文件夹的材料，这里更容易长出新连接。",
                anchorLabel = connectionDirection?.title.orEmpty(),
                threadKey = connectionDirection?.threadKey.orEmpty(),
            ),
            openQuestion = LocalMaintainerCard(
                line = connectionDirection?.openQuestions?.firstOrNull()
                    ?: connectionDirection?.maintenanceLine
                    ?: connectionDirection?.healthLine
                    ?: "",
                support = connectionDirection?.maintenanceSourceLine
                    ?: connectionDirection?.maintenanceFocusLine
                    ?: "",
                anchorLabel = connectionDirection?.title.orEmpty(),
                threadKey = connectionDirection?.threadKey.orEmpty(),
            ),
            graphPulse = LocalKnowledgeGraphPulse(
                newSourceCount = notes.count { it.updatedAt.toLocalDate(zoneId) == today },
                newNodeCount = snapshot.knowledgeItems.count { it.updatedAt > generatedAt - 36L * 60L * 60L * 1000L } +
                    snapshot.directions.values.count { it.updatedAt > generatedAt - 36L * 60L * 60L * 1000L },
                newEdgeCount = topDirections.count {
                    it.knowledgeObjectLine.isNotBlank() || it.groundingLine.isNotBlank() || it.trustLine.isNotBlank()
                },
                activeHubLabel = topDirections.firstOrNull()?.title.orEmpty(),
                missingLinkLabel = connectionDirection?.maintenanceFocusLine
                    ?: connectionDirection?.openQuestions?.firstOrNull()
                    ?: "",
            ),
            activeDirectionCount = snapshot.directions.size,
            updatedDirectionTitle = topDirections.firstOrNull()?.title.orEmpty(),
        )
        val latestFile = File(rootDir, "latest.md")
        val latestJsonFile = File(rootDir, "latest.json")
        val dailyFile = File(rootDir, "$date.md")
        withContext(Dispatchers.IO) {
            rootDir.mkdirs()
            val content = buildMarkdown(
                snapshot = localSnapshot,
            )
            latestFile.writeText(content)
            dailyFile.writeText(content)
            latestJsonFile.writeText(localSnapshot.toJson().toString(2))
        }
        _snapshot.value = localSnapshot
    }

    private fun buildMarkdown(snapshot: LocalKnowledgeMaintenanceSnapshot): String = buildString {
        appendLine("# Local maintainer")
        appendLine()
        appendLine("- date: ${snapshot.date}")
        appendLine("- model: Gemma 4 E4B")
        appendLine("- new_sources: ${snapshot.graphPulse.newSourceCount}")
        appendLine("- active_directions: ${snapshot.activeDirectionCount}")
        appendLine()
        appendLine("## 今天图谱变化")
        appendLine("- 活跃枢纽：${snapshot.graphPulse.activeHubLabel.ifBlank { "还没形成稳定枢纽" }}")
        appendLine("- 新节点：${snapshot.graphPulse.newNodeCount}")
        appendLine("- 新连接：${snapshot.graphPulse.newEdgeCount}")
        snapshot.graphPulse.missingLinkLabel.takeIf { it.isNotBlank() }?.let {
            appendLine("- 待补连接：$it")
        }
        appendLine()
        appendSection("今天新吸收", snapshot.recentAbsorption)
        appendSection("当前成立", snapshot.currentJudgement)
        appendSection("今天新连接", snapshot.newConnection)
        if (snapshot.openQuestion.hasContent) {
            appendSection("待厘清", snapshot.openQuestion)
        }
    }

    private fun StringBuilder.appendSection(
        title: String,
        card: LocalMaintainerCard,
    ) {
        appendLine("## $title")
        appendLine(card.line.ifBlank { "暂无内容" })
        card.support.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        card.anchorLabel.takeIf { it.isNotBlank() }?.let { appendLine("来自：$it") }
        appendLine()
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

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
}
