package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiDataSensitivityClassifierTest {
    @Test
    fun highSensitivityDetectsCredentialsAndIdentityData() {
        val sensitivity = AiDataSensitivityClassifier.classify(
            AiTaskInput.NoteText("身份证 110101199003071234，银行卡 6222020202020202，密码 pass123"),
        )

        assertThat(sensitivity).isEqualTo(AiDataSensitivity.HIGH)
    }

    @Test
    fun metadataTasksStayLowSensitivity() {
        val sensitivity = AiDataSensitivityClassifier.classify(
            AiTaskInput.NoteText("给这条记录提取三个标签"),
            payloadPolicy = PromptPayloadPolicy.SELECTED_SNIPPETS,
        )

        assertThat(sensitivity).isEqualTo(AiDataSensitivity.LOW)
    }
}
