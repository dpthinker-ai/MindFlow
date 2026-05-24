package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.local.entity.NoteEntity
import com.mindflow.app.data.localmodel.LocalKnowledgeMaintenanceSnapshot
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteHorizon
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource
import com.mindflow.app.data.review.WeeklyReviewState
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class ReviewChatCoreRegressionTest {
    @Test
    fun allHistoryCountUsesWholeCorpusWithoutLinksOrExamples() {
        val packet = packet(
            question = "我总共有多少条记录",
            notes = listOf(
                note(1L, "最早主题", "最早内容"),
                note(2L, "中间主题", "中间内容"),
                note(3L, "最近主题", "最近内容"),
            ),
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.COLLECTION_OVERVIEW)
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(3)
        assertThat(packet.deterministicAnswerSnippets).contains("直接答案｜全部历史的记录共 3 条")
        assertThat(packet.rawNoteEvidence).isEmpty()
        assertThat(packet.skillResult).isNull()
    }

    @Test
    fun allHistoryCategoryQuestionKeepsFullCorpusAndNoFakeEntityFilter() {
        val packet = packet(
            question = "帮我分析所有的记录，都有哪些分类？",
            notes = listOf(
                note(1L, "应用启动页设计", "启动页、图标、名称"),
                note(2L, "OpenCL GPU 调研", "GPU、CPU、LiteRT"),
                note(3L, "注意力决定人生", "个人成长和精力管理"),
                note(4L, "低成本高效率的生存法则", "独立性和生存策略"),
                note(5L, "小朋友病愈复学", "家庭生活记录"),
            ),
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(packet.wantsCategories).isTrue()
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(5)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsExactly(
            "应用启动页设计",
            "OpenCL GPU 调研",
            "注意力决定人生",
            "低成本高效率的生存法则",
            "小朋友病愈复学",
        ).inOrder()
        assertThat(packet.querySummarySnippets).doesNotContain("主题｜分类")
        assertThat(packet.deterministicAnswerSnippets).contains("分类范围｜当前分类必须覆盖 5 条命中记录")
        assertThat(packet.skillResult).isNull()
    }

    @Test
    fun abstractLifeAdviceQuestionSemanticallyRecallsRelatedNotesOnly() {
        val packet = packet(
            question = "我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。",
            notes = listOf(
                note(
                    1L,
                    "人生是多线程运行",
                    "提升价值的同时赚钱，在利他的同时守住边界，在接纳自己的基础上持续改变。",
                ),
                note(
                    2L,
                    "社交边界与自我保护原则",
                    "不要为了取悦别人消耗自己的精力，要守住边界。",
                ),
                note(3L, "产品建议", "建议把输入框做得更顺手。"),
                note(4L, "OpenCL 依赖", "GPU 库和 CPU fallback 的工程问题。"),
            ),
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(packet.wantsBriefAnswer).isTrue()
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(2)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsExactly(
            "人生是多线程运行",
            "社交边界与自我保护原则",
        )
        assertThat(packet.skillResult).isNull()
    }

    @Test
    fun todayScopedLookupIncludesOnlyTodayRecords() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayStart = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = packet(
            question = "我只看今天的",
            notes = listOf(
                note(1L, "今天主题A", "今天第一条记录").copy(createdAt = todayStart + 1_000L),
                note(2L, "今天主题B", "今天第二条记录").copy(createdAt = todayStart + 2_000L),
                note(3L, "昨天主题", "昨天记录").copy(createdAt = yesterdayStart + 1_000L),
            ),
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(2)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsExactly("今天主题A", "今天主题B").inOrder()
        assertThat(ReviewChatPromptFactory.cloud(packet)).doesNotContain("昨天主题")
        assertThat(ReviewChatPromptFactory.onDevice(packet).userMessage).doesNotContain("昨天主题")
    }

    @Test
    fun thisWeekLookupExplainsActivityScopeAndIncludesUpdatedOldRecords() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val thisWeek = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        val oldCreated = today.minusDays(45).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        val oldUpdatedThisWeek = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        val lastWeek = today.minusDays(8).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L

        val packet = packet(
            question = "本周我记录了哪些内容？",
            notes = listOf(
                note(1L, "本周新记录", "本周刚写的想法").copy(createdAt = thisWeek, updatedAt = thisWeek),
                note(2L, "旧记录本周更新", "旧想法这周又补了一段").copy(
                    createdAt = oldCreated,
                    updatedAt = oldUpdatedThisWeek,
                ),
                note(3L, "上周记录", "不应该进入本周").copy(createdAt = lastWeek, updatedAt = lastWeek),
            ),
        )

        assertThat(packet.collectionOverview?.totalCount).isEqualTo(2)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsExactly(
            "本周新记录",
            "旧记录本周更新",
        ).inOrder()
        assertThat(packet.answerTrace?.displayLine).contains("本周")
        assertThat(packet.answerTrace?.displayLine).contains("命中 2 条")
        assertThat(packet.answerTrace?.displayLine).contains("包含更新记录")
        assertThat(packet.answerTrace?.emptyReason).isNull()
    }

    @Test
    fun recentUnadvancedIdeasExplainStatusFilterAndEmptyReason() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val recent = today.minusDays(3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        val stale = today.minusDays(45).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L

        val packet = packet(
            question = "最近一个月有哪些未推进的想法？",
            notes = listOf(
                note(1L, "未推进想法 A", "还停留在想法阶段").copy(
                    status = NoteStatus.IDEA,
                    createdAt = recent,
                    updatedAt = recent,
                ),
                note(2L, "已经推进的方案", "这条已经在做").copy(
                    status = NoteStatus.IN_PROGRESS,
                    createdAt = recent + 1_000L,
                    updatedAt = recent + 1_000L,
                ),
                note(3L, "很久以前的想法", "不在最近一个月").copy(
                    status = NoteStatus.IDEA,
                    createdAt = stale,
                    updatedAt = stale,
                ),
            ),
        )

        assertThat(packet.collectionOverview?.totalCount).isEqualTo(1)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsExactly("未推进想法 A")
        assertThat(packet.answerTrace?.displayLine).contains("最近30天")
        assertThat(packet.answerTrace?.displayLine).contains("状态：IDEA")
        assertThat(packet.answerTrace?.displayLine).contains("命中 1 条")

        val emptyPacket = packet(
            question = "最近一个月有哪些未推进的想法？",
            notes = listOf(
                note(4L, "近期已推进", "不是 IDEA").copy(
                    status = NoteStatus.IN_PROGRESS,
                    createdAt = recent,
                    updatedAt = recent,
                ),
            ),
        )

        assertThat(emptyPacket.collectionOverview?.totalCount).isEqualTo(0)
        assertThat(emptyPacket.answerTrace?.displayLine).contains("范围内 1 条")
        assertThat(emptyPacket.answerTrace?.emptyReason).isEqualTo("时间范围内有记录，但没有符合状态：IDEA的内容")
    }

    @Test
    fun realQuestionRegressionContextsKeepMaterialInsideExpectedScope() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val recent = today.minusDays(4).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        val old = today.minusDays(60).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        val notes = listOf(
            note(1L, "AI 工具关注", "最近在关注 AI 工具体验和云侧配置").copy(createdAt = recent, updatedAt = recent),
            note(2L, "想法一直没动", "这个想法一直没动，仍然只是一个 Spark").copy(
                status = NoteStatus.IDEA,
                createdAt = old,
                updatedAt = old,
            ),
            note(3L, "推进中的方案", "这个已经推进为方案").copy(
                status = NoteStatus.IN_PROGRESS,
                createdAt = old + 1_000L,
                updatedAt = old + 1_000L,
            ),
            note(4L, "最近两周的矛盾", "本周出现了端侧速度和云侧可信之间的矛盾").copy(
                createdAt = recent + 1_000L,
                updatedAt = recent + 1_000L,
            ),
        )

        val recentFocus = packet("我最近在关注什么？", notes)
        assertThat(recentFocus.answerTrace?.displayLine).contains("最近30天")
        assertThat(recentFocus.rawNoteEvidence.map { it.title }).contains("AI 工具关注")

        val stuckIdeas = packet("哪些想法一直没动？", notes)
        assertThat(stuckIdeas.answerTrace?.displayLine).contains("状态：IDEA")
        assertThat(stuckIdeas.rawNoteEvidence.map { it.title }).contains("想法一直没动")
        assertThat(stuckIdeas.rawNoteEvidence.map { it.title }).doesNotContain("推进中的方案")

        val contradictions = packet("最近两周的矛盾帮我串一下", notes)
        assertThat(contradictions.questionMode).isEqualTo(ReviewChatQuestionMode.ANALYSIS)
        assertThat(contradictions.answerTrace?.displayLine).contains("最近两周")
        assertThat(ReviewChatPromptFactory.cloud(contradictions)).contains("最近两周的矛盾")
        assertThat(ReviewChatPromptFactory.cloud(contradictions)).doesNotContain("这个想法一直没动")
    }

    @Test
    fun recordLinksAreReturnedOnlyWhenUserExplicitlyAsks() {
        val date = LocalDate.now(ZoneId.systemDefault()).withMonth(4).withDayOfMonth(10)
        val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val notes = listOf(note(42L, "4 月 10 号讨论", "完整原文").copy(createdAt = timestamp))

        val noLinkContext = corpusContext("把 4 月 10 号那天的完整内容发给我", notes)
        val linkContext = corpusContext("把 4 月 10 号那条记录的原始链接发给我", notes)

        assertThat(noLinkContext.rawNoteDetails).hasSize(1)
        assertThat(noLinkContext.referencedNotes).isEmpty()
        assertThat(linkContext.referencedNotes).hasSize(1)
        assertThat(linkContext.referencedNotes.single().noteId).isEqualTo(42L)
    }

    @Test
    fun externalQuestionDoesNotUseHistoryAsMaterial() {
        val packet = packet(
            question = "今天天气怎么样",
            notes = listOf(note(1L, "产品方向", "今天讨论了推荐链路")),
        )
        val prompt = ReviewChatPromptFactory.cloud(packet)

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.EXTERNAL)
        assertThat(packet.collectionOverview).isNull()
        assertThat(packet.skillResult).isNull()
        assertThat(packet.rawNoteEvidence).isEmpty()
        assertThat(prompt).contains("这是外部或通用问题")
        assertThat(prompt).doesNotContain("原始记录：")
        assertThat(prompt).doesNotContain("产品方向")
    }

    private fun packet(
        question: String,
        notes: List<NoteEntity>,
    ): ReviewChatContextPacket = buildReviewChatContextPacket(
        question = question,
        intent = ReviewChatIntent.RECALL,
        notes = notes,
        weeklyReview = WeeklyReviewState(lines = emptyList()),
        maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
        wikiSnapshot = DirectionWikiSnapshot(),
        sessionSummary = "",
    )

    private fun corpusContext(
        question: String,
        notes: List<NoteEntity>,
    ): ReviewChatCorpusContext = ReviewChatCorpusQueryEngine.build(
        query = ReviewChatQueryParser.parse(question),
        notes = notes,
    )

    private fun note(
        id: Long,
        topic: String,
        content: String,
    ): NoteEntity = NoteEntity(
        id = id,
        content = content,
        topic = topic,
        topicSource = TopicSource.MANUAL,
        folderKey = "work",
        folderSource = FolderSource.MANUAL,
        tags = emptyList(),
        tagSource = TagSource.MANUAL,
        status = NoteStatus.IDEA,
        horizon = NoteHorizon.MEDIUM,
        knowledgeTrust = KnowledgeTrust.NONE,
        isArchived = false,
        createdAt = 1_000L + id,
        updatedAt = 2_000L + id,
    )
}
