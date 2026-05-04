package com.mindflow.app.data.localmodel

import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.model.OnDeviceModelSettings
import com.mindflow.app.data.model.OnDeviceModelStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartupOnDeviceWarmUpPolicyTest {
    @Test
    fun readyAutomaticModelDoesNotWarmUpAtStartup() {
        val settings = readySettings(AiExecutionMode.AUTOMATIC)

        assertThat(StartupOnDeviceWarmUpPolicy.shouldWarmUpAtStartup(settings)).isFalse()
    }

    @Test
    fun readyOnDeviceOnlyModelDoesNotWarmUpAtStartup() {
        val settings = readySettings(AiExecutionMode.ON_DEVICE_ONLY)

        assertThat(StartupOnDeviceWarmUpPolicy.shouldWarmUpAtStartup(settings)).isFalse()
    }

    private fun readySettings(executionMode: AiExecutionMode): OnDeviceModelSettings =
        OnDeviceModelSettings(
            executionMode = executionMode,
            localModelPath = "/data/user/0/com.mindflow.app/files/local-models/gemma-4-E4B-it.litertlm",
            status = OnDeviceModelStatus.READY,
        )
}
