package com.mindflow.app.ui.screens.today

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.connect.ThemeThread
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import org.junit.Test

class TodayDesignModelTest {
    @Test
    fun toTodayDesignModel_preservesReferenceTodayHierarchyFromFlowState() {
        val uiState = TodayUiState(
            todayCount = 2,
            mainlineCandidate = MainlineBetCandidate(
                key = "today-redesign",
                title = "MindFlow 今天页改版",
                summary = "提升行动启动效率，连接更多知识与任务。",
                whyNow = "近期相关记录正在增多，且与当前 MVP 验证目标高度相关。",
                nextStep = "先把参考图三的首页结构迁移到当前工程。",
                threadKey = "today",
            ),
            followedDirections = listOf(
                followedDirection(
                    key = "flow",
                    title = "用户流程卡点优化",
                    summary = "多位用户反馈记录到推进之间仍有卡点。",
                    nextStep = "补齐加入今天的反馈闭环。",
                ),
                followedDirection(
                    key = "graph",
                    title = "图谱价值闭环",
                    summary = "图谱与今天推进之间需要更清晰的关系。",
                    nextStep = "把推进结果沉淀成图谱节点。",
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview(
                title = "今日回看",
                description = "昨天推进回顾：完成 2 个子任务，产出 1 篇设计草图。",
                savedSessionId = 7L,
            ),
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.heroTitle).isEqualTo("今天，MindFlow 发现你有 3 个值得推进的方向")
        assertThat(model.focus.title).isEqualTo("MindFlow 今天页改版")
        assertThat(model.focus.progressLabel).isEqualTo("推进中 · 65%")
        assertThat(model.reason.title).isEqualTo("AI 为什么推荐这个方向?")
        assertThat(model.reason.sourceLine).contains("本地模型")
        assertThat(model.reason.detailLines).hasSize(3)
        assertThat(model.discoveryCards).hasSize(3)
        assertThat(model.discoveryCards.first().title).isEqualTo("用户流程卡点优化")
        assertThat(model.discoveryCards.map { it.destinationLabel }).containsExactly("", "", "")
        assertThat(model.trackingRows.map { it.title }).containsExactly(
            "用户流程卡点优化",
            "图谱价值闭环",
        )
        assertThat(model.trackingRows.map { it.destinationLabel }).containsExactly(
            "详情",
            "详情",
        )
        assertThat(model.discoveryActionLabel).isEqualTo("共 3 个建议")
        assertThat(model.trackingActionLabel).isEqualTo("共 2 个任务")
        assertThat(model.review.title).isEqualTo("今日回看")
        assertThat(model.review.description).contains("昨天推进回顾")
    }

    @Test
    fun toTodayDesignModel_labelsCaptureFallbackDestinations() {
        val uiState = TodayUiState(todayCount = 1)

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.discoveryCards.last().destinationLabel).isEmpty()
        assertThat(model.trackingRows.single().destinationLabel).isEqualTo("新建记录")
    }

    @Test
    fun toTodayDesignModel_capsHeroCountToDisplayedSuggestions() {
        val uiState = TodayUiState(
            followedDirections = (1..5).map { index ->
                followedDirection(
                    key = "direction-$index",
                    title = "方向 $index",
                    summary = "第 $index 条可推进方向",
                    nextStep = "推进第 $index 条方向。",
                )
            },
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.heroTitle).isEqualTo("今天，MindFlow 发现你有 3 个值得推进的方向")
        assertThat(model.discoveryCards).hasSize(3)
    }

    @Test
    fun toTodayDesignModel_prefersSpecificDirectionCopyOverBroadBuckets() {
        val uiState = TodayUiState(
            followedDirections = listOf(
                followedDirection(
                    key = "work",
                    title = "工作",
                    summary = "现代高压社会下的身心枯竭与生存危机",
                    nextStep = "把它拆成一个今天能验证的小动作。",
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.focus.title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
        assertThat(model.discoveryCards.first().title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
        assertThat(model.trackingRows.first().title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
    }

    @Test
    fun toTodayDesignModel_prefersThreadSummaryWhenDirectionSummaryIsGeneric() {
        val uiState = TodayUiState(
            mainlineCandidate = MainlineBetCandidate(
                key = "work",
                title = "工作",
                summary = "这条方向已经进入持续推进阶段，关键是把最近的内容拆成动作。",
                threadKey = "work",
            ),
            followedDirections = listOf(
                followedDirection(
                    key = "work",
                    title = "工作",
                    summary = "这条方向已经进入持续推进阶段，关键是把最近的内容拆成动作。",
                    threadSummary = "现代高压社会下的身心枯竭与生存危机",
                    nextStep = "补上验证结果。",
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.focus.title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
        assertThat(model.discoveryCards.first().title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
        assertThat(model.trackingRows.first().title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
    }

    @Test
    fun toTodayDesignModel_trackingRowsOpenTheDisplayedDirectionThread() {
        val uiState = TodayUiState(
            followedDirections = listOf(
                followedDirection(
                    key = "work",
                    title = "工作",
                    summary = "现代高压社会下的身心枯竭与生存危机",
                    nextStep = "补上验证结果。",
                    focusNoteId = 42L,
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.trackingRows.first().title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
        assertThat(model.trackingRows.first().focusNoteId).isNull()
        assertThat(model.trackingRows.first().threadKey).isEqualTo("work")
        assertThat(model.trackingRows.first().destinationLabel).isEqualTo("详情")
    }

    @Test
    fun toTodayDesignModel_cleansMachineJsonFromTrackingRows() {
        val uiState = TodayUiState(
            followedDirections = listOf(
                followedDirection(
                    key = "project",
                    title = "项目",
                    summary = "项目推进方向",
                    nextStep = """{"summary":"围绕「项目」，您过去记录了10条关键线索","actions":["继续推进"]}""",
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.trackingRows.first().subtitle).doesNotContain("{")
        assertThat(model.trackingRows.first().subtitle).doesNotContain("\"summary\"")
        assertThat(model.trackingRows.first().subtitle).contains("过去记录了10条关键线索")
    }

    @Test
    fun toTodayDesignModel_cleansMachineJsonFromReviewHint() {
        val uiState = TodayUiState(todayCount = 1)

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview(
                title = "今日回看",
                description = "围绕「项目」，我过去记录过哪些关键线：过去记录了10条关键线索，涉及训练编辑功能和技术方案。",
                savedSessionId = 8L,
            ),
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.review.description).doesNotContain("{")
        assertThat(model.review.description).doesNotContain("\"summary\"")
        assertThat(model.review.description).contains("过去记录了10条关键线索")
    }

    @Test
    fun toTodayDesignModel_deduplicatesRepeatedReviewTitleAndExcerpt() {
        val uiState = TodayUiState(todayCount = 1)

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview(
                title = "今日回看",
                description = "我最近在关注什么？",
                savedSessionId = 9L,
            ),
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.review.description).isEqualTo("我最近在关注什么？")
    }

    @Test
    fun toTodayDesignModel_hidesCaptureMetadataPathsFromContinueNote() {
        val uiState = TodayUiState(
            continueNote = note(
                topic = "原始录音：/data/user/0/com.mindflow.app/files/recordings/voice-001.m4a",
                content = """
                    原始录音：/data/user/0/com.mindflow.app/files/recordings/voice-001.m4a
                    识别信息：端侧识别失败，等待重试。
                    语音转写（可编辑）：
                """.trimIndent(),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.focus.title).isEqualTo("语音记录")
        assertThat(model.focus.summary).isEqualTo("等待语音转写")
        assertThat(model.focus.title).doesNotContain("/data/")
        assertThat(model.focus.summary).doesNotContain("/data/")
        assertThat(model.focus.summary).doesNotContain("原始录音")
        assertThat(model.focus.summary).doesNotContain(".m4a")
    }

    @Test
    fun toTodayDesignModel_hidesCaptureMetadataPathsFromDiscoverySource() {
        val uiState = TodayUiState(
            followedDirections = listOf(
                followedDirection(
                    key = "project",
                    title = "项目",
                    summary = "原始录音：/data/user/0/com.mindflow.app/files/recordings/voice-001.m4a · 图谱整理",
                    nextStep = "先补一条真实记录。",
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.discoveryCards.first().source).isEqualTo("来自相关记录")
        assertThat(model.discoveryCards.first().source).doesNotContain("/data/")
        assertThat(model.discoveryCards.first().source).doesNotContain("原始录音")
    }

    @Test
    fun taskDetailFor_buildsReferenceTaskDetailFromDisplayedDirection() {
        val uiState = TodayUiState(
            followedDirections = listOf(
                followedDirection(
                    key = "work",
                    title = "工作",
                    summary = "现代高压社会下的身心枯竭与生存危机",
                    nextStep = "补上验证结果。",
                    lastProgressLine = "最近一次推进：04-30 17:16 补了新的想法。",
                    focusNoteId = 42L,
                    wikiValidatedPoint = "已经完成一次验证。",
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )
        val detail = model.taskDetailFor("work")

        assertThat(detail?.title).isEqualTo("现代高压社会下的身心枯竭与生存危机")
        assertThat(detail?.threadKey).isEqualTo("work")
        assertThat(detail?.progressLabel).isEqualTo("90%")
        assertThat(detail?.nextSuggestion).isEqualTo("补上验证结果。")
        assertThat(detail?.primaryActionLabel).isEqualTo("开始推进")
        assertThat(detail?.secondaryActionLabel).isEqualTo("询问回看")
        assertThat(detail?.tertiaryActionLabel).isEqualTo("拆成任务")
        assertThat(detail?.timeline?.map { it.label }).containsExactly("已识别", "已激活", "推进中", "已完成")
        assertThat(detail?.materials?.map { it.title }).contains("最近推进")
    }

    @Test
    fun toTodayDesignModel_keepsDiscoveryTitlesCompactForEqualHeightCards() {
        val uiState = TodayUiState(
            followedDirections = listOf(
                followedDirection(
                    key = "work",
                    title = "工作",
                    summary = "这是一条非常非常非常长的自动发现方向标题用于高度验证",
                    nextStep = "先压缩卡片标题。",
                ),
            ),
        )

        val model = uiState.toTodayDesignModel(
            reviewPreview = TodayReviewPreview.Empty,
            surface = uiState.toIncubationSurfaceState(),
        )

        assertThat(model.discoveryCards.first().title.length).isAtMost(19)
        assertThat(model.discoveryCards.first().title).endsWith("…")
    }

    private fun followedDirection(
        key: String,
        title: String,
        summary: String,
        nextStep: String,
        threadSummary: String = summary,
        focusNoteId: Long? = null,
        lastProgressLine: String = "",
        wikiValidatedPoint: String = "",
    ): FollowedDirectionSummary = FollowedDirectionSummary(
        thread = ThemeThread(
            key = key,
            title = title,
            summary = threadSummary,
            noteCount = 6,
        ),
        focusNoteId = focusNoteId,
        summary = summary,
        whyNow = "近 7 天多次出现",
        lastProgressLine = lastProgressLine,
        nextStep = nextStep,
        wikiValidatedPoint = wikiValidatedPoint,
    )

    private fun note(
        topic: String,
        content: String,
    ) = NoteEntity(
        id = 7L,
        content = content,
        topic = topic,
        topicSource = TopicSource.AI,
        folderKey = null,
        folderSource = FolderSource.RULE,
        tags = listOf("语音"),
        tagSource = TagSource.RULE,
        status = NoteStatus.IN_PROGRESS,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )
}
