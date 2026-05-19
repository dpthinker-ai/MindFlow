package com.mindflow.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.topic.AiChatResult
import org.junit.Test

class AiMediaTaskContractTest {
    @Test
    fun mediaTaskInputsKeepRawLocalFilePaths() {
        val audio = AiTaskInput.AudioFile(
            path = "/data/user/0/com.mindflow.app/files/captures/voice/sample.m4a",
            mimeType = "audio/mp4",
            localeHint = "zh-CN",
        )
        val image = AiTaskInput.ImageFile(
            path = "/data/user/0/com.mindflow.app/files/captures/images/sample.jpg",
            mimeType = "image/jpeg",
            userNote = "这是一张会议白板",
        )

        assertThat(audio.path).endsWith("sample.m4a")
        assertThat(audio.localeHint).isEqualTo("zh-CN")
        assertThat(image.path).endsWith("sample.jpg")
        assertThat(image.userNote).contains("会议白板")
    }

    @Test
    fun mediaTaskPayloadsParseStructuredJson() {
        val transcription = AiChatResult.Success(
            """{"transcript":"今天讨论图片识别链路","language":"zh-CN","topic":"图片识别链路","confidence":0.91}""",
        ).toPayloadOrNull(AiTaskType.TRANSCRIBE_AUDIO) as AiTaskPayload.AudioTranscription

        assertThat(transcription.transcript).contains("图片识别")
        assertThat(transcription.topic).isEmpty()
        assertThat(transcription.confidence).isWithin(0.001f).of(0.91f)

        val translation = AiChatResult.Success(
            """{"translatedText":"Discuss the image understanding flow.","sourceLanguage":"zh-CN","targetLanguage":"en","confidence":0.88}""",
        ).toPayloadOrNull(AiTaskType.TRANSLATE_AUDIO) as AiTaskPayload.AudioTranslation

        assertThat(translation.targetLanguage).isEqualTo("en")
        assertThat(translation.translatedText).contains("image understanding")

        val image = AiChatResult.Success(
            """{"summary":"一张包含流程图的白板照片","imageType":"whiteboard","extractedText":"Gemma 4 -> 本地记录","objects":["白板","流程图"],"confidence":0.86}""",
        ).toPayloadOrNull(AiTaskType.UNDERSTAND_IMAGE) as AiTaskPayload.ImageUnderstanding

        assertThat(image.summary).contains("白板")
        assertThat(image.imageType).isEqualTo("whiteboard")
        assertThat(image.extractedText).contains("Gemma 4")
        assertThat(image.objects).containsExactly("白板", "流程图").inOrder()
    }

    @Test
    fun audioTranscriptionDropsCurrentPromptLeakage() {
        val transcription = AiChatResult.Success(
            """{"transcript":"今天继续推进编译方案。你在做语音转写。目标：把录音中的真实说话内容逐字转成可编辑文本。","language":"zh-CN","confidence":0.81}""",
        ).toPayloadOrNull(AiTaskType.TRANSCRIBE_AUDIO) as AiTaskPayload.AudioTranscription

        assertThat(transcription.transcript).isEqualTo("今天继续推进编译方案。")
        assertThat(transcription.topic).isEmpty()
        assertThat(transcription.confidence).isWithin(0.001f).of(0.81f)
    }

    @Test
    fun noteInsightPayloadParsesStructuredJson() {
        val insight = AiChatResult.Success(
            """{"summary":"正文强调阅读页应优先完整呈现原文。","keyPoints":["取消手动刷新入口","洞察后台生成并持久化"]}""",
        ).toPayloadOrNull(AiTaskType.SUMMARIZE_NOTE) as AiTaskPayload.NoteInsight

        assertThat(insight.summary).contains("完整呈现")
        assertThat(insight.keyPoints).containsExactly("取消手动刷新入口", "洞察后台生成并持久化").inOrder()
    }
}
