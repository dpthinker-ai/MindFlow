package com.mindflow.app.data.settings

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import org.junit.Test

class OnDeviceExecutionModeCodecTest {
    @Test
    fun `legacy preferOnDevice true migrates to automatic`() {
        assertThat(OnDeviceExecutionModeCodec.decode(raw = null, legacyPreferOnDevice = true))
            .isEqualTo(AiExecutionMode.AUTOMATIC)
    }

    @Test
    fun `legacy preferOnDevice false migrates to cloud only`() {
        assertThat(OnDeviceExecutionModeCodec.decode(raw = null, legacyPreferOnDevice = false))
            .isEqualTo(AiExecutionMode.CLOUD_ONLY)
    }

    @Test
    fun `explicit stored mode wins over legacy flag`() {
        assertThat(OnDeviceExecutionModeCodec.decode(raw = "ON_DEVICE_ONLY", legacyPreferOnDevice = false))
            .isEqualTo(AiExecutionMode.ON_DEVICE_ONLY)
    }
}
