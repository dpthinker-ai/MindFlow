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
import org.junit.Test

class ReviewChatPromptRegressionTest {
    @Test
    fun briefTopicPromptForCloudAndOnDeviceForbidsEvidenceNextStepAndRecords() {
        val packet = packet(
            question = "我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。",
            notes = listOf(
                note(1L, "人生是多线程运行", "提升价值的同时赚钱，在利他的同时守住边界。"),
                note(2L, "社交边界与自我保护原则", "不要为了取悦别人损耗自己的精力。"),
            ),
        )

        val cloud = ReviewChatPromptFactory.cloud(packet)
        val onDevice = ReviewChatPromptFactory.onDevice(packet)

        assertThat(cloud).contains("SkillResult：")
        assertThat(cloud).contains("coverage｜范围内 2 条；命中 2 条；已处理 2 条；完整覆盖=true")
        assertThat(cloud).contains("只把简短总结写进 summary，不要创建 `依据`、`下一步` 或 `记录` section")
        assertThat(cloud).contains("record｜")
        assertThat(onDevice.systemInstruction).contains("不要创建 `依据`、`下一步` 或 `记录` section")
        assertThat(onDevice.userMessage).contains("SkillResult：")
        assertThat(onDevice.extraContext["skill_matched_count"]).isEqualTo("2")
    }

    @Test
    fun categoryPromptRequiresOneCategoryPerItemAndFullCoverage() {
        val packet = packet(
            question = "帮我分析所有的记录，都有哪些分类？",
            notes = listOf(
                note(1L, "应用启动页设计", "启动页、图标、名称"),
                note(2L, "OpenCL GPU 调研", "GPU、CPU、LiteRT"),
                note(3L, "注意力决定人生", "个人成长和精力管理"),
                note(4L, "小朋友病愈复学", "家庭生活记录"),
            ),
        )

        val cloud = ReviewChatPromptFactory.cloud(packet)
        val onDevice = ReviewChatPromptFactory.onDevice(packet)

        assertThat(packet.wantsCategories).isTrue()
        assertThat(cloud).contains("coverage｜范围内 4 条；命中 4 条；已处理 4 条；完整覆盖=true")
        assertThat(cloud).contains("每个数组元素只放一条 `类别名称：包含的信息`")
        assertThat(cloud).contains("不要把多个类别塞进同一个 items 字符串")
        assertThat(cloud).contains("分类范围｜当前分类必须覆盖 4 条命中记录")
        assertThat(onDevice.systemInstruction).contains("每个数组元素只放一条 `类别名称：包含的信息`")
        assertThat(onDevice.systemInstruction).contains("不要把多个类别塞进同一个 items 字符串")
        assertThat(onDevice.userMessage).contains("coverage｜范围内 4 条；命中 4 条；已处理 4 条；完整覆盖=true")
    }

    @Test
    fun externalPromptDoesNotCarrySkillResultOrHistoryMaterial() {
        val packet = packet(
            question = "今天天气怎么样",
            notes = listOf(note(1L, "产品方向", "今天讨论了推荐链路")),
            availableSkillSnippets = listOf("- history-query：Query and visualize MindFlow historical notes.（输出：text/webview）"),
        )

        val cloud = ReviewChatPromptFactory.cloud(packet)
        val onDevice = ReviewChatPromptFactory.onDevice(packet)

        assertThat(packet.skillResult).isNull()
        assertThat(cloud).contains("你正在回答一个通用问题")
        assertThat(cloud).contains("不要引用个人历史记录")
        assertThat(cloud).doesNotContain("SkillResult：")
        assertThat(cloud).doesNotContain("可用技能：")
        assertThat(cloud).doesNotContain("原始记录：")
        assertThat(cloud).doesNotContain("产品方向")
        assertThat(onDevice.systemInstruction).contains("不要引用个人历史记录")
        assertThat(onDevice.userMessage).doesNotContain("SkillResult：")
        assertThat(onDevice.userMessage).doesNotContain("可用技能：")
        assertThat(onDevice.userMessage).doesNotContain("产品方向")
    }

    @Test
    fun historyPromptCarriesAvailableSkillSummariesBeforeSkillResult() {
        val packet = packet(
            question = "今天记录了什么？",
            notes = listOf(note(1L, "产品方向", "今天讨论了推荐链路")),
            availableSkillSnippets = listOf("- history-query：Query and visualize MindFlow historical notes.（输出：text/webview）"),
        )

        val cloud = ReviewChatPromptFactory.cloud(packet)
        val onDevice = ReviewChatPromptFactory.onDevice(packet)

        assertThat(cloud).contains("可用技能：")
        assertThat(cloud).contains("- history-query：Query and visualize MindFlow historical notes.（输出：text/webview）")
        assertThat(cloud.indexOf("可用技能：")).isLessThan(cloud.indexOf("SkillResult："))
        assertThat(onDevice.userMessage).contains("可用技能：")
        assertThat(onDevice.extraContext["available_skill_count"]).isEqualTo("1")
    }

    @Test
    fun formatterKeepsStructuredCategoryItemsAsSeparateMarkdownBullets() {
        val answer = ReviewChatStructuredAnswer(
            sections = listOf(
                ReviewChatStructuredSection(
                    title = "答复",
                    body = listOf("这些记录主要分成三类。"),
                    items = emptyList(),
                ),
                ReviewChatStructuredSection(
                    title = "类别",
                    body = emptyList(),
                    items = listOf(
                        "应用开发：启动页、图标、功能",
                        "技术优化：OpenCL、GC、OCR",
                        "个人成长：注意力、边界、精力",
                    ),
                ),
            ),
        )

        val markdown = renderReviewChatStructuredAnswerAsMarkdown(answer)

        assertThat(markdown).contains("这些记录主要分成三类。\n\n类别：")
        assertThat(markdown).contains("\n- 应用开发：启动页、图标、功能")
        assertThat(markdown).contains("\n- 技术优化：OpenCL、GC、OCR")
        assertThat(markdown).contains("\n- 个人成长：注意力、边界、精力")
        assertThat(markdown).doesNotContain("功能-技术优化")
    }

    private fun packet(
        question: String,
        notes: List<NoteEntity>,
        availableSkillSnippets: List<String> = emptyList(),
    ): ReviewChatContextPacket = buildReviewChatContextPacket(
        question = question,
        intent = ReviewChatIntent.RECALL,
        notes = notes,
        weeklyReview = WeeklyReviewState(lines = emptyList()),
        maintenanceSnapshot = LocalKnowledgeMaintenanceSnapshot(),
        wikiSnapshot = DirectionWikiSnapshot(),
        sessionSummary = "",
        availableSkillSnippets = availableSkillSnippets,
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
