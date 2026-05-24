package com.mindflow.app.data.settings

import com.mindflow.app.data.ai.AiExecutionMode

object OnDeviceExecutionModeCodec {
    fun decode(raw: String?, legacyPreferOnDevice: Boolean?): AiExecutionMode {
        raw?.trim()?.takeIf { it.isNotBlank() }?.let { candidate ->
            return runCatching { AiExecutionMode.valueOf(candidate) }.getOrElse { AiExecutionMode.AUTOMATIC }
        }
        return when (legacyPreferOnDevice) {
            true -> AiExecutionMode.AUTOMATIC
            false -> AiExecutionMode.CLOUD_ONLY
            null -> AiExecutionMode.AUTOMATIC
        }
    }
}
