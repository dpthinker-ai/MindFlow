package com.mindflow.app.data.ai

data class CloudUsageBudgetDecision(
    val allowed: Boolean,
    val reason: String? = null,
)

class CloudUsageBudgetGuard(
    private val runtimeSettings: AiRuntimeSettings,
) {
    fun canUseBackgroundCloud(
        requestsToday: Int,
        tokensToday: Int,
    ): CloudUsageBudgetDecision =
        canUseCloud(
            triggerMode = AiTriggerMode.BACKGROUND_AUTOMATION,
            requestsToday = requestsToday,
            tokensToday = tokensToday,
        )

    fun canUseCloud(
        triggerMode: AiTriggerMode,
        requestsToday: Int,
        tokensToday: Int,
    ): CloudUsageBudgetDecision {
        if (triggerMode == AiTriggerMode.FOREGROUND_USER_ACTION) {
            return CloudUsageBudgetDecision(allowed = true)
        }
        if (runtimeSettings.dailyBackgroundCloudRequestLimit >= 0 &&
            requestsToday >= runtimeSettings.dailyBackgroundCloudRequestLimit
        ) {
            return CloudUsageBudgetDecision(allowed = false, reason = "budget_blocked")
        }
        if (runtimeSettings.dailyBackgroundTokenSoftLimit >= 0 &&
            tokensToday >= runtimeSettings.dailyBackgroundTokenSoftLimit
        ) {
            return CloudUsageBudgetDecision(allowed = false, reason = "budget_blocked")
        }
        return CloudUsageBudgetDecision(allowed = true)
    }
}
