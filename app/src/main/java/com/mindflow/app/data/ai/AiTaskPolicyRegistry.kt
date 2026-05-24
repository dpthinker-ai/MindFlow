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
        AiTaskType.GRAPH_GENERATE_RELATIONS -> AiTaskPolicy(
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
    }
}
