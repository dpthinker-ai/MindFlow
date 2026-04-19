package com.mindflow.app.data.ai

class AiTaskRouter(
    private val resolveMode: suspend () -> AiExecutionMode,
    private val onDeviceProvider: AiTaskProvider,
    private val cloudProvider: AiTaskProvider,
    private val traceRecorder: AiTaskTraceRecorder? = null,
) {
    suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): AiTaskResult<T> {
        val mode = resolveMode()
        val startedAt = System.currentTimeMillis()
        val providers = when (mode) {
            AiExecutionMode.AUTOMATIC -> when (request.automaticPreference) {
                AiAutomaticPreference.PREFER_ON_DEVICE -> listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD)
                AiAutomaticPreference.PREFER_CLOUD -> listOf(AiProvider.CLOUD, AiProvider.ON_DEVICE)
            }
            AiExecutionMode.ON_DEVICE_ONLY -> listOf(AiProvider.ON_DEVICE)
            AiExecutionMode.CLOUD_ONLY -> listOf(AiProvider.CLOUD)
        }

        var firstFailureReason: String? = null
        for ((index, provider) in providers.withIndex()) {
            val payload = when (provider) {
                AiProvider.ON_DEVICE -> onDeviceProvider.run(request)
                AiProvider.CLOUD -> cloudProvider.run(request)
            }
            if (payload == null) {
                if (firstFailureReason == null) {
                    firstFailureReason = "empty_payload"
                }
                continue
            }
            if (!request.validate(payload)) {
                if (firstFailureReason == null) {
                    firstFailureReason = "quality_gate_failed"
                }
                continue
            }
            val result = AiTaskResult(
                payload = payload,
                meta = AiTaskMeta(
                    providerUsed = provider,
                    fallbackOccurred = index > 0,
                    fallbackReason = if (index > 0) firstFailureReason else null,
                    latencyMs = System.currentTimeMillis() - startedAt,
                ),
            )
            traceRecorder?.append(request.type, result.meta)
            return result
        }

        throw AiTaskRoutingException(
            mode = mode,
            taskType = request.type,
            firstFailureReason = firstFailureReason,
        )
    }
}
