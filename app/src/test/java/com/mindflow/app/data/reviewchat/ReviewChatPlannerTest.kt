package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.localmodel.LocalMaintainerCard
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiFailureReason
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import com.mindflow.app.data.wiki.KnowledgeLayerSearchType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReviewChatPlannerTest {
    @Test
    fun classifyReviewChatIntent_prioritizesSynthesisLanguage() {
        assertThat(classifyReviewChatIntent("把工作和副项目里的共同主题串一下"))
            .isEqualTo(ReviewChatIntent.SYNTHESIZE)
        assertThat(classifyReviewChatIntent("为什么这个方向现在更值得继续推"))
            .isEqualTo(ReviewChatIntent.DISCUSS)
        assertThat(classifyReviewChatIntent("我之前什么时候提过这个问题"))
            .isEqualTo(ReviewChatIntent.RECALL)
    }

    @Test
    fun buildReviewChatContextPacket_includesRawAndStructuredInputsButCapsLength() {
        val packet = buildReviewChatContextPacket(
            question = "把最近两周的矛盾串一下",
            intent = ReviewChatIntent.SYNTHESIZE,
            notes = List(12) { index -> sampleNote(index.toLong() + 1L, "主题$index", "正文$index") },
            weeklyReview = WeeklyReviewState(lines = listOf("主线", "推进", "重启", "串联")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
                recentAbsorption = LocalMaintainerCard(line = "最近吸收", support = "吸收依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                directions = mapOf(
                    "product" to DirectionWikiDirectionSummary(
                        threadKey = "product",
                        slug = "product",
                        title = "产品方向",
                        conclusionLine = "一个关键结论",
                    ),
                ),
                knowledgeItems = listOf(
                    KnowledgeLayerSearchItem(
                        id = "k1",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "关键结论",
                        summary = "已经被沉淀下来的判断",
                    ),
                ),
            ),
            sessionSummary = "上一轮在讨论方向冲突",
        )

        assertThat(packet.rawNoteSnippets).hasSize(4)
        assertThat(packet.knowledgeBaseSnippets.first()).contains("当前判断")
        assertThat(packet.wikiSnippets.joinToString("\n")).contains("关键结论")
        assertThat(packet.structuredSnippets.joinToString("\n")).contains("当前判断")
        assertThat(packet.structuredSnippets.joinToString("\n")).contains("关键结论")
    }

    @Test
    fun buildReviewChatContextPacket_recall_prioritizesRawNotesAndStableWiki() {
        val packet = buildReviewChatContextPacket(
            question = "我之前什么时候提过产品方向",
            intent = ReviewChatIntent.RECALL,
            notes = List(12) { index -> sampleNote(index.toLong() + 1L, "产品方向$index", "正文$index") },
            weeklyReview = WeeklyReviewState(lines = listOf("主线", "推进")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
                recentAbsorption = LocalMaintainerCard(line = "最近吸收", support = "吸收依据"),
                openQuestion = LocalMaintainerCard(line = "待厘清问题", support = "问题依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                directions = mapOf(
                    "product" to DirectionWikiDirectionSummary(
                        threadKey = "product",
                        slug = "product",
                        title = "产品方向",
                        conclusionLine = "一个关键结论",
                    ),
                ),
                knowledgeItems = listOf(
                    KnowledgeLayerSearchItem(
                        id = "k1",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "产品方向判断",
                        summary = "已经被沉淀下来的判断",
                    ),
                ),
            ),
            sessionSummary = "",
        )

        assertThat(packet.rawNoteSnippets).hasSize(6)
        assertThat(packet.wikiSnippets.first()).contains("产品方向")
        assertThat(packet.knowledgeBaseSnippets).contains("待厘清问题｜待厘清问题")
    }

    @Test
    fun buildReviewChatContextPacket_genericHistoryQuestion_spreadsRawNotesAcrossTimeline() {
        val packet = buildReviewChatContextPacket(
            question = "我之前都说过什么",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "最早主题", "三个月前的记录").copy(updatedAt = 1_000L),
                sampleNote(2L, "中间主题A", "两个月前的记录").copy(updatedAt = 2_000L),
                sampleNote(3L, "中间主题B", "一个半月前的记录").copy(updatedAt = 3_000L),
                sampleNote(4L, "中间主题C", "一个月前的记录").copy(updatedAt = 4_000L),
                sampleNote(5L, "中间主题D", "三周前的记录").copy(updatedAt = 5_000L),
                sampleNote(6L, "中间主题E", "两周前的记录").copy(updatedAt = 6_000L),
                sampleNote(7L, "最近主题A", "前天的记录").copy(updatedAt = 7_000L),
                sampleNote(8L, "最近主题B", "昨天的记录").copy(updatedAt = 8_000L),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.rawNoteSnippets.joinToString("\n")).contains("最早主题")
        assertThat(packet.rawNoteSnippets.joinToString("\n")).contains("最近主题B")
    }

    @Test
    fun buildReviewChatContextPacket_chineseSentenceQuestionStillFindsShortKeywords() {
        val packet = buildReviewChatContextPacket(
            question = "帮我看一下抖音有哪些事情可以做",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "抖音文案记录", "这里记录了抖音选题、脚本和转化思路"),
                sampleNote(2L, "别的主题", "和抖音无关"),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.rawNoteSnippets.joinToString("\n")).contains("抖音文案记录")
    }

    @Test
    fun answer_automaticModePrefersCloudThenFallsBackToOnDevice() = runTest {
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "主题", "一条正文")) },
            loadWeeklyReview = { WeeklyReviewState(lines = listOf("主线")) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { AiChatResult.Failure(AiFailureReason.NETWORK, "网络失败") },
            runOnDevice = { AiChatResult.Success("端侧补位回答") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把我之前写过的问题串起来",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.ON_DEVICE)
        assertThat(result.fallbackOccurred).isTrue()
        assertThat(result.answer).contains("端侧补位回答")
    }

    @Test
    fun answer_onDeviceOnlyFailure_surfacesUnderlyingMessage() = runTest {
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "主题", "一条正文")) },
            loadWeeklyReview = { WeeklyReviewState(lines = listOf("主线")) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.ON_DEVICE_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { AiChatResult.Success("云侧不会被调用") },
            runOnDevice = {
                AiChatResult.Failure(
                    reason = AiFailureReason.OTHER,
                    message = "本地模型推理失败：上下文过长",
                )
            },
        )

        val error = runCatching {
            planner.answer(
                ReviewChatTurnRequest(
                    question = "帮我回忆之前聊过什么",
                    priorMessages = emptyList(),
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error?.message).contains("本地模型推理失败：上下文过长")
    }

    @Test
    fun answer_fullRecordQuestion_routesToModelWithRawRecordContext() = runTest {
        val april10Timestamp = LocalDate.now(ZoneId.systemDefault())
            .withMonth(4)
            .withDayOfMonth(10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        var capturedPrompt = ""
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(
                        id = 42L,
                        topic = "4 月 10 号讨论",
                        content = "这是 4 月 10 号的完整原文，里面详细讨论了 leakspace 和推荐链路。",
                    ).copy(updatedAt = april10Timestamp)
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompt = prompt
                AiChatResult.Success("基于 4 月 10 号原始记录整理后的回答。")
            },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把 4 月 10 号那条完整记录发给我",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.answer).contains("基于 4 月 10 号原始记录整理后的回答")
        assertThat(result.referencedNoteId).isEqualTo(42L)
        assertThat(capturedPrompt).contains("完整记录：")
        assertThat(capturedPrompt).contains("这是 4 月 10 号的完整原文")
    }

    @Test
    fun answer_outOfScopeQuestion_stillRoutesToModel() = runTest {
        var cloudCalled = false
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "产品方向", "这条记录只在讨论推荐链路和增长")) },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = {
                cloudCalled = true
                AiChatResult.Success("现有历史材料里没有和天气直接相关的记录。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "今天天气不错，有什么好玩的？",
                priorMessages = emptyList(),
            ),
        )

        assertThat(cloudCalled).isTrue()
        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.answer).contains("现有历史材料里没有和天气直接相关的记录")
    }

    @Test
    fun answer_genericHistoryQuestion_isNotBlockedByOutOfScopeGuard() = runTest {
        var cloudCalled = false
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "产品方向", "最近一直在讨论推荐链路和增长")) },
            loadWeeklyReview = { WeeklyReviewState(lines = listOf("最近主要在推进推荐链路验证")) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = {
                cloudCalled = true
                AiChatResult.Success("最近主要在推进推荐链路验证。")
            },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我最近在忙什么",
                priorMessages = emptyList(),
            ),
        )

        assertThat(cloudCalled).isTrue()
        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.answer).contains("最近主要在推进推荐链路验证")
    }

    @Test
    fun answer_todayScopedQuestion_routesToModelWithTodayRecordsInContext() = runTest {
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayStart = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var capturedPrompt = ""

        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "今天主题A", "今天第一条记录").copy(updatedAt = todayStart + 1_000L),
                    sampleNote(2L, "今天主题B", "今天第二条记录").copy(updatedAt = todayStart + 2_000L),
                    sampleNote(3L, "昨天主题", "昨天的记录").copy(updatedAt = yesterdayStart + 3_000L),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompt = prompt
                AiChatResult.Success("今天主要记录了今天主题A和今天主题B。")
            },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我只看今天的",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.answer).contains("今天主题A")
        assertThat(result.answer).contains("今天主题B")
        assertThat(capturedPrompt).contains("完整记录：")
        assertThat(capturedPrompt).contains("今天主题A")
        assertThat(capturedPrompt).contains("今天主题B")
        assertThat(capturedPrompt).doesNotContain("昨天主题")
    }

    @Test
    fun answer_dayFullRecordQuestion_routesToModelWithAllMatchedRecords() = runTest {
        val april10Start = LocalDate.now(ZoneId.systemDefault())
            .withMonth(4)
            .withDayOfMonth(10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        var capturedPrompt = ""
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(
                        id = 42L,
                        topic = "4 月 10 号讨论A",
                        content = "这是 4 月 10 号的第一条完整原文。",
                    ).copy(updatedAt = april10Start),
                    sampleNote(
                        id = 43L,
                        topic = "4 月 10 号讨论B",
                        content = "这是 4 月 10 号的第二条完整原文。",
                    ).copy(updatedAt = april10Start + 100_000L),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompt = prompt
                AiChatResult.Success("4 月 10 号当天有两条相关记录。")
            },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把 4 月 10 号那天的完整内容发给我",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.answer).contains("两条相关记录")
        assertThat(capturedPrompt).contains("完整记录：")
        assertThat(capturedPrompt).contains("4 月 10 号讨论A")
        assertThat(capturedPrompt).contains("4 月 10 号讨论B")
        assertThat(capturedPrompt).contains("第一条完整原文")
        assertThat(capturedPrompt).contains("第二条完整原文")
        assertThat(result.referencedNoteId).isNull()
    }

    @Test
    fun onDevicePrompt_isCondensedAndKeepsCurrentQuestion() {
        val packet = buildReviewChatContextPacket(
            question = "把我过去几个月关于产品方向的分歧串起来",
            intent = ReviewChatIntent.SYNTHESIZE,
            notes = List(10) { index -> sampleNote(index.toLong() + 1L, "主题$index", "正文$index".repeat(20)) },
            weeklyReview = WeeklyReviewState(lines = listOf("主线", "推进", "重启", "串联")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
                recentAbsorption = LocalMaintainerCard(line = "最近吸收", support = "吸收依据"),
                openQuestion = LocalMaintainerCard(line = "待厘清问题", support = "问题依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                directions = mapOf(
                    "product" to DirectionWikiDirectionSummary(
                        threadKey = "product",
                        slug = "product",
                        title = "产品方向",
                        conclusionLine = "一个关键结论",
                    ),
                ),
                knowledgeItems = List(6) { index ->
                    KnowledgeLayerSearchItem(
                        id = "k$index",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "结论$index",
                        summary = "沉淀$index",
                    )
                },
            ),
            sessionSummary = "上一轮在聊定位和增长冲突",
            priorMessages = listOf(
                ReviewChatMessage(
                    role = ReviewChatMessageRole.USER,
                    content = "上一轮问题",
                    createdAt = 1L,
                ),
                ReviewChatMessage(
                    role = ReviewChatMessageRole.ASSISTANT,
                    content = "上一轮回答",
                    createdAt = 2L,
                ),
            ),
        )

        val prompt = ReviewChatPromptFactory.onDevice(packet)

        assertThat(prompt.userMessage).contains("当前问题：把我过去几个月关于产品方向的分歧串起来")
        assertThat(prompt.systemInstruction).contains("回答要求：先直接回答当前问题，不要重复上一轮。")
        assertThat(prompt.userMessage).contains("最近问题：")
        assertThat(prompt.userMessage).contains("用户｜上一轮问题")
        assertThat(prompt.userMessage).doesNotContain("上一轮回答")
        assertThat(prompt.userMessage.indexOf("原始记录：")).isLessThan(prompt.userMessage.indexOf("LM Knowledge Base："))
        assertThat(prompt.userMessage).contains("LM Knowledge Base：")
        assertThat(prompt.userMessage).contains("原始记录：")
        assertThat(prompt.userMessage.length).isGreaterThan(300)
        assertThat(prompt.userMessage.length).isAtMost(1_800)
    }

    @Test
    fun onDevicePrompt_longInputsUseExpandedBudgetAndKeepMultipleSources() {
        val packet = buildReviewChatContextPacket(
            question = "我们想看一下现在 leakspace 这种技术对抖音是不是有优化指导意义，以及这件事和过去讨论过的增长、内容分发、推荐链路到底有没有一条稳定主线",
            intent = ReviewChatIntent.DISCUSS,
            notes = List(12) { index ->
                sampleNote(
                    id = index.toLong() + 1L,
                    topic = "讨论主题$index",
                    content = "这是第$index 条很长的原始记录，里面有很多关于推荐、增长、分发、抖音和 leakspace 的讨论。".repeat(10),
                )
            },
            weeklyReview = WeeklyReviewState(lines = listOf("这周主线很长".repeat(10), "推进判断".repeat(10))),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断".repeat(10), support = "判断依据".repeat(8)),
                recentAbsorption = LocalMaintainerCard(line = "最近吸收".repeat(10), support = "吸收依据".repeat(8)),
                openQuestion = LocalMaintainerCard(line = "待厘清问题".repeat(10), support = "问题依据".repeat(8)),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                directions = mapOf(
                    "product" to DirectionWikiDirectionSummary(
                        threadKey = "product",
                        slug = "product",
                        title = "产品方向".repeat(8),
                        conclusionLine = "一个关键结论".repeat(10),
                    ),
                ),
                knowledgeItems = List(8) { index ->
                    KnowledgeLayerSearchItem(
                        id = "k$index",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "结论$index".repeat(6),
                        summary = "沉淀$index".repeat(8),
                    )
                },
            ),
            sessionSummary = "上一轮在聊定位和增长冲突".repeat(8),
            priorMessages = listOf(
                ReviewChatMessage(
                    role = ReviewChatMessageRole.USER,
                    content = "上一轮问题".repeat(12),
                    createdAt = 1L,
                ),
                ReviewChatMessage(
                    role = ReviewChatMessageRole.ASSISTANT,
                    content = "上一轮回答".repeat(12),
                    createdAt = 2L,
                ),
            ),
        )

        val prompt = ReviewChatPromptFactory.onDevice(packet)

        assertThat(prompt.userMessage).contains("当前问题：")
        assertThat(prompt.userMessage).contains("LM Knowledge Base：")
        assertThat(prompt.userMessage).contains("LLM Wiki：")
        assertThat(prompt.userMessage).contains("原始记录：")
        assertThat(prompt.userMessage.length).isGreaterThan(300)
        assertThat(prompt.userMessage.length).isAtMost(1_800)
    }

    private fun sampleNote(id: Long, topic: String, content: String): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
        folderSource = FolderSource.MANUAL,
        tags = listOf("产品"),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L + id,
        updatedAt = 2_000L + id,
    )
}
