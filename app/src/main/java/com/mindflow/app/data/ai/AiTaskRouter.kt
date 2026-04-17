package com.mindflow.app.data.ai

class AiTaskRouter(
    private val resolveMode: suspend () -> AiExecutionMode,
    private val onDeviceProvider: AiTaskProvider,
    private val cloudProvider: AiTaskProvider,
) {
    suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): AiTaskResult<T> {
        val mode = resolveMode()
        val startedAt = System.currentTimeMillis()
        val providers = when (mode) {
            AiExecutionMode.AUTOMATIC -> listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD)
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
            return AiTaskResult(
                payload = payload,
                meta = AiTaskMeta(
                    providerUsed = provider,
                    fallbackOccurred = index > 0,
                    fallbackReason = if (index > 0) firstFailureReason else null,
                    latencyMs = System.currentTimeMillis() - startedAt,
                ),
            )
        }

        throw AiTaskRoutingException(
            mode = mode,
            taskType = request.type,
            firstFailureReason = firstFailureReason,
        )
    }
}
