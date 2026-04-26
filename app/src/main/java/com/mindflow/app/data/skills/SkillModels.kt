package com.mindflow.app.data.skills

data class SkillInstructions(
    val name: String,
    val description: String,
    val body: String,
)

enum class SkillExecutorType {
    JS,
    NATIVE,
}

enum class SkillOutputType {
    TEXT,
    WEBVIEW,
    IMAGE,
    CARD,
}

data class SkillRemoteConfig(
    val signatureRequired: Boolean = false,
)

data class SkillManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val entry: String,
    val executor: SkillExecutorType = SkillExecutorType.JS,
    val output: Set<SkillOutputType> = setOf(SkillOutputType.TEXT),
    val permissions: Set<String> = emptySet(),
    val nativeApis: Set<String> = emptySet(),
    val remote: SkillRemoteConfig = SkillRemoteConfig(),
)

data class SkillPackage(
    val manifest: SkillManifest,
    val instructions: SkillInstructions,
    val assetBasePath: String,
)

enum class SkillModelPass {
    NONE,
    SUMMARIZE_COMPACT_RESULT,
    ANALYZE_RECORDS,
}

data class SkillInvocation(
    val skillId: String,
    val scriptName: String = "index.html",
    val data: String = "{}",
    val modelPass: SkillModelPass = SkillModelPass.NONE,
)

data class SkillWebViewSpec(
    val url: String,
    val iframe: Boolean = false,
    val aspectRatio: Float = 1.333f,
)

data class SkillImageSpec(
    val base64: String,
)

data class SkillResult(
    val result: String? = null,
    val error: String? = null,
    val image: SkillImageSpec? = null,
    val webview: SkillWebViewSpec? = null,
    val metadata: Map<String, String> = emptyMap(),
    val dataJson: String? = null,
) {
    val isSuccess: Boolean
        get() = error.isNullOrBlank()

    companion object {
        fun failure(message: String): SkillResult = SkillResult(error = message)
    }
}
