package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudUsageBudgetGuardTest {
    @Test
    fun backgroundBudgetBlocksWhenDailyRequestsReachLimit() {
        val decision = CloudUsageBudgetGuard(
            runtimeSettings = AiRuntimeSettings(dailyBackgroundCloudRequestLimit = 1),
        ).canUseBackgroundCloud(requestsToday = 1, tokensToday = 0)

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("budget_blocked")
    }

    @Test
    fun foregroundCloudIsNotBlockedByBackgroundBudget() {
        val decision = CloudUsageBudgetGuard(
            runtimeSettings = AiRuntimeSettings(dailyBackgroundCloudRequestLimit = 1),
        ).canUseCloud(
            triggerMode = AiTriggerMode.FOREGROUND_USER_ACTION,
            requestsToday = 10,
            tokensToday = 100_000,
        )

        assertThat(decision.allowed).isTrue()
    }
}
