package com.mindflow.app.data.skills

interface NativeToolBridge {
    suspend fun invoke(
        apiName: String,
        payloadJson: String,
    ): String

    fun canInvoke(apiName: String): Boolean
}

class UnsupportedNativeToolBridge : NativeToolBridge {
    override suspend fun invoke(
        apiName: String,
        payloadJson: String,
    ): String = error("Native tool bridge is not attached for api=$apiName")

    override fun canInvoke(apiName: String): Boolean = false
}

