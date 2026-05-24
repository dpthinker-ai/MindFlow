package com.mindflow.app.data.ai

class AiTaskRouter(
    private val resolveMode: suspend () -> AiExecutionMode,
    private val onDeviceProvider: AiTaskProvider,
    private val cloudProvider: AiTaskProvider,
    private val traceRecorder: AiTaskTraceRecorder? = null,
    private val resolveRuntimeSettings: (suspend () -> AiRuntimeSettings)? = null,
    private val backgroundCloudUsageSnapshot: suspend () -> Pair<Int, Int> = { 0 to 0 },
) {
    suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): AiTaskResult<T> {
        val runtimeSettings = resolveRuntimeSettings?.invoke()
        val mode = runtimeSettings?.executionMode ?: resolveMode()
        val effectiveRuntimeSettings = runtimeSettings ?: AiRuntimeSettings(executionMode = mode)
        val policy = AiTaskPolicyRegistry.policyFor(request.type)
        val startedAt = System.currentTimeMillis()
        val requestedProviders = when (mode) {
            AiExecutionMode.AUTOMATIC -> when (request.automaticPreference) {
                AiAutomaticPreference.PREFER_ON_DEVICE -> if (request.allowProviderFallback) {
                    listOf(AiProvider.ON_DEVICE, AiProvider.CLOUD)
                } else {
                    listOf(AiProvider.ON_DEVICE)
                }
                AiAutomaticPreference.PREFER_CLOUD -> if (request.allowProviderFallback) {
                    listOf(AiProvider.CLOUD, AiProvider.ON_DEVICE)
                } else {
                    listOf(AiProvider.CLOUD)
                }
            }
            AiExecutionMode.ON_DEVICE_ONLY -> listOf(AiProvider.ON_DEVICE)
            AiExecutionMode.CLOUD_ONLY -> listOf(AiProvider.CLOUD)
        }
        val providers = requestedProviders.filter { provider -> provider in policy.providerOrder }

        var firstFailureReason: String? = null
        for ((index, provider) in providers.withIndex()) {
            if (provider == AiProvider.CLOUD) {
                val cloudDecision = canUseCloud(
                    request = request,
                    policy = policy,
                    runtimeSettings = effectiveRuntimeSettings,
                )
                if (!cloudDecision.allowed) {
                    firstFailureReason = cloudDecision.reason ?: "background_cloud_blocked"
                    continue
                }
            }
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

    private suspend fun canUseCloud(
        request: AiTaskRequest<*>,
        policy: AiTaskPolicy,
        runtimeSettings: AiRuntimeSettings,
    ): CloudUsageBudgetDecision {
        if (request.triggerMode == AiTriggerMode.FOREGROUND_USER_ACTION) {
            return if (runtimeSettings.cloudAllowedForInteractive) {
                CloudUsageBudgetDecision(allowed = true)
            } else {
                CloudUsageBudgetDecision(allowed = false, reason = "interactive_cloud_blocked")
            }
        }

        if (!runtimeSettings.cloudAllowedForBackground || !policy.allowBackgroundCloud) {
            return CloudUsageBudgetDecision(allowed = false, reason = "background_cloud_blocked")
        }

        val payloadPolicy = request.payloadPolicyOverride ?: policy.payloadPolicy
        val sensitivity = AiDataSensitivityClassifier.classify(request.input, payloadPolicy)
        if (sensitivity == AiDataSensitivity.HIGH) {
            return CloudUsageBudgetDecision(allowed = false, reason = "background_cloud_blocked")
        }

        val (requestsToday, tokensToday) = backgroundCloudUsageSnapshot()
        return CloudUsageBudgetGuard(runtimeSettings).canUseCloud(
            triggerMode = request.triggerMode,
            requestsToday = requestsToday,
            tokensToday = tokensToday,
        )
    }
}
