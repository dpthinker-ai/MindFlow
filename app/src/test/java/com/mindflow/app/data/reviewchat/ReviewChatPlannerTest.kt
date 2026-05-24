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
import com.mindflow.app.data.skills.SkillInvocation
import com.mindflow.app.data.skills.SkillResult
import com.mindflow.app.data.skills.SkillRuntime
import com.mindflow.app.data.skills.SkillWebViewSpec
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiFailureReason
import com.mindflow.app.data.wiki.DirectionWikiDirectionSummary
import com.mindflow.app.data.wiki.DirectionWikiSnapshot
import com.mindflow.app.data.wiki.KnowledgeLayerSearchItem
import com.mindflow.app.data.wiki.KnowledgeLayerSearchType
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReviewChatPlannerTest {
    @Test
    fun buildReviewChatQuestionProfile_routesQuestionsIntoClearModes() {
        assertThat(buildReviewChatQuestionProfile("我只看今天的").mode)
            .isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(buildReviewChatQuestionProfile("把 4 月 10 号那天的完整内容发给我").mode)
            .isEqualTo(ReviewChatQuestionMode.FULL_RECORD)
        assertThat(buildReviewChatQuestionProfile("我第一条记录是什么时候").mode)
            .isEqualTo(ReviewChatQuestionMode.TIMELINE_ANCHOR)
        assertThat(buildReviewChatQuestionProfile("我只看3月份的").mode)
            .isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(buildReviewChatQuestionProfile("我3月份一共记了多少条？").mode)
            .isEqualTo(ReviewChatQuestionMode.COLLECTION_OVERVIEW)
        assertThat(buildReviewChatQuestionProfile("我总共有多少条记录").mode)
            .isEqualTo(ReviewChatQuestionMode.COLLECTION_OVERVIEW)
        assertThat(buildReviewChatQuestionProfile("看一下本周末记录了哪些信息，都有哪些类别").mode)
            .isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(buildReviewChatQuestionProfile("看一下本周末记录了哪些信息，都有哪些类别").wantsCategories)
            .isTrue()
        assertThat(buildReviewChatQuestionProfile("帮我看一下所有的记录，帮我总结一下他们都是什么类型的").mode)
            .isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(buildReviewChatQuestionProfile("帮我看一下所有的记录，帮我总结一下他们都是什么类型的").wantsCategories)
            .isTrue()
        assertThat(buildReviewChatQuestionProfile("我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。").mode)
            .isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(buildReviewChatQuestionProfile("我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。").wantsBriefAnswer)
            .isTrue()
        assertThat(buildReviewChatQuestionProfile("把最近两周的矛盾串一下").mode)
            .isEqualTo(ReviewChatQuestionMode.ANALYSIS)
        assertThat(buildReviewChatQuestionProfile("今天天气怎么样").mode)
            .isEqualTo(ReviewChatQuestionMode.EXTERNAL)
        assertThat(buildReviewChatQuestionProfile("今天天气怎么样").isExternalQuestion).isTrue()
    }

    @Test
    fun reviewChatQueryParser_emitsStructuredOperationAndScope() {
        val countQuery = ReviewChatQueryParser.parse("人生态度一共有多少条记录")
        assertThat(countQuery.operation).isEqualTo(ReviewChatQueryOperation.COUNT)
        assertThat(countQuery.timeScope).isEqualTo(ReviewChatTimeScope.AllTime)
        assertThat(countQuery.entityTerms).containsExactly("人生态度")

        val lifeExperienceCategoryCountQuery = ReviewChatQueryParser.parse("人生经验我记录了多少条？都有哪些类别")
        assertThat(lifeExperienceCategoryCountQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(lifeExperienceCategoryCountQuery.wantsCount).isTrue()
        assertThat(lifeExperienceCategoryCountQuery.wantsCategories).isTrue()
        assertThat(lifeExperienceCategoryCountQuery.entityTerms).containsExactly("人生经验")

        val categoryQuery = ReviewChatQueryParser.parse("帮我总结分析一下，我这所有记录可以分为哪些类别？")
        assertThat(categoryQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(categoryQuery.wantsCategories).isTrue()
        assertThat(categoryQuery.entityTerms).isEmpty()

        val categoryQuery2 = ReviewChatQueryParser.parse("帮我分析总结下所有的记录，看看都有哪些类别。")
        assertThat(categoryQuery2.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(categoryQuery2.wantsCategories).isTrue()
        assertThat(categoryQuery2.entityTerms).isEmpty()

        val typeQuery = ReviewChatQueryParser.parse("帮我看一下所有的记录，帮我总结一下他们都是什么类型的")
        assertThat(typeQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(typeQuery.wantsCategories).isTrue()
        assertThat(typeQuery.entityTerms).isEmpty()

        val fullTextQuery = ReviewChatQueryParser.parse("把 4 月 10 号那天的完整内容发给我")
        assertThat(fullTextQuery.operation).isEqualTo(ReviewChatQueryOperation.FULL_TEXT)
        assertThat(fullTextQuery.timeScope).isEqualTo(
            ReviewChatTimeScope.Day(
                LocalDate.now(ZoneId.systemDefault()).withMonth(4).withDayOfMonth(10)
            )
        )
        assertThat(fullTextQuery.entityTerms).isEmpty()
        assertThat(fullTextQuery.wantsLinks).isFalse()

        val weekendQuery = ReviewChatQueryParser.parse("看一下本周末记录了哪些信息，都有哪些类别")
        assertThat(weekendQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(weekendQuery.timeScope).isInstanceOf(ReviewChatTimeScope.Range::class.java)
        assertThat((weekendQuery.timeScope as ReviewChatTimeScope.Range).label).isEqualTo("本周末")
        assertThat(weekendQuery.wantsCategories).isTrue()

        val thisWeekQuery = ReviewChatQueryParser.parse("本周记录了哪些信息")
        assertThat(thisWeekQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(thisWeekQuery.timeScope).isInstanceOf(ReviewChatTimeScope.Range::class.java)
        assertThat((thisWeekQuery.timeScope as ReviewChatTimeScope.Range).label).isEqualTo("本周")

        val briefSummaryQuery = ReviewChatQueryParser.parse("我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。")
        assertThat(briefSummaryQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(briefSummaryQuery.mode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(briefSummaryQuery.entityTerms).containsExactly("人生建议")
        assertThat(briefSummaryQuery.wantsBriefAnswer).isTrue()
    }

    @Test
    fun reviewChatQueryParser_modelPlanOverridesEntityFilteringForAllHistoryCategoryQuery() {
        val planned = ReviewChatQueryParser.parse(
            question = "帮我分析总结下所有的记录，看看都有哪些类别。",
            modelPlan = ReviewChatModelQueryPlan(
                operation = ReviewChatQueryOperation.LIST,
                entityTerms = emptyList(),
                wantsCategories = true,
            ),
        )

        assertThat(planned.mode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(planned.entityTerms).isEmpty()
        assertThat(planned.wantsCategories).isTrue()
        assertThat(planned.intent).isEqualTo(ReviewChatIntent.RECALL)
    }

    @Test
    fun reviewChatQueryParser_localHardSignalsWinOverCloudAnalyzePlan() {
        val analyzePlan = ReviewChatModelQueryPlan(
            operation = ReviewChatQueryOperation.ANALYZE,
            entityTerms = emptyList(),
            wantsCategories = false,
        )

        val categoryQuery = ReviewChatQueryParser.parse(
            question = "帮我分析所有的记录，都有哪些分类？",
            modelPlan = analyzePlan,
        )
        assertThat(categoryQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(categoryQuery.mode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(categoryQuery.wantsCategories).isTrue()
        assertThat(ReviewChatHistorySkill.shouldUseRuntime(categoryQuery)).isTrue()

        val countQuery = ReviewChatQueryParser.parse(
            question = "我总共有多少条记录？给我看一下覆盖情况",
            modelPlan = analyzePlan,
        )
        assertThat(countQuery.operation).isEqualTo(ReviewChatQueryOperation.COUNT)
        assertThat(countQuery.mode).isEqualTo(ReviewChatQuestionMode.COLLECTION_OVERVIEW)
        assertThat(countQuery.wantsCount).isTrue()
        assertThat(ReviewChatHistorySkill.shouldUseRuntime(countQuery)).isTrue()

        val scopedQuery = ReviewChatQueryParser.parse(
            question = "看一下今天记录了什么",
            modelPlan = analyzePlan,
        )
        assertThat(scopedQuery.operation).isEqualTo(ReviewChatQueryOperation.LIST)
        assertThat(scopedQuery.mode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(ReviewChatHistorySkill.shouldUseRuntime(scopedQuery)).isTrue()
    }

    @Test
    fun buildReviewChatContextPacket_briefConceptSummaryFallsBackToSemanticMatches() {
        val packet = buildReviewChatContextPacket(
            question = "我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(
                    1L,
                    "人生是多线程运行",
                    "人生不是排队通关，而是多线程运行：提升价值的同时赚钱，在利他的同时守住边界，在接纳自己的基础上持续改变。",
                ),
                sampleNote(
                    2L,
                    "社交边界与自我保护原则",
                    "利他要有边界，不要为了取悦别人损耗自己的精力。",
                ),
                sampleNote(
                    3L,
                    "前端输入框问题",
                    "输入法回车和发送按钮需要拆开处理。",
                ),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(packet.wantsBriefAnswer).isTrue()
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(2)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsAtLeast(
            "人生是多线程运行",
            "社交边界与自我保护原则",
        )
        assertThat(packet.rawNoteEvidence.map { it.title }).doesNotContain("前端输入框问题")
        assertThat(packet.skillResult).isNull()
    }

    @Test
    fun buildReviewChatContextPacket_lifeExperienceCategoryCountUsesSemanticMatches() {
        val packet = buildReviewChatContextPacket(
            question = "人生经验我记录了多少条？都有哪些类别",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(
                    1L,
                    "人生是多线程运行",
                    "人生不是排队通关，而是多线程运行：提升价值的同时赚钱，在利他的同时守住边界。",
                ),
                sampleNote(
                    2L,
                    "社交边界与自我保护原则",
                    "利他要有边界，不要为了取悦别人损耗自己的精力。",
                ),
                sampleNote(
                    3L,
                    "低成本高效率的生存法则",
                    "保持独立性，摆脱被动思维，用更低成本换取更高效率。",
                ),
                sampleNote(
                    4L,
                    "前端输入框问题",
                    "输入法回车和发送按钮需要拆开处理。",
                ),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(packet.wantsCount).isTrue()
        assertThat(packet.wantsCategories).isTrue()
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(3)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsAtLeast(
            "人生是多线程运行",
            "社交边界与自我保护原则",
            "低成本高效率的生存法则",
        )
        assertThat(packet.rawNoteEvidence.map { it.title }).doesNotContain("前端输入框问题")
        assertThat(packet.categoryDigestSnippets).isNotEmpty()
        assertThat(packet.deterministicAnswerSnippets).contains("直接答案｜关于人生经验的记录共 3 条")
        assertThat(packet.deterministicAnswerSnippets).contains("分类范围｜当前分类必须覆盖 3 条命中记录")
        assertThat(packet.skillResult).isNull()
    }

    @Test
    fun reviewChatRetriever_prioritizesTitleAndTagsOverBodyOnly() {
        val query = ReviewChatQueryParser.parse("抖音有哪些记录")
        val ranked = ReviewChatRetriever.rank(
            query = query,
            notes = listOf(
                sampleNote(1L, "抖音文案记录", "主要是脚本和选题"),
                sampleNote(2L, "增长主题", "内容里提到抖音，但不是主主题").copy(tags = listOf("抖音")),
                sampleNote(3L, "别的主题", "正文里顺带出现一次抖音"),
            ),
        )

        assertThat(ranked.take(3).map { it.note.id }).containsExactly(1L, 2L, 3L).inOrder()
    }

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
    fun buildReviewChatContextPacket_usesRawInputsAndKeepsKnowledgeLayerOutOfChat() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val recentStart = today.minusDays(11).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = buildReviewChatContextPacket(
            question = "把最近两周的矛盾串一下",
            intent = ReviewChatIntent.SYNTHESIZE,
            notes = List(12) { index ->
                val timestamp = recentStart + index * 86_400_000L
                sampleNote(index.toLong() + 1L, "主题$index", "正文$index")
                    .copy(createdAt = timestamp, updatedAt = timestamp)
            },
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

        assertThat(packet.rawNoteEvidence).hasSize(4)
        assertThat(packet.rawNoteEvidence.first().title).contains("主题")
        assertThat(packet.knowledgeBaseSnippets).isEmpty()
        assertThat(packet.wikiSnippets).isEmpty()
        assertThat(packet.structuredSnippets).isEmpty()
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

        assertThat(packet.rawNoteEvidence).hasSize(6)
        assertThat(packet.historyAnchors).hasSize(2)
        assertThat(packet.historyAnchors.first().label).isEqualTo("最早记录")
        assertThat(packet.historyAnchors.first().item.dateLabel).isEqualTo("1970-01-01")
        assertThat(packet.historyAnchors.first().item.title).isEqualTo("产品方向0")
        assertThat(packet.wikiSnippets).isEmpty()
        assertThat(packet.knowledgeBaseSnippets).isEmpty()
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

        assertThat(packet.rawNoteEvidence.map { it.title }).contains("最早主题")
        assertThat(packet.rawNoteEvidence.map { it.title }).contains("最近主题B")
        assertThat(packet.historyAnchors.first().item.title).isEqualTo("最早主题")
    }

    @Test
    fun buildReviewChatContextPacket_recentQuestionUsesRecentlyUpdatedNotes() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val oldCreatedAt = today.minusDays(60).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recentUpdatedAt = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val staleUpdatedAt = today.minusDays(45).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = buildReviewChatContextPacket(
            question = "最近我记录了什么",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "旧创建但最近更新", "这条是最近补充的新信息")
                    .copy(createdAt = oldCreatedAt, updatedAt = recentUpdatedAt),
                sampleNote(2L, "很久没动的旧记录", "这条不应该被最近问题统计")
                    .copy(createdAt = oldCreatedAt, updatedAt = staleUpdatedAt),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.collectionOverview?.scopeLabel).isEqualTo("最近30天")
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(1)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsExactly("旧创建但最近更新")
        assertThat(ReviewChatPromptFactory.cloud(packet)).contains("命中｜共 1 条记录")
        assertThat(ReviewChatPromptFactory.cloud(packet)).doesNotContain("很久没动的旧记录")
    }

    @Test
    fun buildReviewChatContextPacket_thisWeekQuestionUsesCurrentWeekActivity() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val thisWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val oldCreatedAt = thisWeekStart.minusDays(45).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val updatedThisWeek = thisWeekStart.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val updatedBeforeThisWeek = thisWeekStart.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = buildReviewChatContextPacket(
            question = "本周记录了哪些信息",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "旧创建但本周更新", "这条是本周补充的新信息")
                    .copy(createdAt = oldCreatedAt, updatedAt = updatedThisWeek),
                sampleNote(2L, "上周活动记录", "这条不属于本周")
                    .copy(createdAt = oldCreatedAt, updatedAt = updatedBeforeThisWeek),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.collectionOverview?.scopeLabel).isEqualTo("本周")
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(1)
        assertThat(packet.rawNoteEvidence.map { it.title }).containsExactly("旧创建但本周更新")
        assertThat(ReviewChatPromptFactory.cloud(packet)).contains("范围｜本周")
        assertThat(ReviewChatPromptFactory.cloud(packet)).doesNotContain("上周活动记录")
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

        assertThat(packet.rawNoteEvidence.map { it.title }).contains("抖音文案记录")
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
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(
                        id = 42L,
                        topic = "4 月 10 号讨论",
                        content = "这是 4 月 10 号的完整原文，里面详细讨论了 leakspace 和推荐链路。",
                    ).copy(createdAt = april10Timestamp, updatedAt = april10Timestamp + 123_000L)
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompts += prompt
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
        assertThat(result.referencedNoteId).isNull()
        assertThat(result.referencedNotes).isEmpty()
        assertThat(capturedPrompts.first()).contains("完整记录：")
        assertThat(capturedPrompts.first()).contains("这是 4 月 10 号的完整原文")
    }

    @Test
    fun answer_fullRecordQuestion_usesLocalContextWithoutHistorySkillRuntime() = runTest {
        val april10Timestamp = LocalDate.now(ZoneId.systemDefault())
            .withMonth(4)
            .withDayOfMonth(10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val invocations = mutableListOf<SkillInvocation>()
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(
                        id = 42L,
                        topic = "4 月 10 号讨论",
                        content = "旧路径里的完整原文",
                    ).copy(createdAt = april10Timestamp, updatedAt = april10Timestamp + 123_000L)
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            listAvailableSkillSnippets = {
                listOf("- history-query：Query and visualize MindFlow historical notes.（输出：text/webview）")
            },
            skillRuntime = object : SkillRuntime {
                override suspend fun execute(invocation: SkillInvocation): SkillResult {
                    invocations += invocation
                    return SkillResult(
                        result = "命中 1 条记录，已处理 1 条。",
                        webview = SkillWebViewSpec(
                            url = "file:///android_asset/skills/history-query/assets/result-card.html?matched=1",
                            iframe = false,
                            aspectRatio = 1.333f,
                        ),
                        dataJson = """
                            {
                              "coverage": {
                                "totalCount": 1,
                                "matchedCount": 1,
                                "processedCount": 1,
                                "complete": true,
                                "dateRange": {
                                  "start": "2026-04-10",
                                  "end": "2026-04-10"
                                }
                              },
                              "records": [
                                {
                                  "id": "42",
                                  "date": "2026-04-10",
                                  "title": "4 月 10 号讨论",
                                  "summary": "skill runtime 摘要",
                                  "content": "skill runtime 返回的完整原文"
                                }
                              ]
                            }
                        """.trimIndent(),
                    )
                }
            },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("基于 runtime 原始记录整理后的回答。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把 4 月 10 号那条完整记录发给我",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(invocations).isEmpty()
        assertThat(result.skillWebView).isNull()
        assertThat(capturedPrompts.single()).contains("旧路径里的完整原文")
        assertThat(capturedPrompts.single()).doesNotContain("skill runtime 返回的完整原文")
    }

    @Test
    fun answer_outOfScopeQuestion_stillRoutesToModel() = runTest {
        var cloudCalled = false
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "产品方向", "这条记录只在讨论推荐链路和增长")) },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                cloudCalled = true
                capturedPrompts += prompt
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
        assertThat(result.referencedNotes).isEmpty()
        assertThat(capturedPrompts.first()).contains("这是外部或通用问题")
        assertThat(capturedPrompts.first()).doesNotContain("材料不足")
        assertThat(capturedPrompts.first()).doesNotContain("原始记录：")
    }

    @Test
    fun answer_lowIntentProbeAsksForClarificationWithoutCallingModels() = runTest {
        var cloudCalled = false
        var onDeviceCalled = false
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "产品方向", "最近一直在讨论推荐链路和增长")) },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = {
                cloudCalled = true
                AiChatResult.Success("不应该调用云侧")
            },
            runOnDevice = {
                onDeviceCalled = true
                AiChatResult.Success("不应该调用端侧")
            },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "test",
                priorMessages = emptyList(),
            ),
        )

        assertThat(cloudCalled).isFalse()
        assertThat(onDeviceCalled).isFalse()
        assertThat(result.provider).isEqualTo(ReviewChatProvider.SYSTEM)
        assertThat(result.answer).contains("我还没看出你想查什么")
    }

    @Test
    fun answer_shortAsciiWithDirectHistoryMatchStillRoutesToModel() = runTest {
        var cloudCalled = false
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "OpenCL 接入", "GPU 推理和 OpenCL 库能力")) },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            runCloud = {
                cloudCalled = true
                AiChatResult.Success("你有一条 OpenCL 相关记录。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "OpenCL",
                priorMessages = emptyList(),
            ),
        )

        assertThat(cloudCalled).isTrue()
        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.answer).contains("OpenCL")
    }

    @Test
    fun answer_shortAsciiBodyOnlyMatchStillAsksForClarification() = runTest {
        var cloudCalled = false
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "产品方向", "正文里偶然出现 adc 字样")) },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            runCloud = {
                cloudCalled = true
                AiChatResult.Success("不应该调用云侧")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "adc",
                priorMessages = emptyList(),
            ),
        )

        assertThat(cloudCalled).isFalse()
        assertThat(result.provider).isEqualTo(ReviewChatProvider.SYSTEM)
        assertThat(result.answer).contains("我还没看出你想查什么")
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
    fun answer_categoryQuestionDoesNotCallCloudPlannerBeforeAnswer() = runTest {
        var plannerCalls = 0
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "产品设计", "启动页和图标方案"),
                    sampleNote(2L, "精神健康", "减少消费和增加阅读"),
                    sampleNote(3L, "AI实验", "上下文带宽和推理体验"),
                    sampleNote(4L, "训练计划", "跑步和力量循环"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            planQueryWithCloud = {
                plannerCalls += 1
                AiChatResult.Success(
                    """{"operation":"list","entity_terms":["不存在的云侧主题"],"wants_categories":true,"wants_examples":false,"wants_links":false}"""
                )
            },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("可以分成四类。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "帮我分析总结下所有的记录，看看都有哪些类别。",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(plannerCalls).isEqualTo(0)
        assertThat(capturedPrompts.first()).contains("命中｜共 4 条记录")
        assertThat(capturedPrompts.first()).contains("任务｜归纳命中记录的主要类别，不要把时间范围或统计口径当成类别")
        assertThat(capturedPrompts.first()).doesNotContain("主题｜")
    }

    @Test
    fun answer_countQuestion_usesLocalContextWithoutHistorySkillRuntime() = runTest {
        val invocations = mutableListOf<SkillInvocation>()
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "人生建议", "人生是多线程运行"),
                    sampleNote(2L, "技术实现", "MindFlow 接入 OpenCL"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            listAvailableSkillSnippets = {
                listOf("- history-query：Query and visualize MindFlow historical notes.（输出：text/webview）")
            },
            skillRuntime = object : SkillRuntime {
                override suspend fun execute(invocation: SkillInvocation): SkillResult {
                    invocations += invocation
                    return SkillResult(
                        result = "命中 2 条记录，已处理 2 条。",
                        dataJson = """
                            {
                              "coverage": {
                                "totalCount": 2,
                                "matchedCount": 2,
                                "processedCount": 2,
                                "complete": true,
                                "dateRange": {
                                  "start": "1970-01-01",
                                  "end": "1970-01-01"
                                }
                              },
                              "records": [
                                {
                                  "id": "1",
                                  "date": "1970-01-01",
                                  "title": "人生建议",
                                  "summary": "人生是多线程运行"
                                }
                              ]
                            }
                        """.trimIndent(),
                    )
                }
            },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("共 2 条记录。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我总共有多少条记录",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(invocations).isEmpty()
        assertThat(result.skillWebView).isNull()
        assertThat(capturedPrompts.single()).doesNotContain("可用技能：")
        assertThat(capturedPrompts.single()).doesNotContain("SkillResult：")
        assertThat(capturedPrompts.single()).contains("直接答案｜全部历史的记录共 2 条")
    }

    @Test
    fun answer_countQuestion_doesNotExecuteHistorySkillRuntimeOrExposeSkillPrompt() = runTest {
        val invocations = mutableListOf<SkillInvocation>()
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "人生建议", "人生是多线程运行"),
                    sampleNote(2L, "技术实现", "MindFlow 接入 OpenCL"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            listAvailableSkillSnippets = {
                listOf("- history-query：Query and visualize MindFlow historical notes.（输出：text/webview）")
            },
            skillRuntime = object : SkillRuntime {
                override suspend fun execute(invocation: SkillInvocation): SkillResult {
                    invocations += invocation
                    return SkillResult.failure("回看聊天不应该再执行 history-query Skill")
                }
            },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("共 2 条记录。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我总共有多少条记录",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(invocations).isEmpty()
        assertThat(result.skillWebView).isNull()
        assertThat(capturedPrompts.single()).doesNotContain("可用技能：")
        assertThat(capturedPrompts.single()).doesNotContain("SkillResult：")
    }

    @Test
    fun answer_categoryLookupDoesNotExecuteHistorySkillRuntime() = runTest {
        val invocations = mutableListOf<SkillInvocation>()
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "MindFlow 卡片", "聊天界面增加 Skill 可视化卡片"),
                    sampleNote(2L, "人生建议", "人生是多线程运行"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            planQueryWithCloud = {
                AiChatResult.Success(
                    """{"operation":"analyze","entity_terms":[],"wants_categories":false,"wants_examples":false,"wants_links":false}"""
                )
            },
            skillRuntime = object : SkillRuntime {
                override suspend fun execute(invocation: SkillInvocation): SkillResult {
                    invocations += invocation
                    return SkillResult(
                        result = "命中 2 条记录，已处理 2 条。",
                        webview = SkillWebViewSpec(
                            url = "file:///android_asset/skills/history-query/assets/insight-card.html?payload=test",
                            iframe = false,
                            aspectRatio = 0.78f,
                        ),
                        dataJson = """
                            {
                              "coverage": {
                                "totalCount": 2,
                                "matchedCount": 2,
                                "processedCount": 2,
                                "complete": true
                              },
                              "records": []
                            }
                        """.trimIndent(),
                    )
                }
            },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("分类结果")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "帮我分析所有的记录，都有哪些分类？",
                priorMessages = emptyList(),
            ),
        )

        assertThat(invocations).isEmpty()
        assertThat(result.skillWebView).isNull()
        assertThat(capturedPrompts.first()).contains("分类范围｜当前分类必须覆盖 2 条命中记录")
        assertThat(capturedPrompts.first()).doesNotContain("SkillResult：")
    }

    @Test
    fun historySkillRuntimeInvocationPinsPlannerMatchedNoteIdsForSemanticEntityQueries() {
        val query = ReviewChatQueryParser.parse("人生经验我记录了多少条？都有哪些类别")
        val corpusContext = ReviewChatCorpusQueryEngine.build(
            query = query,
            notes = listOf(
                sampleNote(1L, "人生是多线程运行", "提升价值的同时赚钱，在利他的同时守住边界。"),
                sampleNote(2L, "社交边界与自我保护原则", "利他要有边界，不要损耗自己的精力。"),
                sampleNote(3L, "前端输入框问题", "输入法回车和发送按钮需要拆开处理。"),
            ),
        )

        val invocation = ReviewChatHistorySkill.buildRuntimeInvocation(query, corpusContext)

        assertThat(corpusContext.selection.queryNotes.map { it.id }).containsExactly(1L, 2L)
        assertThat(invocation?.data).contains("\"intent\":\"categories\"")
        assertThat(invocation?.data).contains("\"entityTerms\":[\"人生经验\"]")
        assertThat(invocation?.data).contains("\"noteIds\":[\"1\", \"2\"]")
    }

    @Test
    fun answer_categoryLookupKeepsLocalContextWhenRuntimeFails() = runTest {
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "MindFlow 卡片", "聊天界面增加 Skill 可视化卡片"),
                    sampleNote(2L, "人生建议", "人生是多线程运行"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            skillRuntime = object : SkillRuntime {
                override suspend fun execute(invocation: SkillInvocation): SkillResult =
                    SkillResult.failure("WebView JS skill failed")
            },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("分类结果")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "帮我分析所有的记录，都有哪些分类？",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.skillWebView).isNull()
        assertThat(capturedPrompts.first()).contains("分类范围｜当前分类必须覆盖 2 条命中记录")
        assertThat(capturedPrompts.first()).doesNotContain("SkillResult：")
    }

    @Test
    fun answer_countQuestionDoesNotAttachNativeSkillCardWithoutRuntime() = runTest {
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "人生建议", "人生是多线程运行"),
                    sampleNote(2L, "技术实现", "MindFlow 接入 OpenCL"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            skillRuntime = null,
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("共 2 条记录。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我总共有多少条记录",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.skillWebView).isNull()
        assertThat(capturedPrompts.single()).contains("直接答案｜全部历史的记录共 2 条")
        assertThat(capturedPrompts.single()).doesNotContain("SkillResult：")
    }

    @Test
    fun answer_analysisQuestion_doesNotExecuteHistorySkillRuntime() = runTest {
        val invocations = mutableListOf<SkillInvocation>()
        val planner = ReviewChatPlanner(
            loadNotes = { listOf(sampleNote(1L, "产品方向", "最近一直在讨论推荐链路和增长")) },
            loadWeeklyReview = { WeeklyReviewState(lines = listOf("最近主要在推进推荐链路验证")) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            skillRuntime = object : SkillRuntime {
                override suspend fun execute(invocation: SkillInvocation): SkillResult {
                    invocations += invocation
                    return SkillResult.failure("分析问题不应该走 history skill runtime")
                }
            },
            runCloud = { AiChatResult.Success("最近主要在推进推荐链路验证。") },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把最近两周的矛盾串一下",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(invocations).isEmpty()
    }

    @Test
    fun answer_usesCloudStructuringFallbackWhenPrimaryAnswerIsNotStructured() = runTest {
        var cloudCalls = 0
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "产品设计", "启动页和图标方案"),
                    sampleNote(2L, "技术实现", "OCR 和 OpenCL"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            runCloud = { prompt ->
                cloudCalls += 1
                when (cloudCalls) {
                    1 -> AiChatResult.Success("本周末主要记录了两类信息：产品设计和技术实现。")
                    2 -> {
                        assertThat(prompt).contains("把下面这段回答整理成一个 JSON 对象")
                        AiChatResult.Success(
                            """
                            {
                              "summary": "本周末主要记录了两类信息。",
                              "sections": [
                                {
                                  "title": "类别",
                                  "items": [
                                    "产品设计：启动页和图标方案",
                                    "技术实现：OCR 和 OpenCL"
                                  ]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    else -> error("unexpected cloud call")
                }
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "看一下本周末记录了哪些信息，都有哪些类别",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.structuredAnswer).isNotNull()
        assertThat(result.structuredAnswer!!.sections.map { it.title }).contains("类别")
        assertThat(result.structuredAnswer!!.sections.last().items).containsExactly(
            "产品设计：启动页和图标方案",
            "技术实现：OCR 和 OpenCL",
        ).inOrder()
    }

    @Test
    fun answer_recordLookupUsesDeterministicStructureWithoutExtraCloudCall() = runTest {
        var cloudCalls = 0
        val today = LocalDate.now(ZoneId.systemDefault())
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "今天主题A", "今天第一条记录").copy(
                        createdAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
                    ),
                    sampleNote(2L, "今天主题B", "今天第二条记录").copy(
                        createdAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2_000L
                    ),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            runCloud = {
                cloudCalls += 1
                AiChatResult.Success("今天共有 2 条记录。")
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我只看今天的",
                priorMessages = emptyList(),
            ),
        )

        assertThat(cloudCalls).isEqualTo(1)
        assertThat(result.structuredAnswer).isNotNull()
        assertThat(result.structuredAnswer!!.sections.map { it.title }).containsExactly("答复", "记录").inOrder()
        val todayLabel = today.format(reviewChatDateFormatter)
        assertThat(result.structuredAnswer!!.sections[1].items).containsExactly(
            "$todayLabel《今天主题A》：今天第一条记录",
            "$todayLabel《今天主题B》：今天第二条记录",
        ).inOrder()
    }

    @Test
    fun answer_todayScopedQuestion_routesToModelWithTodayRecordsInContext() = runTest {
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayStart = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val capturedPrompts = mutableListOf<String>()

        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "今天主题A", "今天第一条记录").copy(createdAt = todayStart + 1_000L, updatedAt = todayStart + 11_000L),
                    sampleNote(2L, "今天主题B", "今天第二条记录").copy(createdAt = todayStart + 2_000L, updatedAt = todayStart + 12_000L),
                    sampleNote(3L, "昨天主题", "昨天的记录").copy(createdAt = yesterdayStart + 3_000L, updatedAt = todayStart + 13_000L),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompts += prompt
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
        assertThat(result.referencedNotes).isEmpty()
        assertThat(capturedPrompts.first()).contains("原始记录：")
        assertThat(capturedPrompts.first()).contains("今天主题A")
        assertThat(capturedPrompts.first()).contains("摘要：今天第一条记录")
        assertThat(capturedPrompts.first()).doesNotContain("记录｜")
        assertThat(capturedPrompts.first()).contains("今天主题A")
        assertThat(capturedPrompts.first()).contains("今天主题B")
        assertThat(capturedPrompts.first()).doesNotContain("完整记录：")
        assertThat(capturedPrompts.first()).doesNotContain("昨天主题")
        assertThat(capturedPrompts.first()).doesNotContain("LM Knowledge Base：")
        assertThat(capturedPrompts.first()).doesNotContain("LLM Wiki：")
    }

    @Test
    fun answer_dayFullRecordQuestion_routesToModelWithAllMatchedRecords() = runTest {
        val april10Start = LocalDate.now(ZoneId.systemDefault())
            .withMonth(4)
            .withDayOfMonth(10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(
                        id = 42L,
                        topic = "4 月 10 号讨论A",
                        content = "这是 4 月 10 号的第一条完整原文。",
                    ).copy(createdAt = april10Start, updatedAt = april10Start + 400_000L),
                    sampleNote(
                        id = 43L,
                        topic = "4 月 10 号讨论B",
                        content = "这是 4 月 10 号的第二条完整原文。",
                    ).copy(createdAt = april10Start + 100_000L, updatedAt = april10Start + 500_000L),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.AUTOMATIC },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompts += prompt
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
        assertThat(capturedPrompts.first()).contains("完整记录：")
        assertThat(capturedPrompts.first()).contains("4 月 10 号讨论A")
        assertThat(capturedPrompts.first()).contains("4 月 10 号讨论B")
        assertThat(capturedPrompts.first()).contains("第一条完整原文")
        assertThat(capturedPrompts.first()).contains("第二条完整原文")
        assertThat(result.referencedNoteId).isNull()
    }

    @Test
    fun answer_firstRecordQuestion_includesHistoryAnchorsAndUsesCreatedTimeline() = runTest {
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "最早记录", "这是最开始的一条记录").copy(createdAt = 10_000L, updatedAt = 999_000L),
                    sampleNote(2L, "较新的记录", "这是后来的一条记录").copy(createdAt = 20_000L, updatedAt = 50_000L),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("你的第一条记录是在 1970-01-01。")
            },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我第一条记录是什么时候",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(capturedPrompts.first()).contains("历史锚点：")
        assertThat(capturedPrompts.first()).contains("- 最早记录：1970-01-01《最早记录》")
        assertThat(capturedPrompts.first()).doesNotContain("记录｜")
        assertThat(result.referencedNotes).isEmpty()
    }

    @Test
    fun answer_linkQuestion_returnsReferencedNotesOnlyWhenExplicitlyRequested() = runTest {
        val april10Timestamp = LocalDate.now(ZoneId.systemDefault())
            .withMonth(4)
            .withDayOfMonth(10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(42L, "4 月 10 号讨论", "完整原文").copy(createdAt = april10Timestamp, updatedAt = april10Timestamp + 1_000L),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { AiChatResult.Success("这里是内容，同时给你原始链接。") },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把 4 月 10 号那条记录的原始链接发给我",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.referencedNotes).hasSize(1)
        assertThat(result.referencedNotes.single().noteId).isEqualTo(42L)
    }

    @Test
    fun answer_linkQuestionNormalizesModelUrlConfusionToAppRecordReferences() = runTest {
        val april10Timestamp = LocalDate.now(ZoneId.systemDefault())
            .withMonth(4)
            .withDayOfMonth(10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(42L, "4 月 10 号讨论", "完整原文").copy(
                        createdAt = april10Timestamp,
                        updatedAt = april10Timestamp + 1_000L,
                    ),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            runCloud = { AiChatResult.Success("2026-04-10 共1 条记录，但材料中未提供原始链接。") },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "把 4 月 10 号那条记录的原始链接发给我",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.answer).contains("已找到 1 条可打开的原始记录")
        assertThat(result.answer).contains("${LocalDate.now(ZoneId.systemDefault()).year}-04-10｜4 月 10 号讨论")
        assertThat(result.answer).doesNotContain("未提供原始链接")
        assertThat(result.referencedNotes).hasSize(1)
        assertThat(result.referencedNotes.single().noteId).isEqualTo(42L)
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
        assertThat(prompt.userMessage).contains("原始记录：")
        assertThat(prompt.userMessage).doesNotContain("LM Knowledge Base：")
        assertThat(prompt.userMessage).doesNotContain("LLM Wiki：")
        assertThat(prompt.userMessage.length).isGreaterThan(300)
        assertThat(prompt.userMessage.length).isAtMost(1_800)
        assertThat(prompt.systemInstruction).contains("把总答复写进 summary；默认不要创建 `依据` 或 `下一步` section")
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
        assertThat(prompt.userMessage).contains("原始记录：")
        assertThat(prompt.userMessage).doesNotContain("LM Knowledge Base：")
        assertThat(prompt.userMessage).doesNotContain("LLM Wiki：")
        assertThat(prompt.userMessage.length).isGreaterThan(300)
        assertThat(prompt.userMessage.length).isAtMost(1_800)
    }

    @Test
    fun onDevicePrompt_recordLookupDoesNotInjectAnalysisSections() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val packet = buildReviewChatContextPacket(
            question = "我只看今天的",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "今天主题", "今天内容").copy(
                    createdAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            ),
            weeklyReview = WeeklyReviewState(lines = listOf("不该出现")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                knowledgeItems = listOf(
                    KnowledgeLayerSearchItem(
                        id = "k1",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "不该出现",
                        summary = "不该出现",
                    )
                )
            ),
            sessionSummary = "上一轮也不该出现",
            priorMessages = listOf(
                ReviewChatMessage(
                    role = ReviewChatMessageRole.USER,
                    content = "上一轮问题",
                    createdAt = 1L,
                )
            ),
        )

        val prompt = ReviewChatPromptFactory.onDevice(packet)

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(prompt.systemInstruction).contains("只列命中的记录")
        assertThat(prompt.userMessage).doesNotContain("LM Knowledge Base：")
        assertThat(prompt.userMessage).doesNotContain("LLM Wiki：")
        assertThat(prompt.userMessage).doesNotContain("最近问题：")
        assertThat(prompt.userMessage).doesNotContain("完整记录：")
        assertThat(prompt.userMessage).contains("原始记录：")
    }

    @Test
    fun onDevicePrompt_categoryLookupUsesCategoryProtocolWithoutAnalysisSections() {
        val weekendRange = requestedDateRangeForReviewChat("看一下本周末记录了哪些信息，都有哪些类别")
            ?: error("weekend range missing")
        val packet = buildReviewChatContextPacket(
            question = "看一下本周末记录了哪些信息，都有哪些类别",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "周末记录", "周末内容").copy(
                    createdAt = weekendRange.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            ),
            weeklyReview = WeeklyReviewState(lines = listOf("不该出现")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "上一轮也不该出现",
        )

        val prompt = ReviewChatPromptFactory.onDevice(packet)

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(packet.wantsCategories).isTrue()
        assertThat(prompt.systemInstruction).contains("把类别写进 `类别` section 的 items")
        assertThat(prompt.systemInstruction).contains("类别名称：包含的信息")
        assertThat(prompt.systemInstruction).contains("不要把“时间范围”“统计信息”“历史记录”“查询结果”“集合概览”“确定结果”当成类别")
        assertThat(prompt.systemInstruction).contains("不要创建额外 section")
        assertThat(prompt.userMessage).doesNotContain("LM Knowledge Base：")
        assertThat(prompt.userMessage).doesNotContain("LLM Wiki：")
    }

    @Test
    fun answer_briefTopicSummary_dropsEvidenceAndNextStepSections() = runTest {
        var cloudCalls = 0
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "人生是多线程运行", "人生不是排队通关，而是多线程运行。"),
                    sampleNote(2L, "守住边界", "利他的同时也要守住边界。"),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = listOf("不该出现")) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            runCloud = {
                cloudCalls += 1
                AiChatResult.Success(
                    """
                    人生建议的核心在于将人生视为多线程运行，即在提升价值的同时赚钱，在利他的同时守住边界，在接纳自己的基础上持续改变。

                    依据：
                    - 2026-04-05《人生是多线程运行》：人生不是排队通关，而是多线程运行。

                    下一步：
                    - 根据LM Knowledge Base和LLM Wiki的建议，继续往系统性思考推进。
                    """.trimIndent()
                )
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。",
                priorMessages = emptyList(),
            ),
        )

        assertThat(cloudCalls).isEqualTo(1)
        assertThat(result.structuredAnswer).isNotNull()
        assertThat(result.structuredAnswer!!.sections.map { it.title }).containsExactly("答复").inOrder()
        val markdown = renderReviewChatStructuredAnswerAsMarkdown(result.structuredAnswer!!)
        assertThat(markdown).contains("人生建议的核心在于将人生视为多线程运行")
        assertThat(markdown).doesNotContain("依据：")
        assertThat(markdown).doesNotContain("下一步：")
    }

    @Test
    fun buildReviewChatContextPacket_weekendScopeFiltersNotesToWeekend() {
        val weekendRange = requestedDateRangeForReviewChat("看一下本周末记录了哪些信息，都有哪些类别")
            ?: error("weekend range missing")
        val packet = buildReviewChatContextPacket(
            question = "看一下本周末记录了哪些信息，都有哪些类别",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "周末记录一", "周末内容一").copy(
                    createdAt = weekendRange.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                ),
                sampleNote(2L, "周末记录二", "周末内容二").copy(
                    createdAt = weekendRange.endInclusive.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                ),
                sampleNote(3L, "非周末记录", "不该命中").copy(
                    createdAt = weekendRange.start.minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                ),
            ),
            weeklyReview = WeeklyReviewState(),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.querySummarySnippets).contains("范围｜本周末")
        assertThat(packet.rawNoteEvidence.map { it.noteId }).containsExactly(1L, 2L)
    }

    @Test
    fun buildReviewChatContextPacket_externalQuestion_skipsHistoryMaterials() {
        val packet = buildReviewChatContextPacket(
            question = "今天天气怎么样",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(sampleNote(1L, "产品方向", "今天讨论了推荐链路")),
            weeklyReview = WeeklyReviewState(lines = listOf("不该出现")),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(
                currentJudgement = LocalMaintainerCard(line = "当前判断", support = "判断依据"),
            ),
            wikiSnapshot = DirectionWikiSnapshot(
                knowledgeItems = listOf(
                    KnowledgeLayerSearchItem(
                        id = "k1",
                        type = KnowledgeLayerSearchType.CONCLUSION,
                        title = "不该出现",
                        summary = "不该出现",
                    )
                )
            ),
            sessionSummary = "不该出现",
            priorMessages = listOf(
                ReviewChatMessage(
                    role = ReviewChatMessageRole.USER,
                    content = "上一轮问题",
                    createdAt = 1L,
                )
            ),
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.EXTERNAL)
        assertThat(packet.rawNoteEvidence).isEmpty()
        assertThat(packet.knowledgeBaseSnippets).isEmpty()
        assertThat(packet.wikiSnippets).isEmpty()
        assertThat(packet.conversationSnippets).isEmpty()
    }

    @Test
    fun buildReviewChatContextPacket_collectionOverview_includesGlobalStats() {
        val packet = buildReviewChatContextPacket(
            question = "我总共有多少条记录",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "最早主题", "最早内容").copy(createdAt = 1_000L, updatedAt = 2_000L),
                sampleNote(2L, "中间主题", "中间内容").copy(createdAt = 2_000L, updatedAt = 3_000L),
                sampleNote(3L, "最近主题", "最近内容").copy(createdAt = 3_000L, updatedAt = 4_000L),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.COLLECTION_OVERVIEW)
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(3)
        assertThat(packet.collectionOverview?.earliestDateLabel).isEqualTo("1970-01-01")
        assertThat(packet.collectionOverview?.latestDateLabel).isEqualTo("1970-01-01")
        assertThat(packet.deterministicAnswerSnippets).contains("直接答案｜全部历史的记录共 3 条")
        assertThat(packet.rawNoteEvidence).isEmpty()
    }

    @Test
    fun buildReviewChatContextPacket_allHistoryCategoryQuery_keepsFullCorpusInsteadOfFakeEntityHits() {
        val packet = buildReviewChatContextPacket(
            question = "帮我分析总结下所有的记录，看看都有哪些类别。",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "产品设计", "启动页和图标方案"),
                sampleNote(2L, "精神健康", "减少消费和增加阅读"),
                sampleNote(3L, "AI实验", "上下文带宽和推理体验"),
                sampleNote(4L, "训练计划", "跑步和力量循环"),
                sampleNote(5L, "项目复盘", "系统卡顿和闪退问题"),
                sampleNote(6L, "方法论", "注意力决定人生"),
                sampleNote(7L, "未来规划", "虚拟机时间表"),
                sampleNote(8L, "生存法则", "低成本高效率"),
            ),
            weeklyReview = WeeklyReviewState(),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.RECORD_LOOKUP)
        assertThat(packet.wantsCategories).isTrue()
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(8)
        assertThat(packet.querySummarySnippets).contains("命中｜共 8 条记录")
        assertThat(packet.querySummarySnippets).contains("任务｜归纳命中记录的主要类别，不要把时间范围或统计口径当成类别")
        assertThat(packet.deterministicAnswerSnippets).contains("分类范围｜当前分类必须覆盖 8 条命中记录")
        assertThat(packet.categoryDigestSnippets).hasSize(1)
        assertThat(packet.categoryDigestSnippets.first()).contains("批次1｜")
        assertThat(packet.categoryDigestSnippets.first()).contains("产品设计")
        assertThat(packet.categoryDigestSnippets.first()).contains("精神健康")
        assertThat(packet.rawNoteEvidence).hasSize(8)
        assertThat(packet.skillResult).isNull()
    }

    @Test
    fun reviewChatPromptFactory_cloudAndOnDeviceUseLocalCoverageWithoutSkillPrompt() {
        val packet = buildReviewChatContextPacket(
            question = "帮我分析总结下所有的记录，看看都有哪些类别。",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "产品设计", "启动页和图标方案"),
                sampleNote(2L, "精神健康", "减少消费和增加阅读"),
                sampleNote(3L, "AI实验", "上下文带宽和推理体验"),
                sampleNote(4L, "训练计划", "跑步和力量循环"),
            ),
            weeklyReview = WeeklyReviewState(),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        val cloudPrompt = ReviewChatPromptFactory.cloud(packet)
        val onDevicePrompt = ReviewChatPromptFactory.onDevice(packet)
        assertThat(cloudPrompt).doesNotContain("SkillResult：")
        assertThat(cloudPrompt).contains("命中｜共 4 条记录")
        assertThat(cloudPrompt).contains("分类范围｜当前分类必须覆盖 4 条命中记录")
        assertThat(onDevicePrompt.userMessage).doesNotContain("SkillResult：")
        assertThat(onDevicePrompt.userMessage).contains("分类范围｜当前分类必须覆盖 4 条命中记录")
        assertThat(onDevicePrompt.extraContext["skill_id"]).isEmpty()
        assertThat(onDevicePrompt.extraContext["skill_matched_count"]).isEmpty()
    }

    @Test
    fun buildReviewChatContextPacket_collectionOverview_monthScopeFiltersToRequestedMonth() {
        val year = LocalDate.now(ZoneId.systemDefault()).year
        val march1 = LocalDate.of(year, 3, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val march20 = LocalDate.of(year, 3, 20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val april5 = LocalDate.of(year, 4, 5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = buildReviewChatContextPacket(
            question = "我3月份一共记了多少条？",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "三月主题A", "三月内容A").copy(createdAt = march1, updatedAt = march1 + 1_000L),
                sampleNote(2L, "三月主题B", "三月内容B").copy(createdAt = march20, updatedAt = march20 + 1_000L),
                sampleNote(3L, "四月主题", "四月内容").copy(createdAt = april5, updatedAt = april5 + 1_000L),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.COLLECTION_OVERVIEW)
        assertThat(packet.collectionOverview?.scopeLabel).isEqualTo("3月")
        assertThat(packet.collectionOverview?.totalCount).isEqualTo(2)
        assertThat(packet.rawNoteEvidence).isEmpty()
    }

    @Test
    fun buildReviewChatContextPacket_collectionOverview_addsExamplesOnlyWhenExplicitlyRequested() {
        val year = LocalDate.now(ZoneId.systemDefault()).year
        val march1 = LocalDate.of(year, 3, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val march20 = LocalDate.of(year, 3, 20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val april5 = LocalDate.of(year, 4, 5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = buildReviewChatContextPacket(
            question = "列出3月份命中的记录，一共有多少条？",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "三月主题A", "三月内容A").copy(createdAt = march1, updatedAt = march1 + 1_000L),
                sampleNote(2L, "三月主题B", "三月内容B").copy(createdAt = march20, updatedAt = march20 + 1_000L),
                sampleNote(3L, "四月主题", "四月内容").copy(createdAt = april5, updatedAt = april5 + 1_000L),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.questionMode).isEqualTo(ReviewChatQuestionMode.COLLECTION_OVERVIEW)
        assertThat(packet.rawNoteEvidence.map { it.title }).contains("三月主题A")
        assertThat(packet.rawNoteEvidence.map { it.title }).contains("三月主题B")
        assertThat(packet.rawNoteEvidence.map { it.title }).doesNotContain("四月主题")
    }

    @Test
    fun answer_collectionOverviewQuestion_routesToModelWithAccurateStats() = runTest {
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "最早主题", "最早内容").copy(createdAt = 1_000L, updatedAt = 2_000L),
                    sampleNote(2L, "中间主题", "中间内容").copy(createdAt = 2_000L, updatedAt = 3_000L),
                    sampleNote(3L, "最近主题", "最近内容").copy(createdAt = 3_000L, updatedAt = 4_000L),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("你总共有 3 条记录。")
            },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我总共有多少条记录",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.CLOUD)
        assertThat(result.answer).contains("3 条记录")
        assertThat(result.referencedNotes).isEmpty()
        assertThat(capturedPrompts.first()).contains("确定结果：")
        assertThat(capturedPrompts.first()).contains("直接答案｜全部历史的记录共 3 条")
        assertThat(capturedPrompts.first()).contains("集合概览：")
        assertThat(capturedPrompts.first()).contains("- 记录总数：共 3 条记录")
        assertThat(capturedPrompts.first()).doesNotContain("记录总数｜")
    }

    @Test
    fun answer_collectionOverviewQuestion_countsArchivedNotesWhenTheyAreLoaded() = runTest {
        val capturedPrompts = mutableListOf<String>()
        val planner = ReviewChatPlanner(
            loadNotes = {
                listOf(
                    sampleNote(1L, "活跃记录", "内容A"),
                    sampleNote(2L, "归档记录", "内容B").copy(isArchived = true),
                )
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { true },
            runCloud = { prompt ->
                capturedPrompts += prompt
                AiChatResult.Success("你总共有 2 条记录。")
            },
            runOnDevice = { AiChatResult.Success("端侧不应该被调用") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "我总共有多少条记录",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.answer).contains("2 条记录")
        assertThat(capturedPrompts.first()).contains("直接答案｜全部历史的记录共 2 条")
        assertThat(capturedPrompts.first()).contains("- 记录总数：共 2 条记录")
    }

    @Test
    fun answer_broadCategoryQuery_usesSingleModelCallWithoutPreSummarizer() = runTest {
        var cloudCalls = 0
        val planner = ReviewChatPlanner(
            loadNotes = {
                List(12) { index ->
                    sampleNote(
                        id = index.toLong() + 1L,
                        topic = when (index % 4) {
                            0 -> "MindFlow 功能迭代 $index"
                            1 -> "技术优化与调试 $index"
                            2 -> "个人成长方法论 $index"
                            else -> "生活与健康记录 $index"
                        },
                        content = "内容 $index"
                    )
                }
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.CLOUD_ONLY },
            isCloudConfigured = { true },
            isOnDeviceReady = { false },
            runCloud = { prompt ->
                cloudCalls += 1
                assertThat(prompt).doesNotContain("SkillResult：")
                assertThat(prompt).contains("分类范围｜当前分类必须覆盖 12 条命中记录")
                assertThat(prompt).doesNotContain("当前批次：")
                assertThat(prompt).doesNotContain("你在合并多批个人历史记录的类别候选")
                AiChatResult.Success(
                    """
                    {
                      "summary": "这 12 条记录主要分成几类主题。",
                      "sections": [
                        {
                          "title": "类别",
                          "items": [
                            "应用开发：MindFlow 功能迭代、产品体验",
                            "技术优化：性能、调试、系统修复",
                            "个人成长：原则、方法论、认知提升",
                            "生活健康：作息、身体状态、健康管理"
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
            },
            runOnDevice = { AiChatResult.Success("不应该调用端侧") },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                question = "帮我分析所有的记录，都有哪些分类？",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.structuredAnswer).isNotNull()
        assertThat(result.structuredAnswer!!.sections.last().title).isEqualTo("类别")
        assertThat(result.structuredAnswer!!.sections.last().items).containsAtLeast(
            "应用开发：MindFlow 功能迭代、产品体验",
            "技术优化：性能、调试、系统修复",
            "个人成长：原则、方法论、认知提升",
            "生活健康：作息、身体状态、健康管理",
        )
        assertThat(cloudCalls).isEqualTo(1)
    }

    @Test
    fun answer_broadCategoryQuery_onDeviceOnly_usesSingleModelCallWithoutPreSummarizer() = runTest {
        var onDeviceCalls = 0
        val planner = ReviewChatPlanner(
            loadNotes = {
                List(12) { index ->
                    sampleNote(
                        id = index.toLong() + 1L,
                        topic = when (index % 4) {
                            0 -> "MindFlow 功能迭代 $index"
                            1 -> "技术优化与调试 $index"
                            2 -> "个人成长方法论 $index"
                            else -> "生活与健康记录 $index"
                        },
                        content = "内容 $index"
                    )
                }
            },
            loadWeeklyReview = { WeeklyReviewState(lines = emptyList()) },
            loadMaintenanceSnapshot = { LocalKnowledgeMaintenanceSnapshot() },
            loadWikiSnapshot = { DirectionWikiSnapshot() },
            resolveExecutionMode = { AiExecutionMode.ON_DEVICE_ONLY },
            isCloudConfigured = { false },
            isOnDeviceReady = { true },
            runCloud = { error("不应该调用云侧") },
            runOnDevice = { request ->
                onDeviceCalls += 1
                assertThat(request.systemInstruction).contains("每个数组元素只放一条 `类别名称：包含的信息`")
                assertThat(request.systemInstruction).doesNotContain("每行固定格式：类别名称：包含的信息")
                AiChatResult.Success(
                    """
                    {
                      "summary": "这 12 条记录主要分成几类主题。",
                      "sections": [
                        {
                          "title": "类别",
                          "items": [
                            "应用开发：MindFlow 功能迭代、产品体验",
                            "技术优化：性能、调试、系统修复",
                            "个人成长：原则、方法论、认知提升",
                            "生活健康：作息、身体状态、健康管理"
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
            },
        )

        val result = planner.answer(
            ReviewChatTurnRequest(
                sessionId = "review-chat",
                question = "帮我分析所有的记录，都有哪些分类？",
                priorMessages = emptyList(),
            ),
        )

        assertThat(result.provider).isEqualTo(ReviewChatProvider.ON_DEVICE)
        assertThat(result.structuredAnswer).isNotNull()
        assertThat(result.structuredAnswer!!.sections.last().items).containsAtLeast(
            "应用开发：MindFlow 功能迭代、产品体验",
            "技术优化：性能、调试、系统修复",
            "个人成长：原则、方法论、认知提升",
            "生活健康：作息、身体状态、健康管理",
        )
        assertThat(onDeviceCalls).isEqualTo(1)
    }

    @Test
    fun extractReviewChatEntityTerms_removesOperationNoiseAndKeepsSubject() {
        assertThat(extractReviewChatEntityTerms("人生态度记录的时间轴跨度"))
            .contains("人生态度")
        assertThat(extractReviewChatEntityTerms("关于抖音一共有多少条记录"))
            .contains("抖音")
        assertThat(extractReviewChatEntityTerms("把 4 月 10 号那天的完整内容发给我"))
            .isEmpty()
        assertThat(extractReviewChatEntityTerms("把 4 月 10 号那条记录的原始链接发给我"))
            .isEmpty()
    }

    @Test
    fun buildReviewChatContextPacket_collectionOverview_countsEntityAcrossWholeHistory() {
        val year = LocalDate.now(ZoneId.systemDefault()).year
        val march1 = LocalDate.of(year, 3, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val march20 = LocalDate.of(year, 3, 20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val april5 = LocalDate.of(year, 4, 5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val april18 = LocalDate.of(year, 4, 18).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = buildReviewChatContextPacket(
            question = "人生态度一共有多少条记录",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "人生态度｜注意力决定人生", "关注注意力").copy(createdAt = march1, updatedAt = march1 + 1_000L),
                sampleNote(2L, "普通工作记录", "和人生态度无关").copy(createdAt = march20, updatedAt = march20 + 1_000L),
                sampleNote(3L, "人生态度｜面对失败的正确态度", "面对失败").copy(createdAt = april5, updatedAt = april5 + 1_000L),
                sampleNote(4L, "人生态度｜低成本高效率的生存法则", "生存法则").copy(createdAt = april18, updatedAt = april18 + 1_000L),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.collectionOverview?.totalCount).isEqualTo(3)
        assertThat(packet.collectionOverview?.earliestDateLabel).isEqualTo("$year-03-01")
        assertThat(packet.collectionOverview?.latestDateLabel).isEqualTo("$year-04-18")
        assertThat(packet.querySummarySnippets).contains("操作｜统计")
        assertThat(packet.querySummarySnippets).contains("主题｜人生态度")
        assertThat(packet.querySummarySnippets).contains("命中｜共 3 条记录")
        assertThat(packet.deterministicAnswerSnippets).contains("直接答案｜关于人生态度的记录共 3 条")
    }

    @Test
    fun buildReviewChatContextPacket_timelineAnchor_usesEntityMatchedHistory() {
        val year = LocalDate.now(ZoneId.systemDefault()).year
        val march1 = LocalDate.of(year, 3, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val april18 = LocalDate.of(year, 4, 18).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val packet = buildReviewChatContextPacket(
            question = "人生态度第一条记录是什么时候",
            intent = ReviewChatIntent.RECALL,
            notes = listOf(
                sampleNote(1L, "普通工作记录", "与主题无关").copy(createdAt = 1_000L, updatedAt = 2_000L),
                sampleNote(2L, "人生态度｜注意力决定人生", "最早的人生态度记录").copy(createdAt = march1, updatedAt = march1 + 1_000L),
                sampleNote(3L, "人生态度｜低成本高效率的生存法则", "最近的人生态度记录").copy(createdAt = april18, updatedAt = april18 + 1_000L),
            ),
            weeklyReview = WeeklyReviewState(lines = emptyList()),
            maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
            wikiSnapshot = DirectionWikiSnapshot(),
            sessionSummary = "",
        )

        assertThat(packet.historyAnchors.first().item.title).contains("人生态度")
        assertThat(packet.historyAnchors.first().item.dateLabel).isEqualTo("$year-03-01")
        assertThat(packet.deterministicAnswerSnippets).contains("直接答案｜人生态度的最早记录在 $year-03-01，标题《人生态度｜注意力决定人生》")
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

    private fun decodeSkillCardPayload(url: String): String {
        val payload = url.substringAfter("payload=", "")
        return String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
    }
}
