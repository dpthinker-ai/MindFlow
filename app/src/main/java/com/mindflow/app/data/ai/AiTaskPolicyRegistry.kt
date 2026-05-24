package com.mindflow.app.data.ai

data class AiTaskPolicy(
    val providerOrder: List<AiProvider>,
    val allowCloudFallback: Boolean,
    val allowBackgroundCloud: Boolean,
    val dataSensitivity: AiDataSensitivity,
    val payloadPolicy: PromptPayloadPolicy,
    val noticeMode: AiCloudNoticeMode,
)

object AiTaskPolicyRegistry {
    fun policyFor(taskType: AiTaskType): AiTaskPolicy = when (taskType) {
        AiTaskType.TEST_CONNECTION -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.CLOUD),
            allowCloudFallback = false,
            allowBackgroundCloud = false,
            dataSensitivity = AiDataSensitivity.LOW,
            payloadPolicy = PromptPayloadPolicy.METADATA_ONLY,
            noticeMode = AiCloudNoticeMode.NONE,
        )
        AiTaskType.POLISH_CONTENT -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.CLOUD, AiProvider.ON_DEVICE),
            allowCloudFallback = true,
            allowBackgroundCloud = false,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.SINGLE_NOTE_EXCERPT,
            noticeMode = AiCloudNoticeMode.TOAST,
        )
        AiTaskType.POLISH_TITLE -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.CLOUD, AiProvider.ON_DEVICE),
            allowCloudFallback = true,
            allowBackgroundCloud = false,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.SELECTED_SNIPPETS,
            noticeMode = AiCloudNoticeMode.TOAST,
        )
        AiTaskType.SUMMARIZE_NOTE -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD),
            allowCloudFallback = true,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.SINGLE_NOTE_EXCERPT,
            noticeMode = AiCloudNoticeMode.AGGREGATED,
        )
        AiTaskType.EXTRACT_TOPIC,
        AiTaskType.EXTRACT_TAGS,
        AiTaskType.CLASSIFY_CATEGORY -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD),
            allowCloudFallback = true,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.LOW,
            payloadPolicy = PromptPayloadPolicy.SELECTED_SNIPPETS,
            noticeMode = AiCloudNoticeMode.AGGREGATED,
        )
        AiTaskType.GRAPH_EXTRACT_CONCEPTS,
        AiTaskType.GRAPH_CANONICALIZE_CONCEPTS,
        AiTaskType.GRAPH_GENERATE_RELATIONS,
        AiTaskType.GRAPH_GENERATE_SNAPSHOT -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD),
            allowCloudFallback = true,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.MULTI_NOTE_COMPACT_CONTEXT,
            noticeMode = AiCloudNoticeMode.AGGREGATED,
        )
        AiTaskType.TRANSCRIBE_AUDIO,
        AiTaskType.TRANSLATE_AUDIO,
        AiTaskType.UNDERSTAND_IMAGE -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.ON_DEVICE),
            allowCloudFallback = false,
            allowBackgroundCloud = false,
            dataSensitivity = AiDataSensitivity.HIGH,
            payloadPolicy = PromptPayloadPolicy.LOCAL_FILE_ONLY,
            noticeMode = AiCloudNoticeMode.INLINE,
        )
        AiTaskType.DAILY_BRIEF,
        AiTaskType.WEEKLY_REVIEW,
        AiTaskType.FUSION_SUGGESTION,
        AiTaskType.FLOW_MAINLINE,
        AiTaskType.FLOW_SETTLED_KNOWLEDGE,
        AiTaskType.FLOW_BREAKTHROUGH_GAP -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD),
            allowCloudFallback = true,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.COMPACT_SUMMARY,
            noticeMode = AiCloudNoticeMode.AGGREGATED,
        )
        AiTaskType.NEXT_ACTION -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD),
            allowCloudFallback = true,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.SINGLE_NOTE_EXCERPT,
            noticeMode = AiCloudNoticeMode.AGGREGATED,
        )
        AiTaskType.THREAD_WORKSPACE,
        AiTaskType.RESEARCH_ACTION_SUMMARY,
        AiTaskType.THREAD_EXECUTION -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD),
            allowCloudFallback = true,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.COMPACT_THREAD_CONTEXT,
            noticeMode = AiCloudNoticeMode.AGGREGATED,
        )
        AiTaskType.RESEARCH_BRIEF,
        AiTaskType.EXTERNAL_RESEARCH,
        AiTaskType.STALE_RECONNECT -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.CLOUD, AiProvider.ON_DEVICE),
            allowCloudFallback = true,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.METADATA_AND_SUMMARY,
            noticeMode = AiCloudNoticeMode.AGGREGATED,
        )
        AiTaskType.REVIEW_CHAT_REPLY -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.CLOUD, AiProvider.ON_DEVICE),
            allowCloudFallback = true,
            allowBackgroundCloud = false,
            dataSensitivity = AiDataSensitivity.MEDIUM,
            payloadPolicy = PromptPayloadPolicy.MULTI_NOTE_COMPACT_CONTEXT,
            noticeMode = AiCloudNoticeMode.TOAST,
        )
        AiTaskType.REVIEW_CHAT_QUERY_PLAN -> AiTaskPolicy(
            providerOrder = listOf(AiProvider.CLOUD, AiProvider.ON_DEVICE),
            allowCloudFallback = false,
            allowBackgroundCloud = true,
            dataSensitivity = AiDataSensitivity.LOW,
            payloadPolicy = PromptPayloadPolicy.METADATA_ONLY,
            noticeMode = AiCloudNoticeMode.NONE,
        )
    }
}
