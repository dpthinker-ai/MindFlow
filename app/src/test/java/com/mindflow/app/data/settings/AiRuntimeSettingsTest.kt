package com.mindflow.app.data.settings

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiRuntimeSettings
import com.mindflow.app.data.ai.CloudNotificationMode
import org.junit.Test

class AiRuntimeSettingsTest {
    @Test
    fun defaultsAllowLowFrequencyBackgroundCloudWithAutomaticMode() {
        val settings = AiRuntimeSettings()

        assertThat(settings.executionMode).isEqualTo(AiExecutionMode.AUTOMATIC)
        assertThat(settings.cloudAllowedForInteractive).isTrue()
        assertThat(settings.cloudAllowedForBackground).isTrue()
        assertThat(settings.notifyOnCloudUse).isTrue()
        assertThat(settings.backgroundCloudNotificationMode).isEqualTo(CloudNotificationMode.LOW_FREQUENCY)
        assertThat(settings.dailyBackgroundCloudRequestLimit).isEqualTo(30)
        assertThat(settings.dailyBackgroundTokenSoftLimit).isEqualTo(30_000)
    }

    @Test
    fun notificationModeFallsBackToLowFrequencyForUnknownRawValue() {
        assertThat(CloudNotificationMode.fromRaw("LOUD")).isEqualTo(CloudNotificationMode.LOW_FREQUENCY)
    }
}
