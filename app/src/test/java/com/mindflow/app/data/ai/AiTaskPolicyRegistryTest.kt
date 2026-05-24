package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiTaskPolicyRegistryTest {
    @Test
    fun foregroundPolishPrefersCloudAndUsesToastNotice() {
        val policy = AiTaskPolicyRegistry.policyFor(AiTaskType.POLISH_CONTENT)

        assertThat(policy.providerOrder).containsExactly(AiProvider.CLOUD, AiProvider.ON_DEVICE).inOrder()
        assertThat(policy.noticeMode).isEqualTo(AiCloudNoticeMode.TOAST)
        assertThat(policy.payloadPolicy).isEqualTo(PromptPayloadPolicy.SINGLE_NOTE_EXCERPT)
    }

    @Test
    fun mediaTasksRemainOnDeviceOnly() {
        assertThat(AiTaskPolicyRegistry.policyFor(AiTaskType.TRANSCRIBE_AUDIO).providerOrder)
            .containsExactly(AiProvider.ON_DEVICE)
        assertThat(AiTaskPolicyRegistry.policyFor(AiTaskType.UNDERSTAND_IMAGE).providerOrder)
            .containsExactly(AiProvider.ON_DEVICE)
    }

    @Test
    fun backgroundInsightAllowsAggregatedCloudFallbackWithExcerptPolicy() {
        val policy = AiTaskPolicyRegistry.policyFor(AiTaskType.SUMMARIZE_NOTE)

        assertThat(policy.providerOrder).containsExactly(AiProvider.ON_DEVICE, AiProvider.CLOUD).inOrder()
        assertThat(policy.allowBackgroundCloud).isTrue()
        assertThat(policy.noticeMode).isEqualTo(AiCloudNoticeMode.AGGREGATED)
        assertThat(policy.payloadPolicy).isEqualTo(PromptPayloadPolicy.SINGLE_NOTE_EXCERPT)
    }
}
