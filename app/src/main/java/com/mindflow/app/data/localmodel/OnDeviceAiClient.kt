package com.mindflow.app.data.localmodel

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiConnectionResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface OnDeviceAiClient {
    suspend fun testModel(settings: OnDeviceModelSettings): AiConnectionResult
    suspend fun generateFlowMainline(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateFlowSettledKnowledge(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateFlowBreakthroughGap(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateEditorRecall(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
}

class MediaPipeOnDeviceAiClient(
    private val context: Context,
) : OnDeviceAiClient {
    private val engineMutex = Mutex()
    private var cachedModelPath: String? = null
    private var cachedEngine: LlmInference? = null

    override suspend fun testModel(settings: OnDeviceModelSettings): AiConnectionResult = withContext(Dispatchers.IO) {
        if (!settings.isReady) {
            return@withContext AiConnectionResult(
                isConfiguredCorrectly = false,
                message = "请先下载 Gemma 4 E4B 模型",
            )
        }
        val result = runPrompt(
            settings = settings,
            prompt = "请只回复：本地模型就绪",
        )
        when (result) {
            is AiChatResult.Success -> AiConnectionResult(
                isConfiguredCorrectly = true,
                message = "本地模型可用，已准备好为 Flow 提供端侧判断",
            )
            is AiChatResult.Failure -> AiConnectionResult(
                isConfiguredCorrectly = false,
                message = result.message,
            )
        }
    }

    override suspend fun generateFlowMainline(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(
        settings = settings,
        prompt = FlowOnDevicePromptFactory.mainline(contextSummary),
    )

    override suspend fun generateFlowSettledKnowledge(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(
        settings = settings,
        prompt = FlowOnDevicePromptFactory.settled(contextSummary),
    )

    override suspend fun generateFlowBreakthroughGap(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(
        settings = settings,
        prompt = FlowOnDevicePromptFactory.gap(contextSummary),
    )

    override suspend fun generateEditorRecall(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(
        settings = settings,
        prompt = FlowOnDevicePromptFactory.editorRecall(contextSummary),
    )

    private suspend fun runPrompt(
        settings: OnDeviceModelSettings,
        prompt: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        val modelPath = settings.localModelPath.takeIf { it.isNotBlank() && File(it).exists() }
            ?: return@withContext AiChatResult.Failure(
                reason = com.mindflow.app.data.topic.AiFailureReason.CONFIG,
                message = "本地模型还没准备好",
            )

        return@withContext engineMutex.withLock {
            val engine = runCatching { acquireEngine(modelPath) }
                .getOrElse {
                    return@withLock AiChatResult.Failure(
                        reason = com.mindflow.app.data.topic.AiFailureReason.OTHER,
                        message = "本地模型初始化失败：${it.message ?: "请检查模型文件"}",
                    )
                }

            runCatching { engine.generateResponse(prompt) }
                .fold(
                    onSuccess = { content ->
                        if (content.isBlank()) {
                            AiChatResult.Failure(
                                reason = com.mindflow.app.data.topic.AiFailureReason.OTHER,
                                message = "本地模型没有返回可用内容",
                            )
                        } else {
                            AiChatResult.Success(content = content)
                        }
                    },
                    onFailure = {
                        AiChatResult.Failure(
                            reason = com.mindflow.app.data.topic.AiFailureReason.OTHER,
                            message = "本地模型推理失败：${it.message ?: "请稍后再试"}",
                        )
                    },
                )
        }
    }

    private fun acquireEngine(modelPath: String): LlmInference {
        val cached = cachedEngine
        if (cached != null && cachedModelPath == modelPath) return cached

        cached?.close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(384)
            .setMaxTopK(40)
            .setPreferredBackend(LlmInference.Backend.DEFAULT)
            .build()
        return LlmInference.createFromOptions(context, options).also { created ->
            cachedModelPath = modelPath
            cachedEngine = created
        }
    }
}
