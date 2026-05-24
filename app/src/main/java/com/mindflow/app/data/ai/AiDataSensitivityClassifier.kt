package com.mindflow.app.data.ai

object AiDataSensitivityClassifier {
    private val highSensitivityPatterns = listOf(
        Regex("""\b\d{17}[\dXx]\b"""),
        Regex("""\b\d{12,19}\b"""),
        Regex("""(?i)(password|passwd|secret|token|api[_-]?key)"""),
        Regex("""(密码|身份证|银行卡|账号|账户|凭证|密钥|验证码|手机号|电话)"""),
    )

    fun classify(
        input: AiTaskInput,
        payloadPolicy: PromptPayloadPolicy = PromptPayloadPolicy.SINGLE_NOTE_EXCERPT,
    ): AiDataSensitivity {
        if (payloadPolicy == PromptPayloadPolicy.LOCAL_FILE_ONLY ||
            payloadPolicy == PromptPayloadPolicy.FULL_NOTE_EXPLICIT
        ) {
            return AiDataSensitivity.HIGH
        }
        if (payloadPolicy == PromptPayloadPolicy.METADATA_ONLY ||
            payloadPolicy == PromptPayloadPolicy.SELECTED_SNIPPETS
        ) {
            val text = input.textForSensitivity()
            return if (highSensitivityPatterns.any { it.containsMatchIn(text) }) {
                AiDataSensitivity.HIGH
            } else {
                AiDataSensitivity.LOW
            }
        }

        val text = input.textForSensitivity()
        return if (highSensitivityPatterns.any { it.containsMatchIn(text) }) {
            AiDataSensitivity.HIGH
        } else {
            AiDataSensitivity.MEDIUM
        }
    }

    private fun AiTaskInput.textForSensitivity(): String = when (this) {
        is AiTaskInput.NoteText -> content
        is AiTaskInput.TitleText -> "$title\n$content"
        is AiTaskInput.GraphContext -> contextSummary
        is AiTaskInput.AudioFile -> path
        is AiTaskInput.ImageFile -> "$path\n$userNote"
    }
}
