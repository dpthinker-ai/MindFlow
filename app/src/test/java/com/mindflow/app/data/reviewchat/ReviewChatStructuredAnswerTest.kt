package com.mindflow.app.data.reviewchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewChatStructuredAnswerTest {
    @Test
    fun buildReviewChatStructuredOutputSchema_usesConcreteTemplatePerMode() {
        val schema = buildReviewChatStructuredOutputSchema(
            mode = ReviewChatQuestionMode.RECORD_LOOKUP,
            wantsCategories = true,
        )

        assertThat(schema).contains("严格使用这个模板里的 title 和顺序")
        assertThat(schema).contains("\"title\":\"类别\"")
        assertThat(schema).doesNotContain("\"title\":\"记录\"")
        assertThat(schema).doesNotContain("\"title\":\"类别|记录\"")

        val schemaWithRecords = buildReviewChatStructuredOutputSchema(
            mode = ReviewChatQuestionMode.RECORD_LOOKUP,
            wantsCategories = true,
            question = "帮我分类，并列出命中的记录",
        )
        assertThat(schemaWithRecords).contains("\"title\":\"类别\"")
        assertThat(schemaWithRecords).contains("\"title\":\"记录\"")
    }

    @Test
    fun parseReviewChatStructuredAnswer_parsesJsonSchema() {
        val answer = parseReviewChatStructuredAnswer(
            """
            {
              "summary": "本周末主要记录了 3 类信息。",
              "sections": [
                {
                  "title": "类别",
                  "items": [
                    "类别：产品设计：启动页、图标、名称",
                    "类别：技术实现：OCR、OpenCL、GC 水线"
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(answer).isNotNull()
        assertThat(answer!!.sections).hasSize(2)
        assertThat(answer.sections.first().title).isEqualTo("答复")
        assertThat(answer.sections.first().body.single()).isEqualTo("本周末主要记录了 3 类信息。")
        assertThat(answer.sections[1].title).isEqualTo("类别")
        assertThat(answer.sections[1].items).containsExactly(
            "产品设计：启动页、图标、名称",
            "技术实现：OCR、OpenCL、GC 水线",
        ).inOrder()
    }

    @Test
    fun parseReviewChatStructuredAnswer_splitsInlineCategoryItemsOnSingleLine() {
        val answer = parseReviewChatStructuredAnswer(
            "【答复】下面是分类结果。【类别】- 类别：应用设计与功能开发：应用启动页设计、图标及名称确定-类别：技术实现与优化：前端OCR识别问题、利用RL优化GC触发水线-类别：个人成长与效率提升：提高生产力的方法、拒绝内耗，保持精力充沛",
        )

        assertThat(answer).isNotNull()
        val sections = answer!!.sections
        assertThat(sections).hasSize(2)
        assertThat(sections[0].title).isEqualTo("答复")
        assertThat(sections[0].body.single()).isEqualTo("下面是分类结果。")
        assertThat(sections[1].title).isEqualTo("类别")
        assertThat(sections[1].items).containsExactly(
            "应用设计与功能开发：应用启动页设计、图标及名称确定",
            "技术实现与优化：前端OCR识别问题、利用RL优化GC触发水线",
            "个人成长与效率提升：提高生产力的方法、拒绝内耗，保持精力充沛",
        ).inOrder()
    }

    @Test
    fun parseReviewChatStructuredAnswer_parsesPlainSectionHeadings() {
        val answer = parseReviewChatStructuredAnswer(
            """
            人生建议的核心在于将人生视为多线程运行。

            依据：
            - 2026-04-05《人生是多线程运行》：提升价值的同时赚钱。

            下一步：
            - 继续往系统性思考推进。
            """.trimIndent(),
            includePlainSections = true,
        )

        assertThat(answer).isNotNull()
        assertThat(answer!!.sections.map { it.title }).containsExactly("答复", "依据", "下一步").inOrder()
        assertThat(answer.sections.first().body.single()).contains("人生建议的核心")
        assertThat(answer.sections[1].items.single()).contains("2026-04-05《人生是多线程运行》")
    }

    @Test
    fun renderReviewChatStructuredAnswerAsMarkdown_rendersSectionListsDeterministically() {
        val markdown = renderReviewChatStructuredAnswerAsMarkdown(
            ReviewChatStructuredAnswer(
                sections = listOf(
                    ReviewChatStructuredSection(
                        title = "答复",
                        body = listOf("本周末主要记录了 3 类信息。"),
                        items = emptyList(),
                    ),
                    ReviewChatStructuredSection(
                        title = "类别",
                        body = emptyList(),
                        items = listOf(
                            "产品设计：启动页、图标、名称",
                            "技术实现：OCR、OpenCL、GC 水线",
                        ),
                    ),
                ),
            ),
        )

        assertThat(markdown).contains("本周末主要记录了 3 类信息。")
        assertThat(markdown).contains("\n\n类别：\n- 产品设计：启动页、图标、名称")
        assertThat(markdown).contains("\n- 技术实现：OCR、OpenCL、GC 水线")
    }

    @Test
    fun finalizeReviewChatStructuredAnswer_addsDeterministicRecordSection() {
        val packet = ReviewChatContextPacket(
            questionMode = ReviewChatQuestionMode.RECORD_LOOKUP,
            intent = ReviewChatIntent.RECALL,
            question = "我只看今天的",
            isExternalQuestion = false,
            wantsCount = false,
            wantsCategories = false,
            wantsBriefAnswer = false,
            querySummarySnippets = emptyList(),
            deterministicAnswerSnippets = emptyList(),
            categoryDigestSnippets = emptyList(),
            sessionSummary = "",
            collectionOverview = null,
            conversationSnippets = emptyList(),
            historyAnchors = emptyList(),
            memoryDigestSnippets = emptyList(),
            memoryThreadSnippets = emptyList(),
            knowledgeBaseSnippets = emptyList(),
            wikiSnippets = emptyList(),
            rawNoteEvidence = listOf(
                ReviewChatEvidenceItem(
                    noteId = 1L,
                    dateLabel = "2026-04-13",
                    title = "MindFlow开发问题复盘",
                    summary = "系统卡顿并频繁闪退。",
                ),
            ),
            rawNoteDetails = emptyList(),
            structuredSnippets = emptyList(),
        )

        val answer = finalizeReviewChatStructuredAnswer(
            packet = packet,
            rawAnswer = "今天共有 1 条命中记录。",
            candidate = null,
        )

        assertThat(answer).isNotNull()
        assertThat(answer!!.sections.map { it.title }).containsExactly("答复", "记录").inOrder()
        assertThat(answer.sections[1].items).containsExactly(
            "2026-04-13《MindFlow开发问题复盘》：系统卡顿并频繁闪退。",
        )
    }

    @Test
    fun finalizeReviewChatStructuredAnswer_briefSummaryDropsEvidenceAndRecordSections() {
        val packet = ReviewChatContextPacket(
            questionMode = ReviewChatQuestionMode.RECORD_LOOKUP,
            intent = ReviewChatIntent.RECALL,
            question = "我记了哪些人生建议？帮我总结一下，把它们简单总结成几句话。",
            isExternalQuestion = false,
            wantsCount = false,
            wantsCategories = false,
            wantsBriefAnswer = true,
            querySummarySnippets = emptyList(),
            deterministicAnswerSnippets = emptyList(),
            categoryDigestSnippets = emptyList(),
            sessionSummary = "",
            collectionOverview = null,
            conversationSnippets = emptyList(),
            historyAnchors = emptyList(),
            memoryDigestSnippets = emptyList(),
            memoryThreadSnippets = emptyList(),
            knowledgeBaseSnippets = emptyList(),
            wikiSnippets = emptyList(),
            rawNoteEvidence = listOf(
                ReviewChatEvidenceItem(
                    noteId = 1L,
                    dateLabel = "2026-04-05",
                    title = "人生是多线程运行",
                    summary = "提升价值的同时赚钱。",
                ),
            ),
            rawNoteDetails = emptyList(),
            structuredSnippets = emptyList(),
        )

        val answer = finalizeReviewChatStructuredAnswer(
            packet = packet,
            rawAnswer = """
                人生建议的核心在于将人生视为多线程运行。

                依据：
                - 2026-04-05《人生是多线程运行》：提升价值的同时赚钱。

                下一步：
                - 继续往系统性思考推进。
            """.trimIndent(),
            candidate = parseReviewChatStructuredAnswer(
                """
                人生建议的核心在于将人生视为多线程运行。

                依据：
                - 2026-04-05《人生是多线程运行》：提升价值的同时赚钱。

                下一步：
                - 继续往系统性思考推进。
                """.trimIndent(),
                includePlainSections = true,
            ),
        )

        assertThat(answer).isNotNull()
        assertThat(answer!!.sections.map { it.title }).containsExactly("答复").inOrder()
        assertThat(answer.sections.single().body.single()).contains("人生建议的核心")
    }

    @Test
    fun finalizeReviewChatStructuredAnswer_analysisDropsEvidenceAndNextStepUnlessAsked() {
        val packet = basePacket(
            questionMode = ReviewChatQuestionMode.ANALYSIS,
            question = "我记了哪些人生建议？帮我总结成几句话。",
        )

        val answer = finalizeReviewChatStructuredAnswer(
            packet = packet,
            rawAnswer = """
                人生建议主要是提升价值、守住边界和持续改变。

                依据：
                - 来自人生建议记录。

                下一步：
                - 继续形成行动计划。
            """.trimIndent(),
            candidate = parseReviewChatStructuredAnswer(
                """
                人生建议主要是提升价值、守住边界和持续改变。

                依据：
                - 来自人生建议记录。

                下一步：
                - 继续形成行动计划。
                """.trimIndent(),
                includePlainSections = true,
            ),
        )

        assertThat(answer).isNotNull()
        assertThat(answer!!.sections.map { it.title }).containsExactly("答复").inOrder()
    }

    @Test
    fun finalizeReviewChatStructuredAnswer_keepsEvidenceAndNextStepWhenQuestionAsksForThem() {
        val packet = basePacket(
            questionMode = ReviewChatQuestionMode.ANALYSIS,
            question = "为什么会得出这个判断？依据是什么，下一步建议怎么做？",
        )

        val answer = finalizeReviewChatStructuredAnswer(
            packet = packet,
            rawAnswer = """
                这个判断成立。

                依据：
                - 多条记录都提到同一趋势。

                下一步：
                - 先验证一个最小行动。
            """.trimIndent(),
            candidate = parseReviewChatStructuredAnswer(
                """
                这个判断成立。

                依据：
                - 多条记录都提到同一趋势。

                下一步：
                - 先验证一个最小行动。
                """.trimIndent(),
                includePlainSections = true,
            ),
        )

        assertThat(answer).isNotNull()
        assertThat(answer!!.sections.map { it.title }).containsExactly("答复", "依据", "下一步").inOrder()
    }

    private fun basePacket(
        questionMode: ReviewChatQuestionMode,
        question: String,
    ): ReviewChatContextPacket = ReviewChatContextPacket(
        questionMode = questionMode,
        intent = ReviewChatIntent.RECALL,
        question = question,
        isExternalQuestion = false,
        wantsCount = false,
        wantsCategories = false,
        wantsBriefAnswer = false,
        querySummarySnippets = emptyList(),
        deterministicAnswerSnippets = emptyList(),
        categoryDigestSnippets = emptyList(),
        sessionSummary = "",
        collectionOverview = null,
        conversationSnippets = emptyList(),
        historyAnchors = emptyList(),
        memoryDigestSnippets = emptyList(),
        memoryThreadSnippets = emptyList(),
        knowledgeBaseSnippets = emptyList(),
        wikiSnippets = emptyList(),
        rawNoteEvidence = emptyList(),
        rawNoteDetails = emptyList(),
        structuredSnippets = emptyList(),
    )
}
