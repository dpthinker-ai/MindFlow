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

class ImageUnderstandingPlannerTest {
    @Test
    fun understandRoutesImageFileToOnDeviceProvider() = runTest {
        val image = File.createTempFile("mindflow-image", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val capturedPaths = mutableListOf<String>()
        val capturedNotes = mutableListOf<String>()
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = object : AiTaskProvider {
                @Suppress("UNCHECKED_CAST")
                override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
                    assertThat(request.type).isEqualTo(AiTaskType.UNDERSTAND_IMAGE)
                    assertThat(request.automaticPreference.name).isEqualTo("PREFER_ON_DEVICE")
                    assertThat(request.allowProviderFallback).isFalse()
                    val input = request.input as AiTaskInput.ImageFile
                    capturedPaths += input.path
                    capturedNotes += input.userNote
                    assertThat(input.mimeType).isEqualTo("image/jpeg")
                    return AiTaskPayload.ImageUnderstanding(
                        summary = "白板照片包含三步产品流程",
                        imageType = "whiteboard",
                        extractedText = "输入 -> 解析 -> 洞察",
                        objects = listOf("白板", "流程图"),
                        confidence = 0.86f,
                    ) as T
                }
            },
            cloudProvider = object : AiTaskProvider {
                override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? {
                    error("cloud provider should not be used for local image understanding")
                }
            },
        )

        val result = ImageUnderstandingPlanner(router).understand(
            imagePath = image.absolutePath,
            userNote = "会议白板",
        )

        assertThat(result).isInstanceOf(ImageUnderstandingResult.Success::class.java)
        val success = result as ImageUnderstandingResult.Success
        assertThat(success.summary).contains("白板")
        assertThat(success.extractedText).contains("输入")
        assertThat(success.objects).containsExactly("白板", "流程图").inOrder()
        assertThat(success.provider).isEqualTo(AiProvider.ON_DEVICE)
        assertThat(capturedPaths).containsExactly(image.absolutePath)
        assertThat(capturedNotes).containsExactly("会议白板")
    }

    @Test
    fun understandRejectsMissingImageFile() = runTest {
        val router = AiTaskRouter(
            resolveMode = { AiExecutionMode.AUTOMATIC },
            onDeviceProvider = emptyProvider(),
            cloudProvider = emptyProvider(),
        )

        val result = ImageUnderstandingPlanner(router).understand("/tmp/not-found.jpg")

        assertThat(result).isEqualTo(ImageUnderstandingResult.Failure("图片文件不存在，无法识别"))
    }

    private fun emptyProvider(): AiTaskProvider =
        object : AiTaskProvider {
            override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? = null
        }
}
