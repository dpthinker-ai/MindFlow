package com.mindflow.app.data.ai

enum class AiProviderSelectionReason {
    SELECTED_BY_POLICY,
    FALLBACK_FROM_ON_DEVICE_EMPTY,
    FALLBACK_FROM_ON_DEVICE_QUALITY,
    FALLBACK_FROM_ON_DEVICE_UNAVAILABLE,
    FORCED_BY_USER_CLOUD_ONLY,
    EXPLICIT_USER_ACTION,
}

data class AiUsageEvent(
    val eventId: String,
    val timestamp: Long,
    val taskType: AiTaskType,
    val triggerSurface: AiTriggerSurface,
    val triggerMode: AiTriggerMode,
    val providerId: String,
    val providerLabel: String,
    val model: String,
    val executionMode: AiExecutionMode,
    val providerSelectionReason: AiProviderSelectionReason,
    val fallbackOccurred: Boolean,
    val fallbackReason: String?,
    val dataSensitivity: AiDataSensitivity,
    val payloadPolicy: PromptPayloadPolicy,
    val tokenCount: Int?,
    val success: Boolean,
    val failureReason: String?,
    val notifiedAt: Long? = null,
    val notificationBatchId: String? = null,
)

data class AiUsageSummary(
    val requests: Int = 0,
    val backgroundRequests: Int = 0,
    val successes: Int = 0,
    val fallbackCount: Int = 0,
    val tokens: Int = 0,
)
