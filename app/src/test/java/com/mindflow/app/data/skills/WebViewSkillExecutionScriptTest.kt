package com.mindflow.app.data.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebViewSkillExecutionScriptTest {
    @Test
    fun build_doesNotInlineInvocationPayload() {
        val largePayload = "record-content".repeat(10_000)

        val script = WebViewSkillExecutionScript.build()

        assertThat(script.length).isLessThan(2_000)
        assertThat(script).contains("MindFlowSkillInput.getInvocationData()")
        assertThat(script).contains("MindFlowSkillInput.getSecret")
        assertThat(script).doesNotContain(largePayload.take(1_000))
    }
}
