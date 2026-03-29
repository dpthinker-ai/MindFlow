package com.mindflow.app.data.topic

import com.mindflow.app.data.model.AiSettings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class AiFailureReason {
    CONFIG,
    AUTH,
    RATE_LIMIT,
    MODEL,
    NETWORK,
    SERVER,
    OTHER,
}

data class AiConnectionResult(
    val isConfiguredCorrectly: Boolean,
    val message: String,
)

sealed interface AiChatResult {
    data class Success(
        val content: String,
        val totalTokens: Int? = null,
    ) : AiChatResult
    data class Failure(
        val reason: AiFailureReason,
        val message: String,
        val isConfiguredCorrectly: Boolean = false,
    ) : AiChatResult
}

class AiServiceClient {
    suspend fun extractTopic(
        settings: AiSettings,
        content: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = content.take(1_500),
            systemPrompt = "Extract a short note topic in Chinese. Return only the topic, within 12 Chinese characters or 6 English words.",
            maxTokens = 128,
            temperature = 0.2,
            thinkingEnabled = false,
        )
    }

    suspend fun testConnection(settings: AiSettings): AiConnectionResult = withContext(Dispatchers.IO) {
        val result = requestChatCompletion(
            settings = settings,
            userPrompt = "请只回复：连接成功",
            systemPrompt = "You are testing model connectivity. Reply with the exact Chinese phrase provided by the user and nothing else.",
            maxTokens = 64,
            temperature = 0.1,
            thinkingEnabled = false,
        )

        return@withContext when (result) {
            is AiChatResult.Success -> AiConnectionResult(
                isConfiguredCorrectly = true,
                message = "连接成功，智谱模型可以正常调用",
            )
            is AiChatResult.Failure -> AiConnectionResult(
                isConfiguredCorrectly = result.isConfiguredCorrectly,
                message = result.message,
            )
        }
    }

    suspend fun extractTags(
        settings: AiSettings,
        content: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = content.take(1_500),
            systemPrompt = "Extract up to 3 concise Chinese tags from the note. Return only tags separated by commas. No numbering, no explanation.",
            maxTokens = 96,
            temperature = 0.2,
            thinkingEnabled = false,
        )
    }

    suspend fun classifyFolder(
        settings: AiSettings,
        content: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = content.take(2_000),
            systemPrompt = "Classify the note into exactly one folder. Allowed outputs only: work, life, project, health. Any fitness, exercise, workout, running, or body-training related content must be classified as health. Return only one lowercase word from the allowed outputs, with no explanation.",
            maxTokens = 24,
            temperature = 0.1,
            thinkingEnabled = false,
        )
    }

    suspend fun polishContent(
        settings: AiSettings,
        content: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = content.take(4_000),
            systemPrompt = "Rewrite the note in Chinese. Remove filler words and repetition. Keep the original meaning and do not invent new facts. Make it clearer, more structured, more formal, and easier to review. Prefer concise paragraphs, and use short bullet points only when they genuinely improve readability. Return only the polished note content.",
            maxTokens = 1_600,
            temperature = 0.3,
            thinkingEnabled = false,
        )
    }

    suspend fun generateDailyBrief(
        settings: AiSettings,
        contextSummary: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = contextSummary.take(5_000),
            systemPrompt = "You are a strategic thinking assistant for a personal idea system. Based on the note summary, produce exactly 2 short Chinese lines with high-value exploration directions. Do not repeat note titles or obvious facts. Focus on new angles, useful synthesis, technology innovation, product thinking, work improvement, health, or life progress. Each line should be actionable or thought-provoking. Return only the 2 lines, with no intro and no explanation.",
            maxTokens = 220,
            temperature = 0.6,
            thinkingEnabled = false,
        )
    }

    suspend fun generateNextAction(
        settings: AiSettings,
        contextSummary: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = contextSummary.take(3_000),
            systemPrompt = "You are a practical action coach for a personal idea system. Based on the note summary, produce exactly one concise Chinese next action. It must be specific, executable today or very soon, and easy to verify. Do not repeat the note. Do not explain. Return only one line.",
            maxTokens = 120,
            temperature = 0.4,
            thinkingEnabled = false,
        )
    }

    suspend fun generateWeeklyReview(
        settings: AiSettings,
        contextSummary: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = contextSummary.take(5_000),
            systemPrompt = "You are creating a weekly review for a personal idea system. Return exactly 4 concise Chinese lines in this order: 1) the strongest weekly theme, 2) the most valuable direction to keep pushing, 3) one old direction worth reviving or the key missing gap, 4) one synthesis or breakthrough suggestion. Do not number. Do not repeat note titles mechanically. Make the lines useful, concrete, and thought-provoking.",
            maxTokens = 320,
            temperature = 0.6,
            thinkingEnabled = false,
        )
    }

    suspend fun generateFusionSuggestions(
        settings: AiSettings,
        contextSummary: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = contextSummary.take(5_000),
            systemPrompt = "You are generating fusion suggestions for a personal idea system. Return exactly 2 concise Chinese lines. Each line must combine multiple themes, folders, or recurring ideas into one stronger exploration direction. Avoid restating existing notes. Make it forward-looking and useful.",
            maxTokens = 220,
            temperature = 0.65,
            thinkingEnabled = false,
        )
    }

    suspend fun generateThreadWorkspace(
        settings: AiSettings,
        contextSummary: String,
    ): AiChatResult = withContext(Dispatchers.IO) {
        requestChatCompletion(
            settings = settings,
            userPrompt = contextSummary.take(5_000),
            systemPrompt = "You are generating a thread workspace for a personal idea system. Return exactly 3 concise Chinese lines in this order: 1) a summary of the thread's current direction, 2) the main bottleneck or unanswered question, 3) the single most valuable next step. Do not number the lines. Do not repeat note titles mechanically. Be concrete and useful.",
            maxTokens = 280,
            temperature = 0.55,
            thinkingEnabled = false,
        )
    }

    private fun requestChatCompletion(
        settings: AiSettings,
        userPrompt: String,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Double,
        thinkingEnabled: Boolean,
    ): AiChatResult {
        if (!settings.isConfigured) {
            return AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "请先填好 Base URL、Model 和 API Key",
            )
        }
        var lastFailure: AiChatResult.Failure? = null
        val authCandidates = buildAuthCandidates(settings.apiKey.trim())
        authCandidates.forEachIndexed { index, authHeader ->
            when (
                val result = executeChatCompletion(
                    settings = settings,
                    authHeader = authHeader,
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    thinkingEnabled = thinkingEnabled,
                )
            ) {
                is AiChatResult.Success -> return result
                is AiChatResult.Failure -> {
                    lastFailure = result
                    val canRetryAuth = result.reason == AiFailureReason.AUTH && index < authCandidates.lastIndex
                    if (!canRetryAuth) {
                        return result
                    }
                }
            }
        }

        return lastFailure ?: AiChatResult.Failure(
            reason = AiFailureReason.OTHER,
            message = "连接模型服务失败，请稍后再试",
        )
    }

    private fun executeChatCompletion(
        settings: AiSettings,
        authHeader: String,
        userPrompt: String,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Double,
        thinkingEnabled: Boolean,
    ): AiChatResult {
        val connection = runCatching {
            URL("${settings.baseUrl.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection
        }.getOrElse {
            return AiChatResult.Failure(
                reason = AiFailureReason.CONFIG,
                message = "Base URL 格式不正确，请检查地址",
            )
        }

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", authHeader)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.doOutput = true

            val payload = JSONObject()
                .put("model", settings.model.trim())
                .put("temperature", temperature)
                .put("max_tokens", maxTokens)
                .put("stream", false)
                .put(
                    "thinking",
                    JSONObject().put("type", if (thinkingEnabled) "enabled" else "disabled")
                )
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", systemPrompt)
                        )
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", userPrompt)
                        )
                )

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.let { input ->
                BufferedReader(InputStreamReader(input)).use { reader -> reader.readText() }
            }.orEmpty()

            if (responseCode !in 200..299) {
                return parseFailure(responseCode, responseBody)
            }

            val message = JSONObject(responseBody)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
            val content = extractMessageContent(message)

            return if (content.isNullOrBlank()) {
                AiChatResult.Failure(
                    reason = AiFailureReason.SERVER,
                    message = "模型已响应，但没有返回可见文本，请稍后再试",
                    isConfiguredCorrectly = true,
                )
            } else {
                AiChatResult.Success(
                    content = content,
                    totalTokens = JSONObject(responseBody)
                        .optJSONObject("usage")
                        ?.optInt("total_tokens")
                        ?.takeIf { it > 0 },
                )
            }
        } catch (_: SocketTimeoutException) {
            return AiChatResult.Failure(
                reason = AiFailureReason.NETWORK,
                message = "连接超时，请检查网络或稍后再试",
            )
        } catch (_: Exception) {
            return AiChatResult.Failure(
                reason = AiFailureReason.NETWORK,
                message = "连接模型服务失败，请检查网络、Base URL 或账号状态",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun buildAuthCandidates(apiKey: String): List<String> {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return emptyList()

        val rawKey = trimmed.removePrefix("Bearer").trim()
        return listOf(
            rawKey,
            "Bearer $rawKey",
        ).distinct()
    }

    private fun extractMessageContent(message: JSONObject?): String? {
        if (message == null) return null

        val rawContent = message.opt("content")
        return when (rawContent) {
            is String -> rawContent.trim().takeIf { it.isNotBlank() }
            is JSONArray -> {
                buildString {
                    for (index in 0 until rawContent.length()) {
                        val item = rawContent.opt(index)
                        when (item) {
                            is String -> append(item)
                            is JSONObject -> {
                                val text = item.optString("text")
                                if (text.isNotBlank()) {
                                    append(text)
                                }
                            }
                        }
                    }
                }.trim().takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun parseFailure(
        httpCode: Int,
        responseBody: String,
    ): AiChatResult.Failure {
        val root = runCatching { JSONObject(responseBody) }.getOrNull()
        val errorObject = root?.optJSONObject("error")
        val errorCode = errorObject?.optString("code").orEmpty()
        val errorMessage = errorObject?.optString("message").orEmpty()

        return when {
            errorCode == "1302" || errorMessage.contains("速率限制") || errorMessage.contains("rate limit", ignoreCase = true) -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.RATE_LIMIT,
                    message = "已连通智谱，但当前触发了速率限制，请稍后再试",
                    isConfiguredCorrectly = true,
                )
            }

            httpCode == 401 -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.AUTH,
                    message = "鉴权失败，请检查 API Key 是否正确",
                )
            }

            httpCode == 403 -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.AUTH,
                    message = "当前账号没有权限访问这个模型，请检查账号状态或模型权限",
                )
            }

            httpCode == 404 -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.CONFIG,
                    message = "接口地址不可用，请检查 Base URL 是否正确",
                )
            }

            httpCode == 400 && errorMessage.contains("model", ignoreCase = true) -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.MODEL,
                    message = "模型名无效，请检查 Model 是否填写正确",
                )
            }

            httpCode == 400 -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.CONFIG,
                    message = "请求参数无效，请检查 Base URL、Model 和请求内容",
                )
            }

            httpCode == 429 -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.RATE_LIMIT,
                    message = "已连通模型服务，但当前请求过快或账户额度受限，请稍后再试",
                    isConfiguredCorrectly = true,
                )
            }

            errorMessage.isNotBlank() -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.SERVER,
                    message = "请求失败：$errorMessage",
                )
            }

            else -> {
                AiChatResult.Failure(
                    reason = AiFailureReason.SERVER,
                    message = "请求失败：HTTP $httpCode",
                )
            }
        }
    }
}
