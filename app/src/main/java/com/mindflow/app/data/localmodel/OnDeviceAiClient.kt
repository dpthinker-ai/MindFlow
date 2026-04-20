package com.mindflow.app.data.localmodel

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.reviewchat.ReviewChatOnDeviceRequest
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
    suspend fun generateReviewChatReply(settings: OnDeviceModelSettings, request: ReviewChatOnDeviceRequest): AiChatResult
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
    private companion object {
        // Gemma 4 E4B supports a much larger architectural context window.
        // This value is the mobile runtime budget we hand to LiteRT for
        // input + output tokens, not the model card maximum.
        const val RUNTIME_MAX_TOKENS = 2048
    }

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

    override suspend fun generateReviewChatReply(
        settings: OnDeviceModelSettings,
        request: ReviewChatOnDeviceRequest,
    ): AiChatResult = runReviewChatPrompt(settings, request)

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
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.generateGraphRelations(contextSummary))

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
            val cachedEngine = runCatching { acquireEngine(modelPath) }.getOrElse { error ->
                return@withLock AiChatResult.Failure(
                    reason = AiFailureReason.OTHER,
                    message = "本地模型初始化失败：${error.message ?: "请检查模型文件或切换模型后重试"}",
                )
            }

            runCatching {
                cachedEngine.engine.createConversation(defaultConversationConfig()).use { conversation ->
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

    private suspend fun runReviewChatPrompt(
        settings: OnDeviceModelSettings,
        request: ReviewChatOnDeviceRequest,
    ): AiChatResult = withContext(Dispatchers.IO) {
        val modelPath = settings.localModelPath.takeIf { it.isNotBlank() && File(it).exists() }
            ?: return@withContext AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "本地模型还没准备好",
            )

        engineMutex.withLock {
            runCatching {
                val engine = acquireEngine(modelPath)
                engine.engine.createConversation(
                    reviewChatConversationConfig(engine.backendName)
                ).use { conversation ->
                    conversation.sendMessage(request.prompt, emptyMap()).toString().trim() to engine.backendName
                }
            }.recoverCatching { error ->
                if (!shouldRetryReviewChatOnCpu(error)) throw error
                invalidateEngine()
                val engine = acquireEngine(
                    modelPath = modelPath,
                    forcedBackendName = "cpu",
                )
                engine.engine.createConversation(
                    reviewChatConversationConfig(engine.backendName)
                ).use { conversation ->
                    conversation.sendMessage(request.prompt, emptyMap()).toString().trim() to engine.backendName
                }
            }.fold(
                onSuccess = { (content, backendName) ->
                    if (!isUsableReviewChatContent(content)) {
                        AiChatResult.Failure(
                            reason = AiFailureReason.OTHER,
                            message = "本地模型没有返回可用回答",
                        )
                    } else {
                        AiChatResult.Success(
                            content = content,
                        )
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

    private fun acquireEngine(
        modelPath: String,
        forcedBackendName: String? = null,
    ): CachedEngine {
        cachedEngine
            ?.takeIf {
                it.modelPath == modelPath &&
                    (forcedBackendName == null || it.backendName == forcedBackendName)
            }
            ?.let { return it }

        invalidateEngine()

        var lastError: Throwable? = null
        for ((backendName, backend) in backendCandidates(forcedBackendName)) {
            try {
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        maxNumTokens = RUNTIME_MAX_TOKENS,
                        cacheDir = cacheDir.absolutePath,
                    )
                )
                engine.initialize()
                val cached = CachedEngine(
                    modelPath = modelPath,
                    backendName = backendName,
                    engine = engine,
                )
                cachedEngine = cached
                return cached
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("无法初始化本地模型引擎")
    }

    private fun backendCandidates(
        forcedBackendName: String? = null,
    ): List<Pair<String, Backend>> {
        val candidates = listOf(
            "gpu" to Backend.GPU(),
            "cpu" to Backend.CPU(),
        )
        return if (forcedBackendName == null) candidates else candidates.filter { it.first == forcedBackendName }
    }

    private fun shouldRetryReviewChatOnCpu(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return listOf(
            "opencl",
            "gpu",
            "delegate",
            "cdsprpc",
        ).any(message::contains)
    }

    private fun invalidateEngine() {
        cachedEngine?.engine?.close()
        cachedEngine = null
    }

    private fun isUsableReviewChatContent(content: String): Boolean {
        val normalized = content
            .replace(Regex("[\\p{Punct}\\p{P}\\s]+"), "")
            .trim()
        return normalized.length >= 4
    }

    private fun defaultConversationConfig(): ConversationConfig = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK = 40,
            topP = 0.95,
            temperature = 0.45,
            seed = 0,
        )
    )

    private fun reviewChatConversationConfig(
        backendName: String,
    ): ConversationConfig = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK = 64,
            topP = 0.95,
            temperature = 0.9,
            seed = 0,
        )
    )
}
