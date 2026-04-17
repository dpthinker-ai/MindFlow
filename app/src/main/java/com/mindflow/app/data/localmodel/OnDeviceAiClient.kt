package com.mindflow.app.data.localmodel

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiConnectionResult
import com.mindflow.app.data.topic.AiFailureReason
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
    suspend fun generateLocalKnowledgeShape(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateLocalOpenQuestion(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateEditorRecall(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun extractTopic(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun extractTags(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun classifyFolder(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun polishContent(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun extractConceptGraphConcepts(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun canonicalizeConceptGraphConcepts(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateConceptGraphRelations(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
}

class LiteRtLmOnDeviceAiClient(
    private val context: Context,
) : OnDeviceAiClient {
    private data class CachedEngine(
        val modelPath: String,
        val backendName: String,
        val engine: Engine,
    )

    private val engineMutex = Mutex()
    private var cachedEngine: CachedEngine? = null
    private val cacheDir by lazy {
        File(context.cacheDir, "litert-lm").apply { mkdirs() }
    }

    override suspend fun testModel(settings: OnDeviceModelSettings): AiConnectionResult = withContext(Dispatchers.IO) {
        if (!settings.isReady) {
            return@withContext AiConnectionResult(
                isConfiguredCorrectly = false,
                message = "请先下载 Gemma 4 E4B 模型",
            )
        }
        when (val result = runPrompt(settings, "请只回复：本地模型就绪")) {
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
    ): AiChatResult = runPrompt(settings, FlowOnDevicePromptFactory.mainline(contextSummary))

    override suspend fun generateFlowSettledKnowledge(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, FlowOnDevicePromptFactory.settled(contextSummary))

    override suspend fun generateFlowBreakthroughGap(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, FlowOnDevicePromptFactory.gap(contextSummary))

    override suspend fun generateLocalKnowledgeShape(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, FlowOnDevicePromptFactory.knowledgeShape(contextSummary))

    override suspend fun generateLocalOpenQuestion(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, FlowOnDevicePromptFactory.openQuestion(contextSummary))

    override suspend fun generateEditorRecall(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, FlowOnDevicePromptFactory.editorRecall(contextSummary))

    override suspend fun extractTopic(
        settings: OnDeviceModelSettings,
        content: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.extractTopic(content))

    override suspend fun extractTags(
        settings: OnDeviceModelSettings,
        content: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.extractTags(content))

    override suspend fun classifyFolder(
        settings: OnDeviceModelSettings,
        content: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.classifyFolder(content))

    override suspend fun polishContent(
        settings: OnDeviceModelSettings,
        content: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.polish(content))

    override suspend fun extractConceptGraphConcepts(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.extractGraphConcepts(contextSummary))

    override suspend fun canonicalizeConceptGraphConcepts(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.canonicalizeGraphConcepts(contextSummary))

    override suspend fun generateConceptGraphRelations(
        settings: OnDeviceModelSettings,
        contextSummary: String,
    ): AiChatResult = runPrompt(settings, contextSummary)

    private suspend fun runPrompt(
        settings: OnDeviceModelSettings,
        prompt: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        val modelPath = settings.localModelPath.takeIf { it.isNotBlank() && File(it).exists() }
            ?: return@withContext AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "本地模型还没准备好",
            )

        engineMutex.withLock {
            val engine = runCatching { acquireEngine(modelPath) }.getOrElse { error ->
                return@withLock AiChatResult.Failure(
                    reason = AiFailureReason.OTHER,
                    message = "本地模型初始化失败：${error.message ?: "请检查模型文件或切换模型后重试"}",
                )
            }

            runCatching {
                engine.createConversation(defaultConversationConfig()).use { conversation ->
                    conversation.sendMessage(prompt, emptyMap()).toString().trim()
                }
            }.fold(
                onSuccess = { content ->
                    if (content.isBlank()) {
                        AiChatResult.Failure(
                            reason = AiFailureReason.OTHER,
                            message = "本地模型没有返回可用内容",
                        )
                    } else {
                        AiChatResult.Success(content = content)
                    }
                },
                onFailure = { error ->
                    AiChatResult.Failure(
                        reason = AiFailureReason.OTHER,
                        message = "本地模型推理失败：${error.message ?: "请稍后再试"}",
                    )
                },
            )
        }
    }

    private fun acquireEngine(modelPath: String): Engine {
        cachedEngine?.takeIf { it.modelPath == modelPath }?.let { return it.engine }
        cachedEngine?.engine?.close()

        val backendCandidates = listOf(
            "gpu" to Backend.GPU(),
            "cpu" to Backend.CPU(),
        )
        var lastError: Throwable? = null
        for ((backendName, backend) in backendCandidates) {
            try {
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        maxNumTokens = 384,
                        cacheDir = cacheDir.absolutePath,
                    )
                )
                engine.initialize()
                cachedEngine = CachedEngine(
                    modelPath = modelPath,
                    backendName = backendName,
                    engine = engine,
                )
                return engine
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("无法初始化本地模型引擎")
    }

    private fun defaultConversationConfig(): ConversationConfig = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK = 40,
            topP = 0.95,
            temperature = 0.45,
            seed = 0,
        )
    )
}
