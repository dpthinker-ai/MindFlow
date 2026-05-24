package com.mindflow.app.data.ai

enum class CloudNotificationMode {
    LOW_FREQUENCY,
    IN_APP_ONLY,
    OFF,
    ;

    companion object {
        fun fromRaw(raw: String?): CloudNotificationMode =
            raw?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { candidate -> runCatching { valueOf(candidate) }.getOrNull() }
                ?: LOW_FREQUENCY
    }
}

data class AiRuntimeSettings(
    val executionMode: AiExecutionMode = AiExecutionMode.AUTOMATIC,
    val cloudAllowedForInteractive: Boolean = true,
    val cloudAllowedForBackground: Boolean = true,
    val notifyOnCloudUse: Boolean = true,
    val backgroundCloudNotificationMode: CloudNotificationMode = CloudNotificationMode.LOW_FREQUENCY,
    val dailyBackgroundCloudRequestLimit: Int = 30,
    val dailyBackgroundTokenSoftLimit: Int = 30_000,
)
