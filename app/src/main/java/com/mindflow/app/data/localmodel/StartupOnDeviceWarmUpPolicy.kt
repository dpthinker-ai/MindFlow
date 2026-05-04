package com.mindflow.app.data.localmodel

import com.mindflow.app.data.model.OnDeviceModelSettings

object StartupOnDeviceWarmUpPolicy {
    fun shouldWarmUpAtStartup(settings: OnDeviceModelSettings): Boolean = false
}
