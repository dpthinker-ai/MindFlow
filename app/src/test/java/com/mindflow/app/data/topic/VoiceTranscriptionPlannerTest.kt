package com.mindflow.app.data.topic

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiProvider
import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskProvider
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import com.mindflow.app.data.ai.AiTaskType
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoiceTranscriptionPlannerTest {
    @Test
    fun transcribeRoutesAudioFileToOnDeviceProvider() {
        val audio = File.createTempFile("mindflow-voice", ".wav").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val capturedPaths = mutableListOf<String>()
        val capturedMimeTypes = mutableListOf<String?>()
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = object : AiTaskProvider {
                @Suppress("UNCHECKED_CAST")
                override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
                    assertThat(request.type).isEqualTo(AiTaskType.TRANSCRIBE_AUDIO)
                    assertThat(request.automaticPreference.name).isEqualTo("PREFER_ON_DEVICE")
                    assertThat(request.allowProviderFallback).isFalse()
                    val input = request.input as AiTaskInput.AudioFile
                    capturedPaths += input.path
                    capturedMimeTypes += input.mimeType
                    return AiTaskPayload.AudioTranscription(
                        transcript = "今天确认语音转写链路",
                        language = "zh-CN",
                        topic = "语音转写链路",
                        confidence = 0.9f,
                    ) as T
                }
            },
            cloudProvider = object : AiTaskProvider {
                override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
                    error("cloud provider should not be used for local voice transcription")
                }
            },
        )

        lateinit var result: VoiceTranscriptionResult
        runTest {
            result = VoiceTranscriptionPlanner(router).transcribe(audio.absolutePath)
        }

        assertThat(result).isInstanceOf(VoiceTranscriptionResult.Success::class.java)
        val success = result as VoiceTranscriptionResult.Success
        assertThat(success.transcript).isEqualTo("今天确认语音转写链路")
        assertThat(success.topic).isEmpty()
        assertThat(success.provider).isEqualTo(AiProvider.ON_DEVICE)
        assertThat(capturedPaths).containsExactly(audio.absolutePath)
        assertThat(capturedMimeTypes).containsExactly("audio/wav")
    }

    @Test
    fun transcribeRejectsMissingAudioFile() {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = emptyProvider(),
            cloudProvider = emptyProvider(),
        )

        lateinit var result: VoiceTranscriptionResult
        runTest {
            result = VoiceTranscriptionPlanner(router).transcribe("/tmp/not-found.m4a")
        }

        assertThat(result).isEqualTo(VoiceTranscriptionResult.Failure("录音文件不存在，无法转写"))
    }

    private fun emptyProvider(): AiTaskProvider =
        object : AiTaskProvider {
            override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? = null
        }
}
