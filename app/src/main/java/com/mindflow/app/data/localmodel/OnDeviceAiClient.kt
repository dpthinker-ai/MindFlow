package com.mindflow.app.data.localmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.reviewchat.ReviewChatOnDeviceRequest
import com.mindflow.app.data.reviewchat.ReviewChatOnDeviceTraceEvent
import com.mindflow.app.data.topic.AiChatResult
import com.mindflow.app.data.topic.AiConnectionResult
import com.mindflow.app.data.topic.AiFailureReason
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

internal const val LITERT_LM_DEFAULT_RUNTIME_MAX_TOKENS = 2048
internal const val LITERT_LM_EMULATOR_RUNTIME_MAX_TOKENS = 512
internal const val LITERT_LM_EMULATOR_UNSAFE_MODEL_BYTES = 2L * 1024L * 1024L * 1024L
internal const val LITERT_LM_IMAGE_MAX_SIDE = 1024

interface OnDeviceAiClient {
    suspend fun testModel(settings: OnDeviceModelSettings): AiConnectionResult
    suspend fun warmUp(settings: OnDeviceModelSettings): AiConnectionResult
    suspend fun generateFlowMainline(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateFlowSettledKnowledge(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateFlowBreakthroughGap(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateLocalKnowledgeShape(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateLocalOpenQuestion(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateEditorRecall(settings: OnDeviceModelSettings, contextSummary: String): AiChatResult
    suspend fun generateReviewChatReply(settings: OnDeviceModelSettings, request: ReviewChatOnDeviceRequest): AiChatResult
    fun streamReviewChatReply(settings: OnDeviceModelSettings, request: ReviewChatOnDeviceRequest): Flow<String>
    suspend fun extractTopic(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun extractTags(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun classifyFolder(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun polishTitle(settings: OnDeviceModelSettings, title: String, content: String): AiChatResult
    suspend fun summarizeNote(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun polishContent(settings: OnDeviceModelSettings, content: String): AiChatResult
    suspend fun transcribeAudio(settings: OnDeviceModelSettings, audioPath: String, localeHint: String?): AiChatResult
    suspend fun translateAudio(settings: OnDeviceModelSettings, audioPath: String, targetLanguage: String): AiChatResult
    suspend fun understandImage(settings: OnDeviceModelSettings, imagePath: String, userNote: String): AiChatResult
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
        val audioEnabled: Boolean,
        val imageEnabled: Boolean,
        val engine: Engine,
    )

    private data class LiteRtLmEngineBackend(
        val name: String,
        val backend: Backend,
        val visionBackend: Backend? = null,
    )

    private val engineMutex = Mutex()
    private var cachedEngine: CachedEngine? = null
    private val legacyDiskCacheDir by lazy {
        File(context.cacheDir, "litert-lm")
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

    override suspend fun warmUp(settings: OnDeviceModelSettings): AiConnectionResult = withContext(Dispatchers.IO) {
        val modelPath = settings.localModelPath.takeIf { settings.isReady && it.isNotBlank() && File(it).exists() }
            ?: return@withContext AiConnectionResult(
                isConfiguredCorrectly = false,
                message = "请先下载 Gemma 4 E4B 模型",
            )
        liteRtLmRuntimeSafetyFailureMessage(File(modelPath))?.let { message ->
            return@withContext AiConnectionResult(
                isConfiguredCorrectly = false,
                message = message,
            )
        }

        engineMutex.withLock {
            runCatching {
                acquireEngine(modelPath = modelPath)
            }.fold(
                onSuccess = { engine ->
                    AiConnectionResult(
                        isConfiguredCorrectly = true,
                        message = "本地模型已预热（${engine.backendName.uppercase()}），后续聊天会复用内存中的 LiteRT 引擎",
                    )
                },
                onFailure = { error ->
                    AiConnectionResult(
                        isConfiguredCorrectly = false,
                        message = "本地模型预热失败：${error.message ?: "请检查模型文件或稍后重试"}",
                    )
                },
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

    override fun streamReviewChatReply(
        settings: OnDeviceModelSettings,
        request: ReviewChatOnDeviceRequest,
    ): Flow<String> = callbackFlow {
        val modelPath = settings.localModelPath.takeIf { it.isNotBlank() && File(it).exists() }
            ?: run {
                close(
                    IllegalStateException("本地模型还没准备好")
                )
                return@callbackFlow
            }
        liteRtLmRuntimeSafetyFailureMessage(File(modelPath))?.let { message ->
            close(IllegalStateException(message))
            return@callbackFlow
        }

        val job = launch(Dispatchers.IO) {
            runCatching {
                streamReviewChatPrompt(
                    modelPath = modelPath,
                    request = request,
                    emitChunk = { chunk ->
                        trySend(chunk).getOrThrow()
                    },
                )
            }.onSuccess {
                close()
            }.onFailure { error ->
                close(error)
            }
        }
        awaitClose { job.cancel() }
    }

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

    override suspend fun polishTitle(
        settings: OnDeviceModelSettings,
        title: String,
        content: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.polishTitle(title, content))

    override suspend fun summarizeNote(
        settings: OnDeviceModelSettings,
        content: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.summarizeNote(content))

    override suspend fun polishContent(
        settings: OnDeviceModelSettings,
        content: String,
    ): AiChatResult = runPrompt(settings, GemmaTaskPromptFactory.polish(content))

    override suspend fun transcribeAudio(
        settings: OnDeviceModelSettings,
        audioPath: String,
        localeHint: String?,
    ): AiChatResult = runAudioPrompt(
        settings = settings,
        audioPath = audioPath,
        prompt = GemmaTaskPromptFactory.transcribeAudio(audioPath, localeHint),
    )

    override suspend fun translateAudio(
        settings: OnDeviceModelSettings,
        audioPath: String,
        targetLanguage: String,
    ): AiChatResult = runAudioPrompt(
        settings = settings,
        audioPath = audioPath,
        prompt = GemmaTaskPromptFactory.translateAudio(audioPath, targetLanguage),
    )

    override suspend fun understandImage(
        settings: OnDeviceModelSettings,
        imagePath: String,
        userNote: String,
    ): AiChatResult = runImagePrompt(
        settings = settings,
        imagePath = imagePath,
        prompt = GemmaTaskPromptFactory.understandImage(userNote),
    )

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
        liteRtLmRuntimeSafetyFailureMessage(File(modelPath))?.let { message ->
            return@withContext AiChatResult.Failure(
                reason = AiFailureReason.OTHER,
                message = message,
            )
        }

        engineMutex.withLock {
            val cachedEngine = runCatching { acquireEngine(modelPath = modelPath) }.getOrElse { error ->
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

    private suspend fun runAudioPrompt(
        settings: OnDeviceModelSettings,
        audioPath: String,
        prompt: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        val modelPath = settings.localModelPath.takeIf { it.isNotBlank() && File(it).exists() }
            ?: return@withContext AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "本地模型还没准备好",
            )
        val audioFile = File(audioPath.trim())
        if (!audioFile.exists() || !audioFile.isFile || audioFile.length() <= 0L) {
            return@withContext AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "录音文件不存在，无法转写",
            )
        }
        liteRtLmRuntimeSafetyFailureMessage(File(modelPath))?.let { message ->
            return@withContext AiChatResult.Failure(
                reason = AiFailureReason.OTHER,
                message = message,
            )
        }

        engineMutex.withLock {
            val cachedEngine = runCatching { acquireEngine(modelPath = modelPath, audioEnabled = true) }.getOrElse { error ->
                return@withLock AiChatResult.Failure(
                    reason = AiFailureReason.OTHER,
                    message = "本地模型初始化失败：${error.message ?: "请检查模型文件或切换模型后重试"}",
                )
            }

            runCatching {
                cachedEngine.engine.createConversation(defaultConversationConfig()).use { conversation ->
                    conversation.sendMessage(
                        Contents.of(
                            Content.AudioBytes(audioFile.readBytes()),
                            Content.Text(prompt),
                        ),
                        emptyMap(),
                    ).toString().trim()
                }
            }.fold(
                onSuccess = { content ->
                    if (content.isBlank()) {
                        AiChatResult.Failure(
                            reason = AiFailureReason.OTHER,
                            message = "Gemma 4 没有返回可用转写内容",
                        )
                    } else {
                        AiChatResult.Success(content = content)
                    }
                },
                onFailure = { error ->
                    AiChatResult.Failure(
                        reason = AiFailureReason.OTHER,
                        message = "端侧语音转写失败：${error.message ?: "请确认当前 LiteRT-LM 模型和运行时支持音频输入"}",
                    )
                },
            )
        }
    }

    private suspend fun runImagePrompt(
        settings: OnDeviceModelSettings,
        imagePath: String,
        prompt: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        val modelPath = settings.localModelPath.takeIf { it.isNotBlank() && File(it).exists() }
            ?: return@withContext AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "本地模型还没准备好",
            )
        val imageFile = File(imagePath.trim())
        if (!imageFile.exists() || !imageFile.isFile || imageFile.length() <= 0L) {
            return@withContext AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "图片文件不存在，无法识别",
            )
        }
        liteRtLmRuntimeSafetyFailureMessage(File(modelPath))?.let { message ->
            return@withContext AiChatResult.Failure(
                reason = AiFailureReason.OTHER,
                message = message,
            )
        }
        val imageBytes = decodeImageAsPngBytes(imageFile)
            ?: return@withContext AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "图片解码失败，请换一张常见格式图片重试",
            )

        engineMutex.withLock {
            runCatching {
                sendImagePrompt(
                    modelPath = modelPath,
                    imageBytes = imageBytes,
                    prompt = prompt,
                )
            }.recoverCatching { error ->
                if (!shouldRetryOnCpu(error)) throw error
                invalidateEngine()
                sendImagePrompt(
                    modelPath = modelPath,
                    imageBytes = imageBytes,
                    prompt = prompt,
                    forcedBackendName = "cpu",
                )
            }.fold(
                onSuccess = { content ->
                    if (content.isBlank()) {
                        AiChatResult.Failure(
                            reason = AiFailureReason.OTHER,
                            message = "Gemma 4 没有返回可用图片理解内容",
                        )
                    } else {
                        AiChatResult.Success(content = content)
                    }
                },
                onFailure = { error ->
                    AiChatResult.Failure(
                        reason = AiFailureReason.OTHER,
                        message = "端侧图片理解失败：${error.message ?: "请确认当前模型和运行时支持图片输入"}",
                    )
                },
            )
        }
    }

    private fun sendImagePrompt(
        modelPath: String,
        imageBytes: ByteArray,
        prompt: String,
        forcedBackendName: String? = null,
    ): String {
        val cachedEngine = acquireEngine(
            modelPath = modelPath,
            forcedBackendName = forcedBackendName,
            imageEnabled = true,
        )
        return cachedEngine.engine.createConversation(defaultConversationConfig()).use { conversation ->
            conversation.sendMessage(
                Contents.of(
                    Content.ImageBytes(imageBytes),
                    Content.Text(prompt),
                ),
                emptyMap(),
            ).toString().trim()
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
        liteRtLmRuntimeSafetyFailureMessage(File(modelPath))?.let { message ->
            return@withContext AiChatResult.Failure(
                reason = AiFailureReason.OTHER,
                message = message,
            )
        }

        engineMutex.withLock {
            runCatching {
                val engine = acquireEngine(
                    modelPath = modelPath,
                    trace = request.trace,
                )
                val inferenceStartedAt = monotonicNow()
                request.trace?.invoke(ReviewChatOnDeviceTraceEvent.InferenceStarted(engine.backendName))
                engine.engine.createConversation(
                    reviewChatConversationConfig(
                        systemInstruction = request.systemInstruction,
                    )
                ).use { conversation ->
                    val content = conversation.sendMessage(request.prompt, request.extraContext).toString().trim()
                    request.trace?.invoke(
                        ReviewChatOnDeviceTraceEvent.InferenceFinished(
                            backendName = engine.backendName,
                            durationMs = elapsedMs(inferenceStartedAt),
                            outputChars = content.length,
                        )
                    )
                    content to engine.backendName
                }
            }.recoverCatching { error ->
                if (!shouldRetryReviewChatOnCpu(error)) throw error
                invalidateEngine()
                val engine = acquireEngine(
                    modelPath = modelPath,
                    forcedBackendName = "cpu",
                    trace = request.trace,
                )
                val inferenceStartedAt = monotonicNow()
                request.trace?.invoke(ReviewChatOnDeviceTraceEvent.InferenceStarted(engine.backendName))
                engine.engine.createConversation(
                    reviewChatConversationConfig(
                        systemInstruction = request.systemInstruction,
                    )
                ).use { conversation ->
                    val content = conversation.sendMessage(request.prompt, request.extraContext).toString().trim()
                    request.trace?.invoke(
                        ReviewChatOnDeviceTraceEvent.InferenceFinished(
                            backendName = engine.backendName,
                            durationMs = elapsedMs(inferenceStartedAt),
                            outputChars = content.length,
                        )
                    )
                    content to engine.backendName
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
                    request.trace?.invoke(
                        ReviewChatOnDeviceTraceEvent.InferenceFailed(
                            backendName = "unknown",
                            durationMs = 0L,
                            message = error.message ?: "推理失败",
                        )
                    )
                    AiChatResult.Failure(
                        reason = AiFailureReason.OTHER,
                        message = "本地模型推理失败：${error.message ?: "请稍后再试"}",
                    )
                },
            )
        }
    }

    private suspend fun streamReviewChatPrompt(
        modelPath: String,
        request: ReviewChatOnDeviceRequest,
        emitChunk: suspend (String) -> Unit,
    ) {
        engineMutex.withLock {
            var emittedAnyChunk = false
            try {
                val engine = acquireEngine(
                    modelPath = modelPath,
                    trace = request.trace,
                )
                emittedAnyChunk = streamReviewChatWithEngine(
                    engine = engine,
                    request = request,
                    emitChunk = emitChunk,
                )
            } catch (error: Throwable) {
                if (emittedAnyChunk || !shouldRetryReviewChatOnCpu(error)) {
                    throw error
                }
                invalidateEngine()
                val engine = acquireEngine(
                    modelPath = modelPath,
                    forcedBackendName = "cpu",
                    trace = request.trace,
                )
                streamReviewChatWithEngine(
                    engine = engine,
                    request = request,
                    emitChunk = emitChunk,
                )
            }
        }
    }

    private suspend fun streamReviewChatWithEngine(
        engine: CachedEngine,
        request: ReviewChatOnDeviceRequest,
        emitChunk: suspend (String) -> Unit,
    ): Boolean {
        var emittedAnyChunk = false
        var outputChars = 0
        val inferenceStartedAt = monotonicNow()
        engine.engine.createConversation(
            reviewChatConversationConfig(
                systemInstruction = request.systemInstruction,
            )
        ).use { conversation ->
            request.trace?.invoke(ReviewChatOnDeviceTraceEvent.InferenceStarted(engine.backendName))
            conversation.sendMessageAsync(request.prompt, request.extraContext).collect { message ->
                val chunk = message.toString()
                if (chunk.isBlank()) return@collect
                if (!emittedAnyChunk) {
                    request.trace?.invoke(
                        ReviewChatOnDeviceTraceEvent.FirstToken(
                            backendName = engine.backendName,
                            durationMs = elapsedMs(inferenceStartedAt),
                        )
                    )
                }
                emittedAnyChunk = true
                outputChars += chunk.length
                emitChunk(chunk)
            }
        }
        request.trace?.invoke(
            ReviewChatOnDeviceTraceEvent.InferenceFinished(
                backendName = engine.backendName,
                durationMs = elapsedMs(inferenceStartedAt),
                outputChars = outputChars,
            )
        )
        return emittedAnyChunk
    }

    private fun acquireEngine(
        modelPath: String,
        forcedBackendName: String? = null,
        audioEnabled: Boolean = false,
        imageEnabled: Boolean = false,
        trace: ((ReviewChatOnDeviceTraceEvent) -> Unit)? = null,
    ): CachedEngine {
        cachedEngine
            ?.takeIf {
                it.modelPath == modelPath &&
                    (forcedBackendName == null || it.backendName == forcedBackendName) &&
                    (!audioEnabled || it.audioEnabled) &&
                    (!imageEnabled || it.imageEnabled)
            }
            ?.let { cached ->
                trace?.invoke(
                    ReviewChatOnDeviceTraceEvent.ModelLoadFinished(
                        backendName = cached.backendName,
                        durationMs = 0L,
                        cached = true,
                    )
                )
                return cached
            }

        invalidateEngine()
        deleteLegacyLiteRtLmDiskCache(legacyDiskCacheDir)

        var lastError: Throwable? = null
        for (candidate in backendCandidates(forcedBackendName, imageEnabled)) {
            val startedAt = monotonicNow()
            trace?.invoke(
                ReviewChatOnDeviceTraceEvent.ModelLoadStarted(
                    backendName = candidate.name,
                    cached = false,
                )
            )
            try {
                val engine = Engine(
                    buildLiteRtLmTextEngineConfig(
                        modelPath = modelPath,
                        backend = candidate.backend,
                        visionBackend = candidate.visionBackend,
                        audioEnabled = audioEnabled,
                        maxNumTokens = liteRtLmRuntimeMaxTokens(),
                    )
                )
                engine.initialize()
                val cached = CachedEngine(
                    modelPath = modelPath,
                    backendName = candidate.name,
                    audioEnabled = audioEnabled,
                    imageEnabled = imageEnabled,
                    engine = engine,
                )
                cachedEngine = cached
                trace?.invoke(
                    ReviewChatOnDeviceTraceEvent.ModelLoadFinished(
                        backendName = candidate.name,
                        durationMs = elapsedMs(startedAt),
                        cached = false,
                    )
                )
                return cached
            } catch (error: Throwable) {
                lastError = error
                trace?.invoke(
                    ReviewChatOnDeviceTraceEvent.ModelLoadFailed(
                        backendName = candidate.name,
                        durationMs = elapsedMs(startedAt),
                        message = error.message ?: "初始化失败",
                    )
                )
            }
        }
        throw lastError ?: IllegalStateException("无法初始化本地模型引擎")
    }

    private fun monotonicNow(): Long = System.nanoTime()

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private fun backendCandidates(
        forcedBackendName: String? = null,
        imageEnabled: Boolean = false,
    ): List<LiteRtLmEngineBackend> =
        if (imageEnabled) {
            liteRtLmImageBackendCandidates(forcedBackendName).map { candidate ->
                LiteRtLmEngineBackend(
                    name = candidate.name,
                    backend = candidate.backend,
                    visionBackend = candidate.visionBackend,
                )
            }
        } else {
            liteRtLmTextBackendCandidates(forcedBackendName).map { (name, backend) ->
                LiteRtLmEngineBackend(name = name, backend = backend)
            }
        }

    private fun shouldRetryReviewChatOnCpu(error: Throwable): Boolean = shouldRetryOnCpu(error)

    private fun shouldRetryOnCpu(error: Throwable): Boolean {
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
        systemInstruction: String,
    ): ConversationConfig = ConversationConfig(
        systemInstruction = systemInstruction
            .takeIf { it.isNotBlank() }
            ?.let(Contents::of),
        samplerConfig = SamplerConfig(
            topK = 64,
            topP = 0.95,
            temperature = 0.9,
            seed = 0,
        )
    )
}

internal fun buildLiteRtLmTextEngineConfig(
    modelPath: String,
    backend: Backend,
    visionBackend: Backend? = null,
    audioEnabled: Boolean = false,
    maxNumTokens: Int,
): EngineConfig = EngineConfig(
    modelPath = modelPath,
    backend = backend,
    visionBackend = visionBackend,
    audioBackend = if (audioEnabled) Backend.CPU() else null,
    maxNumTokens = maxNumTokens,
    cacheDir = null,
)

internal data class LiteRtLmImageBackendCandidate(
    val name: String,
    val backend: Backend,
    val visionBackend: Backend,
)

internal fun deleteLegacyLiteRtLmDiskCache(cacheDir: File): Boolean =
    runCatching {
        cacheDir.exists() && cacheDir.deleteRecursively()
    }.getOrDefault(false)

internal fun liteRtLmRuntimeMaxTokens(
    deviceProfile: LocalInferenceDeviceProfile = LocalInferenceDeviceProfile.current(),
): Int =
    if (deviceProfile.isEmulator()) {
        LITERT_LM_EMULATOR_RUNTIME_MAX_TOKENS
    } else {
        LITERT_LM_DEFAULT_RUNTIME_MAX_TOKENS
    }

internal fun liteRtLmRuntimeSafetyFailureMessage(
    modelFile: File,
    deviceProfile: LocalInferenceDeviceProfile = LocalInferenceDeviceProfile.current(),
): String? {
    if (!deviceProfile.isEmulator()) return null
    if (modelFile.length() < LITERT_LM_EMULATOR_UNSAFE_MODEL_BYTES) return null
    return "模拟器 CPU 内存不足，暂不在模拟器初始化 3GB+ Gemma 4 模型，避免 LiteRT native 崩溃；请在真机上测试本地模型，或换用更小的端侧模型。"
}

internal data class LocalInferenceDeviceProfile(
    val fingerprint: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val product: String,
    val hardware: String,
) {
    companion object {
        fun current(): LocalInferenceDeviceProfile = LocalInferenceDeviceProfile(
            fingerprint = Build.FINGERPRINT.orEmpty(),
            model = Build.MODEL.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
        )
    }
}

internal fun liteRtLmTextBackendCandidates(
    forcedBackendName: String? = null,
    deviceProfile: LocalInferenceDeviceProfile = LocalInferenceDeviceProfile.current(),
): List<Pair<String, Backend>> {
    val candidates = if (deviceProfile.isEmulator()) {
        listOf("cpu" to Backend.CPU(1))
    } else {
        listOf(
            "gpu" to Backend.GPU(),
            "npu" to Backend.NPU(),
            "cpu" to Backend.CPU(),
        )
    }
    return if (forcedBackendName == null) candidates else candidates.filter { it.first == forcedBackendName }
}

internal fun liteRtLmImageBackendCandidates(
    forcedBackendName: String? = null,
    deviceProfile: LocalInferenceDeviceProfile = LocalInferenceDeviceProfile.current(),
): List<LiteRtLmImageBackendCandidate> {
    val candidates = if (deviceProfile.isEmulator()) {
        listOf(
            LiteRtLmImageBackendCandidate(
                name = "cpu",
                backend = Backend.CPU(1),
                visionBackend = Backend.CPU(1),
            )
        )
    } else {
        listOf(
            LiteRtLmImageBackendCandidate(
                name = "gpu",
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
            ),
            LiteRtLmImageBackendCandidate(
                name = "cpu",
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
            ),
        )
    }
    return if (forcedBackendName == null) candidates else candidates.filter { it.name == forcedBackendName }
}

internal fun calculateLiteRtLmImageSampleSize(
    width: Int,
    height: Int,
    maxSide: Int = LITERT_LM_IMAGE_MAX_SIDE,
): Int {
    if (width <= 0 || height <= 0 || maxSide <= 0) return 1
    var sampleSize = 1
    if (height > maxSide || width > maxSide) {
        val heightRatio = (height.toFloat() / maxSide.toFloat()).roundToInt()
        val widthRatio = (width.toFloat() / maxSide.toFloat()).roundToInt()
        sampleSize = max(heightRatio, widthRatio).coerceAtLeast(1)
    }
    return sampleSize
}

private fun decodeImageAsPngBytes(
    imageFile: File,
    maxSide: Int = LITERT_LM_IMAGE_MAX_SIDE,
): ByteArray? {
    val orientation = runCatching {
        ExifInterface(imageFile.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
    val sampleSize = calculateLiteRtLmImageSampleSize(
        width = bounds.outWidth,
        height = bounds.outHeight,
        maxSide = maxSide,
    )
    val bitmap = BitmapFactory.decodeFile(
        imageFile.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        },
    ) ?: return null
    val normalized = bitmap.rotateForExifOrientation(orientation)
    return ByteArrayOutputStream().use { output ->
        val compressed = normalized.compress(Bitmap.CompressFormat.PNG, 100, output)
        if (normalized !== bitmap) normalized.recycle()
        bitmap.recycle()
        if (!compressed) return null
        output.toByteArray()
    }
}

private fun Bitmap.rotateForExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1.0f, 1.0f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1.0f, 1.0f)
        }
        ExifInterface.ORIENTATION_NORMAL -> return this
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

internal fun LocalInferenceDeviceProfile.isEmulator(): Boolean {
    val fingerprint = fingerprint.lowercase()
    val model = model.lowercase()
    val manufacturer = manufacturer.lowercase()
    val brand = brand.lowercase()
    val device = device.lowercase()
    val product = product.lowercase()
    val hardware = hardware.lowercase()

    return fingerprint.startsWith("generic") ||
        model.contains("sdk") ||
        model.contains("emulator") ||
        model.contains("android sdk built for") ||
        manufacturer.contains("genymotion") ||
        hardware == "goldfish" ||
        hardware == "ranchu" ||
        product.contains("sdk") ||
        product.contains("emulator") ||
        product.contains("vbox86p") ||
        device.contains("emulator") ||
        (brand.startsWith("generic") && device.startsWith("generic"))
}
