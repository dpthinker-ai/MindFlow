package com.mindflow.app.data.reviewchat

internal data class ReviewChatResponsePolicy(
    val allowedSectionTitles: List<String>,
) {
    fun allows(sectionTitle: String): Boolean = sectionTitle in allowedSectionTitles

    companion object {
        fun resolve(packet: ReviewChatContextPacket): ReviewChatResponsePolicy =
            resolve(
                mode = packet.questionMode,
                wantsCategories = packet.wantsCategories,
                wantsBriefAnswer = packet.wantsBriefAnswer,
                question = packet.question,
            )

        fun resolve(
            mode: ReviewChatQuestionMode,
            wantsCategories: Boolean,
            wantsBriefAnswer: Boolean,
            question: String = "",
        ): ReviewChatResponsePolicy {
            val wantsSupport = wantsSupportSection(question, mode)
            val wantsNextStep = wantsNextStepSection(question)
            val wantsExamples = wantsRecordExamples(question)

            val sections = when (mode) {
                ReviewChatQuestionMode.EXTERNAL -> if (wantsNextStep) listOf(NEXT_STEP) else emptyList()
                ReviewChatQuestionMode.COLLECTION_OVERVIEW -> when {
                    wantsCategories -> listOf(CATEGORY)
                    wantsSupport -> listOf(EVIDENCE)
                    wantsExamples -> listOf(RECORD)
                    else -> emptyList()
                }
                ReviewChatQuestionMode.RECORD_LOOKUP -> when {
                    wantsBriefAnswer -> emptyList()
                    wantsCategories -> listOf(CATEGORY) + if (wantsExamples) listOf(RECORD) else emptyList()
                    else -> listOf(RECORD)
                }
                ReviewChatQuestionMode.FULL_RECORD -> listOf(FULL_RECORD)
                ReviewChatQuestionMode.TIMELINE_ANCHOR -> listOf(TIMELINE)
                ReviewChatQuestionMode.ANALYSIS -> buildList {
                    if (wantsSupport) add(EVIDENCE)
                    if (wantsNextStep) add(NEXT_STEP)
                }
            }
            return ReviewChatResponsePolicy(allowedSectionTitles = sections)
        }
    }
}

internal const val REVIEW_CHAT_SECTION_ANSWER = "答复"
internal const val REVIEW_CHAT_SECTION_EVIDENCE = "依据"
internal const val REVIEW_CHAT_SECTION_NEXT_STEP = "下一步"
internal const val REVIEW_CHAT_SECTION_RECORD = "记录"
internal const val REVIEW_CHAT_SECTION_FULL_RECORD = "完整记录"
internal const val REVIEW_CHAT_SECTION_TIMELINE = "时间线"
internal const val REVIEW_CHAT_SECTION_CATEGORY = "类别"

internal fun buildReviewChatAllowedSectionTitles(
    mode: ReviewChatQuestionMode,
    wantsCategories: Boolean,
    wantsBriefAnswer: Boolean,
    question: String = "",
): List<String> =
    ReviewChatResponsePolicy.resolve(
        mode = mode,
        wantsCategories = wantsCategories,
        wantsBriefAnswer = wantsBriefAnswer,
        question = question,
    ).allowedSectionTitles

private object ReviewChatResponsePolicyPhrases {
    val support = listOf(
        "依据",
        "证据",
        "为什么",
        "原因",
        "基于什么",
        "从哪里看出",
        "怎么得出",
        "哪些记录支撑",
    )

    val collectionScopeSupport = listOf(
        "统计口径",
        "范围",
        "怎么算",
    )

    val nextStep = listOf(
        "下一步",
        "下步",
        "给我建议",
        "有什么建议",
        "你的建议",
        "改进建议",
        "建议怎么",
        "建议我",
        "怎么办",
        "怎么做",
        "如何推进",
        "怎么推进",
        "行动项",
        "待办",
        "计划",
    )

    val recordExamples = listOf(
        "列出",
        "列一下",
        "逐条",
        "明细",
        "具体记录",
        "命中的记录",
        "哪些记录",
        "举例",
        "例子",
        "展示",
    )
}

private fun wantsSupportSection(
    question: String,
    mode: ReviewChatQuestionMode,
): Boolean {
    val normalized = question.lowercase()
    if (ReviewChatResponsePolicyPhrases.support.any(normalized::contains)) return true
    return mode == ReviewChatQuestionMode.COLLECTION_OVERVIEW &&
        ReviewChatResponsePolicyPhrases.collectionScopeSupport.any(normalized::contains)
}

private fun wantsNextStepSection(question: String): Boolean {
    val normalized = question.lowercase()
    return ReviewChatResponsePolicyPhrases.nextStep.any(normalized::contains)
}

private fun wantsRecordExamples(question: String): Boolean {
    val normalized = question.lowercase()
    return ReviewChatResponsePolicyPhrases.recordExamples.any(normalized::contains)
}

private const val EVIDENCE = REVIEW_CHAT_SECTION_EVIDENCE
private const val NEXT_STEP = REVIEW_CHAT_SECTION_NEXT_STEP
private const val RECORD = REVIEW_CHAT_SECTION_RECORD
private const val FULL_RECORD = REVIEW_CHAT_SECTION_FULL_RECORD
private const val TIMELINE = REVIEW_CHAT_SECTION_TIMELINE
private const val CATEGORY = REVIEW_CHAT_SECTION_CATEGORY
